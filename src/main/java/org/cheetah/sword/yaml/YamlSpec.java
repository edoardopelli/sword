package org.cheetah.sword.yaml;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class YamlSpec {

    private Model model;
    private Generation generation;
    private List<Table> tables = new ArrayList<>();

    @Data
    public static class Model {
        private String basePackage;
        private String schema;
        private String catalog;
    }

    @Data
    public static class Generation {
        private String outputDir;
    }

    @Data
    public static class Table {
        private String name;
        private List<Column> columns = new ArrayList<>();
        private List<String> primaryKeyColumns = new ArrayList<>();
        private List<ForeignKey> foreignKeys = new ArrayList<>();
    }

    @Data
    public static class Column {
        private String name;
        private int jdbcType;          // java.sql.Types value
        private String jdbcTypeName;   // database type name
        private boolean nullable;
        private Integer size;
        private Integer scale;
    }

    @Data
    public static class ForeignKey {
        private String name;
        private List<String> fromColumns = new ArrayList<>();
        private String toTable;
        private List<String> toColumns = new ArrayList<>();
        private String cardinality; // MANY_TO_ONE / ONE_TO_ONE
    }
}