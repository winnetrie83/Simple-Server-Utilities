package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Durable generic per-player state shared by quests, NPCs, minigames and dungeons. */
public final class PlayerProgressionData {
    public static final int CURRENT_SCHEMA = 1;
    private static final int MAX_ENTRIES_PER_SECTION = 50_000;

    int schema = CURRENT_SCHEMA;
    String uuid = "";
    String lastKnownName = "";
    Set<String> flags = new LinkedHashSet<>();
    Map<String, Long> counters = new LinkedHashMap<>();
    Set<String> unlocks = new LinkedHashSet<>();
    Map<String, Integer> reputation = new LinkedHashMap<>();
    long updatedAtEpochMilli;

    public PlayerProgressionData() {
    }

    PlayerProgressionData(UUID playerId, String playerName) {
        uuid = playerId == null ? "" : playerId.toString();
        lastKnownName = playerName == null ? "" : playerName;
        updatedAtEpochMilli = System.currentTimeMillis();
    }

    void normalize(UUID fallbackId) {
        if (schema > CURRENT_SCHEMA) {
            throw new IllegalStateException("Unsupported future player progression schema " + schema + ".");
        }
        schema = CURRENT_SCHEMA;
        UUID parsed = fallbackId;
        if (uuid != null && !uuid.isBlank()) {
            UUID stored = UUID.fromString(uuid);
            if (fallbackId != null && !fallbackId.equals(stored)) {
                throw new IllegalArgumentException("Player progression UUID does not match its filename.");
            }
            parsed = stored;
        }
        if (parsed == null) throw new IllegalArgumentException("Player progression UUID is missing.");
        uuid = parsed.toString();
        lastKnownName = lastKnownName == null ? "" : lastKnownName.trim();
        flags = normalizeSet(flags);
        unlocks = normalizeSet(unlocks);
        counters = normalizeLongMap(counters, true);
        reputation = normalizeIntMap(reputation);
        updatedAtEpochMilli = Math.max(0L, updatedAtEpochMilli);
    }

    public int schema() { return schema; }
    public UUID playerId() { return UUID.fromString(uuid); }
    public String lastKnownName() { return lastKnownName; }
    public Set<String> flags() { return Set.copyOf(flags); }
    public Map<String, Long> counters() { return Map.copyOf(counters); }
    public Set<String> unlocks() { return Set.copyOf(unlocks); }
    public Map<String, Integer> reputation() { return Map.copyOf(reputation); }
    public long updatedAtEpochMilli() { return updatedAtEpochMilli; }

    private static Set<String> normalizeSet(Set<String> source) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (source == null) return normalized;
        for (String raw : source) {
            String key = ContentId.normalize(raw);
            if (!key.isBlank()) normalized.add(key);
            if (normalized.size() >= MAX_ENTRIES_PER_SECTION) break;
        }
        return normalized;
    }

    private static Map<String, Long> normalizeLongMap(Map<String, Long> source, boolean nonNegative) {
        LinkedHashMap<String, Long> normalized = new LinkedHashMap<>();
        if (source == null) return normalized;
        Iterator<Map.Entry<String, Long>> iterator = source.entrySet().iterator();
        while (iterator.hasNext() && normalized.size() < MAX_ENTRIES_PER_SECTION) {
            Map.Entry<String, Long> entry = iterator.next();
            String key = ContentId.normalize(entry.getKey());
            if (key.isBlank() || entry.getValue() == null) continue;
            long value = nonNegative ? Math.max(0L, entry.getValue()) : entry.getValue();
            if (value != 0L) normalized.put(key, value);
        }
        return normalized;
    }

    private static Map<String, Integer> normalizeIntMap(Map<String, Integer> source) {
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
        if (source == null) return normalized;
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            if (normalized.size() >= MAX_ENTRIES_PER_SECTION) break;
            String key = ContentId.normalize(entry.getKey());
            if (key.isBlank() || entry.getValue() == null || entry.getValue() == 0) continue;
            normalized.put(key, clampReputation(entry.getValue()));
        }
        return normalized;
    }

    static int clampReputation(long value) {
        return (int) Math.max(-1_000_000L, Math.min(1_000_000L, value));
    }
}
