package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.service.records.ScalarFieldInfo;
import org.cheetah.sword.util.NamingUtils;
import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

/**
 * Generates Spring Data repository interfaces.
 * Extracted from GenerationService#writeRepository.
 */
@Component
public class RepositoryWriter {
public void writeRepository(String repositoryPackage,
                                 String entityPackage,
                                 Path rootPath,
                                 String entitySimpleName,
                                 TypeName idTypeForRepository,
                                 List<ScalarFieldInfo> scalarFields,
                                 AnnotationSpec generatedAnn) throws IOException {

        ClassName entityClass = ClassName.get(entityPackage, entitySimpleName);
        TypeName idType = (idTypeForRepository != null) ? idTypeForRepository : ClassName.get(Long.class);

        ParameterizedTypeName jpaRepoType = ParameterizedTypeName.get(
                ClassName.get("org.springframework.data.jpa.repository", "JpaRepository"),
                entityClass,
                idType
        );

        String repoSimpleName = NamingUtils.pluralizeSimpleName(entitySimpleName) + "Repository";

        TypeSpec.Builder repo = TypeSpec.interfaceBuilder(repoSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(jpaRepoType)
                .addAnnotation(ClassName.get("org.springframework.stereotype", "Repository"))
                .addAnnotation(generatedAnn);

        // add finder methods for non-PK scalar fields
        for (ScalarFieldInfo sf : scalarFields) {
            String fieldName = sf.javaFieldName();
            TypeName fieldType = sf.javaType();
            String methodName = "findBy" + NamingUtils.upperFirst(fieldName);

            MethodSpec finder = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(
                            ParameterizedTypeName.get(
                                    ClassName.get("org.springframework.data.domain", "Page"),
                                    entityClass
                            )
                    )
                    .addParameter(fieldType, fieldName)
                    .addParameter(ClassName.get("org.springframework.data.domain", "Pageable"), "pageable")
                    .build();

            repo.addMethod(finder);
        }

        JavaFile.builder(repositoryPackage, repo.build())
                .build()
                .writeTo(rootPath);
    }
}
