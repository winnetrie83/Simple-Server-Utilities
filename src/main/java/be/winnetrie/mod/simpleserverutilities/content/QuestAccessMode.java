package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Locale;

/** Exclusive player-facing quest entry point. */
public enum QuestAccessMode {
    MENU,
    NPC;

    public static QuestAccessMode parse(String raw) {
        if (raw == null || raw.isBlank()) return MENU;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MENU;
        }
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
