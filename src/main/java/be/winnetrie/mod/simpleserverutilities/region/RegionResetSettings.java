package be.winnetrie.mod.simpleserverutilities.region;

/** Persistent, server-authoritative automatic reset configuration for one region. */
public final class RegionResetSettings {
    public static final long DEFAULT_INTERVAL_SECONDS = 3_600L;
    public static final long MIN_INTERVAL_SECONDS = 10L;
    public static final long MAX_INTERVAL_SECONDS = 31_536_000L;

    private boolean enabled;
    private long intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    private RegionResetMode mode = RegionResetMode.SNAPSHOT;
    private boolean onlyWhenEmpty = true;
    private String weightedPreset = "";
    private long nextResetAt = -1L;
    private long lastResetAt = -1L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(long intervalSeconds) {
        this.intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, intervalSeconds));
    }
    public RegionResetMode getMode() { return mode == null ? RegionResetMode.SNAPSHOT : mode; }
    public void setMode(RegionResetMode mode) { this.mode = mode == null ? RegionResetMode.SNAPSHOT : mode; }
    public boolean isOnlyWhenEmpty() { return onlyWhenEmpty; }
    public void setOnlyWhenEmpty(boolean onlyWhenEmpty) { this.onlyWhenEmpty = onlyWhenEmpty; }
    public String getWeightedPreset() { return weightedPreset == null ? "" : weightedPreset; }
    public void setWeightedPreset(String weightedPreset) {
        this.weightedPreset = weightedPreset == null ? "" : weightedPreset.trim();
    }
    public long getNextResetAt() { return nextResetAt; }
    public void setNextResetAt(long nextResetAt) { this.nextResetAt = nextResetAt; }
    public long getLastResetAt() { return lastResetAt; }
    public void setLastResetAt(long lastResetAt) { this.lastResetAt = lastResetAt; }

    public void scheduleFrom(long nowMillis) {
        nextResetAt = nowMillis + intervalSeconds * 1_000L;
    }

    public void normalize(long nowMillis) {
        setIntervalSeconds(intervalSeconds);
        setMode(mode);
        setWeightedPreset(weightedPreset);
        if (!enabled) nextResetAt = -1L;
        else if (nextResetAt <= 0L) scheduleFrom(nowMillis);
    }

    public void copyFrom(RegionResetSettings other) {
        if (other == null) return;
        enabled = other.enabled;
        intervalSeconds = other.intervalSeconds;
        mode = other.mode;
        onlyWhenEmpty = other.onlyWhenEmpty;
        weightedPreset = other.weightedPreset;
        nextResetAt = other.nextResetAt;
        lastResetAt = other.lastResetAt;
    }
}
