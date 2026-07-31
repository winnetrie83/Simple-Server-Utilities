package be.winnetrie.mod.simpleserverutilities.statistics;

import java.util.Locale;

/** Persistent administrator-defined statistic rule. */
public final class PlayerStatisticDefinition {
    public static final int CURRENT_SCHEMA = 1;

    public int schemaVersion = CURRENT_SCHEMA;
    public String id = "statistic";
    public String displayName = "Statistic";
    public StatisticEventType eventType = StatisticEventType.BLOCK_BROKEN;
    /** Registry identifier filter or * for every target. Ignored when unsupported. */
    public String target = "*";
    public String unit = "";
    public boolean enabled = true;
    public long createdAtEpochMilli;
    public long updatedAtEpochMilli;

    public PlayerStatisticDefinition normalize() {
        schemaVersion = CURRENT_SCHEMA;
        id = sanitizeId(id);
        displayName = limit(displayName == null || displayName.isBlank() ? id : displayName.trim(), 64);
        eventType = eventType == null ? StatisticEventType.BLOCK_BROKEN : eventType;
        target = normalizeTarget(eventType, target);
        unit = limit(unit == null || unit.isBlank() ? eventType.defaultUnit() : unit.trim(), 24);
        long now = System.currentTimeMillis();
        if (createdAtEpochMilli <= 0L) createdAtEpochMilli = now;
        if (updatedAtEpochMilli <= 0L) updatedAtEpochMilli = createdAtEpochMilli;
        return this;
    }

    public static String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) return "statistic";
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    public static String normalizeTarget(StatisticEventType type, String raw) {
        if (type == null || !type.targetSupported()) return "*";
        String value = raw == null || raw.isBlank() ? "*" : raw.trim().toLowerCase(Locale.ROOT);
        if ("*".equals(value)) return value;
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Target must be * or a registry ID such as minecraft:diamond_ore.");
        }
        return limit(value, 128);
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
