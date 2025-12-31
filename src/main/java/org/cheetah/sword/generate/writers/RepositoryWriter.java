package org.cheetah.sword.generate.writers;

import java.nio.file.Path;

import org.cheetah.sword.generate.IdTypeHelper;
import org.cheetah.sword.model.TableModel;
import org.springframework.data.jpa.repository.JpaRepository;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public class RepositoryWriter {

    private final IdTypeHelper idTypeHelper = new IdTypeHelper();

    public void write(Path outputDir, String basePackage, TableModel table) {
        String entityName = IdTypeHelper.toPascalCase(table.getName()) + "Entity";
        String repoName = IdTypeHelper.toPascalCase(table.getName()) + "Repository";

        ClassName entityType = ClassName.get(basePackage + ".entities", entityName);
        ClassName repoType = ClassName.get(basePackage + ".repositories", repoName);

        TypeName idType = idTypeHelper.repositoryIdType(basePackage, table);

        ParameterizedTypeName jpaRepo = ParameterizedTypeName.get(
                ClassName.get(JpaRepository.class),
                entityType,
                idType
        );

        TypeSpec type = TypeSpec.interfaceBuilder(repoType)
                .addModifiers(javax.lang.model.element.Modifier.PUBLIC)
                .addSuperinterface(jpaRepo)
                .build();

        JavaFile javaFile = JavaFile.builder(repoType.packageName(), type)
                .indent("    ")
                .build();

        try {
            javaFile.writeTo(outputDir);
        } catch (Exception ex) {
            throw new IllegalStateException("Repository generation failed for table: " + table.getName(), ex);
        }
    }
}