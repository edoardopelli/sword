package org.cheetah.sword.service;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.cheetah.sword.events.Events.GenerateRequestedEvent;
import org.cheetah.sword.events.Events.GenerationCompletedEvent;
import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.FkMode;
import org.cheetah.sword.model.RelationFetch;
import org.cheetah.sword.model.SchemaSelection;
import org.cheetah.sword.util.SqlTypeMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates entities, optional DTOs, and optional MapStruct mappers.
 *
 * High-level flow:
 * 1. Read database metadata for all tables in selected catalog/schema.
 * 2. Build a per-table model (columns, PKs, single-column FKs).
 * 3. For each table:
 *    - Generate @Entity class (+ @EmbeddedId when composite PK).
 *    - Optionally generate DTO class and mapper interface.
 */
@Service
public class GenerationService {

    private final MetadataService metadataService;
    private final NamingConfigService namingConfigService;
    private final ApplicationEventPublisher publisher;

    public GenerationService(MetadataService metadataService,
                             NamingConfigService namingConfigService,
                             ApplicationEventPublisher publisher) {
        this.metadataService = metadataService;
        this.namingConfigService = namingConfigService;
        this.publisher = publisher;
    }

    @EventListener(GenerateRequestedEvent.class)
    public void onGenerate(GenerateRequestedEvent event) {
        ConnectionConfig cfg = event.config();
        SchemaSelection selection = event.selection();
        int generated = 0;

        try (Connection connection = metadataService.open(cfg)) {
            String catalog = selection.catalog();
            String schema = selection.schema();

            System.out.printf("%n→ Scanning catalog=%s schema=%s ...%n", nvl(catalog), nvl(schema));
            List<String> tables = metadataService.listTables(connection, catalog, schema);
            System.out.printf("   Found %d table(s).%n", tables.size());

            String entityPackage = normalizePackage(cfg.getBasePackage());
            String dtoPackage = siblingPackage(entityPackage, "dtos");
            String mapperPackage = siblingPackage(entityPackage, "mappers");
            Path rootPath = cfg.getOutputPath();
            Files.createDirectories(rootPath);

            System.out.printf("   Output root     : %s%n", rootPath.toAbsolutePath());
            System.out.printf("   Entity package  : %s%n", entityPackage);
            System.out.printf("   DTO package     : %s%n", dtoPackage);
            System.out.printf("   Mapper package  : %s%n", mapperPackage);
            System.out.printf("   FK mode         : %s%n", cfg.getFkMode());
            System.out.printf("   Relation fetch  : %s%n", cfg.getRelationFetch());
            System.out.printf("   Generate DTO    : %s%n", cfg.getGenerateDto());

            DatabaseMetaData metaData = connection.getMetaData();
            String dbProduct = metaData.getDatabaseProductName();

            // Phase 1: build model for all tables
            List<EntityModel> models = new ArrayList<>();
            for (String table : tables) {
                EntityModel model = loadEntityModel(
                        metaData,
                        catalog,
                        schema,
                        table,
                        dbProduct
                );
                models.add(model);
            }

            // Phase 2: generate per-table code
            for (EntityModel model : models) {
                writeEntityFiles(
                        entityPackage,
                        dtoPackage,
                        mapperPackage,
                        rootPath,
                        model,
                        models,
                        dbProduct,
                        cfg.getFkMode(),
                        cfg.getRelationFetch(),
                        cfg.getGenerateDto(),
                        metaData
                );
                generated++;
            }

            publisher.publishEvent(new GenerationCompletedEvent(generated, rootPath));
            System.out.printf("✓ Generation complete. %d entit%s created.%n",
                    generated, generated == 1 ? "y" : "ies");
        } catch (Exception e) {
            System.err.println("Generation failed:");
            e.printStackTrace();
        }
    }

    /**
     * Reads table metadata (columns, PK columns, single-column FKs).
     */
    private EntityModel loadEntityModel(DatabaseMetaData md,
                                        String catalog,
                                        String schema,
                                        String table,
                                        String dbProduct) throws SQLException {

        Map<String, ColumnModel> columns = new LinkedHashMap<>();
        Set<String> pkCols = new LinkedHashSet<>();

        // Columns
        try (ResultSet rs = md.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                String isAuto = nullSafe(rs.getString("IS_AUTOINCREMENT"));
                String columnDef = rs.getString("COLUMN_DEF");
                boolean autoIncrement = detectAutoIncrement(dbProduct, isAuto, typeName, columnDef);
                columns.put(name, new ColumnModel(name, dataType, typeName, nullable, columnDef, autoIncrement));
            }
        }

