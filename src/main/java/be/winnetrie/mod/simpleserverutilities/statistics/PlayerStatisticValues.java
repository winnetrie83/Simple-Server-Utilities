package be.winnetrie.mod.simpleserverutilities.statistics;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent values for one player. Damage statistics use hundredths. */
public final class PlayerStatisticValues {
    public static final int CURRENT_SCHEMA = 2;

    public int schemaVersion = CURRENT_SCHEMA;
    public String uuid = "";
    public String lastKnownName = "";
    public Map<String, Long> values = new HashMap<>();
    public Set<String> processedDurableEvents = new LinkedHashSet<>();
    public long updatedAtEpochMilli;

    public PlayerStatisticValues() {
    }

    public PlayerStatisticValues(UUID playerId, String name) {
        uuid = playerId == null ? "" : playerId.toString();
        lastKnownName = name == null ? "" : name;
    }

    public void normalize() {
        if (schemaVersion > CURRENT_SCHEMA) throw new IllegalStateException("Player statistic schema " + schemaVersion + " is newer than supported schema " + CURRENT_SCHEMA + ".");
        schemaVersion = CURRENT_SCHEMA;
        if (lastKnownName == null) lastKnownName = "";
        if (values == null) values = new HashMap<>();
        if (processedDurableEvents == null) processedDurableEvents = new LinkedHashSet<>();
        if (processedDurableEvents.size() > 4096) {
            LinkedHashSet<String> keep = new LinkedHashSet<>();
            int skip = processedDurableEvents.size() - 4096, index = 0;
            for (String value : processedDurableEvents) if (index++ >= skip) keep.add(value);
            processedDurableEvents = keep;
        }
        Map<String, Long> normalized = new HashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            String id = PlayerStatisticDefinition.sanitizeId(entry.getKey());
            long value = entry.getValue() == null ? 0L : Math.max(0L, entry.getValue());
            if (value > 0L) normalized.put(id, value);
        }
        values = normalized;
        updatedAtEpochMilli = Math.max(0L, updatedAtEpochMilli);
    }
}
