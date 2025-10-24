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
import org.cheetah.sword.model.SchemaSelection;
import org.cheetah.sword.util.NamingUtils;
import org.cheetah.sword.util.SqlTypeMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S.W.O.R.D. - GenerationService (Scalar-FK only) + @GeneratedValue on identity/serial/sequence PK
 *
 * Scrive i file sotto l'outputPath scelto dall'utente.
 * Esempio:
 *   outputPath = "."
 *   basePackage = "org.cheetah.entities"
 *
 * Risultato:
 *   ./org/cheetah/entities/<Entity>.java
 *
 * NIENTE più doppia cartella.
 */
@Service
public class GenerationService {

    private final MetadataService metadataService;
    private final ApplicationEventPublisher publisher;

    public GenerationService(MetadataService metadataService, ApplicationEventPublisher publisher) {
        this.metadataService = metadataService;
        this.publisher = publisher;
    }

    @EventListener(GenerateRequestedEvent.class)
    public void onGenerate(GenerateRequestedEvent ev) {
        ConnectionConfig cfg = ev.config();
        SchemaSelection sel = ev.selection();

        int generated = 0;
        try (Connection c = metadataService.open(cfg)) {

            String catalog = sel.catalog();
            String schema  = sel.schema();

            System.out.printf("%n→ Scanning catalog=%s schema=%s ...%n", nvl(catalog), nvl(schema));

            List<String> tables = metadataService.listTables(c, catalog, schema);
            System.out.printf("   Found %d table(s).%n", tables.size());

            // normalizza sempre il package in forma dotted (org.cheetah...)
            String normalizedBasePackage = normalizePackage(cfg.getBasePackage());

            // root di output: SOLO quella scelta dall'utente
            Path rootPath = cfg.getOutputPath();
            Files.createDirectories(rootPath);
            System.out.printf("   Output root folder: %s%n", rootPath.toAbsolutePath());
            System.out.printf("   Package %s will be written under that root.%n", normalizedBasePackage);

            DatabaseMetaData md = c.getMetaData();
            String dbProduct = md.getDatabaseProductName();

            for (String table : tables) {
                EntityModel model = loadEntityModel(md, catalog, schema, table, dbProduct);
                writeEntityFiles(normalizedBasePackage, rootPath, model, dbProduct);
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

    /* =========================
       ====  MODEL LOADER  ====
       ========================= */

    private EntityModel loadEntityModel(DatabaseMetaData md, String catalog, String schema, String table, String dbProduct) throws SQLException {
        Map<String, ColumnModel> columns = new LinkedHashMap<>();
        Set<String> pkCols = new LinkedHashSet<>();

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

        try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                pkCols.add(rs.getString("COLUMN_NAME"));
            }
        }

        return new EntityModel(schema, table, columns, pkCols);
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static String nvl(String s) { return s == null ? "(null)" : s; }

    private boolean detectAutoIncrement(String dbProduct, String isAuto, String typeName, String columnDef) {
        String db = dbProduct == null ? "" : dbProduct.toLowerCase();
        String tn = typeName == null ? "" : typeName.toLowerCase();
        String def = columnDef == null ? "" : columnDef.toLowerCase();

        if ("yes".equalsIgnoreCase(isAuto)) return true;

        if (db.contains("postgres")) {
            if (def.contains("nextval(")) return true;
        }
        if (db.contains("sql server")) {
            if (tn.contains("identity") || def.contains("identity")) return true;
        }
        if (db.contains("h2")) {
            if (tn.contains("identity") || def.contains("identity") || def.contains("auto_increment")) return true;
        }
        if (db.contains("db2")) {
            if (def.contains("generated") && def.contains("identity")) return true;
        }
        if (db.contains("mysql") || db.contains("mariadb")) {
            if (def.contains("auto_increment")) return true;
        }
        return false;
    }

    /* =========================
       ====  FILE WRITERS   ====
       ========================= */

    private void writeEntityFiles(String basePackage, Path rootPath, EntityModel model, String dbProduct) throws IOException {
        String className = NamingUtils.toClassName(model.table());
        String packageName = basePackage;

        boolean compositePk = model.pkCols().size() > 1;
        String idClassName = className + "Id";

        String nowIso = java.time.OffsetDateTime.now().toString();
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

        TypeSpec.Builder entity = TypeSpec.classBuilder(className)
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
            System.out.printf("   [+] %sId.java%n", idClassName);
        }

        for (ColumnModel col : model.columns().values()) {
            if (compositePk && model.pkCols().contains(col.name())) continue;

            String fieldName = NamingUtils.toFieldName(col.name());
            TypeName javaType = SqlTypeMapper.map(col.dataType(), col.typeName(), col.nullable(), dbProduct);

            FieldSpec.Builder field = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE);

            if (!compositePk && model.pkCols().contains(col.name())) {
                field.addAnnotation(ClassName.get("jakarta.persistence", "Id"));
                field.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
                field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));

                if (col.autoIncrement()) {
                    if (isPostgres(dbProduct) && col.columnDef() != null && col.columnDef().toLowerCase().contains("nextval(")) {
                        String seqName = extractPgSequenceName(col.columnDef());
                        String genName = (model.table() + "_" + col.name() + "_seq_gen").replaceAll("[^A-Za-z0-9_]", "_");

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

        JavaFile.builder(packageName, entity.build())
                .build()
                .writeTo(rootPath);

        System.out.printf("   [+] %s.java%n", className);
    }

    private void writeEmbeddedId(
            String basePackage,
            Path rootPath,
            String idClassName,
            EntityModel model,
            String dbProduct,
            AnnotationSpec generatedAnn
    ) throws IOException {

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
            String fieldName = NamingUtils.toFieldName(pkCol);
            TypeName javaType = SqlTypeMapper.map(col.dataType(), col.typeName(), col.nullable(), dbProduct);

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

    private boolean isPostgres(String dbProduct) {
        return dbProduct != null && dbProduct.toLowerCase().contains("postgres");
    }

    private String extractPgSequenceName(String columnDef) {
        if (columnDef == null) return null;
        Matcher m = Pattern.compile("nextval\\('([^']+)'").matcher(columnDef);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String normalizePackage(String raw) {
        if (raw == null) return "";
        String p = raw.replace('/', '.')
                      .replace('\\', '.')
                      .trim();
        while (p.contains("..")) p = p.replace("..", ".");
        if (p.startsWith(".")) p = p.substring(1);
        if (p.endsWith(".")) p = p.substring(0, p.length()-1);
        return p;
    }

    /* =========================
       ========  RECORDS  ======
       ========================= */

    record EntityModel(String schema, String table, Map<String, ColumnModel> columns, Set<String> pkCols) {}
    record ColumnModel(String name, int dataType, String typeName, boolean nullable, String columnDef, boolean autoIncrement) {}
}