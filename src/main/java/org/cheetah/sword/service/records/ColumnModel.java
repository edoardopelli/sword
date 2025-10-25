package org.cheetah.sword.service.records;

public record ColumnModel(String name,
        int dataType,
        String typeName,
        boolean nullable,
        String columnDef,
        boolean autoIncrement) {
}