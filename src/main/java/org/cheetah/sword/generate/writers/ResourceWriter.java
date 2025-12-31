package org.cheetah.sword.generate.writers;

import java.nio.file.Path;

import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import lombok.Data;

public class ResourceWriter {

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, String basePackage, TableModel table) {
        String className = toPascalCase(table.getName()) + "Resource";
        ClassName resType = ClassName.get(basePackage + ".resources", className);

        TypeSpec.Builder type = TypeSpec.classBuilder(resType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(Data.class);

        for (ColumnModel col : table.getColumns()) {
            FieldSpec field = FieldSpec.builder(typeResolver.toJavaType(col.getJdbcType()), toCamelCase(col.getName()))
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .build();
            type.addField(field);
        }

        JavaFile javaFile = JavaFile.builder(resType.packageName(), type.build())
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("Resource generation failed for table: " + table.getName(), ex);
        }
    }

    private static String toPascalCase(String s) {
        String camel = toCamelCase(s);
        return camel.isEmpty() ? camel : Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    private static String toCamelCase(String s) {
        String[] parts = s.toLowerCase().split("[^a-z0-9]+");
        if (parts.length == 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }
}