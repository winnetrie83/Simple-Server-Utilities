package be.winnetrie.mod.simpleserverutilities.content.objective;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Generic event-driven objective shared by achievements and future progression features. */
public final class ContentObjectiveDefinition {
    public static final int CURRENT_SCHEMA = 1;

    public int schemaVersion = CURRENT_SCHEMA;
    public String id = "objective";
    public String description = "Objective";
    public String eventType = "block_broken";
    public TargetMode targetMode = TargetMode.ANY;
    public List<String> targets = new ArrayList<>();
    public Aggregator aggregator = Aggregator.SUM;
    public long targetAmount = 1L;
    public boolean optional;
    /** Exact event metadata filters; use * as a wildcard value. */
    public Map<String, String> metadata = new LinkedHashMap<>();

    public ContentObjectiveDefinition normalize() {
        if (schemaVersion > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Objective schema " + schemaVersion + " is newer than supported schema " + CURRENT_SCHEMA + ".");
        }
        schemaVersion = CURRENT_SCHEMA;
        id = sanitizeId(id);
        description = bound(description == null || description.isBlank() ? id : description.trim(), 160);
        eventType = ContentId.require(eventType, "Objective event type");
        targetMode = targetMode == null ? TargetMode.ANY : targetMode;
        aggregator = aggregator == null ? Aggregator.SUM : aggregator;
        targetAmount = Math.max(1L, targetAmount);
        if (aggregator == Aggregator.UNIQUE && targetAmount > 4096L) {
            throw new IllegalArgumentException("UNIQUE objectives may require at most 4096 unique values.");
        }

        LinkedHashSet<String> normalizedTargets = new LinkedHashSet<>();
        if (targets != null) {
            for (String raw : targets) {
                if (raw == null || raw.isBlank()) continue;
                String value = raw.trim().toLowerCase(Locale.ROOT);
                if (value.length() > 160) value = value.substring(0, 160);
                normalizedTargets.add(value);
                if (normalizedTargets.size() >= 128) break;
            }
        }
        targets = new ArrayList<>(normalizedTargets);
        if (targetMode == TargetMode.ANY) targets.clear();
        if (targetMode != TargetMode.ANY && targets.isEmpty()) {
            throw new IllegalArgumentException("Objective '" + id + "' needs at least one target.");
        }

        LinkedHashMap<String, String> normalizedMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                String key = ContentId.normalize(entry.getKey());
                if (key.isBlank()) continue;
                String value = entry.getValue() == null ? "" : entry.getValue().trim();
                if (value.length() > 256) value = value.substring(0, 256);
                normalizedMetadata.put(key, value);
                if (normalizedMetadata.size() >= 32) break;
            }
        }
        metadata = normalizedMetadata;
        return this;
    }

    public ContentObjectiveDefinition copy() {
        ContentObjectiveDefinition value = new ContentObjectiveDefinition();
        value.schemaVersion = schemaVersion;
        value.id = id;
        value.description = description;
        value.eventType = eventType;
        value.targetMode = targetMode;
        value.targets = new ArrayList<>(targets == null ? List.of() : targets);
        value.aggregator = aggregator;
        value.targetAmount = targetAmount;
        value.optional = optional;
        value.metadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        return value;
    }

    public static String sanitizeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (value.isBlank()) value = "objective";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static String bound(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public enum TargetMode { ANY, EXACT, LIST, TAG }
    public enum Aggregator { COUNT, SUM, MAX, UNIQUE }
}
