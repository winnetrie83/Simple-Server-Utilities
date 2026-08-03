package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionRank {

    private int priority = 0;
    private List<String> inherits = new ArrayList<>();
    private Map<String, String> permissions = new HashMap<>();
    private Map<String, PermissionScope> dimensionPermissions = new HashMap<>();

    public PermissionRank() {
        // Required for Gson
    }

    public PermissionRank(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<String> getInherits() {
        if (inherits == null) {
            inherits = new ArrayList<>();
        }

        return inherits;
    }

    public Map<String, String> getPermissions() {
        if (permissions == null) {
            permissions = new HashMap<>();
        }

        return permissions;
    }


    public Map<String, PermissionScope> getDimensionPermissions() {
        if (dimensionPermissions == null) {
            dimensionPermissions = new HashMap<>();
        }
        return dimensionPermissions;
    }

    public PermissionScope getOrCreateDimensionScope(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("Dimension id is required.");
        }
        return getDimensionPermissions().computeIfAbsent(dimensionId.trim(), ignored -> new PermissionScope());
    }

    public PermissionScope getDimensionScope(String dimensionId) {
        return dimensionId == null ? null : getDimensionPermissions().get(dimensionId.trim());
    }

    public void removeDimensionScopeIfEmpty(String dimensionId) {
        PermissionScope scope = getDimensionScope(dimensionId);
        if (scope != null && scope.getPermissions().isEmpty()) {
            getDimensionPermissions().remove(dimensionId.trim());
        }
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
