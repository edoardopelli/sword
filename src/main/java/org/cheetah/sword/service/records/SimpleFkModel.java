package org.cheetah.sword.service.records;

public record SimpleFkModel(String localColumn,
        String targetTable,
        String targetColumn) {
}