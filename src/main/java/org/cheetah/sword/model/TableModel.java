package org.cheetah.sword.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class TableModel {
    String name;

    @Singular
    List<ColumnModel> columns;

    @Builder.Default
    List<String> primaryKeyColumns = new ArrayList<>();

    @Singular
    List<ForeignKeyModel> foreignKeys;

    public boolean hasCompositePrimaryKey() {
        return primaryKeyColumns != null && primaryKeyColumns.size() > 1;
    }

    public boolean hasSinglePrimaryKey() {
        return primaryKeyColumns != null && primaryKeyColumns.size() == 1;
    }
}