package be.winnetrie.mod.simpleserverutilities.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent values for one player. Damage statistics use hundredths. */
public final class PlayerStatisticValues {
    public static final int CURRENT_SCHEMA = 1;

    public int schemaVersion = CURRENT_SCHEMA;
    public String uuid = "";
    public String lastKnownName = "";
    public Map<String, Long> values = new HashMap<>();
    public long updatedAtEpochMilli;

    public PlayerStatisticValues() {
    }

    public PlayerStatisticValues(UUID playerId, String name) {
        uuid = playerId == null ? "" : playerId.toString();
        lastKnownName = name == null ? "" : name;
    }

    public void normalize() {
        schemaVersion = CURRENT_SCHEMA;
        if (lastKnownName == null) lastKnownName = "";
        if (values == null) values = new HashMap<>();
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
