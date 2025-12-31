package org.cheetah.sword.cli;

import java.nio.file.Path;

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
			NameResolver resolver = new NameResolver(spec.getModel().getBasePackage(), true, null, spec.getResourceOverrides() );
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

			String schema = p.ask("Schema (blank = default)", "");
			String catalog = p.ask("Catalog (blank = default)", "");
			String pattern = p.ask("Table pattern (SQL LIKE, blank = %)", "%");
			boolean includeViews = p.askYesNo("Include views?", false);

			DataSource ds = createDataSource(cs);

			DbModel model = dbIntrospector.introspect(ds, blankToNull(catalog), blankToNull(schema), pattern,
					includeViews);

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