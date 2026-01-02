package org.cheetah.sword.generate.writers;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.ForeignKeyModel;
import org.cheetah.sword.model.IdGeneration;
import org.cheetah.sword.model.RelationCardinality;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

public class EntityWriter {

    private static final String EMBEDDED_ID_FIELD_NAME = "id";

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, NameResolver resolver, String schema, TableModel table) {
        ClassName entityType = ClassName.get(resolver.entitiesPackage(), resolver.entitySimpleName(table));
        ClassName embeddedIdType = embeddedIdClassName(resolver, table);

        TypeSpec.Builder type = TypeSpec.classBuilder(entityType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(Data.class)
                .addAnnotation(Entity.class)
                .addAnnotation(buildTableAnnotation(schema, table.getName()));

        // If single PK is SEQUENCE, add @SequenceGenerator at entity level.
        maybeAddSequenceGenerator(type, table);

        Set<String> usedFieldNames = new HashSet<>();

        if (table.hasCompositePrimaryKey()) {
            type.addField(FieldSpec.builder(embeddedIdType, EMBEDDED_ID_FIELD_NAME)
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .addAnnotation(EmbeddedId.class)
                    .build());
            usedFieldNames.add(EMBEDDED_ID_FIELD_NAME);

            writeEmbeddedId(outputDir, resolver, table);
        }

        for (ColumnModel col : table.getColumns()) {
            boolean isPkCol = table.getPrimaryKeyColumns() != null && table.getPrimaryKeyColumns().contains(col.getName());

            if (table.hasCompositePrimaryKey() && isPkCol) {
                continue;
            }

            String fieldName = resolver.columnPropertyName(table, col);
            usedFieldNames.add(fieldName);

            TypeName fieldType = resolveEntityFieldType(col);

            FieldSpec.Builder field = FieldSpec.builder(fieldType, fieldName)
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .addAnnotation(buildColumnAnnotation(col));

            // Hibernate 6 JSON handling for json/jsonb columns (PostgreSQL).
            if (isJsonColumn(col)) {
                field.addAnnotation(buildJdbcTypeCodeJsonAnnotation());
            }

            if (table.hasSinglePrimaryKey() && isPkCol) {
                field.addAnnotation(Id.class);
                maybeAddGeneratedValue(field, table, col);
            }

            type.addField(field.build());
        }

        if (table.getForeignKeys() != null) {
            for (ForeignKeyModel fk : table.getForeignKeys()) {
                addRelationField(type, resolver, fk, usedFieldNames);
            }
        }

        JavaFile javaFile = JavaFile.builder(entityType.packageName(), type.build())
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("Entity generation failed for table: " + table.getName(), ex);
        }
    }

    private TypeName resolveEntityFieldType(ColumnModel col) {
        if (isJsonColumn(col)) {
        	return ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(Object.class)
            );
        }
        return typeResolver.toJavaType(col.getJdbcType());
    }

    private static AnnotationSpec buildJdbcTypeCodeJsonAnnotation() {
        // @JdbcTypeCode(SqlTypes.JSON)
        ClassName jdbcTypeCode = ClassName.get("org.hibernate.annotations", "JdbcTypeCode");
        ClassName sqlTypes = ClassName.get("org.hibernate.type", "SqlTypes");
        return AnnotationSpec.builder(jdbcTypeCode)
                .addMember("value", "$T.JSON", sqlTypes)
                .build();
    }

    private static AnnotationSpec buildColumnAnnotation(ColumnModel col) {
        AnnotationSpec.Builder b = AnnotationSpec.builder(Column.class)
                .addMember("name", "$S", col.getName())
                .addMember("nullable", "$L", col.isNullable());

        // For PostgreSQL json/jsonb, forcing columnDefinition avoids wrong binding/cast issues.
        if (isJsonColumn(col)) {
            String def = safeLower(col.getJdbcTypeName());
            if (def == null || def.isBlank()) {
                def = "jsonb";
            }
            b.addMember("columnDefinition", "$S", def);
        }

        return b.build();
    }

    private void maybeAddGeneratedValue(FieldSpec.Builder field, TableModel table, ColumnModel pkCol) {
        if (pkCol.getIdGeneration() == null || pkCol.getIdGeneration() == IdGeneration.NONE) {
            return;
        }

        if (pkCol.getIdGeneration() == IdGeneration.IDENTITY) {
            field.addAnnotation(AnnotationSpec.builder(GeneratedValue.class)
                    .addMember("strategy", "$T.IDENTITY", GenerationType.class)
                    .build());
            return;
        }

        if (pkCol.getIdGeneration() == IdGeneration.SEQUENCE) {
            String seq = pkCol.getSequenceName();
            if (seq == null || seq.isBlank()) {
                // Sequence strategy requires a sequence name; if missing, do not generate broken annotations.
                return;
            }

            String generatorName = sequenceGeneratorName(table);

            field.addAnnotation(AnnotationSpec.builder(GeneratedValue.class)
                    .addMember("strategy", "$T.SEQUENCE", GenerationType.class)
                    .addMember("generator", "$S", generatorName)
                    .build());
        }
    }

    private void maybeAddSequenceGenerator(TypeSpec.Builder entityType, TableModel table) {
        if (!table.hasSinglePrimaryKey()) {
            return;
        }

        String pkName = table.getPrimaryKeyColumns().get(0);
        ColumnModel pkCol = table.getColumns().stream()
                .filter(c -> pkName.equals(c.getName()))
                .findFirst()
                .orElse(null);

        if (pkCol == null || pkCol.getIdGeneration() != IdGeneration.SEQUENCE) {
            return;
        }

        String seq = pkCol.getSequenceName();
        if (seq == null || seq.isBlank()) {
            return;
        }

        String generatorName = sequenceGeneratorName(table);

        entityType.addAnnotation(AnnotationSpec.builder(SequenceGenerator.class)
                .addMember("name", "$S", generatorName)
                .addMember("sequenceName", "$S", seq)
                .addMember("allocationSize", "$L", 1)
                .build());
    }

    private static String sequenceGeneratorName(TableModel table) {
        return NameUtil.toLowerCamel(table.getName()) + "_id_seq_gen";
    }

    private static AnnotationSpec buildTableAnnotation(String schema, String tableName) {
        AnnotationSpec.Builder b = AnnotationSpec.builder(Table.class)
                .addMember("name", "$S", tableName);

        if (schema != null && !schema.isBlank()) {
            b.addMember("schema", "$S", schema);
        }

        return b.build();
    }

    private void addRelationField(TypeSpec.Builder entity,
                                  NameResolver resolver,
                                  ForeignKeyModel fk,
                                  Set<String> usedFieldNames) {

        String toEntitySimpleName = resolver.entitySimpleName(TableModel.builder().name(fk.getToTable()).build());
        ClassName toEntityType = ClassName.get(resolver.entitiesPackage(), toEntitySimpleName);

        String baseFieldName = NameUtil.toLowerCamel(fk.getToTable());
        String fieldName = uniqueFieldName(baseFieldName, usedFieldNames);
        usedFieldNames.add(fieldName);

        AnnotationSpec relationAnn;
        if (fk.getCardinality() == RelationCardinality.ONE_TO_ONE) {
            relationAnn = AnnotationSpec.builder(OneToOne.class)
                    .addMember("fetch", "$T.LAZY", FetchType.class)
                    .build();
        } else {
            relationAnn = AnnotationSpec.builder(ManyToOne.class)
                    .addMember("fetch", "$T.LAZY", FetchType.class)
                    .build();
        }

        FieldSpec.Builder field = FieldSpec.builder(toEntityType, fieldName)
                .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                .addAnnotation(relationAnn);

        if (fk.getFromColumns().size() == 1) {
            field.addAnnotation(AnnotationSpec.builder(JoinColumn.class)
                    .addMember("name", "$S", fk.getFromColumns().get(0))
                    .addMember("referencedColumnName", "$S", fk.getToColumns().get(0))
                    .addMember("insertable", "$L", false)
                    .addMember("updatable", "$L", false)
                    .build());
        } else {
            AnnotationSpec.Builder joinColumns = AnnotationSpec.builder(JoinColumns.class);
            for (int i = 0; i < fk.getFromColumns().size(); i++) {
                joinColumns.addMember("value", "$L", AnnotationSpec.builder(JoinColumn.class)
                        .addMember("name", "$S", fk.getFromColumns().get(i))
                        .addMember("referencedColumnName", "$S", fk.getToColumns().get(i))
                        .addMember("insertable", "$L", false)
                        .addMember("updatable", "$L", false)
                        .build());
            }
            field.addAnnotation(joinColumns.build());
        }

        entity.addField(field.build());
    }

    private static String uniqueFieldName(String base, Set<String> used) {
        if (!used.contains(base)) {
            return base;
        }
        int i = 1;
        while (used.contains(base + "Ref" + i)) {
            i++;
        }
        return base + "Ref" + i;
    }

    private void writeEmbeddedId(Path outputDir, NameResolver resolver, TableModel table) {
        ClassName idType = embeddedIdClassName(resolver, table);

        TypeSpec.Builder id = TypeSpec.classBuilder(idType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(Serializable.class))
                .addAnnotation(Embeddable.class)
                .addAnnotation(Data.class)
                .addAnnotation(AllArgsConstructor.class)
                .addAnnotation(EqualsAndHashCode.class);

        id.addField(FieldSpec.builder(TypeName.LONG, "serialVersionUID")
                .addModifiers(javax.lang.model.element.Modifier.PRIVATE,
                        javax.lang.model.element.Modifier.STATIC,
                        javax.lang.model.element.Modifier.FINAL)
                .initializer("$LL", 1L)
                .build());

        for (String pkColName : table.getPrimaryKeyColumns()) {
            ColumnModel pkCol = findColumn(table, pkColName)
                    .orElseThrow(() -> new IllegalStateException("PK column not found in table columns: " + pkColName));

            String fieldName = resolver.columnPropertyName(table, pkCol);

            id.addField(FieldSpec.builder(typeResolver.toJavaType(pkCol.getJdbcType()), fieldName)
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .addAnnotation(AnnotationSpec.builder(Column.class)
                            .addMember("name", "$S", pkCol.getName())
                            .addMember("nullable", "$L", false)
                            .build())
                    .build());
        }

        JavaFile javaFile = JavaFile.builder(idType.packageName(), id.build())
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("EmbeddedId generation failed for table: " + table.getName(), ex);
        }
    }

    private static Optional<ColumnModel> findColumn(TableModel table, String columnName) {
        return table.getColumns().stream()
                .filter(c -> columnName.equals(c.getName()))
                .findFirst();
    }

    private static ClassName embeddedIdClassName(NameResolver resolver, TableModel table) {
        String idSimpleName = NameUtil.toUpperCamel(table.getName()) + "Id";
        return ClassName.get(resolver.entityIdsPackage(), idSimpleName);
    }

    private static boolean isJsonColumn(ColumnModel col) {
        String t = safeLower(col.getJdbcTypeName());
        if ("jsonb".equals(t) || "json".equals(t)) {
            return true;
        }
        // Fallback: some drivers expose OTHER with typeName null; we can try the column name heuristics is NOT ok,
        // so we keep it strict. If you want a fallback based on jdbcType, uncomment:
        // return col.getJdbcType() == java.sql.Types.OTHER && (t == null || t.isBlank());
        return false;
    }

    private static String safeLower(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.toLowerCase();
    }
}