package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.HashMap;
import java.util.Map;

public class PermissionScope {

    private Map<String, String> permissions = new HashMap<>();

    public PermissionScope() {
        // Required for Gson
    }

    public PermissionScope(Map<String, String> permissions) {
        this.permissions = permissions == null ? new HashMap<>() : permissions;
    }

    public Map<String, String> getPermissions() {
        if (permissions == null) {
            permissions = new HashMap<>();
        }

        return permissions;
    }

    public String getPermission(String key) {
        return PermissionValueResolver.getValue(getPermissions(), key);
    }

    public void setPermission(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }

        if (value == null || value.isBlank()) {
            removePermission(key);
            return;
        }

        getPermissions().put(key.trim(), value.trim());
    }

    public void removePermission(String key) {
        if (key == null) {
            return;
        }

        getPermissions().remove(key.trim());
    }
}
