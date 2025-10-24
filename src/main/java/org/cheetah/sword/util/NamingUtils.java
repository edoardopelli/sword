package org.cheetah.sword.util;

import java.util.Locale;

public final class NamingUtils {

    private NamingUtils() {}

    /**
     * Convert a SQL column name (snake_case etc.) into a Java field name lowerCamelCase.
     *
     * "user_id"      -> "userId"
     * "CREATED_AT"   -> "createdAt"
     * "zip-code"     -> "zipCode"
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
     * Build default entity class name from a physical table name.
     *
     * Rules:
     * 1. If the table name already contains uppercase letters (CamelCase style) and
     *    doesn't contain underscores/hyphens, we keep its internal capitalization and
     *    only singularize the tail.
     *
     *    "DetectionMean"      -> "DetectionMean"
     *    "ProblemType"        -> "ProblemType"
     *
     * 2. Else we split on non-alphanumeric separators, singularize each chunk (basic English:
     *    users -> user, batches -> batch, companies -> company), capitalize and concat.
     *
     *    "users"                -> "User"
     *    "user_profiles"        -> "UserProfile"
     *    "BATCHES_HISTORY"      -> "BatchHistory"
     *    "phases_problem"       -> "PhasesProblem"
     */
    public static String defaultEntityName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return tableName;
        }

        boolean looksCamel = tableName.matches(".*[A-Z].*")
                && !tableName.contains("_")
                && !tableName.contains("-");

        if (looksCamel) {
            // Keep internal caps, just singularize crudely
            return capitalize(singularize(tableName));
        }

        String[] parts = tableName.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            String singular = singularize(p.toLowerCase(Locale.ROOT));
            sb.append(capitalize(singular));
        }
        return sb.toString();
    }

    /**
     * Naive plural -> singular heuristic.
     */
    private static String singularize(String word) {
        String w = word;
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

        // generic plural: users -> user, problems -> problem
        if (lw.endsWith("s") && w.length() > 1) {
            return w.substring(0, w.length() - 1);
        }

        return w;
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        if (s.length() == 1) return s.toUpperCase(Locale.ROOT);
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}