package org.cheetah.sword.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Provides resolution of Java entity class names (for tables) and Java field names (for columns).
 *
 * Precedence:
 * 1. If a YAML override is provided at runtime with --naming-file=... or --namingFile=...,
 *    those overrides are applied first.
 * 2. Otherwise, names are derived automatically.
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
 *   alarm_events:
 *     entityName: AlarmEvent
 *     columns:
 *       event_code: code
 *       event_timestamp: timestamp
 *
 * Behavior:
 * - Tables not listed in YAML still get generated using default naming rules.
 * - Columns not listed in YAML still get generated using default naming rules.
 */
@Service
public class NamingConfigService {

    /**
     * Holds table-level overrides from YAML.
     * Key: physical table name (lowercased).
     * Value: override for entity simple name and per-column mappings.
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
            System.out.println("No naming override file provided (use --naming-file=... or --namingFile=...).");
        }
    }

    /**
     * Returns the entity simple class name for a given physical table name.
     * Precedence:
     * 1. YAML override (tables.<table>.entityName)
     * 2. Default derivation from the table name
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
     * Precedence:
     * 1. YAML override (tables.<table>.columns.<column>)
     * 2. Default camelCase derivation
     */
    public String resolveColumnName(String tableName, String columnName) {
        TableOverride override = tableOverrides.get(tableName.toLowerCase(Locale.ROOT));
        if (override != null && override.columns().containsKey(columnName)) {
            return override.columns().get(columnName);
        }
        return toPropertyName(columnName);
    }

    /**
     * Resolves CLI argument for the naming override YAML file.
     * Supports both --naming-file=... and --namingFile=...
     */
    private Path resolveOverridePath(ApplicationArguments args) {
        String pathCandidate = null;

        if (args.containsOption("naming-file")) {
            List<String> values = args.getOptionValues("naming-file");
            if (values != null && !values.isEmpty()) {
                pathCandidate = values.get(0);
            }
        } else if (args.containsOption("namingFile")) {
            List<String> values = args.getOptionValues("namingFile");
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
     * Loads table/column overrides from a YAML file.
     * Safe to call even if file does not exist or is malformed (will fallback to defaults).
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
     * Derives an entity simple name from a physical table name.
     *
     * Rules:
     * 1. Split table name into tokens.
     *    - If it contains '_' or '-' or other non-alphanumeric separators, split on these.
     *    - Else, attempt camelCase split (CustomerOrders -> ["Customer", "Orders"]).
     *    - Else, fall back to the full table name as a single token.
     *
     * 2. Singularize each token ("users" -> "user", "companies" -> "company", "subcategories" -> "subcategory").
     *
     * 3. Capitalize each singular token (user -> User, subcategory -> Subcategory).
     *
     * 4. Concatenate all tokens.
     *
     * Examples:
     *   "users"                     -> "User"
     *   "incidents_history"         -> "IncidentHistory"
     *   "intervention_subcategories"-> "InterventionSubcategory"
     *   "PBS_CODE"                  -> "PbsCode"
     *   "CUSTOMER_ORDERS"           -> "CustomerOrder"
     *   "IncidentsMaintenance"      -> "IncidentMaintenance"
     *
     * Limitation:
     *   If the table name is a single all-lowercase word with no separators
     *   and is not clearly plural (e.g. "incidentsmaintenance"), word
     *   boundaries cannot be inferred, so the result may remain unsplit
     *   (e.g. "Incidentsmaintenance").
     */
    private String deriveEntityName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return tableName;
        }

        List<String> tokens = splitIntoTokens(tableName);

        List<String> singularTokens = new ArrayList<>();
        for (String t : tokens) {
            String singular = singularize(t);
            singularTokens.add(capitalize(singular.toLowerCase(Locale.ROOT)));
        }

        if (singularTokens.isEmpty()) {
            return capitalize(tableName.toLowerCase(Locale.ROOT));
        }

        return String.join("", singularTokens);
    }

    /**
     * Returns the Java field/property name for a column name if not overridden.
     *
     * Behavior depends on the structure:
     *
     * Case A: snake_case / SCREAMING_SNAKE_CASE
     *   "problem_id"         -> "problemId"
     *   "ASSET_CODE"         -> "assetCode"
     *   "INCIDENT_SEVERITY"  -> "incidentSeverity"
     *
     * Case B: already camel-like or contains internal capitals
     *   "PBSCode"            -> "pBSCode"
     *
     * Logic:
     * - If the column contains "_" → split on "_" and build standard lowerCamelCase.
     * - Otherwise → keep original capitalization, only force the very first letter to lowercase.
     */
    private String toPropertyName(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return columnName;
        }

