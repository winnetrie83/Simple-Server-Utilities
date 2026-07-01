package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.UUID;

public class PlayerClaimLimits {

    private UUID player;
    private int maxChunks;
    private int maxClaimGroups;

    public PlayerClaimLimits() {
        // Required for Gson
    }

    public PlayerClaimLimits(UUID player, int maxChunks, int maxClaimGroups) {
        this.player = player;
        this.maxChunks = maxChunks;
        this.maxClaimGroups = maxClaimGroups;
    }

    public UUID getPlayer() {
        return player;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        this.maxChunks = Math.max(0, maxChunks);
    }

    public void addMaxChunks(int amount) {
        setMaxChunks(maxChunks + amount);
    }

    public int getMaxClaimGroups() {
        return maxClaimGroups;
    }

    public void setMaxClaimGroups(int maxClaimGroups) {
        this.maxClaimGroups = Math.max(0, maxClaimGroups);
    }

    public void addMaxClaimGroups(int amount) {
        setMaxClaimGroups(maxClaimGroups + amount);
    }
}