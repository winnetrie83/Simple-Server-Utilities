package be.winnetrie.mod.simpleserverutilities.dungeon;

import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Named checkpoint inside one reusable dungeon arena. */
public final class DungeonCheckpointDefinition {
    public String id = "checkpoint_1";
    public String displayName = "Checkpoint 1";
    public DungeonLocation location = new DungeonLocation();

    public DungeonCheckpointDefinition() {}
    public DungeonCheckpointDefinition(String id, DungeonLocation location) { this.id = id; this.location = location; normalize(); }

    public void normalize() {
        id = ContentId.require(id, "Dungeon checkpoint ID");
        displayName = bound(displayName, 128, id);
        if (location == null) location = new DungeonLocation();
        location.normalize();
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
