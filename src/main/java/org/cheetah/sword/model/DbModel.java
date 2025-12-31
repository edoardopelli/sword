package org.cheetah.sword.model;

import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class DbModel {
    String schema;
    String catalog;

    @Singular
    List<TableModel> tables;
}