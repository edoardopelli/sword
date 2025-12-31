package org.cheetah.sword.generate.writers;

import java.nio.file.Path;

import org.cheetah.sword.generate.IdTypeHelper;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
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

    private final IdTypeHelper idTypeHelper = new IdTypeHelper();
    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, String basePackage, TableModel table) {
        String name = IdTypeHelper.toPascalCase(table.getName()) + "Controller";
        ClassName controllerType = ClassName.get(basePackage + ".controllers", name);

        ClassName serviceType = ClassName.get(basePackage + ".services", IdTypeHelper.toPascalCase(table.getName()) + "Service");
        ClassName dtoType = ClassName.get(basePackage + ".dtos", IdTypeHelper.toPascalCase(table.getName()) + "DTO");
        ClassName resourceType = ClassName.get(basePackage + ".resources", IdTypeHelper.toPascalCase(table.getName()) + "Resource");
        ClassName resourceMapperType = ClassName.get(basePackage + ".mappers", IdTypeHelper.toPascalCase(table.getName()) + "ResourceMapper");

        TypeName idType = idTypeHelper.repositoryIdType(basePackage, table);

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

        MethodSpec getById = buildGetById(basePackage, table, dtoType, resourceType, idType);
        MethodSpec create = buildCreate(dtoType, resourceType);
        MethodSpec delete = buildDelete(basePackage, table, idType);

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

    private MethodSpec buildGetById(String basePackage, TableModel table, ClassName dtoType, ClassName resourceType, TypeName idType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("getById")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(resourceType);

        if (table.hasCompositePrimaryKey()) {
            String path = compositePkPath(table);
            m.addAnnotation(AnnotationSpec.builder(GetMapping.class).addMember("value", "$S", path).build());

            // Path variables for each PK column (flattened)
            for (String pkCol : table.getPrimaryKeyColumns()) {
                ColumnModel col = findColumn(table, pkCol);
                TypeName pkType = typeResolver.toJavaType(col.getJdbcType());
                String paramName = IdTypeHelper.toCamelCase(pkCol);
                m.addParameter(ParameterSpecFactory.pathVariable(pkType, paramName));
            }

            // Build EmbeddedId object
            ClassName idClass = idTypeHelper.embeddedIdClassName(basePackage, table);
            String ctorArgs = table.getPrimaryKeyColumns().stream()
                    .map(IdTypeHelper::toCamelCase)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            m.addStatement("$T id = new $T($L)", idType, idClass, ctorArgs);
            m.addStatement("$T dto = service.getById(id)", dtoType);
            m.addStatement("return mapper.toResource(dto)");
            return m.build();
        }

        // Single PK
        m.addAnnotation(AnnotationSpec.builder(GetMapping.class).addMember("value", "$S", "/{id}").build());
        m.addParameter(ParameterSpecFactory.pathVariable(idType, "id"));
        m.addStatement("$T dto = service.getById(id)", dtoType);
        m.addStatement("return mapper.toResource(dto)");
        return m.build();
    }

    private MethodSpec buildCreate(ClassName dtoType, ClassName resourceType) {
        return MethodSpec.methodBuilder("create")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .returns(resourceType)
                .addAnnotation(AnnotationSpec.builder(PostMapping.class).build())
                .addParameter(ParameterSpecFactory.requestBody(resourceType, "resource"))
                .addStatement("$T dto = mapper.toDto(resource)", dtoType)
                .addStatement("$T saved = service.create(dto)", dtoType)
                .addStatement("return mapper.toResource(saved)")
                .build();
    }

    private MethodSpec buildDelete(String basePackage, TableModel table, TypeName idType) {
        MethodSpec.Builder m = MethodSpec.methodBuilder("delete")
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC);

        if (table.hasCompositePrimaryKey()) {
            String path = compositePkPath(table);
            m.addAnnotation(AnnotationSpec.builder(DeleteMapping.class).addMember("value", "$S", path).build());

            for (String pkCol : table.getPrimaryKeyColumns()) {
                ColumnModel col = findColumn(table, pkCol);
                TypeName pkType = typeResolver.toJavaType(col.getJdbcType());
                String paramName = IdTypeHelper.toCamelCase(pkCol);
                m.addParameter(ParameterSpecFactory.pathVariable(pkType, paramName));
            }

            ClassName idClass = idTypeHelper.embeddedIdClassName(basePackage, table);
            String ctorArgs = table.getPrimaryKeyColumns().stream()
                    .map(IdTypeHelper::toCamelCase)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            m.addStatement("$T id = new $T($L)", idType, idClass, ctorArgs);
            m.addStatement("service.delete(id)");
            return m.build();
        }

        // Single PK
        m.addAnnotation(AnnotationSpec.builder(DeleteMapping.class).addMember("value", "$S", "/{id}").build());
        m.addParameter(ParameterSpecFactory.pathVariable(idType, "id"));
        m.addStatement("service.delete(id)");
        return m.build();
    }

    private static String compositePkPath(TableModel table) {
        StringBuilder sb = new StringBuilder();
        for (String pkCol : table.getPrimaryKeyColumns()) {
            sb.append("/{").append(IdTypeHelper.toCamelCase(pkCol)).append("}");
        }
        return sb.toString();
    }

    private static ColumnModel findColumn(TableModel table, String colName) {
        return table.getColumns().stream()
                .filter(c -> colName.equals(c.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PK column not found in columns list: " + colName));
    }

    private static final class ParameterSpecFactory {

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

        private ParameterSpecFactory() {
        }
    }
}