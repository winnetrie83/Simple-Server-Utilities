package be.winnetrie.mod.simpleserverutilities.minigame;

/** One team-specific arena spawn. Team numbers are one-based for admins. */
public final class MinigameSpawnPoint {
    public int team = 1;
    public MinigameLocation location = new MinigameLocation();

    public MinigameSpawnPoint() {
    }

    public MinigameSpawnPoint(int team, MinigameLocation location) {
        this.team = team;
        this.location = location;
        normalize();
    }

    public void normalize() {
        team = Math.max(1, Math.min(16, team));
        if (location == null) location = new MinigameLocation();
        location.normalize();
    }
}
