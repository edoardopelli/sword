package org.cheetah.sword.generate.writers;

import java.nio.file.Path;
import java.util.Optional;

import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.generate.TypeResolver;
import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.springframework.data.jpa.repository.JpaRepository;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public class RepositoryWriter {

    private final TypeResolver typeResolver = new TypeResolver();

    public void write(Path outputDir, NameResolver resolver, TableModel table) {
        ClassName entityType = ClassName.get(resolver.entitiesPackage(), resolver.entitySimpleName(table));

        String repoSimpleName = NameUtil.toUpperCamel(table.getName()) + "Repository";
        ClassName repoType = ClassName.get(resolver.repositoriesPackage(), repoSimpleName);

        TypeName idType = repositoryIdType(resolver, table);

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