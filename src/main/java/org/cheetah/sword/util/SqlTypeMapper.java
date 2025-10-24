package org.cheetah.sword.util;

import java.sql.Types;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

public final class SqlTypeMapper {
    private SqlTypeMapper() {}

    public static TypeName map(int sqlType, String typeName, boolean nullable, String dbProductName) {
        String tn = typeName == null ? "" : typeName.trim().toLowerCase();
        String db = dbProductName == null ? "" : dbProductName.toLowerCase();

        // PostgreSQL json/jsonb
        if ((db.contains("postgres")) && (tn.equals("jsonb") || tn.equals("json"))) {
            return ParameterizedTypeName.get(ClassName.get(java.util.Map.class),
                    ClassName.get(String.class), ClassName.get(Object.class));
        }

        return switch (sqlType) {
            case Types.VARCHAR, Types.LONGVARCHAR, Types.CHAR, Types.CLOB -> ClassName.get(String.class);
            case Types.INTEGER, Types.SMALLINT -> ClassName.get(Integer.class);
            case Types.BIGINT -> ClassName.get(Long.class);
            case Types.DECIMAL, Types.NUMERIC -> ClassName.get(java.math.BigDecimal.class);
            case Types.BIT, Types.BOOLEAN -> ClassName.get(Boolean.class);
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ClassName.get(java.time.OffsetDateTime.class);
            case Types.DATE -> ClassName.get(java.time.LocalDate.class);
            case Types.TIME -> ClassName.get(java.time.LocalTime.class);
            case Types.BINARY, Types.VARBINARY, Types.BLOB -> ArrayTypeName.of(TypeName.BYTE);
            default -> ClassName.get(Object.class);
        };
    }
}