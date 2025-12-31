package org.cheetah.sword.introspect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.DbModel;
import org.cheetah.sword.model.ForeignKeyModel;
import org.cheetah.sword.model.RelationCardinality;
import org.cheetah.sword.model.TableModel;
import org.springframework.stereotype.Service;

@Service
public class DbIntrospector {

    public DbModel introspect(DataSource dataSource, String catalog, String schema, String tablePattern, boolean includeViews) {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();

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

                try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
                    while (rs.next()) {
                        ColumnModel col = ColumnModel.builder()
                                .name(rs.getString("COLUMN_NAME"))
                                .jdbcType(rs.getInt("DATA_TYPE"))
                                .jdbcTypeName(rs.getString("TYPE_NAME"))
                                .nullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")))
                                .size(intOrNull(rs, "COLUMN_SIZE"))
                                .scale(intOrNull(rs, "DECIMAL_DIGITS"))
                                .build();
                        tb.column(col);
                    }
                }

                List<String> pkCols = new ArrayList<>();
                try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
                    while (rs.next()) {
                        pkCols.add(rs.getString("COLUMN_NAME"));
                    }
                }
                tb.primaryKeyColumns(pkCols);
            }

            // Foreign keys (imported keys)
            for (String tableName : tables.keySet()) {
                TableModel.TableModelBuilder tb = tables.get(tableName);

                Map<String, ForeignKeyAccumulator> byFkName = new LinkedHashMap<>();
                try (ResultSet rs = meta.getImportedKeys(catalog, schema, tableName)) {
                    while (rs.next()) {
                        String fkName = safe(rs.getString("FK_NAME"), "fk_" + tableName + "_" + rs.getString("PKTABLE_NAME"));
                        ForeignKeyAccumulator acc = byFkName.computeIfAbsent(fkName, k -> new ForeignKeyAccumulator(fkName));
                        acc.fromColumns.add(rs.getString("FKCOLUMN_NAME"));
                        acc.toTable = rs.getString("PKTABLE_NAME");
                        acc.toColumns.add(rs.getString("PKCOLUMN_NAME"));
                    }
                }

                // Determine ONE_TO_ONE vs MANY_TO_ONE (best effort):
                // - if FK columns == PK columns of from table OR FK columns are covered by a UNIQUE index -> ONE_TO_ONE
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