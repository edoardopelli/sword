package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

/**
 * Generates the generic PageDto<T> container.
 * Extracted from GenerationService#writePageDtoOnce.
 */
@Component
public class PageDtoWriter {
public void writePageDtoOnce(String servicesPackage,
                                  Path rootPath,
                                  AnnotationSpec generatedAnn) throws IOException {

        // naive approach: always write. If file already exists on disk from previous run
        // JavaPoet's writeTo will update it (overwrites). That's acceptable for now.

        TypeVariableName typeT = TypeVariableName.get("T");

        TypeSpec.Builder pageDto = TypeSpec.classBuilder("PageDto")
                .addTypeVariable(typeT)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(generatedAnn);

        pageDto.addField(
                FieldSpec.builder(
                                ParameterizedTypeName.get(
                                        ClassName.get(List.class),
                                        typeT
                                ),
                                "content",
                                Modifier.PRIVATE
                        )
                        .build()
        );
        pageDto.addField(FieldSpec.builder(TypeName.INT, "pageNumber", Modifier.PRIVATE).build());
        pageDto.addField(FieldSpec.builder(TypeName.INT, "pageSize", Modifier.PRIVATE).build());
        pageDto.addField(FieldSpec.builder(TypeName.LONG, "totalElements", Modifier.PRIVATE).build());
        pageDto.addField(FieldSpec.builder(TypeName.INT, "totalPages", Modifier.PRIVATE).build());

        JavaFile.builder(servicesPackage, pageDto.build())
                .build()
                .writeTo(rootPath);
    }
}
