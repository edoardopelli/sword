package org.cheetah.sword.yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class YamlSpec {

    private Model model;
    private Generation generation;

    /**
     * Optional naming overrides.
     * If code generation is executed FROM YAML, these names win.
     * If code generation is executed FROM DB (no YAML input), defaults are used.
     */
    private Naming naming;

    /**
     * Optional resource field overrides (rename and/or java type override).
     */
    private ResourceOverrides resourceOverrides;

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
    public static class Naming {
        private Map<String, TableNaming> tables = new LinkedHashMap<>();
    }

    @Data
    public static class TableNaming {
        private String entityName;
        private String dtoName;
        private String resourceName;

        /**
         * Optional per-column Java property override.
         * Key: db column name
         * Value: java property name
         */
        private Map<String, String> columns = new LinkedHashMap<>();
    }

    @Data
    public static class ResourceOverrides {
        private Map<String, ResourceTableOverride> tables = new LinkedHashMap<>();
    }

    @Data
    public static class ResourceTableOverride {
        private String resourceName;

        /**
         * Key: resource field name (target)
         * Value: mapping configuration (source dto field + optional javaType override)
         */
        private Map<String, ResourceFieldOverride> fields = new LinkedHashMap<>();
    }

    @Data
    public static class ResourceFieldOverride {
        private String sourceDtoField;
        private String javaType; // e.g. "java.lang.String", "int", "java.time.LocalDateTime"
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
        private String propertyName; // snake_case -> camelCase persisted in YAML
        private int jdbcType;
        private String jdbcTypeName;
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