package be.winnetrie.mod.simpleserverutilities.statistics;

import java.util.Locale;

/** Supported server-side event sources for custom player statistics. */
public enum StatisticEventType {
    BLOCK_BROKEN(true, false, "blocks"),
    BLOCK_PLACED(true, false, "blocks"),
    ENTITY_KILLED(true, false, "kills"),
    PLAYER_DEATH(false, false, "deaths"),
    DAMAGE_DEALT(true, true, "damage"),
    DAMAGE_TAKEN(true, true, "damage"),
    PLAY_TIME(false, false, "seconds"),
    MINIGAME_KILL(true, false, "kills"),
    MINIGAME_DEATH(true, false, "deaths"),
    MINIGAME_ASSIST(true, false, "assists"),
    MINIGAME_DAMAGE(true, true, "damage"),
    MINIGAME_HEALING(true, true, "healing"),
    MINIGAME_CAPTURE(true, false, "captures"),
    MINIGAME_DEFENSE(true, false, "defenses"),
    MINIGAME_OBJECTIVE_TIME(true, false, "seconds"),
    MINIGAME_WIN(true, false, "wins"),
    MINIGAME_COMPLETED(true, false, "matches");

    private final boolean targetSupported;
    private final boolean decimal;
    private final String defaultUnit;

    StatisticEventType(boolean targetSupported, boolean decimal, String defaultUnit) {
        this.targetSupported = targetSupported;
        this.decimal = decimal;
        this.defaultUnit = defaultUnit;
    }

    public boolean targetSupported() {
        return targetSupported;
    }

    public boolean decimal() {
        return decimal;
    }

    public String defaultUnit() {
        return defaultUnit;
    }

    public static StatisticEventType parse(String raw) {
        if (raw == null || raw.isBlank()) return BLOCK_BROKEN;
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
