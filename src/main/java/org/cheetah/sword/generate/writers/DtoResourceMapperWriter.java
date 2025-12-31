package org.cheetah.sword.generate.writers;

import java.nio.file.Path;

import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public class DtoResourceMapperWriter {

    public void write(Path outputDir, String basePackage, TableModel table) {
        String mapperName = toPascalCase(table.getName()) + "ResourceMapper";
        ClassName mapperType = ClassName.get(basePackage + ".mappers", mapperName);

        ClassName dtoType = ClassName.get(basePackage + ".dtos", toPascalCase(table.getName()) + "DTO");
        ClassName resType = ClassName.get(basePackage + ".resources", toPascalCase(table.getName()) + "Resource");

        AnnotationSpec.Builder mappingsToRes = AnnotationSpec.builder(Mappings.class);
        AnnotationSpec.Builder mappingsToDto = AnnotationSpec.builder(Mappings.class);

        for (ColumnModel col : table.getColumns()) {
            String field = toCamelCase(col.getName());
            mappingsToRes.addMember("value", "$L", AnnotationSpec.builder(Mapping.class)
                    .addMember("target", "$S", field)
                    .addMember("source", "$S", field)
                    .build());
            mappingsToDto.addMember("value", "$L", AnnotationSpec.builder(Mapping.class)
                    .addMember("target", "$S", field)
                    .addMember("source", "$S", field)
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

    private static String toPascalCase(String s) {
        String[] parts = s.toLowerCase().split("[^a-z0-9]+");
        if (parts.length == 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
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