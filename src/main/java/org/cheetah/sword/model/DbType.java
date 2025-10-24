package org.cheetah.sword.model;

public enum DbType {
    POSTGRES("PostgreSQL", "org.postgresql.Driver", 5432) {
        @Override public String buildJdbcUrl(String host, int port, String dbName) {
            String db = (dbName == null || dbName.isBlank()) ? "postgres" : dbName;
            return "jdbc:postgresql://" + host + ":" + port + "/" + db;
        }
        @Override public boolean usesSchema() { return true; }   // es. public, audit...
        @Override public boolean usesCatalog() { return false; }
        @Override public String defaultDatabase() { return "postgres"; }
    },
    MARIADB("MariaDB", "org.mariadb.jdbc.Driver", 3306) {
        @Override public String buildJdbcUrl(String host, int port, String dbName) {
            if (dbName == null || dbName.isBlank()) {
                return "jdbc:mariadb://" + host + ":" + port + "/";
            }
            return "jdbc:mariadb://" + host + ":" + port + "/" + dbName;
        }
        @Override public boolean usesSchema() { return false; }
        @Override public boolean usesCatalog() { return true; } // il database è il catalog
        @Override public String defaultDatabase() { return ""; } // opzionale (si possono listare i catalog)
    },
    MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", 3306) {
        @Override public String buildJdbcUrl(String host, int port, String dbName) {
            if (dbName == null || dbName.isBlank()) {
                return "jdbc:mysql://" + host + ":" + port + "/";
            }
            return "jdbc:mysql://" + host + ":" + port + "/" + dbName;
        }
        @Override public boolean usesSchema() { return false; }
        @Override public boolean usesCatalog() { return true; } // il database è il catalog
        @Override public String defaultDatabase() { return ""; }
    },
    MSSQL("MS SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433) {
        @Override public String buildJdbcUrl(String host, int port, String dbName) {
            String db = (dbName == null || dbName.isBlank()) ? "master" : dbName;
            return "jdbc:sqlserver://" + host + ":" + port + ";encrypt=false;databaseName=" + db;
        }
        @Override public boolean usesSchema() { return true; }   // es. dbo
        @Override public boolean usesCatalog() { return true; }  // il database è il catalog
        @Override public String defaultDatabase() { return "master"; }
    },
    H2("H2", "org.h2.Driver", 9092) {
        @Override public String buildJdbcUrl(String host, int port, String dbName) {
            String db = (dbName == null || dbName.isBlank()) ? "~/test" : dbName;
            return "jdbc:h2:tcp://" + host + ":" + port + "/" + db;
        }
        @Override public boolean usesSchema() { return true; }   // es. PUBLIC
        @Override public boolean usesCatalog() { return false; }
        @Override public String defaultDatabase() { return "~/test"; }
    },
    DB2("IBM DB2", "com.ibm.db2.jcc.DB2Driver", 50000) {
        @Override public String buildJdbcUrl(String host, int port, String dbName) {
            String db = (dbName == null || dbName.isBlank()) ? "SAMPLE" : dbName;
            return "jdbc:db2://" + host + ":" + port + "/" + db;
        }
        @Override public boolean usesSchema() { return true; }
        @Override public boolean usesCatalog() { return true; } // il database è il catalog
        @Override public String defaultDatabase() { return "SAMPLE"; }
    };

    private final String display;
    private final String driver;
    private final int defaultPort;

    DbType(String display, String driver, int defaultPort) {
        this.display = display;
        this.driver = driver;
        this.defaultPort = defaultPort;
    }

    public String displayName() { return display; }
    public String driverClass() { return driver; }
    public int defaultPort() { return defaultPort; }

    /** Costruisce l'URL JDBC completo includendo il database quando richiesto dal dialect. */
    public abstract String buildJdbcUrl(String host, int port, String dbName);
    public abstract boolean usesSchema();
    public abstract boolean usesCatalog();

    /** Database predefinito quando l'utente salta l'input (es. postgres, master, SAMPLE). */
    public abstract String defaultDatabase();
}