package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.yaml.YamlSpec;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public class DtoResourceMapperWriter {

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        String mapperName = resolver.resourceSimpleName(table) + "Mapper";
        ClassName mapperType = ClassName.get(resolver.mappersPackage(), mapperName);

        ClassName dtoType = ClassName.get(resolver.dtosPackage(), resolver.dtoSimpleName(table));
        ClassName resType = ClassName.get(resolver.resourcesPackage(), resolver.resourceSimpleName(table));

        // Build resource field map: targetResourceField -> sourceDtoField
        Map<String, String> resourceToDto = buildResourceToDtoFieldMap(resolver, table);

        AnnotationSpec.Builder mappingsToRes = AnnotationSpec.builder(Mappings.class);
        AnnotationSpec.Builder mappingsToDto = AnnotationSpec.builder(Mappings.class);

        // toResource: target=resourceField, source=dtoField
        for (Map.Entry<String, String> e : resourceToDto.entrySet()) {
            mappingsToRes.addMember("value", "$L",
                    AnnotationSpec.builder(Mapping.class)
                            .addMember("target", "$S", e.getKey())
                            .addMember("source", "$S", e.getValue())
                            .build());
        }

        // toDto: target=dtoField, source=resourceField (reverse)
        // Use the same pairs reversed to keep explicit mappings (rename-safe).
        for (Map.Entry<String, String> e : resourceToDto.entrySet()) {
            mappingsToDto.addMember("value", "$L",
                    AnnotationSpec.builder(Mapping.class)
                            .addMember("target", "$S", e.getValue())
                            .addMember("source", "$S", e.getKey())
                            .build());
        }

        MethodSpec toResource = MethodSpec.methodBuilder("toResource")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.ABSTRACT)
                .returns(resType)
                .addParameter(dtoType, "dto")
                .addAnnotation(mappingsToRes.build())
                .build();

        MethodSpec toDto = MethodSpec.methodBuilder("toDto")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.ABSTRACT)
                .returns(dtoType)
                .addParameter(resType, "resource")
                .addAnnotation(mappingsToDto.build())
                .build();

        TypeSpec type = TypeSpec.interfaceBuilder(mapperType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Mapper.class)
                        .addMember("componentModel", "$S", "spring")
                        .build())
                .addMethod(toResource)
                .addMethod(toDto)
                .build();

        JavaFile javaFile = JavaFile.builder(mapperType.packageName(), type)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("DTO<->Resource mapper generation failed for table: " + table.getName(), ex);
        }
    }

    private Map<String, String> buildResourceToDtoFieldMap(NameResolver resolver, TableModel table) {
        // Default: 1:1 mapping based on DTO field names.
        Map<String, String> resourceToDto = new LinkedHashMap<>();
        for (ColumnModel col : table.getColumns()) {
            String dtoField = resolver.columnPropertyName(table, col);
            resourceToDto.put(dtoField, dtoField);
        }

        YamlSpec.ResourceTableOverride overrides = resolver.resourceOverride(table);
        if (overrides == null || overrides.getFields() == null) {
            return resourceToDto;
        }

        for (Map.Entry<String, YamlSpec.ResourceFieldOverride> e : overrides.getFields().entrySet()) {
            String targetResourceField = e.getKey();
            YamlSpec.ResourceFieldOverride cfg = e.getValue();
            if (cfg == null || cfg.getSourceDtoField() == null || cfg.getSourceDtoField().isBlank()) {
                continue;
            }

            String sourceDtoField = cfg.getSourceDtoField().trim();

            // Rename semantics: remove original 1:1 mapping if target differs.
            if (!targetResourceField.equals(sourceDtoField)) {
                resourceToDto.remove(sourceDtoField);
            }
            resourceToDto.put(targetResourceField, sourceDtoField);
        }

        return resourceToDto;
    }
}