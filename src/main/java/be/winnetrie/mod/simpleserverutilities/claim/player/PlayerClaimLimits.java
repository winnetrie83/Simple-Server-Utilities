package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.UUID;

public class PlayerClaimLimits {

    private UUID player;
    private int maxChunks;
    private int maxClaimGroups;

    /*
     * Nullable on purpose. Older save files do not contain these fields.
     * During load, null is migrated by comparing the stored value with the
     * configured default that was previously copied into every limit record.
     */
    private Boolean maxChunksOverride;
    private Boolean maxClaimGroupsOverride;

    public PlayerClaimLimits() {
        // Required for Gson
    }

    public PlayerClaimLimits(UUID player, int defaultMaxChunks, int defaultMaxClaimGroups) {
        this.player = player;
        this.maxChunks = Math.max(0, defaultMaxChunks);
        this.maxClaimGroups = Math.max(0, defaultMaxClaimGroups);
        this.maxChunksOverride = false;
        this.maxClaimGroupsOverride = false;
    }

    public UUID getPlayer() {
        return player;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        this.maxChunks = Math.max(0, maxChunks);
        this.maxChunksOverride = true;
    }

    public void addMaxChunks(int amount, int fallbackValue) {
        int base = hasMaxChunksOverride() ? maxChunks : Math.max(0, fallbackValue);
        setMaxChunks(base + amount);
    }

    public boolean hasMaxChunksOverride() {
        return Boolean.TRUE.equals(maxChunksOverride);
    }

    public void clearMaxChunksOverride(int defaultValue) {
        this.maxChunks = Math.max(0, defaultValue);
        this.maxChunksOverride = false;
    }

    public int getMaxClaimGroups() {
        return maxClaimGroups;
    }

    public void setMaxClaimGroups(int maxClaimGroups) {
        this.maxClaimGroups = Math.max(0, maxClaimGroups);
        this.maxClaimGroupsOverride = true;
    }

    public void addMaxClaimGroups(int amount, int fallbackValue) {
        int base = hasMaxClaimGroupsOverride() ? maxClaimGroups : Math.max(0, fallbackValue);
        setMaxClaimGroups(base + amount);
    }

    public boolean hasMaxClaimGroupsOverride() {
        return Boolean.TRUE.equals(maxClaimGroupsOverride);
    }

    public void clearMaxClaimGroupsOverride(int defaultValue) {
        this.maxClaimGroups = Math.max(0, defaultValue);
        this.maxClaimGroupsOverride = false;
    }

    public boolean hasAnyOverride() {
        return hasMaxChunksOverride() || hasMaxClaimGroupsOverride();
    }

    /**
     * Migrates pre-dev2.1 records that contained values but no explicit marker.
     * Values equal to the then-current defaults are treated as automatically
     * generated defaults; differing values are preserved as explicit overrides.
     */
    public void migrateLegacyOverrideState(int defaultMaxChunks, int defaultMaxClaimGroups) {
        if (maxChunksOverride == null) {
            maxChunksOverride = maxChunks != Math.max(0, defaultMaxChunks);
        }
        if (maxClaimGroupsOverride == null) {
            maxClaimGroupsOverride = maxClaimGroups != Math.max(0, defaultMaxClaimGroups);
        }
    }
}
