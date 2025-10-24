package org.cheetah.sword.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Provides resolution of entity simple names and field names based on:
 * 1. Optional YAML overrides.
 * 2. Default naming rules (camelCase for columns, PascalCase for entities).
 *
 * YAML structure example:
 *
 * tables:
 *   problems:
 *     entityName: Problem
 *     columns:
 *       problem_id: id
 *       problem_type: type
 *   incidents:
 *     entityName: Incident
 *     columns:
 *       incident_id: id
 *       INCIDENT_SEVERITY: severityLevel
 *
 * The YAML file path is provided at runtime with a command-line argument:
 *
 *   --naming-file=/path/to/file.yml
 *   or
 *   --namingFile=/path/to/file.yml
 *
 * If no argument is provided, no overrides are applied.
 */
@Service
public class NamingConfigService {

    /**
     * Holds table-level overrides (entity simple name + per-column overrides).
     * Key is the physical table name in lowercase, as seen in the DB metadata.
     */
    private final Map<String, TableOverride> tableOverrides = new HashMap<>();

    /**
     * Creates the service and loads overrides (if any) from the YAML file
     * specified via application arguments.
     */
    public NamingConfigService(ApplicationArguments args) {
        Path overridePath = resolveOverridePath(args);
        if (overridePath != null) {
            loadOverrides(overridePath);
        } else {
            System.out.println("No naming override file provided (use --naming-file=...).");
        }
    }

    /**
     * Resolves the YAML override path from application arguments.
     * Supports both --naming-file=... and --namingFile=...
     * Returns null if not provided or empty.
     */
    private Path resolveOverridePath(ApplicationArguments args) {
        String pathCandidate = null;

        if (args.containsOption("naming-file")) {
            var values = args.getOptionValues("naming-file");
            if (values != null && !values.isEmpty()) {
                pathCandidate = values.get(0);
            }
        } else if (args.containsOption("namingFile")) {
            var values = args.getOptionValues("namingFile");
            if (values != null && !values.isEmpty()) {
                pathCandidate = values.get(0);
            }
        }

        if (pathCandidate == null || pathCandidate.isBlank()) {
            return null;
        }

        return Path.of(pathCandidate.trim());
    }

    /**
     * Loads the table and column overrides from the provided YAML file.
     * The file is optional. If it does not exist or cannot be parsed,
     * only default naming rules will be used.
     */
    @SuppressWarnings("unchecked")
    private void loadOverrides(Path path) {
        if (!Files.exists(path)) {
            System.err.println("naming override file not found: " + path.toAbsolutePath());
            return;
        }

        try (InputStream in = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                System.err.println("naming override file is empty: " + path.toAbsolutePath());
                return;
            }

            Map<String, Object> tables = (Map<String, Object>) root.get("tables");
            if (tables == null) {
                System.out.println("naming override file loaded, but 'tables' section is missing: " + path.toAbsolutePath());
                return;
            }

            for (Map.Entry<String, Object> entry : tables.entrySet()) {
                String tableName = entry.getKey();
                Map<String, Object> tbl = (Map<String, Object>) entry.getValue();

                String entityName = (String) tbl.get("entityName");

                Map<String, String> colMap = new HashMap<>();
                Map<String, Object> cols = (Map<String, Object>) tbl.get("columns");
                if (cols != null) {
                    for (Map.Entry<String, Object> c : cols.entrySet()) {
                        colMap.put(c.getKey(), c.getValue().toString());
                    }
                }

                tableOverrides.put(
                        tableName.toLowerCase(Locale.ROOT),
                        new TableOverride(entityName, colMap)
                );
            }

            System.out.println("Loaded naming overrides from " + path.toAbsolutePath()
                    + " (" + tableOverrides.size() + " table(s) configured)");
        } catch (Exception e) {
            System.err.println("Failed to load naming override file '" + path.toAbsolutePath() + "': " + e.getMessage());
        }
    }

    /**
     * Returns the entity simple class name for the given physical table name.
     * Priority:
     * 1. YAML override (tables.<table>.entityName)
     * 2. Default derivation (camelCase + capitalize first letter)
     *
     * Example:
     *   table "incidents_log" -> "IncidentsLog"
     *   override entityName: "IncidentLog"
     */
    public String resolveEntityName(String tableName) {
        TableOverride override = tableOverrides.get(tableName.toLowerCase(Locale.ROOT));
        if (override != null && override.entityName() != null && !override.entityName().isBlank()) {
            return override.entityName();
        }
        return deriveEntityName(tableName);
    }

    /**
     * Returns the Java field/property name for a given physical column name.
     * Priority:
     * 1. YAML override (tables.<table>.columns.<column>)
     * 2. Default camelCase derivation
     *
     * Example:
     *   column "INCIDENT_SEVERITY" -> "incidentSeverity"
     *   column "problem_id"       -> "problemId"
     *   override columns.problem_id: "id"
     */
    public String resolveColumnName(String tableName, String columnName) {
        TableOverride override = tableOverrides.get(tableName.toLowerCase(Locale.ROOT));
        if (override != null && override.columns().containsKey(columnName)) {
            return override.columns().get(columnName);
        }
        return toCamelCase(columnName);
    }

    /**
     * Converts a column identifier to lowerCamelCase.
     * Rules:
     * - Underscore ("_") splits words and triggers capitalization of next character.
     * - Characters after underscores are preserved in uppercase for that first letter.
     * - All non-underscore characters are lowercased unless promoted by the previous rule.
     *
     * Examples:
     *   "alarm_type_id"   -> "alarmTypeId"
     *   "ASSET_CODE"      -> "assetCode"
     *   "PBSCode"         -> "pBSCode"
     *   "INCIDENT_SEVERITY" -> "incidentSeverity"
     *
     * Note: internal uppercase sequences in the original name are generally collapsed
     * to standard camelCase, except the first promoted letter after an underscore.
     */
    private String toCamelCase(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;

        for (char c : raw.toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }

        String result = sb.toString();
        if (!result.isEmpty()) {
            result = Character.toLowerCase(result.charAt(0)) + result.substring(1);
        }
        return result;
    }

    /**
     * Derives an entity class name from a table name using camelCase + leading capital.
     * Example:
     *   "incidents_log" -> "IncidentsLog"
     *   "FRACAS_EVENT"  -> "FracasEvent"
     */
    private String deriveEntityName(String tableName) {
        String camel = toCamelCase(tableName);
        if (camel == null || camel.isEmpty()) {
            return tableName;
        }
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    /**
     * In-memory representation of overrides for a single table.
     * entityName: desired entity simple name for the table (optional).
     * columns: map from physical column name -> desired Java field name.
     */
    record TableOverride(String entityName, Map<String, String> columns) {}
}