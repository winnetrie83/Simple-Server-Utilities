package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.Map;

public final class PermissionValueResolver {

    private PermissionValueResolver() {
    }

    public static String getValue(Map<String, String> permissions, String key) {
        if (permissions == null || key == null || key.isBlank()) {
            return null;
        }

        String exactValue = permissions.get(key);
        if (exactValue != null) {
            return exactValue;
        }

        String wildcardKey = key;

        while (wildcardKey.contains(".")) {
            int lastDot = wildcardKey.lastIndexOf('.');
            wildcardKey = wildcardKey.substring(0, lastDot);

            String value = permissions.get(wildcardKey + ".*");
            if (value != null) {
                return value;
            }
        }

        return permissions.get("*");
    }
}
