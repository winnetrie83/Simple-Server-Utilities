package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Reusable arena slot owned by one minigame definition. */
public final class MinigameArenaDefinition {
    public static final int MAX_TEAM_SPAWNS = 64;

    public String id = "arena_1";
    public String displayName = "Arena 1";
    public boolean enabled = true;
    public String regionId = "";
    public boolean resetRegionAfterMatch;
    public MinigameLocation lobby = new MinigameLocation();
    public MinigameLocation spectator = new MinigameLocation();
    public List<MinigameSpawnPoint> teamSpawns = new ArrayList<>();

    public MinigameArenaDefinition() {
        teamSpawns.add(new MinigameSpawnPoint(1, new MinigameLocation()));
        teamSpawns.add(new MinigameSpawnPoint(2, new MinigameLocation()));
    }

    public void normalize() {
        id = ContentId.require(id, "Minigame arena ID");
        displayName = bound(displayName, 128, id);
        regionId = bound(regionId, 128, "").trim();
        if (lobby == null) lobby = new MinigameLocation();
        if (spectator == null) spectator = lobby.copy();
        lobby.normalize();
        spectator.normalize();
        ArrayList<MinigameSpawnPoint> normalized = new ArrayList<>();
        if (teamSpawns != null) {
            for (MinigameSpawnPoint spawn : teamSpawns) {
                if (spawn == null) continue;
                spawn.normalize();
                normalized.add(spawn);
                if (normalized.size() >= MAX_TEAM_SPAWNS) break;
            }
        }
        teamSpawns = normalized;
    }

    public MinigameLocation spawnForTeam(int team) {
        for (MinigameSpawnPoint spawn : teamSpawns) if (spawn.team == team) return spawn.location;
        return lobby;
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
