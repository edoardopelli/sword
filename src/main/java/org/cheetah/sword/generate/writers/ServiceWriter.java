package org.cheetah.sword.generate.writers;

import java.nio.file.Path;

import org.cheetah.sword.generate.IdTypeHelper;
import org.cheetah.sword.model.TableModel;
import org.springframework.stereotype.Service;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public class ServiceWriter {

    private final IdTypeHelper idTypeHelper = new IdTypeHelper();

    public void write(Path outputDir, String basePackage, TableModel table) {
        String name = IdTypeHelper.toPascalCase(table.getName()) + "Service";
        ClassName serviceType = ClassName.get(basePackage + ".services", name);

        ClassName repoType = ClassName.get(basePackage + ".repositories", IdTypeHelper.toPascalCase(table.getName()) + "Repository");
        ClassName entityType = ClassName.get(basePackage + ".entities", IdTypeHelper.toPascalCase(table.getName()) + "Entity");
        ClassName dtoType = ClassName.get(basePackage + ".dtos", IdTypeHelper.toPascalCase(table.getName()) + "DTO");
        ClassName mapperType = ClassName.get(basePackage + ".mappers", IdTypeHelper.toPascalCase(table.getName()) + "EntityMapper");

        TypeName idType = idTypeHelper.repositoryIdType(basePackage, table);

        FieldSpec repo = FieldSpec.builder(repoType, "repository", javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL).build();
        FieldSpec mapper = FieldSpec.builder(mapperType, "mapper", javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL).build();

        MethodSpec ctor = MethodSpec.constructorBuilder()
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addParameter(repoType, "repository")
                .addParameter(mapperType, "mapper")
                .addStatement("this.repository = repository")
                .addStatement("this.mapper = mapper")
                .build();

        MethodSpec getById = MethodSpec.methodBuilder("getById")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(dtoType)
                .addParameter(idType, "id")
                .addStatement("$T entity = repository.findById(id).orElseThrow()", entityType)
                .addStatement("return mapper.toDto(entity)")
                .build();

        MethodSpec create = MethodSpec.methodBuilder("create")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(dtoType)
                .addParameter(dtoType, "dto")
                .addStatement("$T entity = mapper.toEntity(dto)", entityType)
                .addStatement("$T saved = repository.save(entity)", entityType)
                .addStatement("return mapper.toDto(saved)")
                .build();

        MethodSpec delete = MethodSpec.methodBuilder("delete")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addParameter(idType, "id")
                .addStatement("repository.deleteById(id)")
                .build();

        TypeSpec type = TypeSpec.classBuilder(serviceType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Service.class).build())
                .addField(repo)
                .addField(mapper)
                .addMethod(ctor)
                .addMethod(getById)
                .addMethod(create)
                .addMethod(delete)
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
}