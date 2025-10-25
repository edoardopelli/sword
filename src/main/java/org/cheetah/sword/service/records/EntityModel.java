package org.cheetah.sword.service.records;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record EntityModel(String catalog,
        String schema,
        String table,
        Map<String, ColumnModel> columns,
        Set<String> pkCols,
        List<SimpleFkModel> simpleFks) {
}
