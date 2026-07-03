package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.HashMap;
import java.util.Map;

public class PermissionData {

    private Map<String, PermissionRank> ranks = new HashMap<>();
    private Map<String, PlayerPermissionData> players = new HashMap<>();
    private Map<String, PermissionScope> dimensions = new HashMap<>();
    private Map<String, PermissionScope> playerClaimContext = new HashMap<>();

    public PermissionData() {
        // Required for Gson
    }

    public Map<String, PermissionRank> getRanks() {
        if (ranks == null) {
            ranks = new HashMap<>();
        }

        return ranks;
    }

    public Map<String, PlayerPermissionData> getPlayers() {
        if (players == null) {
            players = new HashMap<>();
        }

        return players;
    }

    public Map<String, PermissionScope> getDimensions() {
        if (dimensions == null) {
            dimensions = new HashMap<>();
        }

        return dimensions;
    }

    public Map<String, PermissionScope> getPlayerClaimContext() {
        if (playerClaimContext == null) {
            playerClaimContext = new HashMap<>();
        }

        return playerClaimContext;
    }
}
