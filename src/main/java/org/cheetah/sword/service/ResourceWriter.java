package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.service.records.ScalarFieldInfo;
import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * This writer generates the Resource POJO used as REST payload.
 * It mirrors DTO fields and is decoupled from persistence concerns.
 *
 * Assumptions:
 * - ScalarFieldInfo provides javaFieldName() and javaType() (already a JavaPoet TypeName).
 */
@Component
public class ResourceWriter {

    /**
     * Generates the Resource POJO class.
     *
     * @param resourcesPackage Target package for resource classes (e.g. baseRoot + ".web.resource").
     * @param rootPath         Root output path for sources.
     * @param entitySimpleName Simple name of the entity (e.g. "Incident").
     * @param idType           JavaPoet TypeName for the id type (e.g. ClassName.get(Long.class)).
     * @param scalarFieldInfos List of scalar fields (name + java type) collected during entity inspection.
     * @param generatedAnn     @Generated annotation to be applied on generated types.
     */
    public void writeResource(String resourcesPackage,
                              Path rootPath,
                              String entitySimpleName,
                              TypeName idType,
                              List<ScalarFieldInfo> scalarFieldInfos,
                              AnnotationSpec generatedAnn) throws IOException {

        String resourceSimpleName = entitySimpleName + "Resource";

        TypeSpec.Builder type = TypeSpec.classBuilder(resourceSimpleName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(ClassName.get("lombok", "Data"))
            .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
            .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
            .addAnnotation(ClassName.get("lombok", "Builder"))
            .addAnnotation(generatedAnn);

        // id field
        type.addField(FieldSpec.builder(idType, "id", Modifier.PRIVATE).build());

        // scalar fields
        for (ScalarFieldInfo f : scalarFieldInfos) {
            type.addField(FieldSpec.builder(f.javaType(), f.javaFieldName(), Modifier.PRIVATE).build());
        }

        JavaFile.builder(resourcesPackage, type.build()).build().writeTo(rootPath);
    }
}
