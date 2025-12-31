package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeNameParser;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.yaml.YamlSpec;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.Data;

public class ResourceWriter {

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        ClassName resType = ClassName.get(resolver.resourcesPackage(), resolver.resourceSimpleName(table));

        // Start from DTO-equivalent fields (default behavior).
        Map<String, TypeName> resourceFields = new LinkedHashMap<>();
        for (ColumnModel col : table.getColumns()) {
            String dtoField = resolver.columnPropertyName(table, col);
            resourceFields.put(dtoField, typeResolver.toJavaType(col.getJdbcType()));
        }

        // Apply YAML overrides (rename/type changes) only if present (yaml-driven).
        YamlSpec.ResourceTableOverride overrides = resolver.resourceOverride(table);
        if (overrides != null && overrides.getFields() != null && !overrides.getFields().isEmpty()) {
            for (Map.Entry<String, YamlSpec.ResourceFieldOverride> e : overrides.getFields().entrySet()) {
                String targetResourceField = e.getKey();
                YamlSpec.ResourceFieldOverride cfg = e.getValue();
                if (cfg == null || cfg.getSourceDtoField() == null || cfg.getSourceDtoField().isBlank()) {
                    continue;
                }

                String sourceDtoField = cfg.getSourceDtoField().trim();

                // Rename semantics: remove original field if target differs.
                if (!targetResourceField.equals(sourceDtoField)) {
                    resourceFields.remove(sourceDtoField);
                }

                TypeName targetType;
                if (cfg.getJavaType() != null && !cfg.getJavaType().isBlank()) {
                    targetType = TypeNameParser.parse(cfg.getJavaType());
                } else {
                    // If no override type, keep the source DTO field type if it exists.
                    targetType = resourceFields.getOrDefault(sourceDtoField, ClassName.get(Object.class));
                }

                resourceFields.put(targetResourceField, targetType);
            }
        }

        TypeSpec.Builder type = TypeSpec.classBuilder(resType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(Data.class);

        for (Map.Entry<String, TypeName> f : resourceFields.entrySet()) {
            type.addField(FieldSpec.builder(f.getValue(), f.getKey())
                    .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                    .build());
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
}