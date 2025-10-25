package org.cheetah.sword.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.cheetah.sword.events.Events.GenerateRequestedEvent;
import org.cheetah.sword.events.Events.GenerationCompletedEvent;
import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.SchemaSelection;
import org.cheetah.sword.service.records.ColumnModel;
import org.cheetah.sword.service.records.EntityModel;
import org.cheetah.sword.service.records.ImportedFkRow;
import org.cheetah.sword.service.records.SimpleFkModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates code generation by delegating to specialized writer classes.
 * Behavior preserved; responsibilities split according to SRP.
 */
@Service
@RequiredArgsConstructor
public class GenerationService {

	private final DtoAndMapperWriter dtoAndMapperWriter;

	private final MetadataService metadataService;
	private final NamingConfigService namingConfigService;
	private final ApplicationEventPublisher publisher;

	private final EntityFilesWriter entityFilesWriter;
	private final RepositoryWriter repositoryWriter;
	private final PageDtoWriter pageDtoWriter;
	private final ServiceWriter serviceWriter;

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
			String servicesPackage = siblingPackage(entityPackage, "services");
			String controllersPackage = siblingPackage(entityPackage, "controllers");
			String resourcesPackage = siblingPackage(entityPackage, "resources");
			String resourcesMapperPackage = siblingPackage(entityPackage, "resourceMappers");

			Path rootPath = cfg.getOutputPath();
			Files.createDirectories(rootPath);

			System.out.printf("   Output root        : %s%n", rootPath.toAbsolutePath());
			System.out.printf("   Entity package     : %s%n", entityPackage);
			System.out.printf("   DTO package        : %s%n", dtoPackage);
			System.out.printf("   Mapper package     : %s%n", mapperPackage);
			System.out.printf("   Repository package : %s%n", repositoryPackage);
			System.out.printf("   Service package    : %s%n", servicesPackage);
			System.out.printf("   FK mode            : %s%n", cfg.getFkMode());
			System.out.printf("   Relation fetch     : %s%n", cfg.getRelationFetch());
			System.out.printf("   Generate DTO       : %s%n", cfg.isGenerateDto());
			System.out.printf("   Generate Repo      : %s%n", cfg.isGenerateRepositories());
			System.out.printf("   Generate Services  : %s%n", cfg.isGenerateServices());
			System.out.printf("   Generate Controllers  : %s%n", cfg.isGenerateControllers());

			DatabaseMetaData metaData = connection.getMetaData();
			String dbProduct = metaData.getDatabaseProductName();

			// build table models
			List<EntityModel> models = new ArrayList<>();
			for (String table : tables) {
				models.add(loadEntityModel(metaData, catalog, schema, table, dbProduct));
			}

			// per-table generation
			for (EntityModel model : models) {
				entityFilesWriter.writeEntityFiles(entityPackage, dtoPackage, mapperPackage, repositoryPackage,
						servicesPackage,controllersPackage,resourcesPackage,resourcesMapperPackage, rootPath, model, models, dbProduct, cfg.getFkMode(), cfg.getRelationFetch(),
						cfg.isGenerateDto(), cfg.isGenerateRepositories(), cfg.isGenerateServices(),cfg.isGenerateControllers(), metaData);
				generated++;
			}

			publisher.publishEvent(new GenerationCompletedEvent(generated, rootPath));
			System.out.printf("✓ Generation complete. %d entit%s created.%n", generated, generated == 1 ? "y" : "ies");
		} catch (Exception e) {
			System.err.println("Generation failed:");
			e.printStackTrace();
		}
	}

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

		// foreign keys
		Map<String, List<ImportedFkRow>> fkGroups = new LinkedHashMap<>();
		try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
			while (rs.next()) {
				String fkName = rs.getString("FK_NAME");
				String pkTable = rs.getString("PKTABLE_NAME");
				String pkColumn = rs.getString("PKCOLUMN_NAME");
				String fkColumn = rs.getString("FKCOLUMN_NAME");
				int keySeq = rs.getInt("KEY_SEQ");
				if (fkName == null || fkName.isBlank())
					fkName = pkTable + "__" + fkColumn;
				ImportedFkRow row = new ImportedFkRow(fkName, fkColumn, pkTable, pkColumn, keySeq);
				fkGroups.computeIfAbsent(fkName, k -> new ArrayList<>()).add(row);
			}
		}

		// keep only single-column FK sets
		List<SimpleFkModel> simpleFks = new ArrayList<>();
		for (List<ImportedFkRow> rows : fkGroups.values()) {
			if (rows.size() == 1) {
				ImportedFkRow r = rows.get(0);
				simpleFks.add(new SimpleFkModel(r.localColumn(), r.pkTable(), r.pkColumn()));
			}
		}

		return new EntityModel(catalog, schema, table, columns, pkCols, simpleFks);
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

	private static String nullSafe(String s) {
		return s == null ? "" : s;
	}

	private static String nvl(String s) {
		return s == null ? "(null)" : s;
	}

	private static String normalizePackage(String raw) {
		if (raw == null)
			return "";
		String p = raw.replace('/', '.').replace('\\', '.').trim();
		while (p.contains("..")) {
			p = p.replace("..", ".");
		}
		if (p.startsWith("."))
			p = p.substring(1);
		if (p.endsWith("."))
			p = p.substring(0, p.length() - 1);
		return p;
	}

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
}
