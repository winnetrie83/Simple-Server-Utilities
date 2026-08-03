package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.Locale;

/** Durable per-player quest lifecycle state. */
public enum QuestStatus {
    ACTIVE,
    READY_TO_TURN_IN,
    COMPLETED,
    ABANDONED,
    FAILED;

    public static QuestStatus parse(String raw) {
        if (raw == null || raw.isBlank()) return ACTIVE;
        try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return ACTIVE; }
    }

    public String serializedName() { return name().toLowerCase(Locale.ROOT); }
}
