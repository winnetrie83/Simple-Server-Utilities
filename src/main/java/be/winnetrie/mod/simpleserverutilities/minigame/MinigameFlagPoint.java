package be.winnetrie.mod.simpleserverutilities.minigame;

/** One physical Capture the Flag base position. */
public final class MinigameFlagPoint {
    public int team = 1;
    public MinigameLocation location = new MinigameLocation();

    public MinigameFlagPoint() {
    }

    public MinigameFlagPoint(int team, MinigameLocation location) {
        this.team = team;
        this.location = location;
        normalize();
    }

    public void normalize() {
        team = team == 2 ? 2 : 1;
        if (location == null) location = new MinigameLocation();
        location.normalize();
    }
}
