package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.LinkedHashMap;
import java.util.Map;
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

    /** Permanent, idempotent claim-capacity confiscations keyed by tax settlement id. */
    private Map<String, Integer> confiscatedChunkPenalties = new LinkedHashMap<>();

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
        long result = (long) base + amount;
        setMaxChunks((int) Math.max(0L, Math.min(Integer.MAX_VALUE, result)));
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
        long result = (long) base + amount;
        setMaxClaimGroups((int) Math.max(0L, Math.min(Integer.MAX_VALUE, result)));
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

    public int getConfiscatedChunks() {
        normalizeConfiscations();
        long total = 0L;
        for (int amount : confiscatedChunkPenalties.values()) {
            total = Math.min(Integer.MAX_VALUE, total + Math.max(0, amount));
        }
        return (int) total;
    }

    public boolean applyConfiscation(String settlementId, int amount) {
        normalizeConfiscations();
        if (settlementId == null || settlementId.isBlank() || amount <= 0) return false;
        return confiscatedChunkPenalties.putIfAbsent(settlementId.trim(), amount) == null;
    }

    public boolean removeConfiscation(String settlementId) {
        normalizeConfiscations();
        return settlementId != null && confiscatedChunkPenalties.remove(settlementId.trim()) != null;
    }

    public Map<String, Integer> getConfiscationEntries() {
        normalizeConfiscations();
        return Map.copyOf(confiscatedChunkPenalties);
    }

    public int getConfiscationAmount(String settlementId) {
        normalizeConfiscations();
        if (settlementId == null || settlementId.isBlank()) return 0;
        return Math.max(0, confiscatedChunkPenalties.getOrDefault(settlementId.trim(), 0));
    }

    public boolean hasAnyData() {
        return hasAnyOverride() || getConfiscatedChunks() > 0;
    }

    private void normalizeConfiscations() {
        if (confiscatedChunkPenalties == null) confiscatedChunkPenalties = new LinkedHashMap<>();
        Map<String, Integer> normalized = new LinkedHashMap<>();
        confiscatedChunkPenalties.forEach((key, amount) -> {
            if (key != null && !key.isBlank() && amount != null && amount > 0) {
                normalized.putIfAbsent(key.trim(), amount);
            }
        });
        confiscatedChunkPenalties = normalized;
    }

    /**
     * Migrates pre-dev2.1 records that contained values but no explicit marker.
     * Values equal to the then-current defaults are treated as automatically
     * generated defaults; differing values are preserved as explicit overrides.
     */
    public void migrateLegacyOverrideState(int defaultMaxChunks, int defaultMaxClaimGroups) {
        normalizeConfiscations();
        if (maxChunksOverride == null) {
            maxChunksOverride = maxChunks != Math.max(0, defaultMaxChunks);
        }
        if (maxClaimGroupsOverride == null) {
            maxClaimGroupsOverride = maxClaimGroups != Math.max(0, defaultMaxClaimGroups);
        }
    }
}
