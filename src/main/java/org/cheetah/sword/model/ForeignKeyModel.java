package org.cheetah.sword.model;

import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class ForeignKeyModel {
    String name;

    @Singular("fromColumn")
    List<String> fromColumns;

    String toTable;

    @Singular("toColumn")
    List<String> toColumns;

    RelationCardinality cardinality;
}