package be.winnetrie.mod.simpleserverutilities.time;

import java.util.Locale;

/** Seven-day Minecraft world week. World day zero starts on Monday. */
public enum GameWeekday {
    MONDAY("Monday", "Mon"),
    TUESDAY("Tuesday", "Tue"),
    WEDNESDAY("Wednesday", "Wed"),
    THURSDAY("Thursday", "Thu"),
    FRIDAY("Friday", "Fri"),
    SATURDAY("Saturday", "Sat"),
    SUNDAY("Sunday", "Sun");

    private final String displayName;
    private final String shortName;

    GameWeekday(String displayName, String shortName) {
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public String displayName() { return displayName; }
    public String shortName() { return shortName; }
    public int bit() { return 1 << ordinal(); }

    public static GameWeekday fromWorldDay(long worldDay) {
        return values()[(int) Math.floorMod(worldDay, values().length)];
    }

    public static GameWeekday previous(GameWeekday value) {
        return values()[Math.floorMod(value.ordinal() - 1, values().length)];
    }

    public static GameWeekday parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (GameWeekday day : values()) {
            if (day.name().toLowerCase(Locale.ROOT).equals(value)
                    || day.displayName.toLowerCase(Locale.ROOT).equals(value)
                    || day.shortName.toLowerCase(Locale.ROOT).equals(value)) return day;
        }
        return MONDAY;
    }
}
