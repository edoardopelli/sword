package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.service.records.ScalarFieldInfo;
import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * Generates the REST controller that exposes Resource payloads.
 * Responsibilities:
 * - HTTP exposure only (no business logic).
 * - Delegates to Service (DTO-level) and converts via Resource<->DTO mapper.
 * - Emits CRUD endpoints and a "findBy<Field>" endpoint for each scalar field.
 */
@Component
public class ControllerWriter {

    /**
     * Writes the REST controller source for the given entity.
     *
     * @param controllersPackage Target package for controllers (e.g. "<base>.web.rest").
     * @param servicesPackage    Package of services (e.g. "<base>.service").
     * @param resourcesPackage   Package of resource POJOs (e.g. "<base>.web.resource").
     * @param mappersPackage     Package of web mappers (e.g. "<base>.web.mapper").
     * @param rootPath           Root output path for sources.
     * @param entitySimpleName   Simple entity name (e.g. "Incident").
     * @param idType             JavaPoet TypeName for the id path variable.
     * @param scalarFieldInfos   Scalar fields of the entity (name + TypeName).
     * @param generatedAnn       @Generated annotation to apply on the type.
     */
    public void writeController(String controllersPackage,
                                String servicesPackage,
                                String resourcesPackage,
                                String mappersPackage,
                                Path rootPath,
                                String entitySimpleName,
                                TypeName idType,
                                List<ScalarFieldInfo> scalarFieldInfos,
                                AnnotationSpec generatedAnn) throws IOException {

        // Naming policy: <Entity>Resource as controller name (package distinguishes it from the Resource POJO).
        String controllerName     = entitySimpleName + "Controller";
        String serviceName        = entitySimpleName + "Service";
        String resourceClassName  = entitySimpleName + "Resource";
        String resourceMapperName = entitySimpleName + "ResourceMapper";

        // Common type handles
        ClassName responseEntity = ClassName.get("org.springframework.http", "ResponseEntity");
        ClassName list           = ClassName.get("java.util", "List");
        ClassName mediaType      = ClassName.get("org.springframework.http", "MediaType");

        ClassName serviceType    = ClassName.get(servicesPackage,   serviceName);
        ClassName resourceType   = ClassName.get(resourcesPackage,  resourceClassName);
        ClassName mapperType     = ClassName.get(mappersPackage,    resourceMapperName);

        TypeName listOfResource      = ParameterizedTypeName.get(list, resourceType);
        TypeName responseEntityList  = ParameterizedTypeName.get(responseEntity, listOfResource);
        TypeName responseEntityOne   = ParameterizedTypeName.get(responseEntity, resourceType);
        TypeName responseEntityVoid  = ParameterizedTypeName.get(responseEntity, ClassName.get(Void.class));

        // @RestController, @RequestMapping
        AnnotationSpec restController = AnnotationSpec.builder(
                ClassName.get("org.springframework.web.bind.annotation", "RestController")).build();

        AnnotationSpec reqMapping = AnnotationSpec.builder(
                ClassName.get("org.springframework.web.bind.annotation", "RequestMapping"))
            .addMember("value", "$S", "/" + entitySimpleName.toLowerCase(java.util.Locale.ROOT))
            .build();

        // Fields
        com.squareup.javapoet.FieldSpec serviceField =
            com.squareup.javapoet.FieldSpec.builder(serviceType, "service", Modifier.PRIVATE, Modifier.FINAL).build();
        com.squareup.javapoet.FieldSpec mapperField  =
            com.squareup.javapoet.FieldSpec.builder(mapperType, "mapper",  Modifier.PRIVATE, Modifier.FINAL).build();

        // Ctor
        MethodSpec ctor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(serviceType, "service")
            .addParameter(mapperType,  "mapper")
            .addStatement("this.service = service")
            .addStatement("this.mapper = mapper")
            .build();

        // --- Parameters (annotated) ---
        ParameterSpec pathVarIdParam = ParameterSpec.builder(idType, "id")
            .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "PathVariable"))
            .build();

        ParameterSpec requestBodyParam = ParameterSpec.builder(resourceType, "body")
            .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "RequestBody"))
            .build();

        // --- CRUD endpoints ---

        MethodSpec getAll = MethodSpec.methodBuilder("getAll")
            .addJavadoc("Returns the full list of resources.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaType).build())
            .returns(responseEntityList)
            .addStatement("return $T.ok(mapper.toResourceList(service.findAll()))", responseEntity)
            .build();

        MethodSpec getById = MethodSpec.methodBuilder("getById")
            .addJavadoc("Returns one resource by id.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                .addMember("value", "$S", "/{id}")
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaType).build())
            .addParameter(pathVarIdParam)
            .returns(responseEntityOne)
            .addStatement("return $T.ok(mapper.toResource(service.findById(id)))", responseEntity)
            .build();

        MethodSpec create = MethodSpec.methodBuilder("create")
            .addJavadoc("Creates and returns the persisted resource.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PostMapping"))
                .addMember("consumes", "$T.APPLICATION_JSON_VALUE", mediaType)
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaType).build())
            .addParameter(requestBodyParam)
            .returns(responseEntityOne)
            .addStatement("return $T.ok(mapper.toResource(service.create(mapper.toDto(body))))", responseEntity)
            .build();

        MethodSpec update = MethodSpec.methodBuilder("update")
            .addJavadoc("Updates and returns the resource.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PutMapping"))
                .addMember("value", "$S", "/{id}")
                .addMember("consumes", "$T.APPLICATION_JSON_VALUE", mediaType)
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaType).build())
            .addParameter(pathVarIdParam)
            .addParameter(requestBodyParam)
            .returns(responseEntityOne)
            .addStatement("return $T.ok(mapper.toResource(service.update(id, mapper.toDto(body))))", responseEntity)
            .build();

        MethodSpec delete = MethodSpec.methodBuilder("delete")
            .addJavadoc("Deletes the resource by id.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "DeleteMapping"))
                .addMember("value", "$S", "/{id}").build())
            .addParameter(pathVarIdParam)
            .returns(responseEntityVoid)
            .addStatement("service.delete(id)")
            .addStatement("return $T.noContent().build()", responseEntity)
            .build();

        // --- Controller type builder ---
        TypeSpec.Builder type = TypeSpec.classBuilder(controllerName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(restController)
            .addAnnotation(reqMapping)
            .addAnnotation(generatedAnn)
            .addField(serviceField)
            .addField(mapperField)
            .addMethod(ctor)
            .addMethod(getAll)
            .addMethod(getById)
            .addMethod(create)
            .addMethod(update)
            .addMethod(delete);

        // --- findBy<Field> endpoints for each scalar field ---
        for (ScalarFieldInfo f : scalarFieldInfos) {
            // Skip id because it's already exposed by the "/{id}" endpoint
            if ("id".equals(f.javaFieldName())) continue;

            String field = f.javaFieldName();
            String cap   = Character.toUpperCase(field.charAt(0)) + field.substring(1);

            // @RequestParam <Type> <field>
            ParameterSpec reqParam = ParameterSpec.builder(f.javaType(), field)
                .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                .build();

            MethodSpec findBy = MethodSpec.methodBuilder("findBy" + cap)
                .addJavadoc("Returns resources filtered by {@code $L}.\n", field)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                    .addMember("value", "$S", "/by-" + field)
                    .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaType).build())
                .addParameter(reqParam)
                .returns(responseEntityList)
                // service.findBy<Field>(param) → List<DTO> → mapper.toResourceList(...)
                .addStatement("return $T.ok(mapper.toResourceList(service.findBy$L($L)))",
                        responseEntity, cap, field)
                .build();

            type.addMethod(findBy);
        }

        JavaFile.builder(controllersPackage, type.build()).build().writeTo(rootPath);
    }
}