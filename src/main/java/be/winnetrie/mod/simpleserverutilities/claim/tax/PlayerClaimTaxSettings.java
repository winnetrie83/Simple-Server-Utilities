package be.winnetrie.mod.simpleserverutilities.claim.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Persistent administrator settings for per-claim recurring taxation. */
public final class PlayerClaimTaxSettings {
    public static final int MAX_DIMENSION_MULTIPLIERS = 64;
    public static final int SCHEMA_VERSION = 2;
    public static final long DEFAULT_INTERVAL_MILLIS = Duration.ofDays(7).toMillis();
    public static final long DEFAULT_REMINDER_MILLIS = Duration.ofDays(1).toMillis();
    public static final long MULTIPLIER_SCALE = 10_000L;

    private int schemaVersion = SCHEMA_VERSION;
    private boolean enabled;
    private long rateMinorPerChunk;
    private long intervalMillis = DEFAULT_INTERVAL_MILLIS;
    private long reminderLeadMillis = DEFAULT_REMINDER_MILLIS;
    private Map<String, Double> dimensionMultipliers = defaultMultipliers();

    // Legacy dev18.5 aggregate-cycle fields. They remain readable so existing
    // settings files migrate without data loss, but are no longer used for billing.
    @SuppressWarnings("unused") private long nextChargeAt;
    @SuppressWarnings("unused") private long reminderSentForChargeAt;
    @SuppressWarnings("unused") private long reminderSnapshotForChargeAt;
    @SuppressWarnings("unused") private Map<String, Long> reminderAmounts = new LinkedHashMap<>();

    public void normalize(long now) {
        schemaVersion = SCHEMA_VERSION;
        rateMinorPerChunk = Math.max(0L, rateMinorPerChunk);
        intervalMillis = clamp(intervalMillis, Duration.ofHours(1).toMillis(), Duration.ofDays(3650).toMillis());
        reminderLeadMillis = clamp(reminderLeadMillis, 0L, Math.max(0L, intervalMillis - 1L));
        if (dimensionMultipliers == null) dimensionMultipliers = defaultMultipliers();
        Map<String, Double> normalized = new LinkedHashMap<>();
        dimensionMultipliers.entrySet().stream().limit(MAX_DIMENSION_MULTIPLIERS).forEach(entry -> {
            String key = normalizeDimension(entry.getKey());
            Double multiplier = entry.getValue();
            if (!key.isBlank() && multiplier != null && Double.isFinite(multiplier)) {
                normalized.put(key, clampMultiplier(multiplier));
            }
        });
        normalized.putIfAbsent("minecraft:overworld", 1.0D);
        normalized.putIfAbsent("minecraft:the_nether", 1.2D);
        normalized.putIfAbsent("minecraft:the_end", 1.5D);
        dimensionMultipliers = normalized;
        nextChargeAt = 0L;
        reminderSentForChargeAt = 0L;
        reminderSnapshotForChargeAt = 0L;
        if (reminderAmounts == null) reminderAmounts = new LinkedHashMap<>();
        else reminderAmounts.clear();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getRateMinorPerChunk() { return Math.max(0L, rateMinorPerChunk); }
    public void setRateMinorPerChunk(long value) { rateMinorPerChunk = Math.max(0L, value); }
    public long getIntervalMillis() { return intervalMillis; }
    public void setIntervalMillis(long value) { intervalMillis = value; }
    public long getReminderLeadMillis() { return reminderLeadMillis; }
    public void setReminderLeadMillis(long value) { reminderLeadMillis = value; }
    public Map<String, Double> getDimensionMultipliers() { return Map.copyOf(dimensionMultipliers); }

    /** Compatibility accessor; the manager now calculates the earliest per-claim due time. */
    public long getNextChargeAt() { return 0L; }

    public double multiplier(String dimension) {
        return dimensionMultipliers.getOrDefault(normalizeDimension(dimension), 1.0D);
    }

    public long multiplierBasisPoints(String dimension) {
        try {
            return BigDecimal.valueOf(multiplier(dimension))
                    .multiply(BigDecimal.valueOf(MULTIPLIER_SCALE))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public void setMultiplier(String dimension, double multiplier) {
        String key = normalizeDimension(dimension);
        if (key.isBlank()) throw new IllegalArgumentException("Enter a valid dimension id.");
        if (!dimensionMultipliers.containsKey(key) && dimensionMultipliers.size() >= MAX_DIMENSION_MULTIPLIERS) {
            throw new IllegalArgumentException("At most " + MAX_DIMENSION_MULTIPLIERS + " dimension multipliers may be configured.");
        }
        dimensionMultipliers.put(key, clampMultiplier(multiplier));
    }

    public void removeMultiplier(String dimension) {
        String key = normalizeDimension(dimension);
        if (key.equals("minecraft:overworld") || key.equals("minecraft:the_nether") || key.equals("minecraft:the_end")) {
            throw new IllegalArgumentException("Vanilla dimension multipliers can be changed but not removed.");
        }
        dimensionMultipliers.remove(key);
    }

    private static Map<String, Double> defaultMultipliers() {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("minecraft:overworld", 1.0D);
        defaults.put("minecraft:the_nether", 1.2D);
        defaults.put("minecraft:the_end", 1.5D);
        return defaults;
    }

    private static String normalizeDimension(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return "";
        return normalized;
    }

    private static double clampMultiplier(double value) { return Math.max(0.0D, Math.min(1000.0D, value)); }
    private static long clamp(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }
}
