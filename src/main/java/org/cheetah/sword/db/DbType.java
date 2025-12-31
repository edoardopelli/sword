package org.cheetah.sword.db;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DbType {

    POSTGRESQL("PostgreSQL", 5432, "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s"),
    MARIADB("MariaDB", 3306, "org.mariadb.jdbc.Driver", "jdbc:mariadb://%s:%d/%s"),
    MYSQL("MySQL", 3306, "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s"),
    SQLSERVER("SQL Server", 1433, "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://%s:%d;databaseName=%s");

    private final String label;
    private final int defaultPort;
    private final String driverClassName;
    private final String urlTemplate;

    public String buildJdbcUrl(String host, int port, String database) {
        return urlTemplate.formatted(host, port, database);
    }
}