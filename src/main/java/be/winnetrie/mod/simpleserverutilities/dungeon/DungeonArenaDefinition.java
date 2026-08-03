package be.winnetrie.mod.simpleserverutilities.dungeon;

import java.util.ArrayList;
import java.util.List;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Reusable region-backed arena slot owned by one dungeon definition. */
public final class DungeonArenaDefinition {
    public static final int MAX_CHECKPOINTS = 64;

    public String id = "arena_1";
    public String displayName = "Dungeon Arena 1";
    public boolean enabled = true;
    public String regionId = "";
    public boolean resetRegionAfterRun = true;
    public DungeonLocation lobby = new DungeonLocation();
    public DungeonLocation start = new DungeonLocation();
    public DungeonLocation spectator = new DungeonLocation();
    public List<DungeonCheckpointDefinition> checkpoints = new ArrayList<>();

    public DungeonArenaDefinition() { checkpoints.add(new DungeonCheckpointDefinition()); }

    public void normalize() {
        id = ContentId.require(id, "Dungeon arena ID");
        displayName = bound(displayName, 128, id);
        regionId = bound(regionId, 128, "");
        if (lobby == null) lobby = new DungeonLocation();
        if (start == null) start = lobby.copy();
        if (spectator == null) spectator = lobby.copy();
        lobby.normalize(); start.normalize(); spectator.normalize();
        ArrayList<DungeonCheckpointDefinition> normalized = new ArrayList<>();
        if (checkpoints != null) {
            for (DungeonCheckpointDefinition checkpoint : checkpoints) {
                if (checkpoint == null) continue;
                checkpoint.normalize(); normalized.add(checkpoint);
                if (normalized.size() >= MAX_CHECKPOINTS) break;
            }
        }
        checkpoints = normalized;
    }

    public DungeonCheckpointDefinition checkpoint(String rawId) {
        String id = rawId == null ? "" : rawId.trim().toLowerCase(java.util.Locale.ROOT);
        for (DungeonCheckpointDefinition checkpoint : checkpoints) if (checkpoint.id.equals(id)) return checkpoint;
        return null;
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
