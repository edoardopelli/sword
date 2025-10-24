package org.cheetah.sword.wizard;

import org.cheetah.sword.events.Events.*;
import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.DbType;
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
                println(terminal, "  [" + (i+1) + "] " + vals[i].displayName());
            }
            int choice = Integer.parseInt(reader.readLine("Choose [1-" + vals.length + "]: "));
            DbType db = vals[choice-1];

            String host = readDefault(reader, "Host", "localhost");
            String portStr = readDefault(reader, "Port", String.valueOf(db.defaultPort()));
            int port = Integer.parseInt(portStr);
            String user = reader.readLine("Username: ");
            String pass = reader.readLine("Password: ", (char)0);

            // Database name input
            String dbLabel = switch (db) {
                case POSTGRES -> "Database name (e.g., postgres, mydb)";
                case MSSQL   -> "Database name (default: master)";
                case DB2     -> "Database name (default: SAMPLE)";
                case H2      -> "Database/path (e.g., ~/test)";
                default      -> "Database name (optional for MySQL/MariaDB)";
            };
            String dbDefault = db.defaultDatabase();
            if (db == DbType.MYSQL || db == DbType.MARIADB) dbDefault = "";
            String dbName = readDefault(reader, dbLabel, dbDefault);

            ConnectionConfig cfg = new ConnectionConfig();
            cfg.setDbType(db);
            cfg.setHost(host);
            cfg.setPort(port);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setDbName(dbName);

            // test connessione
            println(terminal, "\nConnecting to " + db.displayName() + " ...");
            try (Connection conn = metadata.open(cfg)) {
                println(terminal, "✓ Connected.");
            }

            publisher.publishEvent(new ConnectionReadyEvent(cfg));

            // scelta catalog/schema
            String chosenCatalog = null;
            String chosenSchema  = null;

            try (Connection conn = metadata.open(cfg)) {
                if (db.usesCatalog()) {
                    List<String> catalogs = metadata.listCatalogs(conn);
                    if (!catalogs.isEmpty()) {
                        println(terminal, "\nAvailable catalogs:");
                        for (int i = 0; i < catalogs.size(); i++) {
                            println(terminal, "  [" + (i+1) + "] " + catalogs.get(i));
                        }
                        int idx = Integer.parseInt(reader.readLine("Choose catalog [1-" + catalogs.size() + "]: "));
                        chosenCatalog = catalogs.get(idx-1);
                    } else if (!(db == DbType.MYSQL || db == DbType.MARIADB)) {
                        chosenCatalog = (dbName == null || dbName.isBlank()) ? db.defaultDatabase() : dbName;
                    }
                }

                if (db.usesSchema()) {
                    List<String> schemas = metadata.listSchemas(conn);
                    if (!schemas.isEmpty()) {
                        println(terminal, "\nAvailable schemas:");
                        for (int i = 0; i < schemas.size(); i++) {
                            println(terminal, "  [" + (i+1) + "] " + schemas.get(i));
                        }
                        int idx = Integer.parseInt(reader.readLine("Choose schema [1-" + schemas.size() + "]: "));
                        chosenSchema = schemas.get(idx-1);
                    } else {
                        if (db == DbType.POSTGRES) chosenSchema = "public";
                        if (db == DbType.MSSQL)    chosenSchema = "dbo";
                        if (db == DbType.H2)       chosenSchema = "PUBLIC";
                    }
                }
            }

            SchemaSelection selection = new SchemaSelection(chosenCatalog, chosenSchema);

            // package name
            String basePkgRaw = readDefault(reader, "Base package", "com.example.entities");
            // 👇 normalizzazione: se l'utente mette "org/cheetah/fracas/entities"
            // lo trasformiamo in "org.cheetah.fracas.entities"
            String basePkg = normalizePackage(basePkgRaw);

            // output path
            String outPath = readDefault(reader, "Output path", Path.of("").toAbsolutePath().toString());

            cfg.setBasePackage(basePkg);
            cfg.setOutputPath(Path.of(outPath));

            publisher.publishEvent(new SchemaChosenEvent(cfg, selection));
            publisher.publishEvent(new GenerateRequestedEvent(cfg, selection));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String readDefault(LineReader r, String label, String def) {
        String v = r.readLine(label + " [default: " + def + "]: ");
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static void println(Terminal t, String s) {
        t.writer().println(s);
        t.writer().flush();
    }

    /**
     * Normalizza il package:
     * - converte / e \ in .
     * - rimuove doppi punti consecutivi
     * - toglie eventuali . iniziali/finali
     */
    private static String normalizePackage(String raw) {
        if (raw == null) return "";
        // 1. sostituisci / e \ con .
        String p = raw.replace('/', '.')
                      .replace('\\', '.')
                      .trim();
        // 2. collassa .. multipli
        while (p.contains("..")) {
            p = p.replace("..", ".");
        }
        // 3. togli . iniziali/finali
        if (p.startsWith(".")) p = p.substring(1);
        if (p.endsWith(".")) p = p.substring(0, p.length()-1);
        return p;
    }
}