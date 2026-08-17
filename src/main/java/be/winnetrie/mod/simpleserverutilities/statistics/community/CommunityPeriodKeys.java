package be.winnetrie.mod.simpleserverutilities.statistics.community;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** Stable UTC period keys plus the administrator-controlled season id. */
public record CommunityPeriodKeys(String day, String week, String month, String season) {
    public static CommunityPeriodKeys now(String rawSeason) {
        LocalDate date = Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
        WeekFields iso = WeekFields.ISO;
        int week = date.get(iso.weekOfWeekBasedYear());
        int weekYear = date.get(iso.weekBasedYear());
        return new CommunityPeriodKeys(
                date.toString(),
                String.format(Locale.ROOT, "%04d-W%02d", weekYear, week),
                String.format(Locale.ROOT, "%04d-%02d", date.getYear(), date.getMonthValue()),
                sanitizeSeason(rawSeason));
    }

    private static String sanitizeSeason(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (value.isBlank()) value = "season-1";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
