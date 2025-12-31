package org.cheetah.sword.util;

public final class NameUtil {

    private NameUtil() {
    }

    /**
     * Converts a DB identifier to a Java lowerCamelCase property name.
     * Examples:
     * - customer_id -> customerId
     * - CUSTOMER_ID -> customerId
     * - customerId  -> customerId
     */
    public static String toLowerCamel(String dbIdentifier) {
        if (dbIdentifier == null || dbIdentifier.isBlank()) {
            return dbIdentifier;
        }

        String s = dbIdentifier.trim();

        // If it contains separators, normalize and split.
        if (s.matches(".*[^a-zA-Z0-9].*")) {
            String[] parts = s.toLowerCase().split("[^a-z0-9]+");
            if (parts.length == 0) {
                return sanitizeJavaIdentifier(s);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(parts[0]);

            for (int i = 1; i < parts.length; i++) {
                if (parts[i].isEmpty()) {
                    continue;
                }
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }

            return sanitizeJavaIdentifier(sb.toString());
        }

        // No separators: assume already camelCase or mixed case.
        String normalized = Character.toLowerCase(s.charAt(0)) + s.substring(1);
        return sanitizeJavaIdentifier(normalized);
    }

    /**
     * Converts a DB identifier to a Java UpperCamelCase class name.
     * Examples:
     * - order_lines -> OrderLines
     * - orders      -> Orders
     */
    public static String toUpperCamel(String dbIdentifier) {
        String lower = toLowerCamel(dbIdentifier);
        if (lower == null || lower.isBlank()) {
            return lower;
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String sanitizeJavaIdentifier(String s) {
        // Minimal sanitization to avoid illegal identifiers.
        if (s == null || s.isBlank()) {
            return s;
        }
        String v = s.replaceAll("[^a-zA-Z0-9_]", "");
        if (v.isEmpty()) {
            return "_";
        }
        if (Character.isDigit(v.charAt(0))) {
            return "_" + v;
        }
        return v;
    }
}