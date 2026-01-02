package org.cheetah.sword.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ColumnModel {
	private String name;          // DB column name
    private String propertyName;  // Java property name (lowerCamelCase)
    private int jdbcType;
    private String jdbcTypeName;
    private boolean nullable;
    private Integer size;
    private Integer scale;
    private boolean autoIncrement;         // from JDBC metadata IS_AUTOINCREMENT
    private IdGeneration idGeneration;      // NONE / IDENTITY / SEQUENCE	
    private String sequenceName;            // only when idGeneration == SEQUENCE
}