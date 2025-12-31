package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

public class EntityDtoMapperWriter {

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        String mapperSimpleName = NameUtil.toUpperCamel(table.getName()) + "EntityMapper";
        ClassName mapperType = ClassName.get(resolver.mappersPackage(), mapperSimpleName);

        ClassName entityType = ClassName.get(resolver.entitiesPackage(), resolver.entitySimpleName(table));
        ClassName dtoType = ClassName.get(resolver.dtosPackage(), resolver.dtoSimpleName(table));

        MethodSpec toDto = buildToDto(resolver, table, entityType, dtoType);
        MethodSpec toEntity = buildToEntity(resolver, table, entityType, dtoType);

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

    private MethodSpec buildToDto(NameResolver resolver, TableModel table, ClassName entityType, ClassName dtoType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("toDto")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.ABSTRACT)
                .returns(dtoType)
                .addParameter(entityType, "entity");

        if (table.hasCompositePrimaryKey()) {
            AnnotationSpec.Builder mappings = AnnotationSpec.builder(Mappings.class);
            for (String pkColName : table.getPrimaryKeyColumns()) {
                ColumnModel pkCol = findColumn(table, pkColName)
                        .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));

                String dtoField = resolver.columnPropertyName(table, pkCol);
                mappings.addMember("value", "$L", AnnotationSpec.builder(Mapping.class)
                        .addMember("target", "$S", dtoField)
                        .addMember("source", "$S", "id." + dtoField)
                        .build());
            }
            m.addAnnotation(mappings.build());
        }

        return m.build();
    }

    private MethodSpec buildToEntity(NameResolver resolver, TableModel table, ClassName entityType, ClassName dtoType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("toEntity")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.ABSTRACT)
                .returns(entityType)
                .addParameter(dtoType, "dto");

        if (table.hasCompositePrimaryKey()) {
            ClassName idClass = ClassName.get(resolver.entityIdsPackage(), NameUtil.toUpperCamel(table.getName()) + "Id");

            String ctorArgs = table.getPrimaryKeyColumns().stream()
                    .map(pkColName -> {
                        ColumnModel pkCol = findColumn(table, pkColName)
                                .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));
                        String dtoField = resolver.columnPropertyName(table, pkCol);
                        return "dto.get" + NameUtil.toUpperCamel(dtoField) + "()";
                    })
                    .collect(Collectors.joining(", "));

            // MapStruct does not guarantee nested target instantiation; build EmbeddedId explicitly.
            m.addAnnotation(AnnotationSpec.builder(Mapping.class)
                    .addMember("target", "$S", "id")
                    .addMember("expression", "$S", "java(new " + idClass.simpleName() + "(" + ctorArgs + "))")
                    .build());
        }

        return m.build();
    }

    private static Optional<ColumnModel> findColumn(TableModel table, String columnName) {
        return table.getColumns().stream()
                .filter(c -> columnName.equals(c.getName()))
                .findFirst();
    }
}