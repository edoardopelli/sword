package org.cheetah.sword.generate.writers;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.cheetah.sword.generate.IdTypeHelper;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.ForeignKeyModel;
import org.cheetah.sword.model.RelationCardinality;
import org.cheetah.sword.model.TableModel;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

public class EntityWriter {

    private final TypeResolver typeResolver = new TypeResolver();
    private final IdTypeHelper idTypeHelper = new IdTypeHelper();

    public void write(Path outputDir, String basePackage, String schema, TableModel table) {
        String className = IdTypeHelper.toPascalCase(table.getName()) + "Entity";
        ClassName entityType = ClassName.get(basePackage + ".entities", className);

        // Collect FK columns to avoid generating duplicate scalar fields (we *do* want FK scalar fields).
        // For persistence simplicity we keep FK scalar fields and add relation fields as read-only (insertable/updatable=false).
        Set<String> fkColumns = new HashSet<>();
        if (table.getForeignKeys() != null) {
            for (ForeignKeyModel fk : table.getForeignKeys()) {
                fkColumns.addAll(fk.getFromColumns());
            }
        }

        TypeSpec.Builder type = TypeSpec.classBuilder(entityType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(Data.class)
                .addAnnotation(Entity.class)
                .addAnnotation(AnnotationSpec.builder(Table.class)
                        .addMember("name", "$S", table.getName())
                        .addMember("schema", "$S", schema == null ? "" : schema)
                        .build());

        if (table.hasCompositePrimaryKey()) {
            ClassName idClass = idTypeHelper.embeddedIdClassName(basePackage, table);
            type.addField(FieldSpec.builder(idClass, "id")
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .addAnnotation(EmbeddedId.class)
                    .build());

            writeEmbeddedId(outputDir, basePackage, table);
        }

        for (ColumnModel col : table.getColumns()) {
            boolean isPkCol = table.getPrimaryKeyColumns() != null && table.getPrimaryKeyColumns().contains(col.getName());

            // Composite PK columns are stored in the EmbeddedId class (not as direct fields).
            if (table.hasCompositePrimaryKey() && isPkCol) {
                continue;
            }

            FieldSpec.Builder field = FieldSpec.builder(typeResolver.toJavaType(col.getJdbcType()), IdTypeHelper.toCamelCase(col.getName()))
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .addAnnotation(AnnotationSpec.builder(Column.class)
                            .addMember("name", "$S", col.getName())
                            .addMember("nullable", "$L", col.isNullable())
                            .build());

            if (table.hasSinglePrimaryKey() && isPkCol) {
                field.addAnnotation(Id.class);
            }

            type.addField(field.build());
        }

        // Relations (read-only to avoid requiring entity resolution at write time)
        if (table.getForeignKeys() != null) {
            for (ForeignKeyModel fk : table.getForeignKeys()) {
                addRelationField(type, basePackage, fk);
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

    private void addRelationField(TypeSpec.Builder entity, String basePackage, ForeignKeyModel fk) {
        String toEntityName = IdTypeHelper.toPascalCase(fk.getToTable()) + "Entity";
        ClassName toEntityType = ClassName.get(basePackage + ".entities", toEntityName);

        String fieldName = IdTypeHelper.toCamelCase(fk.getToTable());

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

    private void writeEmbeddedId(Path outputDir, String basePackage, TableModel table) {
        ClassName idType = idTypeHelper.embeddedIdClassName(basePackage, table);

        TypeSpec.Builder id = TypeSpec.classBuilder(idType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(Serializable.class))
                .addAnnotation(Embeddable.class)
                .addAnnotation(Data.class)
                .addAnnotation(NoArgsConstructor.class)
                .addAnnotation(AllArgsConstructor.class)
                .addAnnotation(EqualsAndHashCode.class);

        id.addField(FieldSpec.builder(TypeName.LONG, "serialVersionUID")
                .addModifiers(javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.STATIC, javax.lang.model.element.Modifier.FINAL)
                .initializer("$LL", 1L)
                .build());

        for (String pkCol : table.getPrimaryKeyColumns()) {
            ColumnModel col = table.getColumns().stream()
                    .filter(c -> pkCol.equals(c.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("PK column not found in columns list: " + pkCol));

            id.addField(FieldSpec.builder(typeResolver.toJavaType(col.getJdbcType()), IdTypeHelper.toCamelCase(pkCol))
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .addAnnotation(AnnotationSpec.builder(Column.class)
                            .addMember("name", "$S", pkCol)
                            .addMember("nullable", "$L", false)
                            .build())
                    .build());
        }

        // Ensure stable constructor order: pk column order in metadata list is used as-is.
        MethodSpec ctor = MethodSpec.constructorBuilder()
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addComment("Generated constructor is provided by Lombok @AllArgsConstructor.")
                .build();
        id.addMethod(ctor);

        JavaFile javaFile = JavaFile.builder(idType.packageName(), id.build())
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("EmbeddedId generation failed for table: " + table.getName(), ex);
        }
    }
}