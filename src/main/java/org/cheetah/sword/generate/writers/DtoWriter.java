package org.cheetah.sword.generate.writers;

import java.nio.file.Path;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import lombok.Data;

public class DtoWriter {

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        ClassName dtoType = ClassName.get(resolver.dtosPackage(), resolver.dtoSimpleName(table));

        TypeSpec.Builder type = TypeSpec.classBuilder(dtoType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(Data.class);

        for (ColumnModel col : table.getColumns()) {
            String fieldName = resolver.columnPropertyName(table, col);
            FieldSpec field = FieldSpec.builder(typeResolver.toJavaType(col.getJdbcType()), fieldName)
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
}