package org.cheetah.sword.generate;

import java.sql.Types;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

public class TypeResolver {

    public TypeName toJavaType(int jdbcType) {
        return switch (jdbcType) {
            case Types.BIGINT -> ClassName.get(Long.class);
            case Types.INTEGER -> ClassName.get(Integer.class);
            case Types.SMALLINT -> ClassName.get(Short.class);
            case Types.TINYINT -> ClassName.get(Byte.class);
            case Types.BOOLEAN, Types.BIT -> ClassName.get(Boolean.class);

            case Types.DECIMAL, Types.NUMERIC -> ClassName.get(java.math.BigDecimal.class);
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> ClassName.get(Double.class);

            case Types.DATE -> ClassName.get(java.time.LocalDate.class);
            case Types.TIME -> ClassName.get(java.time.LocalTime.class);
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ClassName.get(java.time.LocalDateTime.class);

            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR, Types.LONGNVARCHAR -> ClassName.get(String.class);

            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> ArrayTypeNames.BYTE_ARRAY;

            default -> ClassName.get(String.class);
        };
    }

    private static final class ArrayTypeNames {
        private static final TypeName BYTE_ARRAY = TypeName.get(byte[].class);

        private ArrayTypeNames() {
        }
    }
}