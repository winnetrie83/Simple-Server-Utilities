package be.winnetrie.mod.simpleserverutilities.statistics.community;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Common persisted data shape for one player and the server aggregate. */
public final class CommunityStatisticsRecord {
    public static final int CURRENT_SCHEMA = 1;

    public int schemaVersion = CURRENT_SCHEMA;
    public String ownerId = "";
    public String displayName = "";
    public CommunityStatBucket lifetime = new CommunityStatBucket();
    public CommunityPeriodBucket day = new CommunityPeriodBucket();
    public CommunityPeriodBucket week = new CommunityPeriodBucket();
    public CommunityPeriodBucket month = new CommunityPeriodBucket();
    public CommunityPeriodBucket season = new CommunityPeriodBucket();
    public LinkedHashMap<String, Map<String, Long>> dailyHistory = new LinkedHashMap<>();
    public LinkedHashMap<String, Map<String, Long>> weeklyHistory = new LinkedHashMap<>();
    public LinkedHashMap<String, Map<String, Long>> monthlyHistory = new LinkedHashMap<>();
    public LinkedHashMap<String, Map<String, Long>> seasonHistory = new LinkedHashMap<>();
    public Set<String> processedDurableEvents = new LinkedHashSet<>();
    public long updatedAtEpochMilli;

    public void normalize(int dayRetention) {
        if (schemaVersion > CURRENT_SCHEMA) throw new IllegalStateException(
                "Community statistics schema " + schemaVersion + " is newer than supported schema " + CURRENT_SCHEMA + ".");
        schemaVersion = CURRENT_SCHEMA;
        if (ownerId == null) ownerId = "";
        if (displayName == null) displayName = "";
        if (lifetime == null) lifetime = new CommunityStatBucket();
        if (day == null) day = new CommunityPeriodBucket();
        if (week == null) week = new CommunityPeriodBucket();
        if (month == null) month = new CommunityPeriodBucket();
        if (season == null) season = new CommunityPeriodBucket();
        if (dailyHistory == null) dailyHistory = new LinkedHashMap<>();
        if (weeklyHistory == null) weeklyHistory = new LinkedHashMap<>();
        if (monthlyHistory == null) monthlyHistory = new LinkedHashMap<>();
        if (seasonHistory == null) seasonHistory = new LinkedHashMap<>();
        if (processedDurableEvents == null) processedDurableEvents = new LinkedHashSet<>();
        lifetime.normalize(); day.normalize(); week.normalize(); month.normalize(); season.normalize();
        trim(dailyHistory, Math.max(7, dayRetention));
        trim(weeklyHistory, 104);
        trim(monthlyHistory, 36);
        trim(seasonHistory, 16);
        trimDurableEvents();
        updatedAtEpochMilli = Math.max(0L, updatedAtEpochMilli);
    }

    public boolean rollTo(CommunityPeriodKeys keys, int dayRetention) {
        boolean changed = false;
        changed |= roll(day, keys.day(), dailyHistory, Math.max(7, dayRetention));
        changed |= roll(week, keys.week(), weeklyHistory, 104);
        changed |= roll(month, keys.month(), monthlyHistory, 36);
        changed |= roll(season, keys.season(), seasonHistory, 16);
        return changed;
    }

    public boolean rememberDurableEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) return true;
        if (!processedDurableEvents.add(eventId)) return false;
        trimDurableEvents();
        return true;
    }

    private static boolean roll(CommunityPeriodBucket bucket, String newKey,
                                LinkedHashMap<String, Map<String, Long>> history, int retention) {
        bucket.normalize();
        if (newKey.equals(bucket.key)) return false;
        if (!bucket.key.isBlank() && !bucket.stats.values.isEmpty()) {
            history.put(bucket.key, Map.copyOf(bucket.stats.values));
            trim(history, retention);
        }
        bucket.key = newKey;
        bucket.stats = new CommunityStatBucket();
        return true;
    }

    private void trimDurableEvents() {
        while (processedDurableEvents.size() > 4096) {
            var iterator = processedDurableEvents.iterator();
            if (!iterator.hasNext()) break;
            iterator.next(); iterator.remove();
        }
    }

    private static <K, V> void trim(LinkedHashMap<K, V> map, int maximum) {
        while (map.size() > maximum) {
            var iterator = map.entrySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next(); iterator.remove();
        }
    }
}
