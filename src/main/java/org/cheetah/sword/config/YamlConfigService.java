package org.cheetah.sword.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads optional global YAML configuration that extends naming customization
 * beyond the simple "tables -> entityName -> columns" structure.
 *
 * If this file exists, it provides overrides for:
 * - entityName / dtoName / resourceName per table
 * - propertyName and resourceName per column
 * - generation flags for repositories, services, controllers
 * - global FK / fetch preferences
 *
 * If it does not exist or is malformed, generation continues with defaults.
 */
public class YamlConfigService {

    private final Map<String, EntityOverride> entityOverrides = new HashMap<>();
    private final Map<String, Object> globalSettings = new HashMap<>();

    public YamlConfigService(Path yamlPath) {
        if (yamlPath != null && Files.exists(yamlPath)) {
            load(yamlPath);
        } else {
            System.out.println("No extended YAML config provided, continuing with defaults.");
        }
    }

    @SuppressWarnings("unchecked")
    private void load(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) return;

            // entities section
            Object entitiesObj = root.get("entities");
            if (entitiesObj instanceof Map<?, ?> entities) {
                for (Map.Entry<?, ?> e : entities.entrySet()) {
                    String table = String.valueOf(e.getKey());
                    Map<String, Object> cfg = (Map<String, Object>) e.getValue();
                    EntityOverride eo = new EntityOverride();

                    eo.entityName = asString(cfg.get("entityName"));
                    eo.dtoName = asString(cfg.get("dtoName"));
                    eo.resourceName = asString(cfg.get("resourceName"));

                    Map<String, FieldOverride> fields = new HashMap<>();
                    Object fieldsObj = cfg.get("fields");
                    if (fieldsObj instanceof Map<?, ?> fMap) {
                        for (Map.Entry<?, ?> fe : fMap.entrySet()) {
                            String col = String.valueOf(fe.getKey());
                            Map<String, Object> fieldCfg = (Map<String, Object>) fe.getValue();
                            FieldOverride fo = new FieldOverride();
                            fo.propertyName = asString(fieldCfg.get("propertyName"));
                            fo.resourceName = asString(fieldCfg.get("resourceName"));
                            fields.put(col, fo);
                        }
                    }
                    eo.fields = fields;
                    entityOverrides.put(table.toLowerCase(Locale.ROOT), eo);
                }
            }

            // store all other sections raw
            root.forEach((k, v) -> {
                if (!"entities".equalsIgnoreCase(String.valueOf(k))) {
                    globalSettings.put(String.valueOf(k), v);
                }
            });

            System.out.println("Loaded extended YAML config (" + entityOverrides.size() + " entity sections).");
        } catch (Exception e) {
            System.err.println("Failed to load extended YAML: " + e.getMessage());
        }
    }

    private String asString(Object o) {
        return (o == null) ? null : o.toString().trim();
    }

    /** Returns the override for a given table, or null. */
    public EntityOverride getEntityOverride(String tableName) {
        return entityOverrides.get(tableName.toLowerCase(Locale.ROOT));
    }

    /** Returns true if an extended YAML config is present. */
    public boolean isActive() {
        return !entityOverrides.isEmpty() || !globalSettings.isEmpty();
    }

    public static class EntityOverride {
        public String entityName;
        public String dtoName;
        public String resourceName;
        public Map<String, FieldOverride> fields = new HashMap<>();
    }

    public static class FieldOverride {
        public String propertyName;
        public String resourceName;
    }
}