package org.cheetah.sword.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class NamingUtils {
    private NamingUtils() {}

    public static String toClassName(String table) {
        String[] parts = table.replace('-', '_').split("_");
        return Arrays.stream(parts)
                .filter(p -> !p.isBlank())
                .map(p -> p.substring(0,1).toUpperCase() + p.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }

    public static String toFieldName(String col) {
        String[] parts = col.replace('-', '_').toLowerCase().split("_");
        String first = parts[0];
        String rest = Arrays.stream(parts).skip(1)
                .map(s -> s.substring(0,1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining());
        return first + rest;
    }

    public static String capitalize(String s){
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}