package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public class ControllerWriter {

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        String controllerSimpleName = NameUtil.toUpperCamel(table.getName()) + "Controller";
        ClassName controllerType = ClassName.get(resolver.controllersPackage(), controllerSimpleName);

        String serviceSimpleName = NameUtil.toUpperCamel(table.getName()) + "Service";
        ClassName serviceType = ClassName.get(resolver.servicesPackage(), serviceSimpleName);

        String resourceMapperSimpleName = resolver.resourceSimpleName(table) + "Mapper";
        ClassName resourceMapperType = ClassName.get(resolver.mappersPackage(), resourceMapperSimpleName);

        ClassName dtoType = ClassName.get(resolver.dtosPackage(), resolver.dtoSimpleName(table));
        ClassName resourceType = ClassName.get(resolver.resourcesPackage(), resolver.resourceSimpleName(table));

        FieldSpec service = FieldSpec.builder(serviceType, "service",
                javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL).build();
        FieldSpec mapper = FieldSpec.builder(resourceMapperType, "mapper",
                javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.FINAL).build();

        MethodSpec ctor = MethodSpec.constructorBuilder()
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addParameter(serviceType, "service")
                .addParameter(resourceMapperType, "mapper")
                .addStatement("this.service = service")
                .addStatement("this.mapper = mapper")
                .build();

        String basePath = "/" + table.getName().toLowerCase();

        MethodSpec getById = buildGetById(resolver, table, dtoType, resourceType);
        MethodSpec create = buildCreate(dtoType, resourceType);
        MethodSpec delete = buildDelete(resolver, table);

        TypeSpec type = TypeSpec.classBuilder(controllerType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addAnnotation(RestController.class)
                .addAnnotation(AnnotationSpec.builder(RequestMapping.class).addMember("value", "$S", basePath).build())
                .addField(service)
                .addField(mapper)
                .addMethod(ctor)
                .addMethod(getById)
                .addMethod(create)
                .addMethod(delete)
                .build();

        JavaFile javaFile = JavaFile.builder(controllerType.packageName(), type)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("Controller generation failed for table: " + table.getName(), ex);
        }
    }

    private MethodSpec buildGetById(NameResolver resolver, TableModel table, ClassName dtoType, ClassName resourceType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("getById")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(resourceType);

        if (table.hasCompositePrimaryKey()) {
            String path = compositePkPath(resolver, table);
            m.addAnnotation(AnnotationSpec.builder(GetMapping.class).addMember("value", "$S", path).build());

            // Declare path variables in PK order.
            for (String pkColName : table.getPrimaryKeyColumns()) {
                ColumnModel pkCol = findColumn(table, pkColName)
                        .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));

                String varName = resolver.columnPropertyName(table, pkCol);
                TypeName varType = typeResolver.toJavaType(pkCol.getJdbcType());
                m.addParameter(pathVariable(varType, varName));
            }

            ClassName idClass = ClassName.get(resolver.entityIdsPackage(), NameUtil.toUpperCamel(table.getName()) + "Id");
            String ctorArgs = table.getPrimaryKeyColumns().stream()
                    .map(pkColName -> {
                        ColumnModel pkCol = findColumn(table, pkColName)
                                .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));
                        return resolver.columnPropertyName(table, pkCol);
                    })
                    .collect(Collectors.joining(", "));

            m.addStatement("$T id = new $T($L)", idClass, idClass, ctorArgs);
            m.addStatement("$T dto = service.getById(id)", dtoType);
            m.addStatement("return mapper.toResource(dto)");
            return m.build();
        }

        // Single PK path variable is "id"
        m.addAnnotation(AnnotationSpec.builder(GetMapping.class).addMember("value", "$S", "/{id}").build());
        TypeName idType = singlePkType(table);
        m.addParameter(pathVariable(idType, "id"));
        m.addStatement("$T dto = service.getById(id)", dtoType);
        m.addStatement("return mapper.toResource(dto)");
        return m.build();
    }

    private MethodSpec buildCreate(ClassName dtoType, ClassName resourceType) {
        return MethodSpec.methodBuilder("create")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(resourceType)
                .addAnnotation(AnnotationSpec.builder(PostMapping.class).build())
                .addParameter(requestBody(resourceType, "resource"))
                .addStatement("$T dto = mapper.toDto(resource)", dtoType)
                .addStatement("$T saved = service.create(dto)", dtoType)
                .addStatement("return mapper.toResource(saved)")
                .build();
    }

    private MethodSpec buildDelete(NameResolver resolver, TableModel table) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("delete")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC);

        if (table.hasCompositePrimaryKey()) {
            String path = compositePkPath(resolver, table);
            m.addAnnotation(AnnotationSpec.builder(DeleteMapping.class).addMember("value", "$S", path).build());

            for (String pkColName : table.getPrimaryKeyColumns()) {
                ColumnModel pkCol = findColumn(table, pkColName)
                        .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));

                String varName = resolver.columnPropertyName(table, pkCol);
                TypeName varType = typeResolver.toJavaType(pkCol.getJdbcType());
                m.addParameter(pathVariable(varType, varName));
            }

            ClassName idClass = ClassName.get(resolver.entityIdsPackage(), NameUtil.toUpperCamel(table.getName()) + "Id");
            String ctorArgs = table.getPrimaryKeyColumns().stream()
                    .map(pkColName -> {
                        ColumnModel pkCol = findColumn(table, pkColName)
                                .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));
                        return resolver.columnPropertyName(table, pkCol);
                    })
                    .collect(Collectors.joining(", "));

            m.addStatement("$T id = new $T($L)", idClass, idClass, ctorArgs);
            m.addStatement("service.delete(id)");
            return m.build();
        }

        m.addAnnotation(AnnotationSpec.builder(DeleteMapping.class).addMember("value", "$S", "/{id}").build());
        TypeName idType = singlePkType(table);
        m.addParameter(pathVariable(idType, "id"));
        m.addStatement("service.delete(id)");
        return m.build();
    }

    private static String compositePkPath(NameResolver resolver, TableModel table) {
        StringBuilder sb = new StringBuilder();
        for (String pkColName : table.getPrimaryKeyColumns()) {
            ColumnModel pkCol = findColumn(table, pkColName)
                    .orElseThrow(() -> new IllegalStateException("PK column not found: " + pkColName));
            String varName = resolver.columnPropertyName(table, pkCol);
            sb.append("/{").append(varName).append("}");
        }
        return sb.toString();
    }

    private TypeName singlePkType(TableModel table) {
        if (table.hasSinglePrimaryKey()) {
            String pkColName = table.getPrimaryKeyColumns().get(0);
            Optional<ColumnModel> pk = table.getColumns().stream().filter(c -> pkColName.equals(c.getName())).findFirst();
            if (pk.isPresent()) {
                return typeResolver.toJavaType(pk.get().getJdbcType());
            }
        }
        return ClassName.get(Long.class);
    }

    private static Optional<ColumnModel> findColumn(TableModel table, String columnName) {
        return table.getColumns().stream()
                .filter(c -> columnName.equals(c.getName()))
                .findFirst();
    }

    private static ParameterSpec pathVariable(TypeName type, String name) {
        return ParameterSpec.builder(type, name)
                .addAnnotation(AnnotationSpec.builder(PathVariable.class).addMember("value", "$S", name).build())
                .build();
    }

    private static ParameterSpec requestBody(TypeName type, String name) {
        return ParameterSpec.builder(type, name)
                .addAnnotation(RequestBody.class)
                .build();
    }
}