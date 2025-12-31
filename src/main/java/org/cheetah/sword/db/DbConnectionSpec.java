package org.cheetah.sword.db;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DbConnectionSpec {
	DbType dbType;
    String host;
    int port;
    String database;
    String username;
    String password;
    String jdbcUrl;
    String driverClassName;
    
    public String jdbcUrl() {
        return dbType.buildJdbcUrl(host, port, database);
    }
}