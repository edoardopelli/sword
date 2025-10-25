package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.service.records.ScalarFieldInfo;
import org.cheetah.sword.util.NamingUtils;
import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * Generates Spring @Service classes for entities.
 * Extracted from GenerationService#writeService.
 */
@Component
public class ServiceWriter {
public void writeService(String servicesPackage,
                              String entityPackage,
                              String dtoPackage,
                              String mapperPackage,
                              String repositoryPackage,
                              Path rootPath,
                              String entitySimpleName,
                              TypeName idTypeForRepository,
                              List<ScalarFieldInfo> scalarFields,
                              AnnotationSpec generatedAnn) throws IOException {

        // types
        ClassName entityClass = ClassName.get(entityPackage, entitySimpleName);
        String dtoSimpleName = entitySimpleName + "Dto";
        ClassName dtoClass = ClassName.get(dtoPackage, dtoSimpleName);
        ClassName mapperClass = ClassName.get(mapperPackage, entitySimpleName + "Mapper");

        String repoSimpleName = NamingUtils.pluralizeSimpleName(entitySimpleName) + "Repository";
        ClassName repoClass = ClassName.get(repositoryPackage, repoSimpleName);

        ClassName pageDtoClass = ClassName.get(servicesPackage, "PageDto");

        TypeName idType = (idTypeForRepository != null) ? idTypeForRepository : ClassName.get(Long.class);

        // fields: repository + mapper
        FieldSpec repoField = FieldSpec.builder(repoClass, "repository", Modifier.PRIVATE, Modifier.FINAL).build();
        FieldSpec mapperField = FieldSpec.builder(mapperClass, "mapper", Modifier.PRIVATE, Modifier.FINAL).build();

        // constructor
        MethodSpec ctor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(repoClass, "repository")
                .addParameter(mapperClass, "mapper")
                .addStatement("this.repository = repository")
                .addStatement("this.mapper = mapper")
                .build();

        /*
         * Helper: build PageDto<DTO> from Page<Entity>
         *
         * private PageDto<UserDto> toPageDto(Page<User> page) {
         *     List<UserDto> dtoList = page.getContent().stream()
         *         .map(mapper::toDto)
         *         .toList();
         *     return PageDto.<UserDto>builder()
         *         .content(dtoList)
         *         .pageNumber(page.getNumber())
         *         .pageSize(page.getSize())
         *         .totalElements(page.getTotalElements())
         *         .totalPages(page.getTotalPages())
         *         .build();
         * }
         */
        ClassName springPageClass = ClassName.get("org.springframework.data.domain", "Page");
        TypeName pageOfEntity = ParameterizedTypeName.get(springPageClass, entityClass);

        MethodSpec toPageDtoMethod = MethodSpec.methodBuilder("toPageDto")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(pageDtoClass, dtoClass))
                .addParameter(pageOfEntity, "page")
                .addStatement("java.util.List<$T> dtoList = page.getContent().stream().map(mapper::toDto).toList()", dtoClass)
                .addStatement("return $T.<$T>builder()"
                                + ".content(dtoList)"
                                + ".pageNumber(page.getNumber())"
                                + ".pageSize(page.getSize())"
                                + ".totalElements(page.getTotalElements())"
                                + ".totalPages(page.getTotalPages())"
                                + ".build()",
                        pageDtoClass, dtoClass)
                .build();

        /*
         * findAll(int pageNumber, int maxRecordsPerPage)
         *
         * Page<User> p = repository.findAll(PageRequest.of(pageNumber, maxRecordsPerPage));
         * return toPageDto(p);
         */
        ClassName pageRequestClass = ClassName.get("org.springframework.data.domain", "PageRequest");

