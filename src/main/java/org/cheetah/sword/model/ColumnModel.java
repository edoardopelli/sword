package org.cheetah.sword.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ColumnModel {
    String name;
    int jdbcType;
    String jdbcTypeName;
    boolean nullable;
    Integer size;
    Integer scale;
}