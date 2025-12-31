package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.stream.Collectors;

import org.cheetah.sword.generate.IdTypeHelper;
import org.cheetah.sword.model.TableModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public class EntityDtoMapperWriter {

    private final IdTypeHelper idTypeHelper = new IdTypeHelper();

    public void write(Path outputDir, String basePackage, TableModel table) {
        String mapperName = IdTypeHelper.toPascalCase(table.getName()) + "EntityMapper";
        ClassName mapperType = ClassName.get(basePackage + ".mappers", mapperName);

        ClassName entityType = ClassName.get(basePackage + ".entities", IdTypeHelper.toPascalCase(table.getName()) + "Entity");
        ClassName dtoType = ClassName.get(basePackage + ".dtos", IdTypeHelper.toPascalCase(table.getName()) + "DTO");

        MethodSpec toDto = buildToDto(table, entityType, dtoType);
        MethodSpec toEntity = buildToEntity(table, entityType, dtoType, basePackage);

        TypeSpec type = TypeSpec.interfaceBuilder(mapperType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Mapper.class)
                        .addMember("componentModel", "$S", "spring")
                        .build())
                .addMethod(toDto)
                .addMethod(toEntity)
                .build();

        JavaFile javaFile = JavaFile.builder(mapperType.packageName(), type)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("Entity<->DTO mapper generation failed for table: " + table.getName(), ex);
        }
    }

    private MethodSpec buildToDto(TableModel table, ClassName entityType, ClassName dtoType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("toDto")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.ABSTRACT)
                .returns(dtoType)
                .addParameter(entityType, "entity");

        if (table.hasCompositePrimaryKey()) {
            AnnotationSpec.Builder mappings = AnnotationSpec.builder(Mappings.class);
            for (String pk : table.getPrimaryKeyColumns()) {
                String f = IdTypeHelper.toCamelCase(pk);
                mappings.addMember("value", "$L", AnnotationSpec.builder(Mapping.class)
                        .addMember("target", "$S", f)
                        .addMember("source", "$S", "id." + f)
                        .build());
            }
            m.addAnnotation(mappings.build());
        }

        return m.build();
    }

    private MethodSpec buildToEntity(TableModel table, ClassName entityType, ClassName dtoType, String basePackage) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("toEntity")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.ABSTRACT)
                .returns(entityType)
                .addParameter(dtoType, "dto");

        if (table.hasCompositePrimaryKey()) {
            ClassName idClass = idTypeHelper.embeddedIdClassName(basePackage, table);
            String args = table.getPrimaryKeyColumns().stream()
                    .map(pk -> "dto.get" + IdTypeHelper.toPascalCase(pk) + "()")
                    .collect(Collectors.joining(", "));

            // MapStruct nested target instantiation is not guaranteed; build the EmbeddedId explicitly.
            m.addAnnotation(AnnotationSpec.builder(Mapping.class)
                    .addMember("target", "$S", "id")
                    .addMember("expression", "$S", "java(new " + idClass.simpleName() + "(" + args + "))")
                    .build());
        }

        return m.build();
    }
}