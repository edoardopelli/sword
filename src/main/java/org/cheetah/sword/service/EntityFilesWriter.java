package org.cheetah.sword.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Modifier;

import org.cheetah.sword.model.FkMode;
import org.cheetah.sword.model.RelationFetch;
import org.cheetah.sword.service.records.ColumnModel;
import org.cheetah.sword.service.records.EntityModel;
import org.cheetah.sword.service.records.ScalarFieldInfo;
import org.cheetah.sword.service.records.SimpleFkModel;
import org.cheetah.sword.util.SqlTypeMapper;
import org.springframework.stereotype.Component;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import lombok.AllArgsConstructor;

/**
 * Generates entity source files (and related relations/annotations) and
 * delegates to other writers for DTO, Mapper, Repository, Service and PageDto.
 */
@Component
@AllArgsConstructor
public class EntityFilesWriter {

	private final NamingConfigService namingConfigService;
	private final DtoAndMapperWriter dtoAndMapperWriter;
	private final RepositoryWriter repositoryWriter;
	private final PageDtoWriter pageDtoWriter;
	private final ServiceWriter serviceWriter;
	private final ControllerWriter controllerWriter;
	private final ResourceMapperWriter resourceMapperWriter;
	private final ResourceWriter resourceWriter;


	public void writeEntityFiles(String entityPackage, String dtoPackage, String mapperPackage,
			String repositoryPackage, String servicesPackage,String controllerPackages,String resourcesPackage,String resourceMappersPackage, Path rootPath, EntityModel model,
			List<EntityModel> allModels, String dbProduct, FkMode fkMode, RelationFetch relationFetch,
			boolean generateDto, boolean generateRepositories, boolean generateServices,boolean generateControllers, DatabaseMetaData md)
			throws IOException {

		String entitySimpleName = namingConfigService.resolveEntityName(model.table());

		boolean compositePk = model.pkCols().size() > 1;
		String idClassName = entitySimpleName + "Id";

		String nowIso = OffsetDateTime.now().toString();
		AnnotationSpec generatedAnn = AnnotationSpec.builder(ClassName.get("jakarta.annotation", "Generated"))
				.addMember("value", "$S", "S.W.O.R.D.").addMember("date", "$S", nowIso).build();

		AnnotationSpec toStringAnn = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		AnnotationSpec eqHashAnn = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		TypeSpec.Builder entity = TypeSpec.classBuilder(entitySimpleName).addModifiers(Modifier.PUBLIC)
				.addAnnotation(ClassName.get("jakarta.persistence", "Entity"))
				.addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Table"))
						.addMember("name", "$S", model.table()).build())
				.addAnnotation(ClassName.get("lombok", "Data"))
				.addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "Builder")).addAnnotation(toStringAnn).addAnnotation(eqHashAnn)
				.addAnnotation(generatedAnn);

		// collector for repository generation
		List<ScalarFieldInfo> scalarFieldInfos = new ArrayList<>();
		// ID type for repository/service
		TypeName idTypeForRepository = null;

		// composite PK -> add @EmbeddedId + generate Id class
		if (compositePk) {
			ClassName idClass = ClassName.get(entityPackage, idClassName);
			FieldSpec.Builder idField = FieldSpec.builder(idClass, "id", Modifier.PRIVATE)
					.addAnnotation(ClassName.get("jakarta.persistence", "EmbeddedId"))
					.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
					.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
			entity.addField(idField.build());

			writeEmbeddedId(entityPackage, rootPath, idClassName, model, dbProduct, generatedAnn);

			idTypeForRepository = idClass;
		}

		// relation fields on the child side if RELATION mode
		Set<String> handledFkColumns = new HashSet<>();
		List<FieldSpec> relationFieldsChildSide = new ArrayList<>();

		if (fkMode == FkMode.RELATION) {
			for (SimpleFkModel fk : model.simpleFks()) {
				String localCol = fk.localColumn();
				if (model.pkCols().contains(localCol))
					continue;

				String targetEntityName = namingConfigService.resolveEntityName(fk.targetTable());
				ClassName targetType = ClassName.get(entityPackage, targetEntityName);
				String relFieldName = lowerFirst(targetEntityName);

				boolean unique = isColumnUnique(md, model.catalog(), model.schema(), model.table(), localCol);

				AnnotationSpec.Builder relationAnn = AnnotationSpec
						.builder(ClassName.get("jakarta.persistence", unique ? "OneToOne" : "ManyToOne"))
						.addMember("fetch", "$T.$L", ClassName.get("jakarta.persistence", "FetchType"),
								relationFetch == RelationFetch.EAGER ? "EAGER" : "LAZY");

				AnnotationSpec joinColAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "JoinColumn"))
						.addMember("name", "$S", localCol).build();

				FieldSpec.Builder relField = FieldSpec.builder(targetType, relFieldName, Modifier.PRIVATE)
						.addAnnotation(relationAnn.build()).addAnnotation(joinColAnn);

				relationFieldsChildSide.add(relField.build());
				handledFkColumns.add(localCol);
			}
		}

		// scalar columns + PK field when PK is simple
		for (ColumnModel col : model.columns().values()) {
			boolean isSimplePkColumn = (!compositePk && model.pkCols().contains(col.name()));
			boolean skipForRelation = handledFkColumns.contains(col.name()) && !isSimplePkColumn;
			if (skipForRelation)
				continue;
			if (compositePk && model.pkCols().contains(col.name()))
				continue;

			String fieldName = namingConfigService.resolveColumnName(model.table(), col.name());
			TypeName javaType = SqlTypeMapper.map(col.dataType(), col.typeName(), col.nullable(), dbProduct);

			FieldSpec.Builder field = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE);

			if (isSimplePkColumn) {
				// mark PK
				field.addAnnotation(ClassName.get("jakarta.persistence", "Id"));
				field.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
				field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));

				// GeneratedValue strategy
				if (col.autoIncrement()) {
					if (isPostgres(dbProduct) && col.columnDef() != null
							&& col.columnDef().toLowerCase(Locale.ROOT).contains("nextval(")) {

						String seqName = extractPgSequenceName(col.columnDef());
						String genName = (model.table() + "_" + col.name() + "_seq_gen").replaceAll("[^A-Za-z0-9_]",
								"_");

						if (seqName != null && !seqName.isBlank()) {
							AnnotationSpec seqGen = AnnotationSpec
									.builder(ClassName.get("jakarta.persistence", "SequenceGenerator"))
									.addMember("name", "$S", genName).addMember("sequenceName", "$S", seqName)
									.addMember("allocationSize", "$L", 1).build();
							entity.addAnnotation(seqGen);

							AnnotationSpec genVal = AnnotationSpec
									.builder(ClassName.get("jakarta.persistence", "GeneratedValue"))
									.addMember("strategy", "$T.SEQUENCE",
											ClassName.get("jakarta.persistence", "GenerationType"))
									.addMember("generator", "$S", genName).build();
							field.addAnnotation(genVal);
						} else {
							AnnotationSpec genVal = AnnotationSpec
									.builder(ClassName.get("jakarta.persistence", "GeneratedValue"))
									.addMember("strategy", "$T.IDENTITY",
											ClassName.get("jakarta.persistence", "GenerationType"))
									.build();
							field.addAnnotation(genVal);
						}
					} else {
						AnnotationSpec genVal = AnnotationSpec
								.builder(ClassName.get("jakarta.persistence", "GeneratedValue")).addMember("strategy",
										"$T.IDENTITY", ClassName.get("jakarta.persistence", "GenerationType"))
								.build();
						field.addAnnotation(genVal);
					}
				}

				// remember id type
				if (!compositePk) {
					idTypeForRepository = javaType;
				}

			} else {
				// scalar field, not PK
				if (!javaType.toString().equals("byte[]")) {
					field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));
				}
				scalarFieldInfos.add(new ScalarFieldInfo(fieldName, javaType));
			}

			// @Column
			AnnotationSpec.Builder colAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
					.addMember("name", "$S", col.name());

			// Postgres JSONB/JSON
			String tn = col.typeName() == null ? "" : col.typeName().toLowerCase(Locale.ROOT);
			if ((tn.equals("jsonb") || tn.equals("json")) && isPostgres(dbProduct)) {
				colAnn.addMember("columnDefinition", "$S", tn);
				field.addAnnotation(AnnotationSpec.builder(ClassName.get("org.hibernate.annotations", "JdbcTypeCode"))
						.addMember("value", "$T.JSON", ClassName.get("org.hibernate.type", "SqlTypes")).build());
			}

			field.addAnnotation(colAnn.build());

			entity.addField(field.build());
		}

		// add relation fields on child side
		for (FieldSpec relField : relationFieldsChildSide) {
			entity.addField(relField);
		}

		// inverse relations on parent side
		if (fkMode == FkMode.RELATION) {
			List<FieldSpec> inverseFields = buildInverseRelationFields(model, allModels, entityPackage, md,
					relationFetch);
			for (FieldSpec invField : inverseFields) {
				entity.addField(invField);
			}
		}

		// write entity
		JavaFile.builder(entityPackage, entity.build()).build().writeTo(rootPath);

		// DTO + Mapper
		if (generateDto) {
			this.dtoAndMapperWriter.writeDtoAndMapper(entityPackage, dtoPackage, mapperPackage, rootPath, model,
					dbProduct, entitySimpleName, generatedAnn);
		}

		// Repository
		if (generateRepositories) {
			repositoryWriter.writeRepository(repositoryPackage, entityPackage, rootPath, entitySimpleName,
					idTypeForRepository, scalarFieldInfos, generatedAnn);
		}

		// Service
		if (generateServices) {
			// We assume that DTO, Mapper and Repository are also generated/available.
			this.pageDtoWriter.writePageDtoOnce(servicesPackage, rootPath, generatedAnn);
			this.serviceWriter.writeService(servicesPackage, entityPackage, dtoPackage, mapperPackage,
					repositoryPackage, rootPath, entitySimpleName, idTypeForRepository, scalarFieldInfos, generatedAnn);
		}
		
		if (generateControllers) {
			this.controllerWriter.writeController(controllerPackages, servicesPackage, resourcesPackage, resourceMappersPackage, rootPath, entitySimpleName, idTypeForRepository, scalarFieldInfos, generatedAnn);
			this.resourceWriter.writeResource(resourcesPackage, rootPath, entitySimpleName, idTypeForRepository, scalarFieldInfos, generatedAnn);
			this.resourceMapperWriter.writeResourceMapper(resourceMappersPackage, dtoPackage, resourcesPackage, rootPath, entitySimpleName, generatedAnn);
		}
	}

	private void writeEmbeddedId(String entityPackage, Path rootPath, String idClassName,
			EntityModel model, String dbProduct, AnnotationSpec generatedAnn) throws IOException {

		AnnotationSpec toStringAnn = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		AnnotationSpec eqHashAnn = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		TypeSpec.Builder emb = TypeSpec.classBuilder(idClassName).addModifiers(Modifier.PUBLIC)
				.addAnnotation(ClassName.get("jakarta.persistence", "Embeddable"))
				.addSuperinterface(ClassName.get("java.io", "Serializable"))
				.addAnnotation(ClassName.get("lombok", "Data"))
				.addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "Builder")).addAnnotation(toStringAnn).addAnnotation(eqHashAnn)
				.addAnnotation(generatedAnn);

		for (String pkCol : model.pkCols()) {
			ColumnModel col = model.columns().get(pkCol);

			String fieldName = namingConfigService.resolveColumnName(model.table(), pkCol);
			TypeName javaType = SqlTypeMapper.map(col.dataType(), col.typeName(), col.nullable(), dbProduct);

			FieldSpec f = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE)
					.addAnnotation(AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
							.addMember("name", "$S", pkCol).build())
					.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
					.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include")).build();

			emb.addField(f);
		}

		JavaFile.builder(entityPackage, emb.build()).build().writeTo(rootPath);
	}

	private List<FieldSpec> buildInverseRelationFields(EntityModel parentModel,
			List<EntityModel> allModels, String entityPackage, DatabaseMetaData md,
			RelationFetch relationFetch) {

		List<FieldSpec> fields = new ArrayList<>();
		Set<String> usedFieldNames = new HashSet<>();

		String parentEntityName = namingConfigService.resolveEntityName(parentModel.table());
		String mappedByNameOnChild = lowerFirst(parentEntityName);

		for (EntityModel childModel : allModels) {
			if (childModel == parentModel)
				continue;

			for (SimpleFkModel fk : childModel.simpleFks()) {
				if (!fk.targetTable().equalsIgnoreCase(parentModel.table())) {
					continue;
				}

				String childEntityName = namingConfigService.resolveEntityName(childModel.table());
				ClassName childType = ClassName.get(entityPackage, childEntityName);

				boolean unique = isColumnUnique(md, childModel.catalog(), childModel.schema(), childModel.table(),
						fk.localColumn());

				if (unique) {
					String fieldName = uniquify(lowerFirst(childEntityName), usedFieldNames);

					AnnotationSpec oneToOneBack = AnnotationSpec
							.builder(ClassName.get("jakarta.persistence", "OneToOne"))
							.addMember("mappedBy", "$S", mappedByNameOnChild)
							.addMember("fetch", "$T.$L", ClassName.get("jakarta.persistence", "FetchType"),
									relationFetch == RelationFetch.EAGER ? "EAGER" : "LAZY")
							.build();

					FieldSpec.Builder f = FieldSpec.builder(childType, fieldName, Modifier.PRIVATE)
							.addAnnotation(oneToOneBack);

					fields.add(f.build());
				} else {
					String pluralBase = lowerFirst(childEntityName) + "s";
					String fieldName = uniquify(pluralBase, usedFieldNames);

					ParameterizedTypeName setOfChild = ParameterizedTypeName.get(ClassName.get(Set.class), childType);

					AnnotationSpec oneToManyBack = AnnotationSpec
							.builder(ClassName.get("jakarta.persistence", "OneToMany"))
							.addMember("mappedBy", "$S", mappedByNameOnChild)
							.addMember("fetch", "$T.LAZY", ClassName.get("jakarta.persistence", "FetchType")).build();

					FieldSpec.Builder f = FieldSpec.builder(setOfChild, fieldName, Modifier.PRIVATE)
							.addAnnotation(oneToManyBack);

					fields.add(f.build());
				}
			}
		}

		return fields;
	}

	private boolean isColumnUnique(DatabaseMetaData md, String catalog, String schema, String table, String column) {
		try (ResultSet rs = md.getIndexInfo(catalog, schema, table, true, false)) {
			while (rs.next()) {
				String colName = rs.getString("COLUMN_NAME");
				if (colName != null && column.equalsIgnoreCase(colName)) {
					return true;
				}
			}
		} catch (SQLException ignored) {
		}
		return false;
	}

	private boolean detectAutoIncrement(String dbProduct, String isAuto, String typeName, String columnDef) {
		String db = dbProduct == null ? "" : dbProduct.toLowerCase(Locale.ROOT);
		String tn = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
		String def = columnDef == null ? "" : columnDef.toLowerCase(Locale.ROOT);

		if ("yes".equalsIgnoreCase(isAuto))
			return true;
		if (db.contains("postgres") && def.contains("nextval("))
			return true;
		if (db.contains("sql server") && (tn.contains("identity") || def.contains("identity")))
			return true;
		if (db.contains("h2")
				&& (tn.contains("identity") || def.contains("auto_increment") || def.contains("identity")))
			return true;
		if (db.contains("db2") && def.contains("generated") && def.contains("identity"))
			return true;
		if ((db.contains("mysql") || db.contains("mariadb")) && def.contains("auto_increment"))
			return true;
		return false;
	}

	private String extractPgSequenceName(String columnDef) {
		if (columnDef == null)
			return null;
		Matcher m = Pattern.compile("nextval\\('([^']+)'").matcher(columnDef);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	private boolean isPostgres(String dbProduct) {
		return dbProduct != null && dbProduct.toLowerCase(Locale.ROOT).contains("postgres");
	}

	private String uniquify(String candidate, Set<String> used) {
		String base = candidate;
		int idx = 2;
		while (used.contains(candidate)) {
			candidate = base + idx;
			idx++;
		}
		used.add(candidate);
		return candidate;
	}

	private static String lowerFirst(String s) {
		if (s == null || s.isBlank())
			return s;
		return s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
	}

	private static String upperFirst(String s) {
		if (s == null || s.isBlank())
			return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String pluralizeSimpleName(String simpleName) {
		if (simpleName == null || simpleName.isBlank()) {
			return simpleName;
		}
		char last = simpleName.charAt(simpleName.length() - 1);
		if (last == 's' || last == 'S') {
			return simpleName;
		}
		return simpleName + "s";
	}
}
