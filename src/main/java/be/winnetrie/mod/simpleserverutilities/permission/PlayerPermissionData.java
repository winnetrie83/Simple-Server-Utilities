package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerPermissionData {

    private List<String> ranks = new ArrayList<>();
    private Map<String, String> permissions = new HashMap<>();

    public PlayerPermissionData() {
        // Required for Gson
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
        return rankName.trim().toLowerCase();
    }
}
