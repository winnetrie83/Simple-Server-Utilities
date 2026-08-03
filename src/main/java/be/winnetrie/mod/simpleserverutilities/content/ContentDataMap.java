package be.winnetrie.mod.simpleserverutilities.content;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded normalization for small data-driven string maps used in content envelopes. */
public final class ContentDataMap {
    private ContentDataMap() {
    }

    public static Map<String, String> normalize(Map<String, String> source, int maxEntries, int maxValueLength) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = ContentId.normalize(entry.getKey());
            if (key.isBlank()) continue;
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.length() > maxValueLength) value = value.substring(0, maxValueLength);
            normalized.put(key, value);
            if (normalized.size() >= maxEntries) break;
        }
        return Map.copyOf(normalized);
    }
}
