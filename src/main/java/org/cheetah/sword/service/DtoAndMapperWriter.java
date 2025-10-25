package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.service.records.ColumnModel;
import org.cheetah.sword.service.records.EntityModel;
import org.cheetah.sword.util.SqlTypeMapper;
import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.RequiredArgsConstructor;

/**
 * This writer is responsible for generating DTO and MapStruct Mapper for a
 * given entity. Extracted from GenerationService#writeDtoAndMapper.
 */
@Component
@RequiredArgsConstructor
public class DtoAndMapperWriter {

	private final NamingConfigService namingConfigService;

	public void writeDtoAndMapper(String entityPackage, String dtoPackage, String mapperPackage, Path rootPath,
			EntityModel model, String dbProduct, String entitySimpleName, AnnotationSpec generatedAnn)
			throws IOException {

		String dtoSimpleName = entitySimpleName + "Dto";

		AnnotationSpec toStringAnnDto = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		AnnotationSpec eqHashAnnDto = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		TypeSpec.Builder dto = TypeSpec.classBuilder(dtoSimpleName).addModifiers(Modifier.PUBLIC)
				.addAnnotation(ClassName.get("lombok", "Data"))
				.addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "Builder")).addAnnotation(toStringAnnDto)
				.addAnnotation(eqHashAnnDto).addAnnotation(generatedAnn);

		for (ColumnModel col : model.columns().values()) {
			String fieldName = namingConfigService.resolveColumnName(model.table(), col.name());
			TypeName javaType = SqlTypeMapper.map(col.dataType(), col.typeName(), col.nullable(), dbProduct);
			FieldSpec.Builder f = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE)
					.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
					.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
			dto.addField(f.build());
		}

		JavaFile.builder(dtoPackage, dto.build()).build().writeTo(rootPath);

		AnnotationSpec mapperAnn = AnnotationSpec.builder(ClassName.get("org.mapstruct", "Mapper"))
				.addMember("componentModel", "$S", "spring").build();

		MethodSpec toDto = MethodSpec.methodBuilder("toDto").addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
				.returns(ClassName.get(dtoPackage, dtoSimpleName))
				.addParameter(ClassName.get(entityPackage, entitySimpleName), "entity").build();

		MethodSpec toEntity = MethodSpec.methodBuilder("toEntity").addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
				.returns(ClassName.get(entityPackage, entitySimpleName))
				.addParameter(ClassName.get(dtoPackage, dtoSimpleName), "dto").build();

		TypeSpec mapper = TypeSpec.interfaceBuilder(entitySimpleName + "Mapper").addModifiers(Modifier.PUBLIC)
				.addAnnotation(mapperAnn).addAnnotation(generatedAnn).addMethod(toDto).addMethod(toEntity).build();

		JavaFile.builder(mapperPackage, mapper).build().writeTo(rootPath);
	}
}
