package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Locale;

/** Shared validation and normalization for data-driven SSU content identifiers. */
public final class ContentId {
    public static final int MAX_LENGTH = 128;

    private ContentId() {
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('/', '.')
                .replace('\\', '.')
                .replace(':', '.');
        normalized = normalized.replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("[._-]{2,}", "_");
        while (normalized.startsWith(".") || normalized.startsWith("_") || normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".") || normalized.endsWith("_") || normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.length() > MAX_LENGTH) normalized = normalized.substring(0, MAX_LENGTH);
        return normalized;
    }

    public static String require(String raw, String label) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) throw new IllegalArgumentException(label + " cannot be blank.");
        return normalized;
    }
}
