package org.cheetah.sword.generate;

import java.util.Map;

import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.cheetah.sword.yaml.YamlSpec;

public class NameResolver {

    private final String basePackage;
    private final boolean yamlDriven;
    private final Map<String, YamlSpec.TableNaming> tableNaming;
    private final YamlSpec.ResourceOverrides resourceOverrides;

    public NameResolver(String basePackage,
                        boolean yamlDriven,
                        Map<String, YamlSpec.TableNaming> tableNaming,
                        YamlSpec.ResourceOverrides resourceOverrides) {
        this.basePackage = basePackage;
        this.yamlDriven = yamlDriven;
        this.tableNaming = tableNaming;
        this.resourceOverrides = resourceOverrides;
    }

    public String entitiesPackage() {
        return basePackage + ".entities";
    }

    public String entityIdsPackage() {
        return basePackage + ".entities.ids";
    }

    public String dtosPackage() {
        return basePackage + ".dtos";
    }

    public String resourcesPackage() {
        return basePackage + ".resources";
    }

    public String repositoriesPackage() {
        return basePackage + ".repositories";
    }

    public String servicesPackage() {
        return basePackage + ".services";
    }

    public String controllersPackage() {
        return basePackage + ".controllers";
    }

    public String mappersPackage() {
        return basePackage + ".mappers";
    }

    public String entitySimpleName(TableModel table) {
        String def = NameUtil.toUpperCamel(table.getName()) + "Entity";
        if (!yamlDriven || tableNaming == null) {
            return def;
        }
        YamlSpec.TableNaming tn = tableNaming.get(table.getName());
        return (tn != null && tn.getEntityName() != null && !tn.getEntityName().isBlank()) ? tn.getEntityName() : def;
    }

    public String dtoSimpleName(TableModel table) {
        String def = NameUtil.toUpperCamel(table.getName()) + "DTO";
        if (!yamlDriven || tableNaming == null) {
            return def;
        }
        YamlSpec.TableNaming tn = tableNaming.get(table.getName());
        return (tn != null && tn.getDtoName() != null && !tn.getDtoName().isBlank()) ? tn.getDtoName() : def;
    }

    public String resourceSimpleName(TableModel table) {
        String def = NameUtil.toUpperCamel(table.getName()) + "Resource";

        // Table-level override from resourceOverrides has precedence for resources (yaml-driven only).
        if (yamlDriven && resourceOverrides != null && resourceOverrides.getTables() != null) {
            YamlSpec.ResourceTableOverride rt = resourceOverrides.getTables().get(table.getName());
            if (rt != null && rt.getResourceName() != null && !rt.getResourceName().isBlank()) {
                return rt.getResourceName();
            }
        }

        if (!yamlDriven || tableNaming == null) {
            return def;
        }
        YamlSpec.TableNaming tn = tableNaming.get(table.getName());
        return (tn != null && tn.getResourceName() != null && !tn.getResourceName().isBlank()) ? tn.getResourceName() : def;
    }

    /**
     * Returns the Java property name for a column.
     * The default is columnModel.propertyName, but YAML can override it by table/column mapping.
     */
    public String columnPropertyName(TableModel table, ColumnModel column) {
        String def = column.getPropertyName() != null && !column.getPropertyName().isBlank()
                ? column.getPropertyName()
                : NameUtil.toLowerCamel(column.getName());

        if (!yamlDriven || tableNaming == null) {
            return def;
        }
        YamlSpec.TableNaming tn = tableNaming.get(table.getName());
        if (tn == null || tn.getColumns() == null) {
            return def;
        }
        String override = tn.getColumns().get(column.getName());
        return (override != null && !override.isBlank()) ? override : def;
    }

    public YamlSpec.ResourceTableOverride resourceOverride(TableModel table) {
        if (!yamlDriven || resourceOverrides == null || resourceOverrides.getTables() == null) {
            return null;
        }

        String name = table.getName();
        YamlSpec.ResourceTableOverride direct = resourceOverrides.getTables().get(name);
        if (direct != null) {
            return direct;
        }

        // Case-insensitive fallback
        for (var e : resourceOverrides.getTables().entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }

        return null;
    }
}