        if (columnName.contains("_")) {
            String[] parts = columnName.split("_+");
            if (parts.length == 0) {
                return columnName;
            }

            StringBuilder sb = new StringBuilder();

            // first chunk -> lowercase full chunk
            sb.append(parts[0].toLowerCase(Locale.ROOT));

            // following chunks -> capitalize first char, lowercase the rest
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].toLowerCase(Locale.ROOT);
                sb.append(capitalize(p));
            }

            return sb.toString();
        }

        // no underscore: keep internal capitalization, only lowercase the first character
        if (columnName.length() == 1) {
            return columnName.toLowerCase(Locale.ROOT);
        }
        return columnName.substring(0, 1).toLowerCase(Locale.ROOT) + columnName.substring(1);
    }

    /**
     * Splits a table name into "words".
     *
     * Strategy:
     * 1. If there are non-alphanumeric separators (underscore, dash, etc.), split on those.
     * 2. Else split on camel-case boundaries (e.g. "CustomerOrders" -> ["Customer", "Orders"]).
     * 3. Fallback: one single token [name].
     */
    private List<String> splitIntoTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }

        // Case 1: split on non-alphanumeric (underscore, dash, etc.)
        if (raw.matches(".*[^A-Za-z0-9].*")) {
            String[] parts = raw.split("[^A-Za-z0-9]+");
            List<String> out = new ArrayList<>();
            for (String p : parts) {
                if (!p.isBlank()) {
                    out.add(p);
                }
            }
            return out;
        }

        // Case 2: try camelCase / PascalCase split
        List<String> camelParts = splitCamelCase(raw);
        if (!camelParts.isEmpty()) {
            return camelParts;
        }

        // Case 3: fallback to raw as single token
        return Collections.singletonList(raw);
    }

    /**
     * Splits a string on camelCase / PascalCase boundaries.
     * Example:
     *   "IncidentsMaintenance" -> ["Incidents","Maintenance"]
     *   "CUSTOMEROrders"       -> ["CUSTOMER","Orders"]
     *
     * Implementation:
     *  - boundary at transition [a-z][A-Z]
     *  - boundary at transition [0-9][A-Za-z]
     *  - boundary at transition [A-Z][A-Z][a-z] (e.g. "PBSCode" -> ["PBS","Code"])
     */
    private List<String> splitCamelCase(String s) {
        List<String> parts = new ArrayList<>();
        if (s == null || s.isBlank()) return parts;

        StringBuilder current = new StringBuilder();
        char[] arr = s.toCharArray();

        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];

            if (current.length() == 0) {
                current.append(c);
                continue;
            }

            char prev = arr[i - 1];

            boolean boundary = false;

            // lower -> upper (e.g. tM)
            if (Character.isLowerCase(prev) && Character.isUpperCase(c)) {
                boundary = true;
            }

            // digit -> letter
            if (Character.isDigit(prev) && Character.isLetter(c)) {
                boundary = true;
            }

            // UPPER followed by UPPER then lower (e.g. "PBSc" -> split before 'c')
            if (i < arr.length - 1) {
                char next = arr[i + 1];
                if (Character.isUpperCase(prev) && Character.isUpperCase(c) && Character.isLowerCase(next)) {
                    boundary = true;
                }
            }

            if (boundary) {
                parts.add(current.toString());
                current.setLength(0);
            }
            current.append(c);
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }

    /**
     * Applies a naive plural -> singular heuristic for English-like plurals.
     *
     * Rules:
     * - "companies"   -> "company"
     * - "batches"     -> "batch"
     * - "classes"     -> "class"
     * - "boxes"       -> "box"
     * - "incidents"   -> "incident"
     * - "orders"      -> "order"
     *
     * If none of the patterns match, returns the original token.
     */
    private String singularize(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }

        String w = token;
        String lw = w.toLowerCase(Locale.ROOT);

        // companies -> company
        if (lw.endsWith("ies") && w.length() > 3) {
            return w.substring(0, w.length() - 3) + "y";
        }

        // batches -> batch, classes -> class, boxes -> box, crashes -> crash
        if ((lw.endsWith("ses")
                || lw.endsWith("xes")
                || lw.endsWith("zes")
                || lw.endsWith("ches")
                || lw.endsWith("shes"))
                && w.length() > 3) {
            return w.substring(0, w.length() - 2);
        }

        // generic plural: orders -> order, incidents -> incident
        if (lw.endsWith("s") && w.length() > 1) {
            return w.substring(0, w.length() - 1);
        }

        return w;
    }

    /**
     * Capitalizes the first character and lowercases the rest.
     * Example: "subcategories" -> "Subcategories", "CODE" -> "Code"
     */
    private String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        if (s.length() == 1) {
            return s.toUpperCase(Locale.ROOT);
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * Holds naming overrides for a single table.
     * entityName: desired entity class simple name
     * columns: map: physical column name -> desired Java field name
     */
    record TableOverride(String entityName, Map<String, String> columns) {}
}