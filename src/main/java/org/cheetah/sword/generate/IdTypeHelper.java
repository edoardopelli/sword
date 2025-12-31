package org.cheetah.sword.generate;

import java.util.Optional;

import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.TableModel;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

public class IdTypeHelper {

    private final TypeResolver typeResolver = new TypeResolver();

    public TypeName repositoryIdType(String basePackage, TableModel table) {
        if (table.hasCompositePrimaryKey()) {
            return ClassName.get(basePackage + ".entities.ids", toPascalCase(table.getName()) + "Id");
        }
        if (table.hasSinglePrimaryKey()) {
            String pkCol = table.getPrimaryKeyColumns().get(0);
            Optional<ColumnModel> pk = table.getColumns().stream().filter(c -> pkCol.equals(c.getName())).findFirst();
            if (pk.isPresent()) {
                return typeResolver.toJavaType(pk.get().getJdbcType());
            }
        }
        return ClassName.get(Long.class);
    }

    public ClassName embeddedIdClassName(String basePackage, TableModel table) {
        return ClassName.get(basePackage + ".entities.ids", toPascalCase(table.getName()) + "Id");
    }

    public String singlePkFieldName(TableModel table) {
        return toCamelCase(table.getPrimaryKeyColumns().get(0));
    }

    public static String toPascalCase(String s) {
        String[] parts = s.toLowerCase().split("[^a-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }

    public static String toCamelCase(String s) {
        String[] parts = s.toLowerCase().split("[^a-z0-9]+");
        if (parts.length == 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}