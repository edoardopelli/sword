package org.cheetah.sword.service;

import org.cheetah.sword.model.ConnectionConfig;
import org.cheetah.sword.model.DbType;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetadataService {

    public Connection open(ConnectionConfig cfg) throws Exception {
        DbType t = cfg.getDbType();
        Class.forName(t.driverClass());

        // Se l'utente non ha fornito un dbName e il dialect lo richiede, usa il default.
        String dbName = cfg.getDbName();
        if ((dbName == null || dbName.isBlank())) {
            dbName = t.defaultDatabase();
        }
        String url = t.buildJdbcUrl(cfg.getHost(), cfg.getPort(), dbName);

        return DriverManager.getConnection(url, cfg.getUsername(), cfg.getPassword());
    }

    public List<String> listCatalogs(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        List<String> out = new ArrayList<>();
        try (ResultSet rs = md.getCatalogs()) {
            while (rs.next()) out.add(rs.getString("TABLE_CAT"));
        }
        return out;
    }

    public List<String> listSchemas(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        List<String> out = new ArrayList<>();
        try (ResultSet rs = md.getSchemas()) {
            while (rs.next()) out.add(rs.getString("TABLE_SCHEM"));
        }
        return out;
    }

    public List<String> listTables(Connection c, String catalog, String schema) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                out.add(rs.getString("TABLE_NAME"));
            }
        }
        return out;
    }
}