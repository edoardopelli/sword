package org.cheetah.sword.cli;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.cheetah.sword.db.DbConnectionSpec;
import org.cheetah.sword.db.DbType;
import org.cheetah.sword.generate.CodeGenerationService;
import org.cheetah.sword.generate.NameResolver;
import org.cheetah.sword.introspect.DbIntrospector;
import org.cheetah.sword.model.DbModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.yaml.YamlService;
import org.cheetah.sword.yaml.YamlSpec;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

@Component
public class WizardRunner implements CommandLineRunner {

	private final DbIntrospector dbIntrospector;
	private final YamlService yamlService;
	private final CodeGenerationService codeGenerationService;

	public WizardRunner(DbIntrospector dbIntrospector, YamlService yamlService,
			CodeGenerationService codeGenerationService) {
		this.dbIntrospector = dbIntrospector;
		this.yamlService = yamlService;
		this.codeGenerationService = codeGenerationService;
	}

	@Override
	public void run(String... args) {
		ConsolePrompter p = new ConsolePrompter();

		boolean hasYaml = p.askYesNo("Do you already have a YAML spec?", false);

		if (hasYaml) {
			String yamlPath = p.ask("YAML file path", "model.yml");
			YamlSpec spec = yamlService.read(Path.of(yamlPath));

			String outputDir = p.ask("Output directory",
					spec.getGeneration() != null ? spec.getGeneration().getOutputDir() : "./generated-src");
			DbModel model = yamlService.toDbModel(spec);
			NameResolver resolver = new NameResolver(spec.getModel().getBasePackage(), true, null, spec.getResourceOverrides());
			codeGenerationService.generateAll(Path.of(outputDir), resolver, model);
		} else {
			DbType dbType = askDbType(p);
			String host = p.ask("Hostname", "localhost");
			int port = p.askInt("Port", dbType.getDefaultPort());
			String database = p.ask("Database", "postgres");
			String username = p.ask("Username", "root");
			String password = p.askPassword("Password", "");

			DbConnectionSpec cs = DbConnectionSpec.builder().dbType(dbType).host(host).port(port).database(database)
					.username(username).password(password).build();

			// Connect first (DataSource), then show schemas/catalogs from metadata.
			DataSource ds = createDataSource(cs);

			String schema = chooseSchema(p, ds);
			String catalog = chooseCatalog(p, ds);

			String pattern = p.ask("Table pattern (SQL LIKE, blank = %)", "%");
			boolean includeViews = p.askYesNo("Include views?", false);

			DbModel model = dbIntrospector.introspect(ds, blankToNull(catalog), blankToNull(schema), pattern, includeViews);

			String basePackage = p.ask("Base package", "com.acme.generated");
			String outputDir = p.ask("Output directory", "./generated-src");

			boolean writeYaml = p.askYesNo("Write YAML spec file?", true);
			if (writeYaml) {
				String yamlOut = p.ask("YAML output path", "model.yml");
				yamlService.write(Path.of(yamlOut), basePackage, model, outputDir);
			}

			boolean genCode = p.askYesNo("Generate code now?", true);
			if (genCode) {
				NameResolver resolver = new NameResolver(basePackage, false, null, null);
				codeGenerationService.generateAll(Path.of(outputDir), resolver, model);
			}
		}
	}

	private static String chooseSchema(ConsolePrompter p, DataSource ds) {
		List<String> schemas = readSchemas(ds);

		if (!schemas.isEmpty()) {
			System.out.println("Available schemas:");
			for (int i = 0; i < schemas.size(); i++) {
				System.out.println("  " + (i + 1) + ") " + schemas.get(i));
			}
			System.out.println("You can type the schema name or the number from the list above.");
		}

		String input = p.ask("Schema (blank = default)", "");

		// Allow numeric selection without changing the existing prompt contract.
		String selected = resolveSelection(input, schemas);
		return selected != null ? selected : input;
	}

