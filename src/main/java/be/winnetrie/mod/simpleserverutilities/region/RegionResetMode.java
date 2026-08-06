package be.winnetrie.mod.simpleserverutilities.region;

import java.util.Locale;

/** Source used by the scheduled region-reset service. */
public enum RegionResetMode {
    SNAPSHOT,
    PRESET;

    public static RegionResetMode parse(String raw) {
        if (raw == null || raw.isBlank()) return SNAPSHOT;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SNAPSHOT;
        }
    }

    public RegionResetMode next() {
        return this == SNAPSHOT ? PRESET : SNAPSHOT;
    }
}
