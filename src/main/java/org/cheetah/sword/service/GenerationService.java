package org.cheetah.sword.service;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.cheetah.sword.events.Events.GenerateRequestedEvent;
import org.cheetah.sword.events.Events.GenerationCompletedEvent;
import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.FkMode;
import org.cheetah.sword.model.RelationFetch;
import org.cheetah.sword.model.SchemaSelection;
import org.cheetah.sword.util.SqlTypeMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main generation service. Generates Entities, optional DTOs, optional Mappers,
 * optional Repositories.
 *
 * Generation rules summary:
 *
 * Entities: - Class annotated with @Entity, @Table(name="..."). -
 * Lombok: @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor.
 * - @EqualsAndHashCode(onlyExplicitlyIncluded = true)
 * - @ToString(onlyExplicitlyIncluded = true) - Single-column PK -> @Id on that
 * field, with @GeneratedValue if auto increment. - Composite PK -> @EmbeddedId
 * field of <EntityName>Id, and generated <EntityName>Id @Embeddable. -
 * json/jsonb columns on Postgres get @JdbcTypeCode(SqlTypes.JSON) and
 * columnDefinition. - Relation mode: * SCALAR: FK columns stay as scalar fields
 * (e.g. Long customerId). * RELATION: generate @ManyToOne or @OneToOne
 * with @JoinColumn instead of scalar FK field. - Bidirectional: * Child
 * side: @ManyToOne/@OneToOne to parent. * Parent side: @OneToMany
 * or @OneToOne(mappedBy="...").
 *
 * DTO: - <EntityName>Dto under sibling package "dtos". - All physical columns
 * as plain fields (scalar types), no JPA annotations.
 *
 * Mapper: - <EntityName>Mapper under sibling package "mappers".
 * - @Mapper(componentModel="spring") - toDto(entity) and toEntity(dto)
 *
 * Repository: - <EntityName>Repository under sibling package "repositories". -
 * Extends JpaRepository<Entity, IdType>. - For each NON-PK scalar field,
 * generates: Page<Entity> findBy<FieldName>(FieldType value, Pageable
 * pageable); PK fields and embedded-id parts are excluded.
 */
@Service
public class GenerationService {

	private final MetadataService metadataService;
	private final NamingConfigService namingConfigService;
	private final ApplicationEventPublisher publisher;

	public GenerationService(MetadataService metadataService, NamingConfigService namingConfigService,
			ApplicationEventPublisher publisher) {
		this.metadataService = metadataService;
		this.namingConfigService = namingConfigService;
		this.publisher = publisher;
	}

	@EventListener(GenerateRequestedEvent.class)
	public void onGenerate(GenerateRequestedEvent event) {
		ConnectionConfig cfg = event.config();
		SchemaSelection selection = event.selection();
		int generated = 0;

		try (Connection connection = metadataService.open(cfg)) {
			String catalog = selection.catalog();
			String schema = selection.schema();

			System.out.printf("%n→ Scanning catalog=%s schema=%s ...%n", nvl(catalog), nvl(schema));
			List<String> tables = metadataService.listTables(connection, catalog, schema);
			System.out.printf("   Found %d table(s).%n", tables.size());

			String entityPackage = normalizePackage(cfg.getBasePackage());
			String dtoPackage = siblingPackage(entityPackage, "dtos");
			String mapperPackage = siblingPackage(entityPackage, "mappers");
			String repositoryPackage = siblingPackage(entityPackage, "repositories");

			Path rootPath = cfg.getOutputPath();
			Files.createDirectories(rootPath);

			System.out.printf("   Output root       : %s%n", rootPath.toAbsolutePath());
			System.out.printf("   Entity package    : %s%n", entityPackage);
			System.out.printf("   DTO package       : %s%n", dtoPackage);
			System.out.printf("   Mapper package    : %s%n", mapperPackage);
			System.out.printf("   Repository package: %s%n", repositoryPackage);
			System.out.printf("   FK mode           : %s%n", cfg.getFkMode());
			System.out.printf("   Relation fetch    : %s%n", cfg.getRelationFetch());
			System.out.printf("   Generate DTO      : %s%n", cfg.getGenerateDto());
			System.out.printf("   Generate Repo     : %s%n", cfg.getGenerateRepositories());

			DatabaseMetaData metaData = connection.getMetaData();
			String dbProduct = metaData.getDatabaseProductName();

			// build metadata model for all tables
			List<EntityModel> models = new ArrayList<>();
			for (String table : tables) {
				models.add(loadEntityModel(metaData, catalog, schema, table, dbProduct));
			}

			// generate per table
			for (EntityModel model : models) {
				writeEntityFiles(entityPackage, dtoPackage, mapperPackage, repositoryPackage, rootPath, model, models,
						dbProduct, cfg.getFkMode(), cfg.getRelationFetch(), cfg.getGenerateDto(),
						cfg.getGenerateRepositories(), metaData);
				generated++;
			}

			publisher.publishEvent(new GenerationCompletedEvent(generated, rootPath));
			System.out.printf("✓ Generation complete. %d entit%s created.%n", generated, generated == 1 ? "y" : "ies");
		} catch (Exception e) {
			System.err.println("Generation failed:");
			e.printStackTrace();
		}
	}

