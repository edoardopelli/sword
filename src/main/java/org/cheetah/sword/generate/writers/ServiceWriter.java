package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.Optional;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.RequiredArgsConstructor;

public class ServiceWriter {

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        String serviceSimpleName = NameUtil.toUpperCamel(table.getName()) + "Service";
        ClassName serviceType = ClassName.get(resolver.servicesPackage(), serviceSimpleName);

        String repoSimpleName = NameUtil.toUpperCamel(table.getName()) + "Repository";
        ClassName repoType = ClassName.get(resolver.repositoriesPackage(), repoSimpleName);

        String mapperSimpleName = NameUtil.toUpperCamel(table.getName()) + "EntityMapper";
        ClassName mapperType = ClassName.get(resolver.mappersPackage(), mapperSimpleName);

        ClassName entityType = ClassName.get(resolver.entitiesPackage(), resolver.entitySimpleName(table));
        ClassName dtoType = ClassName.get(resolver.dtosPackage(), resolver.dtoSimpleName(table));

        TypeName idType = repositoryIdType(resolver, table);

        FieldSpec repo = FieldSpec.builder(repoType, "repository",
                        javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL)
                .build();
        FieldSpec mapper = FieldSpec.builder(mapperType, "mapper",
                        javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL)
                .build();

        MethodSpec getById = MethodSpec.methodBuilder("getById")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(dtoType)
                .addParameter(idType, "id")
                .addStatement("$T entity = repository.findById(id).orElseThrow()", entityType)
                .addStatement("return mapper.toDto(entity)")
                .build();

        TypeName pageOfDto = ParameterizedTypeName.get(ClassName.get(Page.class), dtoType);
        MethodSpec getAll = MethodSpec.methodBuilder("getAll")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(pageOfDto)
                .addParameter(ClassName.get(Pageable.class), "pageable")
                .addStatement("return repository.findAll(pageable).map(mapper::toDto)")
                .build();

        MethodSpec create = MethodSpec.methodBuilder("create")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(dtoType)
                .addParameter(dtoType, "dto")
                .addStatement("$T entity = mapper.toEntity(dto)", entityType)
                .addStatement("$T saved = repository.save(entity)", entityType)
                .addStatement("return mapper.toDto(saved)")
                .build();

        // PUT update: ensure entity id is taken from path and not from body.
        MethodSpec update = MethodSpec.methodBuilder("update")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(dtoType)
                .addParameter(idType, "id")
                .addParameter(dtoType, "dto")
                .addStatement("$T entity = mapper.toEntity(dto)", entityType)
                .addStatement("applyId(entity, id)")
                .addStatement("$T saved = repository.save(entity)", entityType)
                .addStatement("return mapper.toDto(saved)")
                .build();

        MethodSpec delete = MethodSpec.methodBuilder("delete")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addParameter(idType, "id")
                .addStatement("repository.deleteById(id)")
                .build();

        // Internal helper to apply the id to the entity for both simple and composite PK.
        MethodSpec applyId = MethodSpec.methodBuilder("applyId")
                .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
                .returns(TypeName.VOID)
                .addParameter(entityType, "entity")
                .addParameter(idType, "id")
                .addCode(buildApplyIdBody(table))
                .build();

        TypeSpec type = TypeSpec.classBuilder(serviceType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Service.class).build())
                .addAnnotation(RequiredArgsConstructor.class)
                .addField(repo)
                .addField(mapper)
                .addMethod(getById)
                .addMethod(getAll)
                .addMethod(create)
                .addMethod(update)
                .addMethod(delete)
                .addMethod(applyId)
                .build();

        JavaFile javaFile = JavaFile.builder(serviceType.packageName(), type)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("Service generation failed for table: " + table.getName(), ex);
        }
    }

    private com.squareup.javapoet.CodeBlock buildApplyIdBody(TableModel table) {
        if (table.hasCompositePrimaryKey()) {
            return com.squareup.javapoet.CodeBlock.builder()
                    .addStatement("entity.setId(id)")
                    .build();
        }

        // Single PK: set the pk field by name.
        String pkColName = table.getPrimaryKeyColumns().get(0);
        ColumnModel pk = table.getColumns().stream()
                .filter(c -> pkColName.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));

        // Uses the camelCase property name already enforced in the model.
        String pkField = pk.getPropertyName();
        String setter = "set" + NameUtil.toUpperCamel(pkField);

        return com.squareup.javapoet.CodeBlock.builder()
                .addStatement("entity.$L(id)", setter)
                .build();
    }

    private TypeName repositoryIdType(NameResolver resolver, TableModel table) {
        if (table.hasCompositePrimaryKey()) {
            String idSimpleName = NameUtil.toUpperCamel(table.getName()) + "Id";
            return ClassName.get(resolver.entityIdsPackage(), idSimpleName);
        }

        if (table.hasSinglePrimaryKey()) {
            String pkColName = table.getPrimaryKeyColumns().get(0);
            Optional<ColumnModel> pk = table.getColumns().stream()
                    .filter(c -> pkColName.equals(c.getName()))
                    .findFirst();
            if (pk.isPresent()) {
                return typeResolver.toJavaType(pk.get().getJdbcType());
            }
        }

        return ClassName.get(Long.class);
    }
}