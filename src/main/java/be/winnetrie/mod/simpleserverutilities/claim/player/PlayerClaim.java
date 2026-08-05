package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerClaim {

    public static final int SCHEMA_VERSION = 2;
    private static final long DEFAULT_MULTIPLIER_BASIS_POINTS = 10_000L;

    private int schemaVersion = SCHEMA_VERSION;
    private UUID id;
    private String name;
    private String dimension;
    private UUID owner;

    private long createdAt;
    private long lastChunkChangeAt;

    // Claim-tax cycle state. The peak is monotonic within one cycle and can
    // therefore never be lower than the current number of claimed chunks.
    private long taxCycleStartedAt;
    private long taxDueAt;
    private int taxPeakChunks;
    private long taxReminderSentForDueAt;
    private long taxRateMinorPerChunkSnapshot;
    private long taxIntervalMillisSnapshot;
    private long taxReminderLeadMillisSnapshot;
    private long taxDimensionMultiplierBasisPointsSnapshot = DEFAULT_MULTIPLIER_BASIS_POINTS;
    private String lastTaxSettlementId = "";

    private String welcomeMessage = "";

    private Set<ClaimChunk> chunks = new HashSet<>();
    private Set<UUID> trustedPlayers = new HashSet<>();
    private ClaimSettings settings = new ClaimSettings();

    public PlayerClaim() {
        // Required for Gson
    }

    public PlayerClaim(String name, String dimension, UUID owner, long timestamp) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.dimension = dimension;
        this.owner = owner;
        this.createdAt = timestamp;
        this.lastChunkChangeAt = timestamp;
    }

    private void ensureDefaults() {
        schemaVersion = SCHEMA_VERSION;
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (chunks == null) {
            chunks = new HashSet<>();
        }

        if (trustedPlayers == null) {
            trustedPlayers = new HashSet<>();
        }

        if (settings == null) {
            settings = new ClaimSettings();
        }

        if (welcomeMessage == null) {
            welcomeMessage = "";
        }

        long fallbackNow = Math.max(1L, System.currentTimeMillis());
        if (createdAt <= 0L) createdAt = lastChunkChangeAt > 0L ? lastChunkChangeAt : fallbackNow;
        if (lastChunkChangeAt <= 0L) lastChunkChangeAt = createdAt;
        taxPeakChunks = Math.max(taxPeakChunks, chunks.size());
        if (taxCycleStartedAt <= 0L) taxCycleStartedAt = createdAt;
        if (lastTaxSettlementId == null) lastTaxSettlementId = "";
        if (taxDimensionMultiplierBasisPointsSnapshot <= 0L) {
            taxDimensionMultiplierBasisPointsSnapshot = DEFAULT_MULTIPLIER_BASIS_POINTS;
        }
    }

    public UUID getId() {
        ensureDefaults();
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDimension() {
        return dimension;
    }

    public UUID getOwner() {
        return owner;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastChunkChangeAt() {
        return lastChunkChangeAt;
    }

    public String getWelcomeMessage() {
        ensureDefaults();
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage == null ? "" : welcomeMessage;
    }

    public Set<ClaimChunk> getChunks() {
        ensureDefaults();
        return chunks;
    }

    public int getChunkCount() {
        return getChunks().size();
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return getChunks().contains(new ClaimChunk(chunkX, chunkZ));
    }

    public boolean addChunk(int chunkX, int chunkZ, long timestamp) {
        boolean added = getChunks().add(new ClaimChunk(chunkX, chunkZ));

        if (added) {
            if (createdAt <= 0) {
                createdAt = timestamp;
            }

            lastChunkChangeAt = timestamp;
            taxPeakChunks = Math.max(taxPeakChunks, getChunkCount());
        }

        return added;
    }

    public boolean removeChunk(int chunkX, int chunkZ, long timestamp) {
        boolean removed = getChunks().remove(new ClaimChunk(chunkX, chunkZ));

        if (removed) {
            lastChunkChangeAt = timestamp;
        }

        return removed;
    }

    public Set<UUID> getTrustedPlayers() {
        ensureDefaults();
        return trustedPlayers;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return getTrustedPlayers().contains(uuid);
    }

    public boolean canBuild(UUID uuid) {
        return isOwner(uuid) || isTrusted(uuid);
    }

    public void trust(UUID uuid) {
        getTrustedPlayers().add(uuid);
    }

    public void untrust(UUID uuid) {
        getTrustedPlayers().remove(uuid);
    }

    public ClaimSettings getSettings() {
        ensureDefaults();
        return settings;
    }

    public String getDisplayName() {
        return name == null ? "unnamed" : name;
    }

    public int getSchemaVersion() {
        ensureDefaults();
        return schemaVersion;
    }

    public boolean hasInitializedTaxCycle() {
        ensureDefaults();
        return taxDueAt > 0L && taxIntervalMillisSnapshot > 0L;
    }

    public void startTaxCycle(long startedAt, long intervalMillis, long reminderLeadMillis,
            long rateMinorPerChunk, long multiplierBasisPoints) {
        ensureDefaults();
        long safeStart = Math.max(1L, startedAt);
        long safeInterval = Math.max(1L, intervalMillis);
        taxCycleStartedAt = safeStart;
        taxDueAt = safeAdd(safeStart, safeInterval);
        taxPeakChunks = getChunkCount();
        taxReminderSentForDueAt = 0L;
        taxRateMinorPerChunkSnapshot = Math.max(0L, rateMinorPerChunk);
        taxIntervalMillisSnapshot = safeInterval;
        taxReminderLeadMillisSnapshot = Math.max(0L, Math.min(reminderLeadMillis, safeInterval - 1L));
        taxDimensionMultiplierBasisPointsSnapshot = Math.max(0L, multiplierBasisPoints);
    }

    public void ensureTaxCycle(long now, long intervalMillis, long reminderLeadMillis,
            long rateMinorPerChunk, long multiplierBasisPoints) {
        ensureDefaults();
        if (!hasInitializedTaxCycle()) {
            startTaxCycle(now, intervalMillis, reminderLeadMillis, rateMinorPerChunk, multiplierBasisPoints);
        } else {
            taxPeakChunks = Math.max(taxPeakChunks, getChunkCount());
        }
    }

    public int getTaxPeakChunks() {
        ensureDefaults();
        taxPeakChunks = Math.max(taxPeakChunks, getChunkCount());
        return taxPeakChunks;
    }

    /** Repairs corrupted/legacy tax data so the recorded peak can never be below the live claim size. */
    public boolean repairTaxPeakInvariant() {
        int before = taxPeakChunks;
        ensureDefaults();
        int current = getChunkCount();
        if (taxPeakChunks < current) taxPeakChunks = current;
        return taxPeakChunks != before;
    }

    public long getTaxCycleStartedAt() { ensureDefaults(); return taxCycleStartedAt; }
    public long getTaxDueAt() { ensureDefaults(); return taxDueAt; }
    public long getTaxReminderSentForDueAt() { ensureDefaults(); return taxReminderSentForDueAt; }
    public long getTaxRateMinorPerChunkSnapshot() { ensureDefaults(); return Math.max(0L, taxRateMinorPerChunkSnapshot); }
    public long getTaxIntervalMillisSnapshot() { ensureDefaults(); return Math.max(1L, taxIntervalMillisSnapshot); }
    public long getTaxReminderLeadMillisSnapshot() { ensureDefaults(); return Math.max(0L, taxReminderLeadMillisSnapshot); }
    public long getTaxDimensionMultiplierBasisPointsSnapshot() {
        ensureDefaults();
        return Math.max(0L, taxDimensionMultiplierBasisPointsSnapshot);
    }


    public boolean completeTaxCycle(String settlementId, long startedAt, long intervalMillis,
            long reminderLeadMillis, long rateMinorPerChunk, long multiplierBasisPoints) {
        ensureDefaults();
        String normalized = settlementId == null ? "" : settlementId.trim();
        if (!normalized.isBlank() && normalized.equals(lastTaxSettlementId)) return false;
        startTaxCycle(startedAt, intervalMillis, reminderLeadMillis, rateMinorPerChunk, multiplierBasisPoints);
        lastTaxSettlementId = normalized;
        return true;
    }

    public String getLastTaxSettlementId() { ensureDefaults(); return lastTaxSettlementId; }

    public void markTaxReminderSent() {
        ensureDefaults();
        taxReminderSentForDueAt = taxDueAt;
    }

    public void postponeTaxDue(long newDueAt) {
        ensureDefaults();
        taxDueAt = Math.max(1L, newDueAt);
        taxReminderSentForDueAt = 0L;
    }

    private static long safeAdd(long first, long second) {
        try { return Math.addExact(first, second); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    public boolean hasAdjacentChunk(int chunkX, int chunkZ) {
        return hasChunk(chunkX + 1, chunkZ)
                || hasChunk(chunkX - 1, chunkZ)
                || hasChunk(chunkX, chunkZ + 1)
                || hasChunk(chunkX, chunkZ - 1);
    }
}