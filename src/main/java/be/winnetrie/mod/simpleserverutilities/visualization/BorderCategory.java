package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.Locale;

/** Semantic border categories. Colors are server-configurable. */
public enum BorderCategory {
    OWN_CLAIM("own_claim", 0x42F56C),
    OTHER_CLAIM("other_claim", 0x4287F5),
    SERVER_REGION("server_region", 0xA855F7),
    SELECTION("selection", 0xFFD447),
    HOSTILE_TERRITORY("hostile_territory", 0xF54242),

    // Reserved now so future systems can reuse the same renderer and settings file.
    ALLIED_TERRITORY("allied_territory", 0x45D9FF),
    SAFE_ZONE("safe_zone", 0x38D9A9),
    QUEST_AREA("quest_area", 0xFF9F43),
    MINIGAME_AREA("minigame_area", 0xE056FD);

    private final String serializedName;
    private final int defaultRgb;

    BorderCategory(String serializedName, int defaultRgb) {
        this.serializedName = serializedName;
        this.defaultRgb = defaultRgb;
    }

    public String serializedName() {
        return serializedName;
    }

    public int defaultRgb() {
        return defaultRgb;
    }

    public int defaultStrokeArgb() {
        return 0xFF000000 | defaultRgb;
    }

    public int defaultFillArgb() {
        return 0x28000000 | defaultRgb;
    }

    public static BorderCategory parse(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (BorderCategory category : values()) {
            if (category.serializedName.equals(normalized)
                    || category.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return category;
            }
        }
        return null;
    }
}