	/**
	 * Load entity model for one DB table: - columns (with SQL type info,
	 * nullability, default, autoincrement) - PK columns - single-column FKs
	 */
	private EntityModel loadEntityModel(DatabaseMetaData md, String catalog, String schema, String table,
			String dbProduct) throws SQLException {

		Map<String, ColumnModel> columns = new LinkedHashMap<>();
		Set<String> pkCols = new LinkedHashSet<>();

		// columns
		try (ResultSet rs = md.getColumns(catalog, schema, table, "%")) {
			while (rs.next()) {
				String name = rs.getString("COLUMN_NAME");
				int dataType = rs.getInt("DATA_TYPE");
				String typeName = rs.getString("TYPE_NAME");
				boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
				String isAuto = nullSafe(rs.getString("IS_AUTOINCREMENT"));
				String columnDef = rs.getString("COLUMN_DEF");
				boolean autoIncrement = detectAutoIncrement(dbProduct, isAuto, typeName, columnDef);
				columns.put(name, new ColumnModel(name, dataType, typeName, nullable, columnDef, autoIncrement));
			}
		}

		// PK columns
		try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
			while (rs.next()) {
				pkCols.add(rs.getString("COLUMN_NAME"));
			}
		}

		// FKs
		Map<String, List<ImportedFkRow>> fkGroups = new LinkedHashMap<>();
		try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
			while (rs.next()) {
				String fkName = rs.getString("FK_NAME");
				String pkTable = rs.getString("PKTABLE_NAME");
				String pkColumn = rs.getString("PKCOLUMN_NAME");
				String fkColumn = rs.getString("FKCOLUMN_NAME");
				int keySeq = rs.getInt("KEY_SEQ");

				if (fkName == null || fkName.isBlank()) {
					fkName = pkTable + "__" + fkColumn;
				}

				ImportedFkRow row = new ImportedFkRow(fkName, fkColumn, pkTable, pkColumn, keySeq);
				fkGroups.computeIfAbsent(fkName, k -> new ArrayList<>()).add(row);
			}
		}

		// keep only single-column FKs
		List<SimpleFkModel> simpleFks = new ArrayList<>();
		for (List<ImportedFkRow> rows : fkGroups.values()) {
			if (rows.size() == 1) {
				ImportedFkRow r = rows.get(0);
				simpleFks.add(new SimpleFkModel(r.localColumn(), r.pkTable(), r.pkColumn()));
			}
		}

		return new EntityModel(catalog, schema, table, columns, pkCols, simpleFks);
	}

	/**
	 * Generate: - entity class (+ embedded id if composite PK) - optional DTO &
	 * mapper - optional repository
	 */
	private void writeEntityFiles(String entityPackage, String dtoPackage, String mapperPackage,
			String repositoryPackage, Path rootPath, EntityModel model, List<EntityModel> allModels, String dbProduct,
			FkMode fkMode, RelationFetch relationFetch, boolean generateDto, boolean generateRepositories,
			DatabaseMetaData md) throws IOException {

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

		// we will collect scalar non-PK fields for Repository findBy
		List<ScalarFieldInfo> scalarFieldInfos = new ArrayList<>();

		// this will be used for JpaRepository<ENTITY, IDTYPE>
		TypeName idTypeForRepository = null;

		/*
		 * Composite PK -> add EmbeddedId field and generate Id class
		 */
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

		/*
		 * Child-side relationships if fkMode == RELATION: - ManyToOne or OneToOne
		 * with @JoinColumn
		 */
		Set<String> handledFkColumns = new HashSet<>();
		List<FieldSpec> relationFieldsChildSide = new ArrayList<>();

		if (fkMode == FkMode.RELATION) {
			for (SimpleFkModel fk : model.simpleFks()) {
				String localCol = fk.localColumn();

				// do not replace PK columns with relations
				if (model.pkCols().contains(localCol)) {
					continue;
				}

				String targetEntityName = namingConfigService.resolveEntityName(fk.targetTable());
				ClassName targetType = ClassName.get(entityPackage, targetEntityName);
				String relFieldName = lowerFirst(targetEntityName);

				boolean unique = isColumnUnique(md, model.catalog(), model.schema(), model.table(), localCol);

				// choose @OneToOne if FK column is unique, else @ManyToOne
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

		/*
		 * Physical columns as scalar fields (or PK simple field).
		 *
		 * Rules: - skip columns replaced by relationFieldsChildSide (if fkMode ==
		 * RELATION) - skip PK columns if compositePk (they're already inside
		 * EmbeddedId) - mark @Id on single-column PK - detect and annotate
		 * GeneratedValue if auto-increment/sequence - collect only NON-PK scalar fields
		 * into scalarFieldInfos so repositories can generate findBy<Field>
		 */
		for (ColumnModel col : model.columns().values()) {

			boolean isSimplePkColumn = (!compositePk && model.pkCols().contains(col.name()));
			boolean skipBecauseRelation = handledFkColumns.contains(col.name()) && !isSimplePkColumn;
			if (skipBecauseRelation) {
				continue;
			}

			if (compositePk && model.pkCols().contains(col.name())) {
				// handled in EmbeddedId class
				continue;
			}

			String fieldName = namingConfigService.resolveColumnName(model.table(), col.name());

			TypeName javaType = SqlTypeMapper.map(col.dataType(), col.typeName(), col.nullable(), dbProduct);

			FieldSpec.Builder field = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE);

			if (isSimplePkColumn) {
				// @Id
				field.addAnnotation(ClassName.get("jakarta.persistence", "Id"));
				// include PK in equals/hashCode and toString
				field.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));
				field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));

				// @GeneratedValue / @SequenceGenerator logic
				if (col.autoIncrement()) {
					if (isPostgres(dbProduct) && col.columnDef() != null
							&& col.columnDef().toLowerCase().contains("nextval(")) {

						// try to detect sequence name
						String seqName = extractPgSequenceName(col.columnDef());
						String genName = (model.table() + "_" + col.name() + "_seq_gen").replaceAll("[^A-Za-z0-9_]",
								"_");

						if (seqName != null && !seqName.isBlank()) {
							// Add SequenceGenerator at class level
							AnnotationSpec seqGen = AnnotationSpec
									.builder(ClassName.get("jakarta.persistence", "SequenceGenerator"))
									.addMember("name", "$S", genName).addMember("sequenceName", "$S", seqName)
									.addMember("allocationSize", "$L", 1).build();
							entity.addAnnotation(seqGen);

							// Add GeneratedValue with strategy=SEQUENCE
							AnnotationSpec genVal = AnnotationSpec
									.builder(ClassName.get("jakarta.persistence", "GeneratedValue"))
									.addMember("strategy", "$T.SEQUENCE",
											ClassName.get("jakarta.persistence", "GenerationType"))
									.addMember("generator", "$S", genName).build();
							field.addAnnotation(genVal);
						} else {
							// fallback to IDENTITY
							AnnotationSpec genVal = AnnotationSpec
									.builder(ClassName.get("jakarta.persistence", "GeneratedValue"))
									.addMember("strategy", "$T.IDENTITY",
											ClassName.get("jakarta.persistence", "GenerationType"))
									.build();
							field.addAnnotation(genVal);
						}
					} else {
						// generic IDENTITY case for MySQL/MariaDB/H2/etc
						AnnotationSpec genVal = AnnotationSpec
								.builder(ClassName.get("jakarta.persistence", "GeneratedValue")).addMember("strategy",
										"$T.IDENTITY", ClassName.get("jakarta.persistence", "GenerationType"))
								.build();
						field.addAnnotation(genVal);
					}
				}

				// remember ID type for repository
				if (!compositePk) {
					idTypeForRepository = javaType;
				}

			} else {
				// Non-PK scalar field:
				// We include it in toString unless it's byte[] (to avoid large/binary printing)
				if (!javaType.toString().equals("byte[]")) {
					field.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"));
				}

				// only NON-PK scalar fields get into scalarFieldInfos for repository finder
				// generation
				scalarFieldInfos.add(new ScalarFieldInfo(fieldName, javaType));
			}

			// @Column(name = "DB_COL")
			AnnotationSpec.Builder colAnn = AnnotationSpec.builder(ClassName.get("jakarta.persistence", "Column"))
					.addMember("name", "$S", col.name());

			// json/jsonb handling for Postgres
			String tn = col.typeName() == null ? "" : col.typeName().toLowerCase(Locale.ROOT);
			if ((tn.equals("jsonb") || tn.equals("json")) && isPostgres(dbProduct)) {
				colAnn.addMember("columnDefinition", "$S", tn);
				field.addAnnotation(AnnotationSpec.builder(ClassName.get("org.hibernate.annotations", "JdbcTypeCode"))
						.addMember("value", "$T.JSON", ClassName.get("org.hibernate.type", "SqlTypes")).build());
			}

			field.addAnnotation(colAnn.build());

			entity.addField(field.build());
		}

		// add child-side relation fields
		for (FieldSpec relField : relationFieldsChildSide) {
			entity.addField(relField);
		}

		/*
		 * Parent-side inverse relations (bidirectional): For each child table that
		 * references this table: - If FK column in child is UNIQUE
		 * -> @OneToOne(mappedBy="parentField") - Else
		 * -> @OneToMany(mappedBy="parentField") Set<Child>
		 */
		if (fkMode == FkMode.RELATION) {
			List<FieldSpec> inverseFields = buildInverseRelationFields(model, allModels, entityPackage, md,
					relationFetch);
			for (FieldSpec invField : inverseFields) {
				entity.addField(invField);
			}
		}

		// write entity source file
		JavaFile.builder(entityPackage, entity.build()).build().writeTo(rootPath);

		/*
		 * DTO + Mapper generation
		 */
		if (generateDto) {
			writeDtoAndMapper(entityPackage, dtoPackage, mapperPackage, rootPath, model, dbProduct, entitySimpleName,
					generatedAnn);
		}

		/*
		 * Repository generation
		 */
		if (generateRepositories) {
			writeRepository(repositoryPackage, entityPackage, rootPath, entitySimpleName, idTypeForRepository,
					scalarFieldInfos, generatedAnn);
		}
	}

	/**
	 * Generates DTO (<EntityName>Dto) and Mapper (<EntityName>Mapper).
	 *
	 * DTO: - one field per DB column (scalar types) -
	 * Lombok @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor
	 *
	 * Mapper: - @Mapper(componentModel="spring") - EntityDto toDto(Entity entity) -
	 * Entity toEntity(EntityDto dto)
	 */
	private void writeDtoAndMapper(String entityPackage, String dtoPackage, String mapperPackage, Path rootPath,
			EntityModel model, String dbProduct, String entitySimpleName, AnnotationSpec generatedAnn)
			throws IOException {

		String dtoSimpleName = entitySimpleName + "Dto";

		AnnotationSpec toStringAnnDto = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		AnnotationSpec eqHashAnnDto = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
				.addMember("onlyExplicitlyIncluded", "$L", true).build();

		// DTO class builder
		TypeSpec.Builder dto = TypeSpec.classBuilder(dtoSimpleName).addModifiers(Modifier.PUBLIC)
				.addAnnotation(ClassName.get("lombok", "Data"))
				.addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
				.addAnnotation(ClassName.get("lombok", "Builder")).addAnnotation(toStringAnnDto)
				.addAnnotation(eqHashAnnDto).addAnnotation(generatedAnn);

		// add every DB column as simple scalar field in the DTO
		for (ColumnModel col : model.columns().values()) {
			String fieldName = namingConfigService.resolveColumnName(model.table(), col.name());

			TypeName javaType = SqlTypeMapper.map(col.dataType(), col.typeName(), col.nullable(), dbProduct);

			FieldSpec.Builder f = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE)
					.addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
					.addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"));

			dto.addField(f.build());
		}

		JavaFile.builder(dtoPackage, dto.build()).build().writeTo(rootPath);

		/*
		 * Mapper interface using MapStruct
		 */
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

	/**
	 * Generates Spring Data repository interface.
	 *
	 * interface <EntityName>Repository extends JpaRepository<EntityName, IdType> {
	 * Page<EntityName> findBy<FieldName>(FieldType value, Pageable pageable); }
	 *
	 * Only NON-PK scalar fields are used to generate findBy methods.
	 */
	private void writeRepository(String repositoryPackage, String entityPackage, Path rootPath, String entitySimpleName,
			TypeName idTypeForRepository, List<ScalarFieldInfo> scalarFields, AnnotationSpec generatedAnn)
			throws IOException {

		ClassName entityClass = ClassName.get(entityPackage, entitySimpleName);

		TypeName idType = (idTypeForRepository != null) ? idTypeForRepository : ClassName.get(Long.class);

		ParameterizedTypeName jpaRepoType = ParameterizedTypeName
				.get(ClassName.get("org.springframework.data.jpa.repository", "JpaRepository"), entityClass, idType);

		String repoSimpleName = pluralizeSimpleName(entitySimpleName) + "Repository";

		TypeSpec.Builder repo = TypeSpec.interfaceBuilder(repoSimpleName).addModifiers(Modifier.PUBLIC)
				.addSuperinterface(jpaRepoType)
				.addAnnotation(ClassName.get("org.springframework.stereotype", "Repository"))
				.addAnnotation(generatedAnn);

		for (ScalarFieldInfo sf : scalarFields) {
			String fieldName = sf.javaFieldName();
			TypeName fieldType = sf.javaType();
			String methodName = "findBy" + upperFirst(fieldName);

			MethodSpec finder = MethodSpec.methodBuilder(methodName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
					.returns(ParameterizedTypeName.get(ClassName.get("org.springframework.data.domain", "Page"),
							entityClass))
					.addParameter(fieldType, fieldName)
					.addParameter(ClassName.get("org.springframework.data.domain", "Pageable"), "pageable").build();

			repo.addMethod(finder);
		}

		JavaFile.builder(repositoryPackage, repo.build()).build().writeTo(rootPath);
	}
	
	/**
	 * Returns a pluralized-ish variant of the simple entity name for repository naming.
	 * If the name already ends with 's' or 'S', it is returned unchanged.
	 * Otherwise 's' is appended.
	 *
	 * Examples:
	 *   User        -> Users
	 *   Person      -> Persons
	 *   IncidentLog -> IncidentLogs
	 *   Jobs        -> Jobs
	 */
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

	/**
	 * Generates the <EntityName>Id embeddable class for composite primary keys. The
	 * class: - @Embeddable - implements Serializable -
	 * Lombok @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor -
	 * Same @ToString/@EqualsAndHashCode with includes - Each PK column is a field
	 * annotated with @Column(name="...") etc.
	 */
	private void writeEmbeddedId(String entityPackage, Path rootPath, String idClassName, EntityModel model,
			String dbProduct, AnnotationSpec generatedAnn) throws IOException {

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

	/**
	 * Build inverse (parent-side) relationship fields.
	 *
	 * For every childModel that has a FK to parentModel: - If FK column in child is
	 * UNIQUE:
	 * 
	 * @OneToOne(mappedBy="parentField", fetch=FetchType.<cfg>) ChildEntity child;
	 *
	 *                                   - Else:
	 * @OneToMany(mappedBy="parentField", fetch=FetchType.LAZY) Set<ChildEntity>
	 *                                    children;
	 *
	 *                                    Collections are always LAZY.
	 */
	private List<FieldSpec> buildInverseRelationFields(EntityModel parentModel, List<EntityModel> allModels,
			String entityPackage, DatabaseMetaData md, RelationFetch relationFetch) {

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
					// backref 1-1
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
					// backref 1-N
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

	/**
	 * Returns true if the given column is part of a UNIQUE index. Used to
	 * distinguish @OneToOne vs @ManyToOne and for inverse side cardinality.
	 */
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

	/**
	 * Detects if a column is auto-generated (identity/sequence/auto_increment)
	 * across vendors.
	 */
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

	/**
	 * Try to extract the sequence name from a Postgres-style default:
	 * nextval('my_seq'::regclass)
	 */
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

	/**
	 * Produces a unique field name when generating inverse relationship
	 * collections. If "children" already used, tries "children2", etc.
	 */
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

	private static String nullSafe(String s) {
		return s == null ? "" : s;
	}

	private static String nvl(String s) {
		return s == null ? "(null)" : s;
	}

	/**
	 * Normalizes a base package string: - turns slashes into dots - collapses
	 * double dots - trims leading/trailing dots
	 */
	private static String normalizePackage(String raw) {
		if (raw == null)
			return "";
		String p = raw.replace('/', '.').replace('\\', '.').trim();
		while (p.contains("..")) {
			p = p.replace("..", ".");
		}
		if (p.startsWith(".")) {
			p = p.substring(1);
		}
		if (p.endsWith(".")) {
			p = p.substring(0, p.length() - 1);
		}
		return p;
	}

	/**
	 * Builds a sibling-level package name: base = "org.cheetah.entities",
	 * siblingPackage(base,"dtos") -> "org.cheetah.dtos" If base has no dot, returns
	 * just the sibling "dtos".
	 */
	private static String siblingPackage(String entityPackage, String name) {
		if (entityPackage == null || entityPackage.isBlank()) {
			return name;
		}
		int idx = entityPackage.lastIndexOf('.');
		if (idx <= 0) {
			return name;
		}
		String parent = entityPackage.substring(0, idx);
		return parent + "." + name;
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

	/*
	 * Helper record used to track which non-PK scalar properties will get a finder
	 * method in the repository.
	 */
	record ScalarFieldInfo(String javaFieldName, TypeName javaType) {
	}

	/*
	 * Internal view of table metadata for generation.
	 */
	record EntityModel(String catalog, String schema, String table, Map<String, ColumnModel> columns,
			Set<String> pkCols, List<SimpleFkModel> simpleFks) {
	}

	/*
	 * Internal column metadata.
	 */
	record ColumnModel(String name, int dataType, String typeName, boolean nullable, String columnDef,
			boolean autoIncrement) {
	}

	/*
	 * Internal single-column FK descriptor.
	 */
	record SimpleFkModel(String localColumn, String targetTable, String targetColumn) {
	}

	/*
	 * Raw row from DatabaseMetaData.getImportedKeys().
	 */
	record ImportedFkRow(String fkName, String localColumn, String pkTable, String pkColumn, int keySeq) {
	}
}