package org.cheetah.sword.util;

import java.util.Locale;

public final class NamingUtils {

    private NamingUtils() {
    }

    /**
     * Convert a SQL column name (snake_case, kebab-case, etc.) into a Java field
     * name in lowerCamelCase, WITHOUT trying to singularize.
     *
     *  "user_id"   -> "userId"
     *  "CREATED_AT"-> "createdAt"
     *  "zip-code"  -> "zipCode"
     */
    public static String toFieldName(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return columnName;
        }

        String[] parts = columnName
                .toLowerCase(Locale.ROOT)
                .split("[^a-zA-Z0-9]+");

        if (parts.length == 0) return columnName;

        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(capitalize(parts[i]));
        }
        return sb.toString();
    }

    /**
     * Build the default Java entity class name from a physical table name.
     * - splits by non-alphanumeric (underscore etc.)
     * - singularizes each chunk
     * - capitalizes each chunk
     *
     *  "users"              -> "User"
     *  "user_profiles"      -> "UserProfile"
     *  "BATCHES_HISTORY"    -> "BatchHistory"
     *  "audit_logs_entries" -> "AuditLogEntry"
     */
    public static String defaultEntityName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return tableName;
        }

        String[] parts = tableName
                .toLowerCase(Locale.ROOT)
                .split("[^a-zA-Z0-9]+");

        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            String singular = singularize(p);
            sb.append(capitalize(singular));
        }
        return sb.toString();
    }

    /**
     * Super basic plural -> singular heuristic, English-ish.
     * It's intentionally simple:
     *   "users" -> "user"
     *   "profiles" -> "profile"
     *   "batches" -> "batch"
     *   "companies" -> "company"
     *   "logs" -> "log"
     *
     * We keep it intentionally dumb, not NLP-perfect, just good enough for 90%.
     */
    private static String singularize(String word) {
        String w = word.toLowerCase(Locale.ROOT);

        // companies -> company
        if (w.endsWith("ies") && w.length() > 3) {
            return w.substring(0, w.length() - 3) + "y";
        }

        // batches -> batch, classes -> class, boxes -> box
        if ((w.endsWith("ses")
                || w.endsWith("xes")
                || w.endsWith("zes")
                || w.endsWith("ches")
                || w.endsWith("shes"))
                && w.length() > 3) {
            return w.substring(0, w.length() - 2); // drop "es"
        }

        // generic plural -> singular
        if (w.endsWith("s") && w.length() > 1) {
            return w.substring(0, w.length() - 1);
        }

        return w;
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        if (s.length() == 1) return s.toUpperCase(Locale.ROOT);
        return s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}