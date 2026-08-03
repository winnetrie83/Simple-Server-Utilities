package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Closed relation values used for players and other NPC factions. */
public enum NpcAttitude {
    FRIENDLY,
    NEUTRAL,
    HOSTILE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NpcAttitude parse(String raw) {
        if (raw == null) return NEUTRAL;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NEUTRAL;
        }
    }

    public NpcAttitude next() {
        return switch (this) {
            case FRIENDLY -> NEUTRAL;
            case NEUTRAL -> HOSTILE;
            case HOSTILE -> FRIENDLY;
        };
    }
}
