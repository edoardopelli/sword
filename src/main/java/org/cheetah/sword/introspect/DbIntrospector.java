package org.cheetah.sword.introspect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.DbModel;
import org.cheetah.sword.model.ForeignKeyModel;
import org.cheetah.sword.model.IdGeneration;
import org.cheetah.sword.model.RelationCardinality;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.springframework.stereotype.Service;

@Service
public class DbIntrospector {

    public DbModel introspect(DataSource dataSource, String catalog, String schema, String tablePattern, boolean includeViews) {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            DbProduct dbProduct = detectDbProduct(meta);

            List<String> types = new ArrayList<>();
            types.add("TABLE");
            if (includeViews) {
                types.add("VIEW");
            }

            Map<String, TableModel.TableModelBuilder> tables = new LinkedHashMap<>();

            try (ResultSet rs = meta.getTables(catalog, schema, tablePattern, types.toArray(String[]::new))) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.put(tableName, TableModel.builder().name(tableName));
                }
            }

            // Columns + PKs
            for (String tableName : tables.keySet()) {
                TableModel.TableModelBuilder tb = tables.get(tableName);

                List<ColumnModel> columns = new ArrayList<>();

                try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");

                        boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                        boolean autoIncrement = "YES".equalsIgnoreCase(safe(rs.getString("IS_AUTOINCREMENT"), "NO"));

                        ColumnModel col = ColumnModel.builder()
                                .name(columnName)
                                .propertyName(NameUtil.toLowerCamel(columnName))
                                .jdbcType(rs.getInt("DATA_TYPE"))
                                .jdbcTypeName(rs.getString("TYPE_NAME"))
                                .nullable(nullable)
                                .size(intOrNull(rs, "COLUMN_SIZE"))
                                .scale(intOrNull(rs, "DECIMAL_DIGITS"))
                                .autoIncrement(autoIncrement)
                                .idGeneration(autoIncrement ? IdGeneration.IDENTITY : IdGeneration.NONE)
                                .sequenceName(null)
                                .build();

                        columns.add(col);
                    }
                }

                List<String> pkCols = readPrimaryKeys(meta, catalog, schema, tableName);
                tb.primaryKeyColumns(pkCols);

                // Post-processing: DB-specific PK generation hints (best-effort).
                if (pkCols.size() == 1) {
                    String pkColName = pkCols.get(0);
                    applyDbSpecificIdGeneration(c, dbProduct, schema, tableName, pkColName, columns);
                }

                // Push columns into builder
                for (ColumnModel col : columns) {
                    tb.column(col);
                }
            }

            // Foreign keys (imported keys)
            for (String tableName : tables.keySet()) {
                TableModel.TableModelBuilder tb = tables.get(tableName);

                Map<String, ForeignKeyAccumulator> byFkName = new LinkedHashMap<>();
                try (ResultSet rs = meta.getImportedKeys(catalog, schema, tableName)) {
                    while (rs.next()) {
                        String pkTable = rs.getString("PKTABLE_NAME");
                        String fkName = safe(rs.getString("FK_NAME"), "fk_" + tableName + "_" + pkTable);

                        ForeignKeyAccumulator acc = byFkName.computeIfAbsent(fkName, k -> new ForeignKeyAccumulator(fkName));
                        acc.fromColumns.add(rs.getString("FKCOLUMN_NAME"));
                        acc.toTable = pkTable;
                        acc.toColumns.add(rs.getString("PKCOLUMN_NAME"));
                    }
                }

                // Determine ONE_TO_ONE vs MANY_TO_ONE (best effort).
                Set<Set<String>> uniqueIndexes = readUniqueIndexes(meta, catalog, schema, tableName);

                List<String> fromPk = tables.get(tableName).build().getPrimaryKeyColumns();

                for (ForeignKeyAccumulator acc : byFkName.values()) {
                    RelationCardinality cardinality = RelationCardinality.MANY_TO_ONE;

                    Set<String> fkColsSet = new HashSet<>(acc.fromColumns);
                    if (fromPk != null && !fromPk.isEmpty() && fkColsSet.equals(new HashSet<>(fromPk))) {
                        cardinality = RelationCardinality.ONE_TO_ONE;
                    } else {
                        for (Set<String> unique : uniqueIndexes) {
                            if (unique.equals(fkColsSet)) {
                                cardinality = RelationCardinality.ONE_TO_ONE;
                                break;
                            }
                        }
                    }

                    tb.foreignKey(ForeignKeyModel.builder()
                            .name(acc.fkName)
                            .toTable(acc.toTable)
                            .cardinality(cardinality)
                            .fromColumns(acc.fromColumns)
                            .toColumns(acc.toColumns)
                            .build());
                }
            }

            DbModel.DbModelBuilder builder = DbModel.builder()
                    .catalog(catalog)
                    .schema(schema);

            for (TableModel.TableModelBuilder tb : tables.values()) {
                builder.table(tb.build());
            }

            return builder.build();
        } catch (Exception ex) {
            throw new IllegalStateException("DB introspection failed.", ex);
        }
    }

    private static List<String> readPrimaryKeys(DatabaseMetaData meta, String catalog, String schema, String tableName) {
        // Preserve PK ordering using KEY_SEQ when available.
        Map<Integer, String> bySeq = new LinkedHashMap<>();
        List<String> fallback = new ArrayList<>();

        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                int seq = rs.getInt("KEY_SEQ");
                if (!rs.wasNull() && seq > 0) {
                    bySeq.put(seq, col);
                } else {
                    fallback.add(col);
                }
            }
        } catch (Exception ex) {
            // Best-effort. If PK reading fails, return empty.
        }

        if (!bySeq.isEmpty()) {
            List<String> ordered = new ArrayList<>(bySeq.size());
            for (int i = 1; i <= bySeq.size(); i++) {
                String col = bySeq.get(i);
                if (col != null) {
                    ordered.add(col);
                }
            }
            // If some sequences are missing, append fallback.
            ordered.addAll(fallback);
            return ordered;
        }

        return fallback;
    }

    private static void applyDbSpecificIdGeneration(Connection c,
                                                    DbProduct dbProduct,
                                                    String schema,
                                                    String tableName,
                                                    String pkColName,
                                                    List<ColumnModel> columns) {

        int pkIdx = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (pkColName.equals(columns.get(i).getName())) {
                pkIdx = i;
                break;
            }
        }
        if (pkIdx < 0) {
            return;
        }

        ColumnModel pkCol = columns.get(pkIdx);

        // If JDBC already says it's autoincrement, keep IDENTITY for most DBs.
        // For PostgreSQL, serial columns may not always be reported as autoincrement;
        // detect serial sequence with pg_get_serial_sequence in that case.
        if (dbProduct == DbProduct.POSTGRESQL) {
            // If already IDENTITY, keep it.
            if (pkCol.getIdGeneration() == IdGeneration.IDENTITY) {
                return;
            }

            // Best-effort: detect serial/bigserial sequence.
            String seq = tryReadPostgresSerialSequence(c, schema, tableName, pkColName);
            if (seq != null && !seq.isBlank()) {
                columns.set(pkIdx, ColumnModel.builder()
                        .name(pkCol.getName())
                        .propertyName(pkCol.getPropertyName())
                        .jdbcType(pkCol.getJdbcType())
                        .jdbcTypeName(pkCol.getJdbcTypeName())
                        .nullable(pkCol.isNullable())
                        .size(pkCol.getSize())
                        .scale(pkCol.getScale())
                        .autoIncrement(pkCol.isAutoIncrement())
                        .idGeneration(IdGeneration.SEQUENCE)
                        .sequenceName(seq)
                        .build());
            }
            return;
        }

        // Other DBs: if autoIncrement was detected, it's already set to IDENTITY.
        // For non-autoincrement columns, leave NONE. Oracle legacy sequence+trigger
        // is not reliably detectable without additional configuration.
    }

    private static String tryReadPostgresSerialSequence(Connection c, String schema, String tableName, String columnName) {
        // pg_get_serial_sequence(text, text) returns the sequence name for a serial/bigserial column.
        // This is best-effort and may return null if the column is not backed by a sequence.
        String sql = "select pg_get_serial_sequence(?, ?)";
        String qualifiedTable = buildPostgresQualifiedName(schema, tableName);
        String col = buildPostgresIdentifier(columnName);

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, qualifiedTable);
            ps.setString(2, col);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception ex) {
            // Best-effort. Ignore errors.
        }

        return null;
    }

    private static String buildPostgresQualifiedName(String schema, String table) {
        if (schema == null || schema.isBlank()) {
            return buildPostgresIdentifier(table);
        }
        return buildPostgresIdentifier(schema) + "." + buildPostgresIdentifier(table);
    }

    private static String buildPostgresIdentifier(String ident) {
        // Quote identifier only when needed to preserve case/special characters.
        if (ident == null) {
            return null;
        }
        String trimmed = ident.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        // Unquoted identifiers in PostgreSQL are folded to lower-case.
        // Quote if not matching a safe unquoted identifier pattern.
        if (trimmed.matches("[a-z_][a-z0-9_]*")) {
            return trimmed;
        }

        String escaped = trimmed.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static Set<Set<String>> readUniqueIndexes(DatabaseMetaData meta, String catalog, String schema, String table) {
        Set<Set<String>> uniques = new HashSet<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, schema, table, true, false)) {
            Map<String, Set<String>> byIndex = new LinkedHashMap<>();
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String colName = rs.getString("COLUMN_NAME");
                if (indexName == null || colName == null) {
                    continue;
                }
                byIndex.computeIfAbsent(indexName, k -> new HashSet<>()).add(colName);
            }
            uniques.addAll(byIndex.values());
        } catch (Exception ex) {
            // Unique index detection is best-effort. Ignore errors.
        }
        return uniques;
    }

    private static Integer intOrNull(ResultSet rs, String col) {
        try {
            int v = rs.getInt(col);
            return rs.wasNull() ? null : v;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String safe(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static DbProduct detectDbProduct(DatabaseMetaData meta) {
        try {
            String name = meta.getDatabaseProductName();
            if (name == null) {
                return DbProduct.OTHER;
            }
            String n = name.toLowerCase(Locale.ROOT);
            if (n.contains("postgres")) {
                return DbProduct.POSTGRESQL;
            }
            if (n.contains("mariadb")) {
                return DbProduct.MARIADB;
            }
            if (n.contains("mysql")) {
                return DbProduct.MYSQL;
            }
            if (n.contains("microsoft") && n.contains("sql")) {
                return DbProduct.SQLSERVER;
            }
            if (n.contains("oracle")) {
                return DbProduct.ORACLE;
            }
            if (n.contains("db2")) {
                return DbProduct.DB2;
            }
            return DbProduct.OTHER;
        } catch (Exception ex) {
            return DbProduct.OTHER;
        }
    }

    private enum DbProduct {
        POSTGRESQL,
        MYSQL,
        MARIADB,
        SQLSERVER,
        ORACLE,
        DB2,
        OTHER
    }

    private static final class ForeignKeyAccumulator {
        private final String fkName;
        private final List<String> fromColumns = new ArrayList<>();
        private final List<String> toColumns = new ArrayList<>();
        private String toTable;

        private ForeignKeyAccumulator(String fkName) {
            this.fkName = fkName;
        }
    }
}