	private static String chooseCatalog(ConsolePrompter p, DataSource ds) {
		List<String> catalogs = readCatalogs(ds);

		if (!catalogs.isEmpty()) {
			System.out.println("Available catalogs:");
			for (int i = 0; i < catalogs.size(); i++) {
				System.out.println("  " + (i + 1) + ") " + catalogs.get(i));
			}
			System.out.println("You can type the catalog name or the number from the list above.");
		}

		String input = p.ask("Catalog (blank = default)", "");

		// Allow numeric selection without changing the existing prompt contract.
		String selected = resolveSelection(input, catalogs);
		return selected != null ? selected : input;
	}

	private static String resolveSelection(String input, List<String> values) {
		if (input == null) {
			return null;
		}
		String trimmed = input.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (values == null || values.isEmpty()) {
			return null;
		}
		try {
			int idx = Integer.parseInt(trimmed);
			if (idx >= 1 && idx <= values.size()) {
				return values.get(idx - 1);
			}
			return null;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static List<String> readSchemas(DataSource ds) {
		List<String> out = new ArrayList<>();
		try (Connection c = ds.getConnection()) {
			DatabaseMetaData meta = c.getMetaData();
			try (ResultSet rs = meta.getSchemas()) {
				while (rs.next()) {
					String s = rs.getString("TABLE_SCHEM");
					if (s != null && !s.isBlank() && !out.contains(s)) {
						out.add(s);
					}
				}
			}
		} catch (Exception ex) {
			// Best effort: if schema listing fails, fallback to manual input.
		}
		return out;
	}

	private static List<String> readCatalogs(DataSource ds) {
		List<String> out = new ArrayList<>();
		try (Connection c = ds.getConnection()) {
			DatabaseMetaData meta = c.getMetaData();
			try (ResultSet rs = meta.getCatalogs()) {
				while (rs.next()) {
					String s = rs.getString("TABLE_CAT");
					if (s != null && !s.isBlank() && !out.contains(s)) {
						out.add(s);
					}
				}
			}
		} catch (Exception ex) {
			// Best effort: if catalog listing fails, fallback to manual input.
		}
		return out;
	}

	private static DbType askDbType(ConsolePrompter p) {
		System.out.println("Select DB type:");
		DbType[] values = DbType.values();
		for (int i = 0; i < values.length; i++) {
			System.out.println("  " + (i + 1) + ") " + values[i].getLabel());
		}
		int idx = p.askInt("DB type", 1);
		idx = Math.max(1, Math.min(values.length, idx));
		return values[idx - 1];
	}

	private static DataSource createDataSource(DbConnectionSpec cs) {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setDriverClassName(cs.getDbType().getDriverClassName());
		ds.setUrl(cs.jdbcUrl());
		ds.setUsername(cs.getUsername());
		ds.setPassword(cs.getPassword());
		return ds;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	// Left as-is (legacy helper, currently not used by the YAML path because YamlService has its own mapper).
	private static DbModel toDbModel(YamlSpec spec) {
		DbModel.DbModelBuilder db = DbModel.builder().schema(spec.getModel().getSchema())
				.catalog(spec.getModel().getCatalog());

		for (YamlSpec.Table t : spec.getTables()) {
			TableModel.TableModelBuilder tb = TableModel.builder().name(t.getName())
					.primaryKeyColumns(t.getPrimaryKeyColumns());

			for (YamlSpec.Column c : t.getColumns()) {
				tb.column(org.cheetah.sword.model.ColumnModel.builder().name(c.getName())
						.jdbcTypeName(c.getJdbcTypeName()).jdbcType(java.sql.Types.VARCHAR) // TODO: optionally store
																							// jdbcType int in YAML too
						.nullable(c.isNullable()).size(c.getSize()).scale(c.getScale()).build());
			}

			db.table(tb.build());
		}

		return db.build();
	}
}