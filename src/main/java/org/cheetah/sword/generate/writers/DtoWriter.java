package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.Data;

public class DtoWriter {

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        ClassName dtoType = ClassName.get(resolver.dtosPackage(), resolver.dtoSimpleName(table));

        TypeSpec.Builder type = TypeSpec.classBuilder(dtoType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(Data.class);

        Set<String> usedFieldNames = new HashSet<>();

        // Composite PK: DTO must contain individual PK fields (no EmbeddedId in DTO).
        // Single PK: DTO contains the PK field like any other column.
        for (ColumnModel col : table.getColumns()) {
            boolean isPkCol = table.getPrimaryKeyColumns() != null && table.getPrimaryKeyColumns().contains(col.getName());

            // In DTO we keep PK columns as fields even for composite PK (requirement).
            // So: never skip PK columns here.
            String fieldName = resolver.columnPropertyName(table, col);
            fieldName = uniqueFieldName(fieldName, usedFieldNames);
            usedFieldNames.add(fieldName);

            TypeName fieldType = resolveDtoFieldType(col);

            FieldSpec field = FieldSpec.builder(fieldType, fieldName)
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .build();

            type.addField(field);
        }

        JavaFile javaFile = JavaFile.builder(dtoType.packageName(), type.build())
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("DTO generation failed for table: " + table.getName(), ex);
        }
    }

    private TypeName resolveDtoFieldType(ColumnModel col) {
        if (isJsonColumn(col)) {
            return ParameterizedTypeName.get(
                    ClassName.get(java.util.Map.class),
                    ClassName.get(String.class),
                    ClassName.get(Object.class));
        }
        return typeResolver.toJavaType(col.getJdbcType());
    }

    private static String uniqueFieldName(String base, Set<String> used) {
        if (!used.contains(base)) {
            return base;
        }
        int i = 1;
        while (used.contains(base + i)) {
            i++;
        }
        return base + i;
    }

    private static boolean isJsonColumn(ColumnModel col) {
        String t = safeLower(col.getJdbcTypeName());
        if (t == null) {
            return false;
        }
        return "jsonb".equals(t) || "json".equals(t);
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