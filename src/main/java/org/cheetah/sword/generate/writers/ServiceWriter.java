package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.Optional;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.springframework.stereotype.Service;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

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
                javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL).build();
        FieldSpec mapper = FieldSpec.builder(mapperType, "mapper",
                javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL).build();

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

    private TypeName repositoryIdType(NameResolver resolver, TableModel table) {
        if (table.hasCompositePrimaryKey()) {
            String idSimpleName = NameUtil.toUpperCamel(table.getName()) + "Id";
            return ClassName.get(resolver.entityIdsPackage(), idSimpleName);
        }

        if (table.hasSinglePrimaryKey()) {
            String pkColName = table.getPrimaryKeyColumns().get(0);
            Optional<ColumnModel> pk = table.getColumns().stream().filter(c -> pkColName.equals(c.getName())).findFirst();
            if (pk.isPresent()) {
                return typeResolver.toJavaType(pk.get().getJdbcType());
            }
        }

        return ClassName.get(Long.class);
    }
}