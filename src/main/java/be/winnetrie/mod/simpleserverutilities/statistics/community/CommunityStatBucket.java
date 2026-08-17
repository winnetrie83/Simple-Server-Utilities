package be.winnetrie.mod.simpleserverutilities.statistics.community;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Mutable persisted metric bucket. Only the owning manager mutates instances. */
public final class CommunityStatBucket {
    private static final int MAX_BREAKDOWN_KEYS_PER_GROUP = 512;
    private static final int MAX_UNIQUE_KEYS_PER_METRIC = 65536;

    public Map<String, Long> values = new LinkedHashMap<>();
    public Map<String, Map<String, Long>> breakdowns = new LinkedHashMap<>();
    public Map<String, Set<String>> uniqueKeys = new HashMap<>();

    public void add(String metricId, long amount) {
        if (metricId == null || metricId.isBlank() || amount <= 0L) return;
        values.merge(metricId, amount, CommunityStatBucket::saturatingAdd);
    }

    public void addBreakdown(String group, String rawKey, long amount) {
        if (group == null || group.isBlank() || rawKey == null || rawKey.isBlank() || amount <= 0L) return;
        String key = normalizeKey(rawKey);
        Map<String, Long> map = breakdowns.computeIfAbsent(group, ignored -> new LinkedHashMap<>());
        if (!map.containsKey(key) && map.size() >= MAX_BREAKDOWN_KEYS_PER_GROUP) key = "other";
        map.merge(key, amount, CommunityStatBucket::saturatingAdd);
    }

    public boolean addDistinct(String metricId, String rawKey) {
        if (metricId == null || metricId.isBlank() || rawKey == null || rawKey.isBlank()) return false;
        String key = normalizeKey(rawKey);
        Set<String> set = uniqueKeys.computeIfAbsent(metricId, ignored -> new LinkedHashSet<>());
        if (set.contains(key)) return false;
        if (set.size() >= MAX_UNIQUE_KEYS_PER_METRIC) return false;
        set.add(key);
        add(metricId, 1L);
        return true;
    }

    public void normalize() {
        if (values == null) values = new LinkedHashMap<>();
        if (breakdowns == null) breakdowns = new LinkedHashMap<>();
        if (uniqueKeys == null) uniqueKeys = new HashMap<>();
        values.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue() <= 0L);
        for (Map.Entry<String, Map<String, Long>> entry : breakdowns.entrySet()) {
            Map<String, Long> map = entry.getValue();
            if (map == null) continue;
            map.entrySet().removeIf(value -> value.getKey() == null || value.getKey().isBlank()
                    || value.getValue() == null || value.getValue() <= 0L);
        }
        breakdowns.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue().isEmpty());
        uniqueKeys.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
    }

    public View view(boolean includeBreakdowns) {
        Map<String, Long> safeValues = Map.copyOf(values);
        if (!includeBreakdowns) return new View(safeValues, Map.of());
        LinkedHashMap<String, Map<String, Long>> safeBreakdowns = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : breakdowns.entrySet()) {
            safeBreakdowns.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return new View(safeValues, Map.copyOf(safeBreakdowns));
    }

    public static long saturatingAdd(long left, long right) {
        if (right <= 0L) return Math.max(0L, left);
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return Math.max(0L, left) + right;
    }

    private static String normalizeKey(String raw) {
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return value.length() <= 160 ? value : value.substring(0, 160);
    }

    public record View(Map<String, Long> values, Map<String, Map<String, Long>> breakdowns) { }
}
