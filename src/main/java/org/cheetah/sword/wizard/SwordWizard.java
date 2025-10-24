package org.cheetah.sword.wizard;

import org.cheetah.sword.events.Events.ConnectionReadyEvent;
import org.cheetah.sword.events.Events.GenerateRequestedEvent;
import org.cheetah.sword.events.Events.SchemaChosenEvent;
import org.cheetah.sword.events.Events.StartWizardEvent;
import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.DbType;
import org.cheetah.sword.model.FkMode;
import org.cheetah.sword.model.RelationFetch;
import org.cheetah.sword.model.SchemaSelection;
import org.cheetah.sword.service.MetadataService;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

/**
 * Interactive console wizard.
 *
 * Workflow:
 * 1. Ask DB vendor (DbType).
 * 2. Ask host / port / username / password / dbName.
 * 3. Test the connection and emit ConnectionReadyEvent.
 * 4. Ask user to choose catalog and/or schema from metadata, then emit SchemaChosenEvent.
 * 5. Ask code generation settings:
 *    - base package
 *    - output path
 *    - FK mode (SCALAR vs RELATION)
 *    - relation fetch mode (LAZY vs EAGER) ONLY IF fkMode == RELATION
 *    - DTO/mapping generation (yes/no)
 *    - Repository generation (yes/no)
 * 6. Emit GenerateRequestedEvent to start entity generation.
 *
 * Uses JLine for terminal IO.
 */
@Component
public class SwordWizard {

    private final ApplicationEventPublisher publisher;
    private final MetadataService metadata;

    public SwordWizard(ApplicationEventPublisher publisher, MetadataService metadata) {
        this.publisher = publisher;
        this.metadata = metadata;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        publisher.publishEvent(new StartWizardEvent());
    }

    @EventListener(StartWizardEvent.class)
    public void runWizard() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

            println(terminal, "\n🗡️  S.W.O.R.D. — Schema-Wide Object Reverse Designer");
            println(terminal, "Select database type:");

            DbType[] vals = DbType.values();
            for (int i = 0; i < vals.length; i++) {
                println(terminal, "  [" + (i + 1) + "] " + vals[i].displayName());
            }
            int choice = Integer.parseInt(reader.readLine("Choose [1-" + vals.length + "]: "));
            DbType db = vals[choice - 1];

            // Host / Port / Credentials
            String host = readDefault(reader, "Host", "localhost");
            String portStr = readDefault(reader, "Port", String.valueOf(db.defaultPort()));
            int port = Integer.parseInt(portStr);

            String user = reader.readLine("Username: ");
            String pass = reader.readLine("Password: ", (char) 0);

            // Logical database name prompt (vendor specific)
            String dbLabel = switch (db) {
                case POSTGRES -> "Database name (e.g., postgres, mydb)";
                case MSSQL   -> "Database name (default: master)";
                case DB2     -> "Database name (default: SAMPLE)";
                case H2      -> "Database/path (e.g., ~/test)";
                default      -> "Database name (optional for MySQL/MariaDB)";
            };
            String dbDefault = db.defaultDatabase();
            if (db == DbType.MYSQL || db == DbType.MARIADB) {
                dbDefault = "";
            }
            String dbName = readDefault(reader, dbLabel, dbDefault);

            // Prepare configuration bean
            ConnectionConfig cfg = new ConnectionConfig();
            cfg.setDbType(db);
            cfg.setHost(host);
            cfg.setPort(port);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setDbName(dbName);

            // Connectivity check
            println(terminal, "\nConnecting to " + db.displayName() + " ...");
            try (Connection conn = metadata.open(cfg)) {
                println(terminal, "✓ Connected.");
            }

            publisher.publishEvent(new ConnectionReadyEvent(cfg));

            /*
             * Catalog / schema selection.
             * Some databases expose catalogs, some schemas, some both.
             */
            String chosenCatalog = null;
            String chosenSchema = null;

            try (Connection conn = metadata.open(cfg)) {
                if (db.usesCatalog()) {
                    List<String> catalogs = metadata.listCatalogs(conn);
                    if (!catalogs.isEmpty()) {
                        println(terminal, "\nAvailable catalogs:");
                        for (int i = 0; i < catalogs.size(); i++) {
                            println(terminal, "  [" + (i + 1) + "] " + catalogs.get(i));
                        }
                        int idx = Integer.parseInt(
                                reader.readLine("Choose catalog [1-" + catalogs.size() + "]: ")
                        );
                        chosenCatalog = catalogs.get(idx - 1);
                    } else if (!(db == DbType.MYSQL || db == DbType.MARIADB)) {
                        chosenCatalog = (dbName == null || dbName.isBlank())
                                ? db.defaultDatabase()
                                : dbName;
                    }
                }

                if (db.usesSchema()) {
                    List<String> schemas = metadata.listSchemas(conn);
                    if (!schemas.isEmpty()) {
                        println(terminal, "\nAvailable schemas:");
                        for (int i = 0; i < schemas.size(); i++) {
                            println(terminal, "  [" + (i + 1) + "] " + schemas.get(i));
                        }
                        int idx = Integer.parseInt(
                                reader.readLine("Choose schema [1-" + schemas.size() + "]: ")
                        );
                        chosenSchema = schemas.get(idx - 1);
                    } else {
                        if (db == DbType.POSTGRES) chosenSchema = "public";
                        if (db == DbType.MSSQL)    chosenSchema = "dbo";
                        if (db == DbType.H2)       chosenSchema = "PUBLIC";
                    }
                }
            }