        MethodSpec findAllMethod = MethodSpec.methodBuilder("findAll")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(pageDtoClass, dtoClass))
                .addParameter(TypeName.INT, "pageNumber")
                .addParameter(TypeName.INT, "maxRecordsPerPage")
                .addStatement("$T p = repository.findAll($T.of(pageNumber, maxRecordsPerPage))",
                        pageOfEntity, pageRequestClass)
                .addStatement("return toPageDto(p)")
                .build();

        /*
         * findById(ID id)
         *
         * return repository.findById(id)
         *     .map(mapper::toDto)
         *     .orElse(null);
         */
        MethodSpec findByIdMethod = MethodSpec.methodBuilder("findById")
                .addModifiers(Modifier.PUBLIC)
                .returns(dtoClass)
                .addParameter(idType, "id")
                .addStatement("return repository.findById(id).map(mapper::toDto).orElse(null)")
                .build();

        /*
         * save(UserDto dto)
         *
         * Entity e = mapper.toEntity(dto);
         * e = repository.save(e);
         * return mapper.toDto(e);
         */
        MethodSpec saveMethod = MethodSpec.methodBuilder("save")
                .addModifiers(Modifier.PUBLIC)
                .returns(dtoClass)
                .addParameter(dtoClass, "dto")
                .addStatement("$T e = mapper.toEntity(dto)", entityClass)
                .addStatement("e = repository.save(e)")
                .addStatement("return mapper.toDto(e)")
                .build();

        /*
         * update(ID id, UserDto dto)
         *
         * Entity e = mapper.toEntity(dto);
         * // set PK on e before save if needed
         * e = repository.save(e);
         * return mapper.toDto(e);
         *
         * For a single-column PK, we generate:
         *   e.set<IdFieldName>(id);
         *
         * For composite PK we assume dto already maps to an entity with the embedded id
         * or the caller passes a full Id. We still overwrite e.setId(id) to be sure.
         */
        MethodSpec.Builder updateBuilder = MethodSpec.methodBuilder("update")
                .addModifiers(Modifier.PUBLIC)
                .returns(dtoClass)
                .addParameter(idType, "id")
                .addParameter(dtoClass, "dto")
                .addStatement("$T e = mapper.toEntity(dto)", entityClass);

        // naive approach to set PK back into the entity before save:
        // if idType is not Long.class default, we still assume setter exists.
        // We do not know the exact PK field name here without deeper modeling,
        // but we DO know this:
        // - if composite => entity has setId(<EntityName>Id)
        // - if simple PK => we can't guess field name perfectly without extra metadata.
        //
        // We'll handle composite PK safely: call setId(id) if compositePk (idType is ClassName same package entitySimpleName+"Id").
        // For simple PK, we skip explicit setter because mapper.toEntity(dto) should already carry PK if present.
        //
        boolean compositePk = (idType instanceof ClassName cName && cName.simpleName().endsWith("Id"));
        if (compositePk) {
            updateBuilder.addStatement("e.setId(id)");
        }

        updateBuilder
                .addStatement("e = repository.save(e)")
                .addStatement("return mapper.toDto(e)");
        MethodSpec updateMethod = updateBuilder.build();

        /*
         * delete(ID id)
         *
         * repository.deleteById(id);
         */
        MethodSpec deleteMethod = MethodSpec.methodBuilder("delete")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(idType, "id")
                .addStatement("repository.deleteById(id)")
                .build();

        /*
         * Finder wrappers for each scalar field:
         *
         * public PageDto<UserDto> findByStatus(String status, int pageNumber, int maxRecordsPerPage) {
         *     Page<User> p = repository.findByStatus(status, PageRequest.of(pageNumber, maxRecordsPerPage));
         *     return toPageDto(p);
         * }
         */
        List<MethodSpec> finderWrapperMethods = new ArrayList<>();
        for (ScalarFieldInfo sf : scalarFields) {
            String fieldName = sf.javaFieldName();
            TypeName fieldType = sf.javaType();
            String repoMethodName = "findBy" + NamingUtils.upperFirst(fieldName);

            MethodSpec finderWrapper = MethodSpec.methodBuilder(repoMethodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(pageDtoClass, dtoClass))
                    .addParameter(fieldType, fieldName)
                    .addParameter(TypeName.INT, "pageNumber")
                    .addParameter(TypeName.INT, "maxRecordsPerPage")
                    .addStatement("$T p = repository.$L($L, $T.of(pageNumber, maxRecordsPerPage))",
                            pageOfEntity,
                            repoMethodName,
                            fieldName,
                            pageRequestClass)
                    .addStatement("return toPageDto(p)")
                    .build();

            finderWrapperMethods.add(finderWrapper);
        }

        // build the service class
        String serviceSimpleName = NamingUtils.pluralizeSimpleName(entitySimpleName) + "Service";

        TypeSpec.Builder serviceType = TypeSpec.classBuilder(serviceSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Service"))
                .addAnnotation(generatedAnn)
                .addField(repoField)
                .addField(mapperField)
                .addMethod(ctor)
                .addMethod(toPageDtoMethod)
                .addMethod(findAllMethod)
                .addMethod(findByIdMethod)
                .addMethod(saveMethod)
                .addMethod(updateMethod)
                .addMethod(deleteMethod);

        for (MethodSpec m : finderWrapperMethods) {
            serviceType.addMethod(m);
        }

        JavaFile.builder(servicesPackage, serviceType.build())
                .build()
                .writeTo(rootPath);
    }
}
