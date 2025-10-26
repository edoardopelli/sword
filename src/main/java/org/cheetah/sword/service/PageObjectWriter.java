package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.wizard.SwordWizard;
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
 * Generates the generic PageDto<T> container. Extracted from
 * GenerationService#writePageDtoOnce.
 */
@Component
public class PageObjectWriter {

	enum PageType {DTO,RESOURCE}
	
	public void writePageObjectOnce(PageType pageType,Path rootPath, AnnotationSpec generatedAnn) throws IOException {

		// naive approach: always write. If file already exists on disk from previous
		// run
		// JavaPoet's writeTo will update it (overwrites). That's acceptable for now.

		TypeVariableName typeT = TypeVariableName.get("T");

		TypeSpec.Builder pageObject = TypeSpec.classBuilder(pageType.equals(PageType.DTO)? "PageDto" : "PageResource").addTypeVariable(typeT).addModifiers(Modifier.PUBLIC)
				.addAnnotation(ClassName.get("lombok", "Data")).addAnnotation(ClassName.get("lombok", "Builder"))
				.addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "AllArgsConstructor")).addAnnotation(generatedAnn);

		pageObject.addField(FieldSpec
				.builder(ParameterizedTypeName.get(ClassName.get(List.class), typeT), "content", Modifier.PRIVATE)
				.build());
		pageObject.addField(FieldSpec.builder(TypeName.INT, "pageNumber", Modifier.PRIVATE).build());
		pageObject.addField(FieldSpec.builder(TypeName.INT, "pageSize", Modifier.PRIVATE).build());
		pageObject.addField(FieldSpec.builder(TypeName.LONG, "totalElements", Modifier.PRIVATE).build());
		pageObject.addField(FieldSpec.builder(TypeName.INT, "totalPages", Modifier.PRIVATE).build());

		JavaFile.builder(pageType.equals(PageType.DTO)? SwordWizard.DTO_PKG : SwordWizard.RESOURCES_PKG, pageObject.build()).build().writeTo(rootPath);
	}
}
