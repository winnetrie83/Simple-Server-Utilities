package be.winnetrie.mod.simpleserverutilities.time;

import java.util.Locale;

/** Shared conversion between Minecraft clock ticks, weekdays and HH:mm time. */
public final class GameCalendar {
    public static final int MINUTES_PER_DAY = 1_440;
    public static final long TICKS_PER_DAY = 24_000L;
    public static final int ALL_DAYS_MASK = 0x7F;

    private GameCalendar() {}

    public static Moment fromClockTime(long clockTime) {
        long shiftedClock = clockTime + 6_000L; // Minecraft day 0 starts at 06:00; calendar days change at midnight.
        long worldDay = Math.floorDiv(shiftedClock, TICKS_PER_DAY);
        long shiftedTicks = Math.floorMod(shiftedClock, TICKS_PER_DAY);
        int minute = (int) ((shiftedTicks * MINUTES_PER_DAY) / TICKS_PER_DAY);
        return new Moment(worldDay, GameWeekday.fromWorldDay(worldDay), minute);
    }

    public static int parseMinute(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return clampMinute(fallback, true);
        String value = raw.trim();
        String[] parts = value.split(":", -1);
        try {
            if (parts.length == 1) return clampMinute(Integer.parseInt(parts[0]), true);
            if (parts.length != 2) return clampMinute(fallback, true);
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour == 24 && minute == 0) return MINUTES_PER_DAY;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return clampMinute(fallback, true);
            return hour * 60 + minute;
        } catch (NumberFormatException ignored) {
            return clampMinute(fallback, true);
        }
    }

    public static String formatMinute(int minute) {
        int safe = clampMinute(minute, true);
        if (safe == MINUTES_PER_DAY) return "24:00";
        return String.format(Locale.ROOT, "%02d:%02d", safe / 60, safe % 60);
    }

    public static int normalizeDaysMask(int mask) {
        int safe = mask & ALL_DAYS_MASK;
        return safe == 0 ? ALL_DAYS_MASK : safe;
    }

    public static boolean isAvailable(int daysMask, int startMinute, int endMinute, Moment moment) {
        int mask = normalizeDaysMask(daysMask);
        int start = clampMinute(startMinute, true);
        int end = clampMinute(endMinute, true);
        if (start == 0 && end == MINUTES_PER_DAY) return (mask & moment.weekday().bit()) != 0;
        if (start == end) return (mask & moment.weekday().bit()) != 0;
        if (end > start) {
            return (mask & moment.weekday().bit()) != 0 && moment.minuteOfDay() >= start && moment.minuteOfDay() < end;
        }
        if (moment.minuteOfDay() >= start) return (mask & moment.weekday().bit()) != 0;
        return moment.minuteOfDay() < end && (mask & GameWeekday.previous(moment.weekday()).bit()) != 0;
    }

    private static int clampMinute(int value, boolean allowEndOfDay) {
        return Math.max(0, Math.min(allowEndOfDay ? MINUTES_PER_DAY : MINUTES_PER_DAY - 1, value));
    }

    public record Moment(long worldDay, GameWeekday weekday, int minuteOfDay) {
        public String clockText() { return formatMinute(minuteOfDay); }
        public String displayText() { return weekday.displayName() + " " + clockText(); }
    }
}
