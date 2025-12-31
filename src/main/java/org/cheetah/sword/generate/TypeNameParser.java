package org.cheetah.sword.generate;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

public final class TypeNameParser {

    private TypeNameParser() {
    }

    public static TypeName parse(String javaType) {
        if (javaType == null || javaType.isBlank()) {
            throw new IllegalArgumentException("javaType is blank.");
        }

        String t = javaType.trim();

        if (t.endsWith("[]")) {
            String component = t.substring(0, t.length() - 2);
            return ArrayTypeName.of(parse(component));
        }

        return switch (t) {
            case "boolean" -> TypeName.BOOLEAN;
            case "byte" -> TypeName.BYTE;
            case "short" -> TypeName.SHORT;
            case "int" -> TypeName.INT;
            case "long" -> TypeName.LONG;
            case "char" -> TypeName.CHAR;
            case "float" -> TypeName.FLOAT;
            case "double" -> TypeName.DOUBLE;
            case "void" -> TypeName.VOID;
            default -> ClassName.bestGuess(t);
        };
    }
}