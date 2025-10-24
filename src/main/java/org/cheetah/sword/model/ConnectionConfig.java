package org.cheetah.sword.model;

import java.nio.file.Path;

public class ConnectionConfig {
    private DbType dbType;
    private String host;
    private int port;
    private String username;
    private String password;
    /** Nome database (Postgres, MSSQL, DB2, H2 path, opzionale per MySQL/MariaDB) */
    private String dbName;

    private String basePackage;
    private Path outputPath;

    public DbType getDbType() { return dbType; }
    public void setDbType(DbType dbType) { this.dbType = dbType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public Path getOutputPath() { return outputPath; }
    public void setOutputPath(Path outputPath) { this.outputPath = outputPath; }
}