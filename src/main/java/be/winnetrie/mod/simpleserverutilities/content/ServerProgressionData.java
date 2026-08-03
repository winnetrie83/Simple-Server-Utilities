package be.winnetrie.mod.simpleserverutilities.content;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Durable world-wide flags, counters and unlocks for shared story or event state. */
public final class ServerProgressionData {
    public static final int CURRENT_SCHEMA = 1;
    private static final int MAX_ENTRIES_PER_SECTION = 50_000;

    int schema = CURRENT_SCHEMA;
    Set<String> flags = new LinkedHashSet<>();
    Map<String, Long> counters = new LinkedHashMap<>();
    Set<String> unlocks = new LinkedHashSet<>();
    long updatedAtEpochMilli;

    public ServerProgressionData() {
    }

    void normalize() {
        if (schema > CURRENT_SCHEMA) {
            throw new IllegalStateException("Unsupported future server progression schema " + schema + ".");
        }
        schema = CURRENT_SCHEMA;
        flags = normalizeSet(flags);
        unlocks = normalizeSet(unlocks);
        counters = normalizeCounters(counters);
        updatedAtEpochMilli = Math.max(0L, updatedAtEpochMilli);
    }

    public int schema() { return schema; }
    public Set<String> flags() { return Set.copyOf(flags); }
    public Map<String, Long> counters() { return Map.copyOf(counters); }
    public Set<String> unlocks() { return Set.copyOf(unlocks); }
    public long updatedAtEpochMilli() { return updatedAtEpochMilli; }

    private static Set<String> normalizeSet(Set<String> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (source == null) return result;
        for (String value : source) {
            String key = ContentId.normalize(value);
            if (!key.isBlank()) result.add(key);
            if (result.size() >= MAX_ENTRIES_PER_SECTION) break;
        }
        return result;
    }

    private static Map<String, Long> normalizeCounters(Map<String, Long> source) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        if (source == null) return result;
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            if (result.size() >= MAX_ENTRIES_PER_SECTION) break;
            String key = ContentId.normalize(entry.getKey());
            if (key.isBlank() || entry.getValue() == null) continue;
            long value = Math.max(0L, entry.getValue());
            if (value != 0L) result.put(key, value);
        }
        return result;
    }
}
