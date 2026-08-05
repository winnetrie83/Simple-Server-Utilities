package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** One persistent Domination capture node inside an arena. */
public final class MinigameControlPoint {
    public String id = "node_1";
    public String displayName = "Node 1";
    public MinigameLocation location = new MinigameLocation();
    /** Spawn used by the owning team when this node is controlled. */
    public MinigameLocation respawn;

    public MinigameControlPoint() {
    }

    public MinigameControlPoint(String id, String displayName, MinigameLocation location) {
        this.id = id;
        this.displayName = displayName;
        this.location = location;
        this.respawn = defaultRespawn(location);
        normalize();
    }

    public void normalize() {
        id = ContentId.require(id, "Domination node ID");
        displayName = bound(displayName, 64, id);
        if (location == null) location = new MinigameLocation();
        location.normalize();
        if (respawn != null) respawn.normalize();
    }

    private static MinigameLocation defaultRespawn(MinigameLocation location) {
        MinigameLocation source = location == null ? new MinigameLocation() : location;
        return new MinigameLocation(source.dimension, source.x + 1.5D, source.y, source.z,
                source.yaw, source.pitch);
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
