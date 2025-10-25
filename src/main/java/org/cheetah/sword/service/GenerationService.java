package org.cheetah.sword.service;

import com.squareup.javapoet.*;
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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates:
 * - Entities (+ EmbeddedId when composite PK)
 * - Optional DTOs
 * - Optional MapStruct mappers
 * - Optional pluralized Spring Data repositories
 * - Optional pluralized services (CRUD + paginated search, using DTOs + mapper + PageDto)
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
            String repositoryPackage = siblingPackage(entityPackage, "repositories");
            String servicesPackage = siblingPackage(entityPackage, "services");

            Path rootPath = cfg.getOutputPath();
            Files.createDirectories(rootPath);

            System.out.printf("   Output root        : %s%n", rootPath.toAbsolutePath());
            System.out.printf("   Entity package     : %s%n", entityPackage);
            System.out.printf("   DTO package        : %s%n", dtoPackage);
            System.out.printf("   Mapper package     : %s%n", mapperPackage);
            System.out.printf("   Repository package : %s%n", repositoryPackage);
            System.out.printf("   Service package    : %s%n", servicesPackage);
            System.out.printf("   FK mode            : %s%n", cfg.getFkMode());
            System.out.printf("   Relation fetch     : %s%n", cfg.getRelationFetch());
            System.out.printf("   Generate DTO       : %s%n", cfg.isGenerateDto());
            System.out.printf("   Generate Repo      : %s%n", cfg.isGenerateRepositories());
            System.out.printf("   Generate Services  : %s%n", cfg.isGenerateServices());

            DatabaseMetaData metaData = connection.getMetaData();
            String dbProduct = metaData.getDatabaseProductName();

            // build table models
            List<EntityModel> models = new ArrayList<>();
            for (String table : tables) {
                models.add(loadEntityModel(metaData, catalog, schema, table, dbProduct));
            }

            // per-table generation
            for (EntityModel model : models) {
                writeEntityFiles(
                        entityPackage,
                        dtoPackage,
                        mapperPackage,
                        repositoryPackage,
                        servicesPackage,
                        rootPath,
                        model,
                        models,
                        dbProduct,
                        cfg.getFkMode(),
                        cfg.getRelationFetch(),
                        cfg.isGenerateDto(),
                        cfg.isGenerateRepositories(),
                        cfg.isGenerateServices(),
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

    private EntityModel loadEntityModel(DatabaseMetaData md,
                                        String catalog,
                                        String schema,
                                        String table,
                                        String dbProduct) throws SQLException {

        Map<String, ColumnModel> columns = new LinkedHashMap<>();
        Set<String> pkCols = new LinkedHashSet<>();

        // columns
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

        // PK columns
        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                pkCols.add(rs.getString("COLUMN_NAME"));
            }
        }

        // foreign keys
        Map<String, List<ImportedFkRow>> fkGroups = new LinkedHashMap<>();
        try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                int keySeq = rs.getInt("KEY_SEQ");
                if (fkName == null || fkName.isBlank()) fkName = pkTable + "__" + fkColumn;
                ImportedFkRow row = new ImportedFkRow(fkName, fkColumn, pkTable, pkColumn, keySeq);
                fkGroups.computeIfAbsent(fkName, k -> new ArrayList<>()).add(row);
            }
        }

        // keep only single-column FK sets
        List<SimpleFkModel> simpleFks = new ArrayList<>();
        for (List<ImportedFkRow> rows : fkGroups.values()) {
            if (rows.size() == 1) {
                ImportedFkRow r = rows.get(0);
                simpleFks.add(new SimpleFkModel(r.localColumn(), r.pkTable(), r.pkColumn()));
            }
        }

        return new EntityModel(catalog, schema, table, columns, pkCols, simpleFks);
    }

    private void writeEntityFiles(String entityPackage,
                                  String dtoPackage,
                                  String mapperPackage,
                                  String repositoryPackage,
                                  String servicesPackage,
                                  Path rootPath,
                                  EntityModel model,
                                  List<EntityModel> allModels,
                                  String dbProduct,
                                  FkMode fkMode,
                                  RelationFetch relationFetch,
                                  boolean generateDto,
                                  boolean generateRepositories,
                                  boolean generateServices,
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
                .addAnnotation(
                        AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Table"))
                                .addMember("name", "$S", model.table())
                                .build()
                )
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(toStringAnn)
                .addAnnotation(eqHashAnn)
                .addAnnotation(generatedAnn);

        // collector for repository generation
        List<ScalarFieldInfo> scalarFieldInfos = new ArrayList<>();
        // ID type for repository/service
        TypeName idTypeForRepository = null;

        // composite PK -> add @EmbeddedId + generate Id class
        if (compositePk) {
            ClassName idClass = ClassName.get(entityPackage, idClassName);
            FieldSpec.Builder idField = FieldSpec.builder(idClass, "id", Modifier.PRIVATE)
                    .addAnnotation(ClassName.get("jakarta.persistence", "EmbeddedId"))
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
            entity.addField(idField.build());

            writeEmbeddedId(entityPackage, rootPath, idClassName, model, dbProduct, generatedAnn);

            idTypeForRepository = idClass;
        }

        // relation fields on the child side if RELATION mode
        Set<String> handledFkColumns = new HashSet<>();
        List<FieldSpec> relationFieldsChildSide = new ArrayList<>();

        if (fkMode == FkMode.RELATION) {
            for (SimpleFkModel fk : model.simpleFks()) {
                String localCol = fk.localColumn();
                if (model.pkCols().contains(localCol)) continue;

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

                AnnotationSpec.Builder relationAnn =
                        AnnotationSpec.builder(
                                ClassName.get("jakarta.persistence", unique ? "OneToOne" : "ManyToOne")
                        ).addMember(
                                "fetch",
                                "$T.$L",
                                ClassName.get("jakarta.persistence", "FetchType"),
                                relationFetch == RelationFetch.EAGER ? "EAGER" : "LAZY"
                        );

                AnnotationSpec joinColAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "JoinColumn"))
                        .addMember("name", "$S", localCol)
                        .build();

                FieldSpec.Builder relField = FieldSpec
                        .builder(targetType, relFieldName, Modifier.PRIVATE)
                        .addAnnotation(relationAnn.build())
                        .addAnnotation(joinColAnn);

                relationFieldsChildSide.add(relField.build());
                handledFkColumns.add(localCol);
            }
        }

        // scalar columns + PK field when PK is simple
        for (ColumnModel col : model.columns().values()) {
            boolean isSimplePkColumn = (!compositePk && model.pkCols().contains(col.name()));
            boolean skipForRelation = handledFkColumns.contains(col.name()) && !isSimplePkColumn;
            if (skipForRelation) continue;
            if (compositePk && model.pkCols().contains(col.name())) continue;

            String fieldName = namingConfigService.resolveColumnName(model.table(), col.name());
            TypeName javaType = SqlTypeMapper.map(
                    col.dataType(),
                    col.typeName(),
                    col.nullable(),
                    dbProduct
            );

            FieldSpec.Builder field = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE);

            if (isSimplePkColumn) {
                // mark PK
                field.addAnnotation(ClassName.get("jakarta.persistence", "Id"));
                field.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
                field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));

                // GeneratedValue strategy
                if (col.autoIncrement()) {
                    if (isPostgres(dbProduct)
                            && col.columnDef() != null
                            && col.columnDef().toLowerCase(Locale.ROOT).contains("nextval(")) {

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

                // remember id type
                if (!compositePk) {
                    idTypeForRepository = javaType;
                }

            } else {
                // scalar field, not PK
                if (!javaType.toString().equals("byte[]")) {
                    field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));
                }
                scalarFieldInfos.add(new ScalarFieldInfo(fieldName, javaType));
            }

            // @Column
            AnnotationSpec.Builder colAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
                    .addMember("name", "$S", col.name());

            // Postgres JSONB/JSON
            String tn = col.typeName() == null ? "" : col.typeName().toLowerCase(Locale.ROOT);
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

        // add relation fields on child side
        for (FieldSpec relField : relationFieldsChildSide) {
            entity.addField(relField);
        }

        // inverse relations on parent side
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

        // write entity
        JavaFile.builder(entityPackage, entity.build())
                .build()
                .writeTo(rootPath);

        // DTO + Mapper
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

        // Repository
        if (generateRepositories) {
            writeRepository(
                    repositoryPackage,
                    entityPackage,
                    rootPath,
                    entitySimpleName,
                    idTypeForRepository,
                    scalarFieldInfos,
                    generatedAnn
            );
        }

        // Service
        if (generateServices) {
            // We assume that DTO, Mapper and Repository are also generated/available.
            writePageDtoOnce(servicesPackage, rootPath, generatedAnn);
            writeService(
                    servicesPackage,
                    entityPackage,
                    dtoPackage,
                    mapperPackage,
                    repositoryPackage,
                    rootPath,
                    entitySimpleName,
                    idTypeForRepository,
                    scalarFieldInfos,
                    generatedAnn
            );
        }
    }

    /**
     * Create DTO + Mapper.
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

        AnnotationSpec toStringAnnDto = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        AnnotationSpec eqHashAnnDto = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        TypeSpec.Builder dto = TypeSpec.classBuilder(dtoSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(toStringAnnDto)
                .addAnnotation(eqHashAnnDto)
                .addAnnotation(generatedAnn);

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
     * Create plural Repository with finder methods.
     */
    private void writeRepository(String repositoryPackage,
                                 String entityPackage,
                                 Path rootPath,
                                 String entitySimpleName,
                                 TypeName idTypeForRepository,
                                 List<ScalarFieldInfo> scalarFields,
                                 AnnotationSpec generatedAnn) throws IOException {

        ClassName entityClass = ClassName.get(entityPackage, entitySimpleName);
        TypeName idType = (idTypeForRepository != null) ? idTypeForRepository : ClassName.get(Long.class);

        ParameterizedTypeName jpaRepoType = ParameterizedTypeName.get(
                ClassName.get("org.springframework.data.jpa.repository", "JpaRepository"),
                entityClass,
                idType
        );

        String repoSimpleName = pluralizeSimpleName(entitySimpleName) + "Repository";

        TypeSpec.Builder repo = TypeSpec.interfaceBuilder(repoSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(jpaRepoType)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Repository"))
                .addAnnotation(generatedAnn);

        // add finder methods for non-PK scalar fields
        for (ScalarFieldInfo sf : scalarFields) {
            String fieldName = sf.javaFieldName();
            TypeName fieldType = sf.javaType();
            String methodName = "findBy" + upperFirst(fieldName);

            MethodSpec finder = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(
                            ParameterizedTypeName.get(
                                    ClassName.get("org.springframework.data.domain", "Page"),
                                    entityClass
                            )
                    )
                    .addParameter(fieldType, fieldName)
                    .addParameter(ClassName.get("org.springframework.data.domain", "Pageable"), "pageable")
                    .build();

            repo.addMethod(finder);
        }

        JavaFile.builder(repositoryPackage, repo.build())
                .build()
                .writeTo(rootPath);
    }

    /**
     * Create PageDto<T> if not already created.
     * PageDto holds paginated DTO data for service responses.
     */
    private void writePageDtoOnce(String servicesPackage,
                                  Path rootPath,
                                  AnnotationSpec generatedAnn) throws IOException {

        // naive approach: always write. If file already exists on disk from previous run
        // JavaPoet's writeTo will update it (overwrites). That's acceptable for now.

        TypeVariableName typeT = TypeVariableName.get("T");

        TypeSpec.Builder pageDto = TypeSpec.classBuilder("PageDto")
                .addTypeVariable(typeT)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(generatedAnn);

        pageDto.addField(
                FieldSpec.builder(
                                ParameterizedTypeName.get(
                                        ClassName.get(List.class),
                                        typeT
                                ),
                                "content",
                                Modifier.PRIVATE
                        )
                        .build()
        );
        pageDto.addField(FieldSpec.builder(TypeName.INT, "pageNumber", Modifier.PRIVATE).build());
        pageDto.addField(FieldSpec.builder(TypeName.INT, "pageSize", Modifier.PRIVATE).build());
        pageDto.addField(FieldSpec.builder(TypeName.LONG, "totalElements", Modifier.PRIVATE).build());
        pageDto.addField(FieldSpec.builder(TypeName.INT, "totalPages", Modifier.PRIVATE).build());

        JavaFile.builder(servicesPackage, pageDto.build())
                .build()
                .writeTo(rootPath);
    }

    /**
     * Create plural Service class.
     *
     * Example: entity "User" -> "UsersService"
     *
     * Methods:
     *  - PageDto<UserDto> findAll(pageNumber, maxRecordsPerPage)
     *  - PageDto<UserDto> findByXxx(value, pageNumber, maxRecordsPerPage) for each finder in repo
     *  - UserDto findById(ID id) // returns null if not found
     *  - UserDto save(UserDto dto)
     *  - UserDto update(ID id, UserDto dto)
     *  - void delete(ID id)
     *
     * The service is annotated with @Service and uses:
     *   - the repository bean
     *   - the mapper bean
     */
    private void writeService(String servicesPackage,
                              String entityPackage,
                              String dtoPackage,
                              String mapperPackage,
                              String repositoryPackage,
                              Path rootPath,
                              String entitySimpleName,
                              TypeName idTypeForRepository,
                              List<ScalarFieldInfo> scalarFields,
                              AnnotationSpec generatedAnn) throws IOException {

        // types
        ClassName entityClass = ClassName.get(entityPackage, entitySimpleName);
        String dtoSimpleName = entitySimpleName + "Dto";
        ClassName dtoClass = ClassName.get(dtoPackage, dtoSimpleName);
        ClassName mapperClass = ClassName.get(mapperPackage, entitySimpleName + "Mapper");

        String repoSimpleName = pluralizeSimpleName(entitySimpleName) + "Repository";
        ClassName repoClass = ClassName.get(repositoryPackage, repoSimpleName);

        ClassName pageDtoClass = ClassName.get(servicesPackage, "PageDto");

        TypeName idType = (idTypeForRepository != null) ? idTypeForRepository : ClassName.get(Long.class);

        // fields: repository + mapper
        FieldSpec repoField = FieldSpec.builder(repoClass, "repository", Modifier.PRIVATE, Modifier.FINAL).build();
        FieldSpec mapperField = FieldSpec.builder(mapperClass, "mapper", Modifier.PRIVATE, Modifier.FINAL).build();

        // constructor
        MethodSpec ctor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(repoClass, "repository")
                .addParameter(mapperClass, "mapper")
                .addStatement("this.repository = repository")
                .addStatement("this.mapper = mapper")
                .build();

        /*
         * Helper: build PageDto<DTO> from Page<Entity>
         *
         * private PageDto<UserDto> toPageDto(Page<User> page) {
         *     List<UserDto> dtoList = page.getContent().stream()
         *         .map(mapper::toDto)
         *         .toList();
         *     return PageDto.<UserDto>builder()
         *         .content(dtoList)
         *         .pageNumber(page.getNumber())
         *         .pageSize(page.getSize())
         *         .totalElements(page.getTotalElements())
         *         .totalPages(page.getTotalPages())
         *         .build();
         * }
         */
        ClassName springPageClass = ClassName.get("org.springframework.data.domain", "Page");
        TypeName pageOfEntity = ParameterizedTypeName.get(springPageClass, entityClass);

        MethodSpec toPageDtoMethod = MethodSpec.methodBuilder("toPageDto")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(pageDtoClass, dtoClass))
                .addParameter(pageOfEntity, "page")
                .addStatement("java.util.List<$T> dtoList = page.getContent().stream().map(mapper::toDto).toList()", dtoClass)
                .addStatement("return $T.<$T>builder()"
                                + ".content(dtoList)"
                                + ".pageNumber(page.getNumber())"
                                + ".pageSize(page.getSize())"
                                + ".totalElements(page.getTotalElements())"
                                + ".totalPages(page.getTotalPages())"
                                + ".build()",
                        pageDtoClass, dtoClass)
                .build();

        /*
         * findAll(int pageNumber, int maxRecordsPerPage)
         *
         * Page<User> p = repository.findAll(PageRequest.of(pageNumber, maxRecordsPerPage));
         * return toPageDto(p);
         */
        ClassName pageRequestClass = ClassName.get("org.springframework.data.domain", "PageRequest");

        MethodSpec findAllMethod = MethodSpec.methodBuilder("findAll")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(pageDtoClass, dtoClass))
                .addParameter(TypeName.INT, "pageNumber")
                .addParameter(TypeName.INT, "maxRecordsPerPage")
                .addStatement("$T p = repository.findAll($T.of(pageNumber, maxRecordsPerPage))",
                        pageOfEntity, pageRequestClass)
                .addStatement("return toPageDto(p)")
                .build();

        /*
         * findById(ID id)
         *
         * return repository.findById(id)
         *     .map(mapper::toDto)
         *     .orElse(null);
         */
        MethodSpec findByIdMethod = MethodSpec.methodBuilder("findById")
                .addModifiers(Modifier.PUBLIC)
                .returns(dtoClass)
                .addParameter(idType, "id")
                .addStatement("return repository.findById(id).map(mapper::toDto).orElse(null)")
                .build();

        /*
         * save(UserDto dto)
         *
         * Entity e = mapper.toEntity(dto);
         * e = repository.save(e);
         * return mapper.toDto(e);
         */
        MethodSpec saveMethod = MethodSpec.methodBuilder("save")
                .addModifiers(Modifier.PUBLIC)
                .returns(dtoClass)
                .addParameter(dtoClass, "dto")
                .addStatement("$T e = mapper.toEntity(dto)", entityClass)
                .addStatement("e = repository.save(e)")
                .addStatement("return mapper.toDto(e)")
                .build();

        /*
         * update(ID id, UserDto dto)
         *
         * Entity e = mapper.toEntity(dto);
         * // set PK on e before save if needed
         * e = repository.save(e);
         * return mapper.toDto(e);
         *
         * For a single-column PK, we generate:
         *   e.set<IdFieldName>(id);
         *
         * For composite PK we assume dto already maps to an entity with the embedded id
         * or the caller passes a full Id. We still overwrite e.setId(id) to be sure.
         */
        MethodSpec.Builder updateBuilder = MethodSpec.methodBuilder("update")
                .addModifiers(Modifier.PUBLIC)
                .returns(dtoClass)
                .addParameter(idType, "id")
                .addParameter(dtoClass, "dto")
                .addStatement("$T e = mapper.toEntity(dto)", entityClass);

        // naive approach to set PK back into the entity before save:
        // if idType is not Long.class default, we still assume setter exists.
        // We do not know the exact PK field name here without deeper modeling,
        // but we DO know this:
        // - if composite => entity has setId(<EntityName>Id)
        // - if simple PK => we can't guess field name perfectly without extra metadata.
        //
        // We'll handle composite PK safely: call setId(id) if compositePk (idType is ClassName same package entitySimpleName+"Id").
        // For simple PK, we skip explicit setter because mapper.toEntity(dto) should already carry PK if present.
        //
        boolean compositePk = (idType instanceof ClassName cName && cName.simpleName().endsWith("Id"));
        if (compositePk) {
            updateBuilder.addStatement("e.setId(id)");
        }

        updateBuilder
                .addStatement("e = repository.save(e)")
                .addStatement("return mapper.toDto(e)");
        MethodSpec updateMethod = updateBuilder.build();

        /*
         * delete(ID id)
         *
         * repository.deleteById(id);
         */
        MethodSpec deleteMethod = MethodSpec.methodBuilder("delete")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(idType, "id")
                .addStatement("repository.deleteById(id)")
                .build();

        /*
         * Finder wrappers for each scalar field:
         *
         * public PageDto<UserDto> findByStatus(String status, int pageNumber, int maxRecordsPerPage) {
         *     Page<User> p = repository.findByStatus(status, PageRequest.of(pageNumber, maxRecordsPerPage));
         *     return toPageDto(p);
         * }
         */
        List<MethodSpec> finderWrapperMethods = new ArrayList<>();
        for (ScalarFieldInfo sf : scalarFields) {
            String fieldName = sf.javaFieldName();
            TypeName fieldType = sf.javaType();
            String repoMethodName = "findBy" + upperFirst(fieldName);

            MethodSpec finderWrapper = MethodSpec.methodBuilder(repoMethodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(pageDtoClass, dtoClass))
                    .addParameter(fieldType, fieldName)
                    .addParameter(TypeName.INT, "pageNumber")
                    .addParameter(TypeName.INT, "maxRecordsPerPage")
                    .addStatement("$T p = repository.$L($L, $T.of(pageNumber, maxRecordsPerPage))",
                            pageOfEntity,
                            repoMethodName,
                            fieldName,
                            pageRequestClass)
                    .addStatement("return toPageDto(p)")
                    .build();

            finderWrapperMethods.add(finderWrapper);
        }

        // build the service class
        String serviceSimpleName = pluralizeSimpleName(entitySimpleName) + "Service";

        TypeSpec.Builder serviceType = TypeSpec.classBuilder(serviceSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Service"))
                .addAnnotation(generatedAnn)
                .addField(repoField)
                .addField(mapperField)
                .addMethod(ctor)
                .addMethod(toPageDtoMethod)
                .addMethod(findAllMethod)
                .addMethod(findByIdMethod)
                .addMethod(saveMethod)
                .addMethod(updateMethod)
                .addMethod(deleteMethod);

        for (MethodSpec m : finderWrapperMethods) {
            serviceType.addMethod(m);
        }

        JavaFile.builder(servicesPackage, serviceType.build())
                .build()
                .writeTo(rootPath);
    }

    /**
     * Build inverse relationships on parent side for RELATION mode.
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
                    String fieldName = uniquify(lowerFirst(childEntityName), usedFieldNames);

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
                    String pluralBase = lowerFirst(childEntityName) + "s";
                    String fieldName = uniquify(pluralBase, usedFieldNames);

                    ParameterizedTypeName setOfChild = ParameterizedTypeName.get(
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
                    .addAnnotation(
                            AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
                                    .addMember("name", "$S", pkCol)
                                    .build()
                    )
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"))
                    .build();

            emb.addField(f);
        }

        JavaFile.builder(entityPackage, emb.build())
                .build()
                .writeTo(rootPath);
    }

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

    private boolean detectAutoIncrement(String dbProduct,
                                        String isAuto,
                                        String typeName,
                                        String columnDef) {
        String db = dbProduct == null ? "" : dbProduct.toLowerCase(Locale.ROOT);
        String tn = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
        String def = columnDef == null ? "" : columnDef.toLowerCase(Locale.ROOT);

        if ("yes".equalsIgnoreCase(isAuto)) return true;
        if (db.contains("postgres") && def.contains("nextval(")) return true;
        if (db.contains("sql server") && (tn.contains("identity") || def.contains("identity"))) return true;
        if (db.contains("h2") && (tn.contains("identity") || def.contains("auto_increment") || def.contains("identity"))) return true;
        if (db.contains("db2") && def.contains("generated") && def.contains("identity")) return true;
        if ((db.contains("mysql") || db.contains("mariadb")) && def.contains("auto_increment")) return true;
        return false;
    }

    private String extractPgSequenceName(String columnDef) {
        if (columnDef == null) return null;
        Matcher m = Pattern.compile("nextval\\('([^']+)'").matcher(columnDef);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private boolean isPostgres(String dbProduct) {
        return dbProduct != null && dbProduct.toLowerCase(Locale.ROOT).contains("postgres");
    }

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
        String p = raw.replace('/', '.').replace('\\', '.').trim();
        while (p.contains("..")) {
            p = p.replace("..", ".");
        }
        if (p.startsWith(".")) p = p.substring(1);
        if (p.endsWith(".")) p = p.substring(0, p.length() - 1);
        return p;
    }

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

    private static String upperFirst(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String pluralizeSimpleName(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return simpleName;
        }
        char last = simpleName.charAt(simpleName.length() - 1);
        if (last == 's' || last == 'S') {
            return simpleName;
        }
        return simpleName + "s";
    }

    /* records for metadata */

    record ScalarFieldInfo(String javaFieldName, TypeName javaType) { }

    record EntityModel(String catalog,
                       String schema,
                       String table,
                       Map<String, ColumnModel> columns,
                       Set<String> pkCols,
                       List<SimpleFkModel> simpleFks) {
    }

    record ColumnModel(String name,
                       int dataType,
                       String typeName,
                       boolean nullable,
                       String columnDef,
                       boolean autoIncrement) {
    }

    record SimpleFkModel(String localColumn,
                         String targetTable,
                         String targetColumn) {
    }

    record ImportedFkRow(String fkName,
                         String localColumn,
                         String pkTable,
                         String pkColumn,
                         int keySeq) {
    }
}