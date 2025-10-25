package org.cheetah.sword.service.records;

public record ImportedFkRow(String fkName,
        String localColumn,
        String pkTable,
        String pkColumn,
        int keySeq) {
}