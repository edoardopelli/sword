package org.cheetah.sword.service;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.cheetah.sword.events.Events.GenerateRequestedEvent;
import org.cheetah.sword.events.Events.GenerationCompletedEvent;
import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.FkMode;
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

            String basePackage = normalizePackage(cfg.getBasePackage());
            Path rootPath = cfg.getOutputPath();
            Files.createDirectories(rootPath);

            System.out.printf("   Output root: %s%n", rootPath.toAbsolutePath());
            System.out.printf("   Package: %s%n", basePackage);
            System.out.printf("   FK mode: %s%n", cfg.getFkMode());

            DatabaseMetaData metaData = connection.getMetaData();
            String dbProduct = metaData.getDatabaseProductName();

            for (String table : tables) {
                EntityModel model = loadEntityModel(metaData, catalog, schema, table, dbProduct);
                writeEntityFiles(basePackage, rootPath, model, dbProduct, cfg.getFkMode(), metaData, catalog, schema);
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
     * Reads table metadata (columns, primary key columns, and foreign keys).
     * Foreign keys are grouped by FK_NAME. Only single-column foreign keys are modeled.
     */
    private EntityModel loadEntityModel(DatabaseMetaData md,
                                        String catalog,
                                        String schema,
                                        String table,
                                        String dbProduct) throws SQLException {

        Map<String, ColumnModel> columns = new LinkedHashMap<>();
        Set<String> pkCols = new LinkedHashSet<>();

        // Load columns
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

        // Load primary keys
        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                pkCols.add(rs.getString("COLUMN_NAME"));
            }
        }

        // Load imported foreign keys
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

        // Build single-column FK models
        List<SimpleFkModel> simpleFks = new ArrayList<>();
        for (List<ImportedFkRow> rows : fkGroups.values()) {
            if (rows.size() == 1) {
                ImportedFkRow r = rows.get(0);
                simpleFks.add(new SimpleFkModel(r.localColumn(), r.pkTable(), r.pkColumn()));
            }
        }

        return new EntityModel(schema, table, columns, pkCols, simpleFks);
    }

    /**
     * Generates the main @Entity class and, if needed, the @Embeddable Id class.
     * Applies SCALAR or RELATION FK mode.
     */
    private void writeEntityFiles(String basePackage,
                                  Path rootPath,
                                  EntityModel model,
                                  String dbProduct,
                                  FkMode fkMode,
                                  DatabaseMetaData md,
                                  String catalog,
                                  String schema) throws IOException {

        String entitySimpleName = namingConfigService.resolveEntityName(model.table());
        String packageName = basePackage;

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

        if (compositePk) {
            ClassName idClass = ClassName.get(packageName, idClassName);

            FieldSpec.Builder idField = FieldSpec.builder(idClass, "id", Modifier.PRIVATE)
                    .addAnnotation(ClassName.get("jakarta.persistence", "EmbeddedId"))
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));

            entity.addField(idField.build());

            writeEmbeddedId(packageName, rootPath, idClassName, model, dbProduct, generatedAnn);
        }

        /*
         * Relation fields (RELATION mode only).
         * Each simple FK column can be represented as:
         * - @OneToOne(fetch = FetchType.LAZY) if the FK column is UNIQUE
         * - @ManyToOne(fetch = FetchType.LAZY) otherwise
         * The relation field is not included in equals/hashCode/toString.
         */
        Set<String> handledFkColumns = new HashSet<>();
        List<FieldSpec> relationFields = new ArrayList<>();

        if (fkMode == FkMode.RELATION) {
            for (SimpleFkModel fk : model.simpleFks()) {
                String localCol = fk.localColumn();

                if (model.pkCols().contains(localCol)) {
                    // Primary key columns are not replaced by relations in this step
                    continue;
                }

                String targetEntityName = namingConfigService.resolveEntityName(fk.targetTable());
                ClassName targetType = ClassName.get(packageName, targetEntityName);

                String relFieldName = lowerFirst(targetEntityName);

                boolean unique = isColumnUnique(md, catalog, model.schema(), model.table(), localCol);

                AnnotationSpec relationAnn;
                if (unique) {
                    relationAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "OneToOne"))
                            .addMember("fetch", "$T.LAZY", ClassName.get("jakarta.persistence", "FetchType"))
                            .build();
                } else {
                    relationAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "ManyToOne"))
                            .addMember("fetch", "$T.LAZY", ClassName.get("jakarta.persistence", "FetchType"))
                            .build();
                }

                AnnotationSpec joinColAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "JoinColumn"))
                        .addMember("name", "$S", localCol)
                        .build();

                FieldSpec.Builder relField = FieldSpec.builder(targetType, relFieldName, Modifier.PRIVATE)
                        .addAnnotation(relationAnn)
                        .addAnnotation(joinColAnn);

                relationFields.add(relField.build());
                handledFkColumns.add(localCol);
            }
        }

        /*
         * Scalar fields for columns.
         * Skip columns that were already modeled as relation fields (RELATION mode),
         * except if the FK column is also the simple primary key.
         * Skip PK columns if composite PK is used, because those are emitted
         * inside the @Embeddable <EntityName>Id class.
         * For single-column PKs, emit @Id and, if applicable, @GeneratedValue.
         */
        for (ColumnModel col : model.columns().values()) {

            boolean isSimplePkColumn = (!compositePk && model.pkCols().contains(col.name()));
            boolean skipForRelation = handledFkColumns.contains(col.name()) && !isSimplePkColumn;
            if (skipForRelation) {
                continue;
            }

            if (compositePk && model.pkCols().contains(col.name())) {
                // Composite PK columns already live in the <EntityName>Id embedded class
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

        // Append relation fields after scalar fields
        for (FieldSpec relField : relationFields) {
            entity.addField(relField);
        }

        // Write main entity file
        JavaFile.builder(packageName, entity.build())
                .build()
                .writeTo(rootPath);
    }

    /**
     * Generates the <EntityName>Id embeddable class for composite primary keys.
     * Each PK column becomes a field in the embeddable class.
     */
    private void writeEmbeddedId(String basePackage,
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

            AnnotationSpec.Builder columnAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
                    .addMember("name", "$S", pkCol);

            String tn = col.typeName() == null ? "" : col.typeName().toLowerCase();
            if ((tn.equals("jsonb") || tn.equals("json")) && isPostgres(dbProduct)) {
                columnAnn.addMember("columnDefinition", "$S", tn);
            }

            FieldSpec f = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE)
                    .addAnnotation(columnAnn.build())
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"))
                    .build();

            emb.addField(f);
        }

        JavaFile.builder(basePackage, emb.build())
                .build()
                .writeTo(rootPath);
    }

    /**
     * Detects whether the column is auto-generated (identity/sequence/auto_increment/...).
     * Logic is database-product specific and uses column metadata and default definitions.
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
     * Checks if the given column participates in a UNIQUE index.
     * If true, a single-column FK referencing a parent entity can be modeled as @OneToOne.
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

    private static String lowerFirst(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * In-memory model of a database table.
     */
    record EntityModel(String schema,
                       String table,
                       Map<String, ColumnModel> columns,
                       Set<String> pkCols,
                       List<SimpleFkModel> simpleFks) {
    }

    /**
     * In-memory model of a table column.
     */
    record ColumnModel(String name,
                       int dataType,
                       String typeName,
                       boolean nullable,
                       String columnDef,
                       boolean autoIncrement) {
    }

    /**
     * In-memory model of a single-column foreign key.
     */
    record SimpleFkModel(String localColumn,
                         String targetTable,
                         String targetColumn) {
    }

    /**
     * Raw row from DatabaseMetaData.getImportedKeys().
     */
    record ImportedFkRow(String fkName,
                         String localColumn,
                         String pkTable,
                         String pkColumn,
                         int keySeq) {
    }
}