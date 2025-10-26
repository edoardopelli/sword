package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.service.records.ScalarFieldInfo;
import org.cheetah.sword.util.NamingUtils;
import org.cheetah.sword.wizard.SwordWizard;
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
     * @param SwordWizard.SERVICE_PKG    Package of services (e.g. "<base>.service").
     * @param resourcesPackage   Package of resource POJOs (e.g. "<base>.web.resource").
     * @param mappersPackage     Package of web mappers (e.g. "<base>.web.mapper").
     * @param rootPath           Root output path for sources.
     * @param entitySimpleName   Simple entity name (e.g. "Incident").
     * @param idType             JavaPoet TypeName for the id path variable.
     * @param scalarFieldInfos   Scalar fields of the entity (name + TypeName).
     * @param generatedAnn       @Generated annotation to apply on the type.
     */
    public void writeController(
                                Path rootPath,
                                String entitySimpleName,
                                TypeName idType,
                                List<ScalarFieldInfo> scalarFieldInfos,
                                AnnotationSpec generatedAnn) throws IOException {

        // Naming policy: <Entity>Resource as controller name (package distinguishes it from the Resource POJO).
        String controllerSimpleName     = NamingUtils.pluralizeSimpleName(entitySimpleName) + "Controller";
        String serviceSimpleName        = NamingUtils.pluralizeSimpleName(entitySimpleName) + "Service";
        System.out.println("ControllerName: "+ controllerSimpleName);
        System.out.println("\tServiceName: "+ serviceSimpleName);
        String resourceSimpleName       = entitySimpleName + "Resource";
        String resourceMapperSimpleName = entitySimpleName + "ResourceMapper";
        String dtoSimpleName            = entitySimpleName + "Dto";

        // Common type handles
        ClassName responseEntityClass   = ClassName.get("org.springframework.http", "ResponseEntity");
        ClassName pageDtoClass          = ClassName.get(SwordWizard.DTO_PKG, "PageDto");
        ClassName pageResourceClass     = ClassName.get(SwordWizard.RESOURCES_PKG, "PageResource");

        ClassName mediaTypeClass        = ClassName.get("org.springframework.http", "MediaType");

        ClassName serviceClass          = ClassName.get(SwordWizard.SERVICE_PKG,   serviceSimpleName);
        ClassName resourceClass         = ClassName.get(SwordWizard.RESOURCES_PKG, resourceSimpleName);
        ClassName dtoClass              = ClassName.get(SwordWizard.DTO_PKG,       dtoSimpleName);
        ClassName resourceMapperClass   = ClassName.get(SwordWizard.RESOURCE_MAPPERS_PKG, resourceMapperSimpleName);
        ClassName pageResourceRawClass  = ClassName.get(SwordWizard.RESOURCES_PKG, "PageResource");

        ClassName listRawClass          = ClassName.get("java.util", "List");

        TypeName resourceListType               = ParameterizedTypeName.get(listRawClass, resourceClass);
        TypeName pageResourceOfResourceType     = ParameterizedTypeName.get(pageResourceRawClass, resourceClass);
        TypeName pageDtoOfDtoType               = ParameterizedTypeName.get(pageDtoClass, dtoClass);
        TypeName responseEntityOfResourceList   = ParameterizedTypeName.get(responseEntityClass, resourceListType);
        TypeName responseEntityOfPageResource   = ParameterizedTypeName.get(responseEntityClass, pageResourceOfResourceType);
        TypeName responseEntityOfResource       = ParameterizedTypeName.get(responseEntityClass, resourceClass);
        TypeName responseEntityOfVoid           = ParameterizedTypeName.get(responseEntityClass, ClassName.get(Void.class));

        // @RestController, @RequestMapping
        AnnotationSpec restControllerAnnotation = AnnotationSpec.builder(
                ClassName.get("org.springframework.web.bind.annotation", "RestController")).build();

        AnnotationSpec requestMappingAnnotation = AnnotationSpec.builder(
                ClassName.get("org.springframework.web.bind.annotation", "RequestMapping"))
            .addMember("value", "$S", "/" + entitySimpleName.toLowerCase(java.util.Locale.ROOT))
            .build();

        // Fields
        com.squareup.javapoet.FieldSpec serviceFieldSpec =
            com.squareup.javapoet.FieldSpec.builder(serviceClass, "service", Modifier.PRIVATE, Modifier.FINAL).build();
        com.squareup.javapoet.FieldSpec mapperFieldSpec  =
            com.squareup.javapoet.FieldSpec.builder(resourceMapperClass, "mapper",  Modifier.PRIVATE, Modifier.FINAL).build();

        // Ctor
        MethodSpec constructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(serviceClass, "service")
            .addParameter(resourceMapperClass,  "mapper")
            .addStatement("this.service = service")
            .addStatement("this.mapper = mapper")
            .build();

        // --- Parameters (annotated) ---
        ParameterSpec idPathVariableParam = ParameterSpec.builder(idType, "id")
            .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "PathVariable"))
            .build();

        ParameterSpec resourceRequestBodyParam = ParameterSpec.builder(resourceClass, "body")
            .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "RequestBody"))
            .build();

        ParameterSpec pageNumberRequestParam = ParameterSpec.builder(TypeName.INT, "pageNumber")
                .addAnnotation(ClassName.get("org.springframework.web.bind.annotation","RequestParam"))
                .build();

        ParameterSpec pageSizeRequestParam = ParameterSpec.builder(TypeName.INT, "pageSize")
                .addAnnotation(ClassName.get("org.springframework.web.bind.annotation","RequestParam"))
                .build();

        ParameterSpec pageDtoParamSpec = ParameterSpec.builder(pageDtoOfDtoType, "pageDto").build();

        // --- CRUD endpoints ---

        MethodSpec getAll = MethodSpec.methodBuilder("getAll")
            .addJavadoc("Returns the full list of resources.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaTypeClass).build())
            .returns(responseEntityOfPageResource)
            .addParameter(pageNumberRequestParam)
            .addParameter(pageSizeRequestParam)
            .addStatement("$T pageDto = service.findAll(pageNumber,pageSize)", pageDtoOfDtoType)
            .addStatement("$T pageResource = toPageResource(pageDto)", pageResourceOfResourceType)
            .addStatement("return $T.ok(pageResource)", responseEntityClass)
            .build();

        MethodSpec toPageResourceMethod = MethodSpec.methodBuilder("toPageResource")
                .addJavadoc("Transforms a PageDto in a PageResource")
                .addModifiers(Modifier.PRIVATE)
                .returns(pageResourceOfResourceType)
                .addParameter(pageDtoParamSpec)
                .addStatement("$T content = pageDto.getContent().stream().map(mapper::toResource).toList()", resourceListType)
                .addStatement("return $T.<$T>builder().content(content).pageNumber(pageDto.getPageNumber()).pageSize(pageDto.getPageSize()).totalElements(pageDto.getTotalElements()).totalPages(pageDto.getTotalPages()).build()", pageResourceClass, resourceClass)
                .build();

        /*
         * 
         * 
         *     List<AddressDto> dtoList = page.getContent().stream().map(mapper::toDto).toList();
         *     return PageDto.<AddressDto>builder().content(dtoList).pageNumber(page.getNumber()).pageSize(page.getSize()).totalElements(page.getTotalElements()).totalPages(page.getTotalPages()).build();
         *
         */
        MethodSpec getById = MethodSpec.methodBuilder("getById")
            .addJavadoc("Returns one resource by id.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                .addMember("value", "$S", "/{id}")
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaTypeClass).build())
            .addParameter(idPathVariableParam)
            .returns(responseEntityOfResource)
            .addStatement("return $T.ok(mapper.toResource(service.findById(id)))", responseEntityClass)
            .build();

        MethodSpec create = MethodSpec.methodBuilder("create")
            .addJavadoc("Creates and returns the persisted resource.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PostMapping"))
                .addMember("consumes", "$T.APPLICATION_JSON_VALUE", mediaTypeClass)
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaTypeClass).build())
            .addParameter(resourceRequestBodyParam)
            .returns(responseEntityOfResource)
            .addStatement("return $T.ok(mapper.toResource(service.save(mapper.toDto(body))))", responseEntityClass)
            .build();

        MethodSpec update = MethodSpec.methodBuilder("update")
            .addJavadoc("Updates and returns the resource.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PutMapping"))
                .addMember("value", "$S", "/{id}")
                .addMember("consumes", "$T.APPLICATION_JSON_VALUE", mediaTypeClass)
                .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaTypeClass).build())
            .addParameter(idPathVariableParam)
            .addParameter(resourceRequestBodyParam)
            .returns(responseEntityOfResource)
            .addStatement("return $T.ok(mapper.toResource(service.update(id, mapper.toDto(body))))", responseEntityClass)
            .build();

        MethodSpec delete = MethodSpec.methodBuilder("delete")
            .addJavadoc("Deletes the resource by id.\n")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "DeleteMapping"))
                .addMember("value", "$S", "/{id}").build())
            .addParameter(idPathVariableParam)
            .returns(responseEntityOfVoid)
            .addStatement("service.delete(id)")
            .addStatement("return $T.noContent().build()", responseEntityClass)
            .build();

        // --- Controller type builder ---
        TypeSpec.Builder controllerTypeBuilder = TypeSpec.classBuilder(controllerSimpleName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(restControllerAnnotation)
            .addAnnotation(requestMappingAnnotation)
            .addAnnotation(generatedAnn)
            .addField(serviceFieldSpec)
            .addField(mapperFieldSpec)
            .addMethod(constructor)
            .addMethod(getAll)
            .addMethod(getById)
            .addMethod(create)
            .addMethod(update)
            .addMethod(delete)
            .addMethod(toPageResourceMethod);

        // --- findBy<Field> endpoints for each scalar field ---
        for (ScalarFieldInfo fieldInfo : scalarFieldInfos) {
            // Skip id because it's already exposed by the "/{id}" endpoint
            if ("id".equals(fieldInfo.javaFieldName())) continue;

            String fieldName = fieldInfo.javaFieldName();
            String capitalizedFieldName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

            // @RequestParam <Type> <field>
            ParameterSpec requestParamForField = ParameterSpec.builder(fieldInfo.javaType(), fieldName)
                .addAnnotation(ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                .build();

            MethodSpec findByMethodSpec = MethodSpec.methodBuilder("findBy" + capitalizedFieldName)
                .addJavadoc("Returns resources filtered by {@code $L}.\n", fieldName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "GetMapping"))
                    .addMember("value", "$S", "/by-" + fieldName)
                    .addMember("produces", "$T.APPLICATION_JSON_VALUE", mediaTypeClass).build())
                .addParameter(requestParamForField)
                .addParameter(pageNumberRequestParam)
                .addParameter(pageSizeRequestParam)
                .returns(responseEntityOfPageResource)
                .addStatement("$T pageDto = service.findBy$L($L, pageNumber,pageSize)", pageDtoOfDtoType,capitalizedFieldName, fieldName)
                .addStatement("$T pageResource = toPageResource(pageDto)", pageResourceOfResourceType)
                .addStatement("return $T.ok(pageResource)", responseEntityClass)
                .build();

            controllerTypeBuilder.addMethod(findByMethodSpec);
        }

        JavaFile.builder(SwordWizard.CONTROLLER_PKG, controllerTypeBuilder.build()).build().writeTo(rootPath);
    }
}