        // Primary keys
        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                pkCols.add(rs.getString("COLUMN_NAME"));
            }
        }

        // Imported foreign keys
        Map<String, List<ImportedFkRow>> fkGroups = new LinkedHashMap<>();
        try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                int keySeq = rs.getInt("KEY_SEQ");

                if (fkName == null || fkName.isBlank()) {
                    fkName = pkTable + "__" + fkColumn;
                }

                ImportedFkRow row = new ImportedFkRow(fkName, fkColumn, pkTable, pkColumn, keySeq);
                fkGroups.computeIfAbsent(fkName, k -> new ArrayList<>()).add(row);
            }
        }

        // Keep only single-column FKs
        List<SimpleFkModel> simpleFks = new ArrayList<>();
        for (List<ImportedFkRow> rows : fkGroups.values()) {
            if (rows.size() == 1) {
                ImportedFkRow r = rows.get(0);
                simpleFks.add(new SimpleFkModel(r.localColumn(), r.pkTable(), r.pkColumn()));
            }
        }

        return new EntityModel(
                catalog,
                schema,
                table,
                columns,
                pkCols,
                simpleFks
        );
    }

    /**
     * Writes:
     * - Entity class (and EmbeddedId if needed)
     * - Optional DTO and MapStruct mapper
     */
    private void writeEntityFiles(String entityPackage,
                                  String dtoPackage,
                                  String mapperPackage,
                                  Path rootPath,
                                  EntityModel model,
                                  List<EntityModel> allModels,
                                  String dbProduct,
                                  FkMode fkMode,
                                  RelationFetch relationFetch,
                                  boolean generateDto,
                                  DatabaseMetaData md) throws IOException {

        String entitySimpleName = namingConfigService.resolveEntityName(model.table());

        boolean compositePk = model.pkCols().size() > 1;
        String idClassName = entitySimpleName + "Id";

        String nowIso = OffsetDateTime.now().toString();
        AnnotationSpec generatedAnn = AnnotationSpec.builder(ClassName.get("jakarta.annotation", "Generated"))
                .addMember("value", "$S", "S.W.O.R.D.")
                .addMember("date", "$S", nowIso)
                .build();

        AnnotationSpec toStringAnn = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        AnnotationSpec eqHashAnn = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        TypeSpec.Builder entity = TypeSpec.classBuilder(entitySimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("jakarta.persistence", "Entity"))
                .addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Table"))
                        .addMember("name", "$S", model.table())
                        .build())
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(toStringAnn)
                .addAnnotation(eqHashAnn)
                .addAnnotation(generatedAnn);

        // Composite PK
        if (compositePk) {
            ClassName idClass = ClassName.get(entityPackage, idClassName);

            FieldSpec.Builder idField = FieldSpec.builder(idClass, "id", Modifier.PRIVATE)
                    .addAnnotation(ClassName.get("jakarta.persistence", "EmbeddedId"))
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));

            entity.addField(idField.build());

            writeEmbeddedId(entityPackage, rootPath, idClassName, model, dbProduct, generatedAnn);
        }

        /*
         * Child-side relationships (ManyToOne / OneToOne).
         * Only if fkMode == RELATION.
         */
        Set<String> handledFkColumns = new HashSet<>();
        List<FieldSpec> relationFieldsChildSide = new ArrayList<>();

        if (fkMode == FkMode.RELATION) {
            for (SimpleFkModel fk : model.simpleFks()) {
                String localCol = fk.localColumn();

                if (model.pkCols().contains(localCol)) {
                    continue;
                }

                String targetEntityName = namingConfigService.resolveEntityName(fk.targetTable());
                ClassName targetType = ClassName.get(entityPackage, targetEntityName);
                String relFieldName = lowerFirst(targetEntityName);

                boolean unique = isColumnUnique(
                        md,
                        model.catalog(),
                        model.schema(),
                        model.table(),
                        localCol
                );

                AnnotationSpec.Builder relationAnn;
                if (unique) {
                    relationAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "OneToOne"))
                            .addMember("fetch", "$T.$L",
                                    ClassName.get("jakarta.persistence", "FetchType"),
                                    relationFetch == RelationFetch.EAGER ? "EAGER" : "LAZY");
                } else {
                    relationAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "ManyToOne"))
                            .addMember("fetch", "$T.$L",
                                    ClassName.get("jakarta.persistence", "FetchType"),
                                    relationFetch == RelationFetch.EAGER ? "EAGER" : "LAZY");
                }

                AnnotationSpec joinColAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "JoinColumn"))
                        .addMember("name", "$S", localCol)
                        .build();

                FieldSpec.Builder relField = FieldSpec.builder(targetType, relFieldName, Modifier.PRIVATE)
                        .addAnnotation(relationAnn.build())
                        .addAnnotation(joinColAnn);

                relationFieldsChildSide.add(relField.build());
                handledFkColumns.add(localCol);
            }
        }

        /*
         * Scalar fields:
         * - all physical columns except those replaced by relation fields (FkMode.RELATION)
         * - skip PK parts if compositePk, because EmbeddedId already has them
         */
        for (ColumnModel col : model.columns().values()) {

            boolean isSimplePkColumn = (!compositePk && model.pkCols().contains(col.name()));
            boolean skipForRelation = handledFkColumns.contains(col.name()) && !isSimplePkColumn;
            if (skipForRelation) {
                continue;
            }

            if (compositePk && model.pkCols().contains(col.name())) {
                continue;
            }

            String fieldName = namingConfigService.resolveColumnName(model.table(), col.name());

            TypeName javaType = SqlTypeMapper.map(
                    col.dataType(),
                    col.typeName(),
                    col.nullable(),
                    dbProduct
            );

            FieldSpec.Builder field = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE);

            if (isSimplePkColumn) {
                field.addAnnotation(ClassName.get("jakarta.persistence", "Id"));
                field.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
                field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));

                if (col.autoIncrement()) {
                    if (isPostgres(dbProduct)
                            && col.columnDef() != null
                            && col.columnDef().toLowerCase().contains("nextval(")) {

                        String seqName = extractPgSequenceName(col.columnDef());
                        String genName = (model.table() + "_" + col.name() + "_seq_gen")
                                .replaceAll("[^A-Za-z0-9_]", "_");

                        if (seqName != null && !seqName.isBlank()) {
                            AnnotationSpec seqGen = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "SequenceGenerator"))
                                    .addMember("name", "$S", genName)
                                    .addMember("sequenceName", "$S", seqName)
                                    .addMember("allocationSize", "$L", 1)
                                    .build();
                            entity.addAnnotation(seqGen);

                            AnnotationSpec genVal = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "GeneratedValue"))
                                    .addMember("strategy", "$T.SEQUENCE", ClassName.get("jakarta.persistence", "GenerationType"))
                                    .addMember("generator", "$S", genName)
                                    .build();
                            field.addAnnotation(genVal);
                        } else {
                            AnnotationSpec genVal = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "GeneratedValue"))
                                    .addMember("strategy", "$T.IDENTITY", ClassName.get("jakarta.persistence", "GenerationType"))
                                    .build();
                            field.addAnnotation(genVal);
                        }
                    } else {
                        AnnotationSpec genVal = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "GeneratedValue"))
                                .addMember("strategy", "$T.IDENTITY", ClassName.get("jakarta.persistence", "GenerationType"))
                                .build();
                        field.addAnnotation(genVal);
                    }
                }
            } else {
                if (!javaType.toString().equals("byte[]")) {
                    field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));
                }
            }

            AnnotationSpec.Builder colAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
                    .addMember("name", "$S", col.name());

            String tn = col.typeName() == null ? "" : col.typeName().toLowerCase();
            if ((tn.equals("jsonb") || tn.equals("json")) && isPostgres(dbProduct)) {
                colAnn.addMember("columnDefinition", "$S", tn);
                field.addAnnotation(
                        AnnotationSpec.builder(ClassName.get("org.hibernate.annotations", "JdbcTypeCode"))
                                .addMember("value", "$T.JSON", ClassName.get("org.hibernate.type", "SqlTypes"))
                                .build()
                );
            }

            field.addAnnotation(colAnn.build());
            entity.addField(field.build());
        }

        // Add relation fields on child side
        for (FieldSpec relField : relationFieldsChildSide) {
            entity.addField(relField);
        }

        /*
         * Parent-side inverse relationships:
         * - @OneToOne(mappedBy="...") for unique FK
         * - @OneToMany(mappedBy="...") Set<ChildEntity> for non-unique FK
         * These are only generated if fkMode == RELATION.
         */
        if (fkMode == FkMode.RELATION) {
            List<FieldSpec> inverseFields = buildInverseRelationFields(
                    model,
                    allModels,
                    entityPackage,
                    md,
                    relationFetch
            );
            for (FieldSpec invField : inverseFields) {
                entity.addField(invField);
            }
        }

        // Write entity source
        JavaFile.builder(entityPackage, entity.build())
                .build()
                .writeTo(rootPath);

        // If composite PK, EmbeddedId source is already handled above.

        /*
         * DTO + Mapper generation (optional).
         * DTOs are generated under <parent>.dtos
         * Mappers are generated under <parent>.mappers
         */
        if (generateDto) {
            writeDtoAndMapper(
                    entityPackage,
                    dtoPackage,
                    mapperPackage,
                    rootPath,
                    model,
                    dbProduct,
                    entitySimpleName,
                    generatedAnn
            );
        }
    }

    /**
     * Generates:
     * 1. <EntityName>Dto
     * 2. <EntityName>Mapper
     *
     * DTO rules:
     * - One field per physical DB column.
     * - FK columns stay as scalar ids.
     * - No JPA annotations.
     * - Collections are not included.
     *
     * Mapper rules:
     * - @Mapper(componentModel = "spring")
     * - toDto(Entity -> Dto)
     * - toEntity(Dto -> Entity)
     *
     * Note: In RELATION mode, entity fields like "Customer customer" will not
     * be populated by toEntity() because the DTO only carries "customerId".
     * Those relation fields remain null unless later enriched manually.
     */
    private void writeDtoAndMapper(String entityPackage,
                                   String dtoPackage,
                                   String mapperPackage,
                                   Path rootPath,
                                   EntityModel model,
                                   String dbProduct,
                                   String entitySimpleName,
                                   AnnotationSpec generatedAnn) throws IOException {

        String dtoSimpleName = entitySimpleName + "Dto";

        AnnotationSpec toStringAnn = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        AnnotationSpec eqHashAnn = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        // DTO class
        TypeSpec.Builder dto = TypeSpec.classBuilder(dtoSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(toStringAnn)
                .addAnnotation(eqHashAnn)
                .addAnnotation(generatedAnn);

        // Add every DB column as a DTO field
        for (ColumnModel col : model.columns().values()) {
            String fieldName = namingConfigService.resolveColumnName(model.table(), col.name());

            TypeName javaType = SqlTypeMapper.map(
                    col.dataType(),
                    col.typeName(),
                    col.nullable(),
                    dbProduct
            );

            FieldSpec.Builder f = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE)
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));

            dto.addField(f.build());
        }

        JavaFile.builder(dtoPackage, dto.build())
                .build()
                .writeTo(rootPath);

        /*
         * Mapper interface.
         * Example generated:
         *
         * @Mapper(componentModel = "spring")
         * @Generated(...)
         * public interface IncidentMapper {
         *     IncidentDto toDto(Incident entity);
         *     Incident toEntity(IncidentDto dto);
         * }
         */
        AnnotationSpec mapperAnn = AnnotationSpec.builder(ClassName.get("org.mapstruct", "Mapper"))
                .addMember("componentModel", "$S", "spring")
                .build();

        MethodSpec toDto = MethodSpec.methodBuilder("toDto")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(ClassName.get(dtoPackage, dtoSimpleName))
                .addParameter(ClassName.get(entityPackage, entitySimpleName), "entity")
                .build();

        MethodSpec toEntity = MethodSpec.methodBuilder("toEntity")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(ClassName.get(entityPackage, entitySimpleName))
                .addParameter(ClassName.get(dtoPackage, dtoSimpleName), "dto")
                .build();

        TypeSpec mapper = TypeSpec.interfaceBuilder(entitySimpleName + "Mapper")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(mapperAnn)
                .addAnnotation(generatedAnn)
                .addMethod(toDto)
                .addMethod(toEntity)
                .build();

        JavaFile.builder(mapperPackage, mapper)
                .build()
                .writeTo(rootPath);
    }

    /**
     * Builds inverse relation fields for the parent side of a relationship.
     *
     * UNIQUE FK in child:
     *   @OneToOne(mappedBy="...", fetch=FetchType.<cfg>)
     *   ChildEntity child;
     *
     * NON-UNIQUE FK in child:
     *   @OneToMany(mappedBy="...", fetch=FetchType.LAZY)
     *   Set<ChildEntity> children;
     *
     * Collections are always LAZY.
     */
    private List<FieldSpec> buildInverseRelationFields(EntityModel parentModel,
                                                       List<EntityModel> allModels,
                                                       String entityPackage,
                                                       DatabaseMetaData md,
                                                       RelationFetch relationFetch) {

        List<FieldSpec> fields = new ArrayList<>();
        Set<String> usedFieldNames = new HashSet<>();

        String parentEntityName = namingConfigService.resolveEntityName(parentModel.table());
        String mappedByNameOnChild = lowerFirst(parentEntityName);

        for (EntityModel childModel : allModels) {
            if (childModel == parentModel) continue;

            for (SimpleFkModel fk : childModel.simpleFks()) {
                if (!fk.targetTable().equalsIgnoreCase(parentModel.table())) {
                    continue;
                }

                String childEntityName = namingConfigService.resolveEntityName(childModel.table());
                ClassName childType = ClassName.get(entityPackage, childEntityName);

                boolean unique = isColumnUnique(
                        md,
                        childModel.catalog(),
                        childModel.schema(),
                        childModel.table(),
                        fk.localColumn()
                );

                if (unique) {
                    // One-to-one back reference
                    String fieldName = uniquify(
                            lowerFirst(childEntityName),
                            usedFieldNames
                    );

                    AnnotationSpec oneToOneBack = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "OneToOne"))
                            .addMember("mappedBy", "$S", mappedByNameOnChild)
                            .addMember("fetch", "$T.$L",
                                    ClassName.get("jakarta.persistence", "FetchType"),
                                    relationFetch == RelationFetch.EAGER ? "EAGER" : "LAZY")
                            .build();

                    FieldSpec.Builder f = FieldSpec.builder(childType, fieldName, Modifier.PRIVATE)
                            .addAnnotation(oneToOneBack);
                    fields.add(f.build());
                } else {
                    // One-to-many back reference
                    String pluralBase = lowerFirst(childEntityName) + "s";
                    String fieldName = uniquify(pluralBase, usedFieldNames);

                    ParameterizedTypeName setOfChild =
                            ParameterizedTypeName.get(
                                    ClassName.get(Set.class),
                                    childType
                            );

                    AnnotationSpec oneToManyBack = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "OneToMany"))
                            .addMember("mappedBy", "$S", mappedByNameOnChild)
                            .addMember("fetch", "$T.LAZY", ClassName.get("jakarta.persistence", "FetchType"))
                            .build();

                    FieldSpec.Builder f = FieldSpec.builder(setOfChild, fieldName, Modifier.PRIVATE)
                            .addAnnotation(oneToManyBack);
                    fields.add(f.build());
                }
            }
        }

        return fields;
    }

    /**
     * Generates the <EntityName>Id embeddable class for composite primary keys.
     */
    private void writeEmbeddedId(String entityPackage,
                                 Path rootPath,
                                 String idClassName,
                                 EntityModel model,
                                 String dbProduct,
                                 AnnotationSpec generatedAnn) throws IOException {

        AnnotationSpec toStringAnn = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        AnnotationSpec eqHashAnn = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        TypeSpec.Builder emb = TypeSpec.classBuilder(idClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("jakarta.persistence", "Embeddable"))
                .addSuperinterface(ClassName.get("java.io", "Serializable"))
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(toStringAnn)
                .addAnnotation(eqHashAnn)
                .addAnnotation(generatedAnn);

        for (String pkCol : model.pkCols()) {
            ColumnModel col = model.columns().get(pkCol);

            String fieldName = namingConfigService.resolveColumnName(model.table(), pkCol);

            TypeName javaType = SqlTypeMapper.map(
                    col.dataType(),
                    col.typeName(),
                    col.nullable(),
                    dbProduct
            );

            FieldSpec f = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE)
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
                            .addMember("name", "$S", pkCol)
                            .build())
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"))
                    .build();

            emb.addField(f);
        }

        JavaFile.builder(entityPackage, emb.build())
                .build()
                .writeTo(rootPath);
    }

    /**
     * Detects auto-generated identity behavior for the given column.
     */
    private boolean detectAutoIncrement(String dbProduct,
                                        String isAuto,
                                        String typeName,
                                        String columnDef) {
        String db = dbProduct == null ? "" : dbProduct.toLowerCase();
        String tn = typeName == null ? "" : typeName.toLowerCase();
        String def = columnDef == null ? "" : columnDef.toLowerCase();

        if ("yes".equalsIgnoreCase(isAuto)) return true;
        if (db.contains("postgres") && def.contains("nextval(")) return true;
        if (db.contains("sql server") && (tn.contains("identity") || def.contains("identity"))) return true;
        if (db.contains("h2") && (tn.contains("identity") || def.contains("auto_increment") || def.contains("identity"))) return true;
        if (db.contains("db2") && def.contains("generated") && def.contains("identity")) return true;
        if ((db.contains("mysql") || db.contains("mariadb")) && def.contains("auto_increment")) return true;
        return false;
    }

    /**
     * Verifies if a column participates in a UNIQUE index.
     * Used to distinguish @OneToOne vs @ManyToOne.
     */
    private boolean isColumnUnique(DatabaseMetaData md,
                                   String catalog,
                                   String schema,
                                   String table,
                                   String column) {
        try (ResultSet rs = md.getIndexInfo(catalog, schema, table, true, false)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                if (colName != null && column.equalsIgnoreCase(colName)) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    /**
     * Extracts sequence name from a PostgreSQL-style nextval('sequence_name'::regclass).
     */
    private String extractPgSequenceName(String columnDef) {
        if (columnDef == null) return null;
        Matcher m = Pattern.compile("nextval\\('([^']+)'").matcher(columnDef);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private boolean isPostgres(String dbProduct) {
        return dbProduct != null && dbProduct.toLowerCase().contains("postgres");
    }

    /**
     * Returns a unique field name. If already taken, appends numeric suffixes.
     */
    private String uniquify(String candidate, Set<String> used) {
        String base = candidate;
        int idx = 2;
        while (used.contains(candidate)) {
            candidate = base + idx;
            idx++;
        }
        used.add(candidate);
        return candidate;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String nvl(String s) {
        return s == null ? "(null)" : s;
    }

    private static String normalizePackage(String raw) {
        if (raw == null) return "";
        String p = raw.replace('/', '.')
                .replace('\\', '.')
                .trim();
        while (p.contains("..")) {
            p = p.replace("..", ".");
        }
        if (p.startsWith(".")) {
            p = p.substring(1);
        }
        if (p.endsWith(".")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * Builds a sibling-level package name.
     * Example:
     *   entityPackage = "org.cheetah.entities"
     *   siblingPackage(entityPackage, "dtos") -> "org.cheetah.dtos"
     *   siblingPackage(entityPackage, "mappers") -> "org.cheetah.mappers"
     *
     * If there is no dot in the base package, fallback is just the sibling name.
     */
    private static String siblingPackage(String entityPackage, String name) {
        if (entityPackage == null || entityPackage.isBlank()) {
            return name;
        }
        int idx = entityPackage.lastIndexOf('.');
        if (idx <= 0) {
            return name;
        }
        String parent = entityPackage.substring(0, idx);
        return parent + "." + name;
    }

    private static String lowerFirst(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    /*
     * Internal metadata view of a table.
     */
    record EntityModel(String catalog,
                       String schema,
                       String table,
                       Map<String, ColumnModel> columns,
                       Set<String> pkCols,
                       List<SimpleFkModel> simpleFks) {
    }

    /*
     * Internal view of a column.
     */
    record ColumnModel(String name,
                       int dataType,
                       String typeName,
                       boolean nullable,
                       String columnDef,
                       boolean autoIncrement) {
    }

    /*
     * Single-column FK model.
     */
    record SimpleFkModel(String localColumn,
                         String targetTable,
                         String targetColumn) {
    }

    /*
     * Raw row from DatabaseMetaData.getImportedKeys().
     */
    record ImportedFkRow(String fkName,
                         String localColumn,
                         String pkTable,
                         String pkColumn,
                         int keySeq) {
    }
}