            cfg.setCatalog(chosenCatalog);
            cfg.setSchema(chosenSchema);

            SchemaSelection selection = new SchemaSelection(chosenCatalog, chosenSchema);

            /*
             * Code generation settings.
             */

            // Base package
            String basePkgRaw = readDefault(reader, "Base package", "com.example.entities");
            String basePkgNormalized = normalizePackage(basePkgRaw);

            // Output path
            String outPath = readDefault(
                    reader,
                    "Output path",
                    Path.of("").toAbsolutePath().toString()
            );

            cfg.setBasePackage(basePkgNormalized);
            cfg.setOutputPath(Path.of(outPath));

            // FK mapping mode
            println(terminal, "\nForeign key mapping mode:");
            println(terminal, "  [1] Scalar FK fields  (Long customerId)  <-- default (no lazy issues)");
            println(terminal, "  [2] Relations         (@ManyToOne / @OneToOne)");
            String fkChoice = readDefault(reader, "Choose [1-2]", "1");
            FkMode fkMode = "2".equals(fkChoice.trim()) ? FkMode.RELATION : FkMode.SCALAR;
            cfg.setFkMode(fkMode);

            // Relation fetch mode only if we are generating relations
            if (fkMode == FkMode.RELATION) {
                println(terminal, "\nRelation fetch for @ManyToOne / @OneToOne:");
                println(terminal, "  [1] LAZY  (recommended)");
                println(terminal, "  [2] EAGER");
                String fetchChoice = readDefault(reader, "Choose [1-2]", "1");
                RelationFetch relationFetch =
                        "2".equals(fetchChoice.trim()) ? RelationFetch.EAGER : RelationFetch.LAZY;
                cfg.setRelationFetch(relationFetch);
            } else {
                cfg.setRelationFetch(RelationFetch.LAZY);
            }

            // DTO / Mapper generation
            println(terminal, "\nDTO / Mapper generation:");
            println(terminal, "  [y] Generate DTOs and MapStruct mappers");
            println(terminal, "  [n] Do not generate DTOs (default)");
            String dtoChoice = readDefault(reader, "Generate DTOs? [y/N]", "n");
            boolean generateDto = dtoChoice.equalsIgnoreCase("y") || dtoChoice.equalsIgnoreCase("yes");
            cfg.setGenerateDto(generateDto);

            // Repository generation (NEW)
            println(terminal, "\nRepository generation:");
            println(terminal, "  [y] Generate Spring Data repositories");
            println(terminal, "  [n] Do not generate repositories (default)");
            String repoChoice = readDefault(reader, "Generate repositories? [y/N]", "n");
            boolean generateRepositories = repoChoice.equalsIgnoreCase("y") || repoChoice.equalsIgnoreCase("yes");
            cfg.setGenerateRepositories(generateRepositories);

            // Summary
            println(terminal, "\nGeneration plan:");
            println(terminal, "  DB Vendor         : " + db.displayName());
            println(terminal, "  Host              : " + cfg.getHost() + ":" + cfg.getPort());
            println(terminal, "  Database          : " + cfg.getDbName());
            println(terminal, "  Catalog           : " + cfg.getCatalog());
            println(terminal, "  Schema            : " + cfg.getSchema());
            println(terminal, "  Base package      : " + cfg.getBasePackage());
            println(terminal, "  Output path       : " + cfg.getOutputPath());
            println(terminal, "  FK mode           : " + cfg.getFkMode());
            println(terminal, "  Relation fetch    : " + cfg.getRelationFetch());
            println(terminal, "  Generate DTO      : " + cfg.getGenerateDto());
            println(terminal, "  Generate Repo     : " + cfg.getGenerateRepositories());

            // Fire events
            publisher.publishEvent(new SchemaChosenEvent(cfg, selection));
            publisher.publishEvent(new GenerateRequestedEvent(cfg, selection));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads a line with a default. Empty input returns the default.
     */
    private static String readDefault(LineReader r, String label, String def) {
        String v = r.readLine(label + " [default: " + def + "]: ");
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    /**
     * Writes a line to the terminal.
     */
    private static void println(Terminal t, String s) {
        t.writer().println(s);
        t.writer().flush();
    }

    /**
     * Normalizes a package string into dot notation.
     * Example:
     *   "org/cheetah/entities" -> "org.cheetah.entities"
     *   "org.cheetah.entities" -> "org.cheetah.entities"
     */
    private static String normalizePackage(String raw) {
        if (raw == null) return "";
        String p = raw.replace('/', '.')
                .replace('\\', '.')
                .trim();
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
}