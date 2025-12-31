package org.cheetah.sword.db;

import lombok.Getter;

@Getter
public enum DbType {
    POSTGRESQL("PostgreSQL", 5432, "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s"),
    MARIADB("MariaDB", 3306, "org.mariadb.jdbc.Driver", "jdbc:mariadb://%s:%d/%s"),
    MYSQL("MySQL", 3306, "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s"),
    SQLSERVER("SQL Server", 1433, "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=true;trustServerCertificate=true"),
    ORACLE("Oracle", 1521, "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@%s:%d/%s"),
    DB2("DB2", 50000, "com.ibm.db2.jcc.DB2Driver", "jdbc:db2://%s:%d/%s");

    private final String label;
    private final int defaultPort;
    private final String driverClassName;
    private final String jdbcUrlTemplate;

    DbType(String label, int defaultPort, String driverClassName, String jdbcUrlTemplate) {
        this.label = label;
        this.defaultPort = defaultPort;
        this.driverClassName = driverClassName;
        this.jdbcUrlTemplate = jdbcUrlTemplate;
    }

    public String buildJdbcUrl(String host, int port, String database) {
        return String.format(jdbcUrlTemplate, host, port, database);
    }
}