package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerPermissionData {

    private String uuid = "";
    private String lastKnownName = "";
    private List<String> ranks = new ArrayList<>();
    private Map<String, String> permissions = new HashMap<>();
    private Map<String, PermissionScope> dimensionPermissions = new HashMap<>();

    public PlayerPermissionData() {
        // Required for Gson
    }

    public String getUuid() {
        return uuid == null ? "" : uuid;
    }

    public void setUuid(java.util.UUID uuid) {
        this.uuid = uuid == null ? "" : uuid.toString();
    }

    public void setUuid(String uuid) {
        this.uuid = uuid == null ? "" : uuid.trim();
    }

    public String getLastKnownName() {
        return lastKnownName == null ? "" : lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName == null ? "" : lastKnownName.trim();
    }

    public List<String> getRanks() {
        if (ranks == null) {
            ranks = new ArrayList<>();
        }

        return ranks;
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

    public void addRank(String rankName) {
        if (rankName == null || rankName.isBlank()) {
            return;
        }

        String normalizedRank = normalizeRankName(rankName);

        if (!getRanks().contains(normalizedRank)) {
            getRanks().add(normalizedRank);
        }
    }

    public void removeRank(String rankName) {
        if (rankName == null) {
            return;
        }

        getRanks().remove(normalizeRankName(rankName));
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

    private String normalizeRankName(String rankName) {
        return rankName.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
