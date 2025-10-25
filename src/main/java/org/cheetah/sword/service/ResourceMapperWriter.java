package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.lang.model.element.Modifier;

import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * This writer generates a MapStruct mapper converting between Resource and DTO.
 */
@Component
public class ResourceMapperWriter {

    /**
     * Generates the Resource<->DTO mapper interface.
     *
     * @param mappersPackage   Target package for web mappers (e.g. baseRoot + ".web.mapper").
     * @param dtoPackage       Package of DTOs (e.g. baseRoot + ".service.dto").
     * @param resourcesPackage Package of Resources (e.g. baseRoot + ".web.resource").
     * @param rootPath         Root output path.
     * @param entitySimpleName Simple name (e.g. "Incident").
     * @param generatedAnn     Generated annotation for traceability.
     */
    public void writeResourceMapper(String mappersPackage,
                                    String dtoPackage,
                                    String resourcesPackage,
                                    Path rootPath,
                                    String entitySimpleName,
                                    AnnotationSpec generatedAnn) throws IOException {

        String dtoName = entitySimpleName + "DTO";
        String resourceName = entitySimpleName + "Resource";
        String mapperName = entitySimpleName + "ResourceMapper";

        ClassName dtoType = ClassName.get(dtoPackage, dtoName);
        ClassName resourceType = ClassName.get(resourcesPackage, resourceName);
        ClassName listType = ClassName.get("java.util", "List");
        ClassName mapperAnn = ClassName.get("org.mapstruct", "Mapper");

        TypeName dtoList = ParameterizedTypeName.get(listType, dtoType);
        TypeName resourceList = ParameterizedTypeName.get(listType, resourceType);

        MethodSpec toResource = MethodSpec.methodBuilder("toResource")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(resourceType)
            .addParameter(dtoType, "dto")
            .build();

        MethodSpec toDto = MethodSpec.methodBuilder("toDto")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(dtoType)
            .addParameter(resourceType, "resource")
            .build();

        MethodSpec toResourceList = MethodSpec.methodBuilder("toResourceList")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(resourceList)
            .addParameter(dtoList, "dtos")
            .build();

        MethodSpec toDtoList = MethodSpec.methodBuilder("toDtoList")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(dtoList)
            .addParameter(resourceList, "resources")
            .build();

        TypeSpec type = TypeSpec.interfaceBuilder(mapperName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(mapperAnn)
                .addMember("componentModel", "$S", "spring").build())
            .addAnnotation(generatedAnn)
            .addMethods(Arrays.asList(toResource, toDto, toResourceList, toDtoList))
            .build();

        JavaFile.builder(mappersPackage, type).build().writeTo(rootPath);
    }
}
