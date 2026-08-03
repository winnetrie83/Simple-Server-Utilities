package be.winnetrie.mod.simpleserverutilities.dimension;

import java.util.Locale;

public enum DimensionPreset {
    OVERWORLD("Overworld"),
    NETHER("Nether"),
    END("End"),
    FLAT("Flat"),
    EMPTY("Empty platform");

    private final String label;

    DimensionPreset(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DimensionPreset parse(String raw) {
        if (raw == null || raw.isBlank()) return OVERWORLD;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OVERWORLD;
        }
    }
}
