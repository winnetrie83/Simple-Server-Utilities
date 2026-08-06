package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;

/** Persisted cosmetic-only minigame progression and balancing ratings. */
public final class MinigameProgressionData {
    public static final int SCHEMA_VERSION = 3;
    public static final int MAX_SETTLEMENTS = 2_000;
    public int schemaVersion = SCHEMA_VERSION;
    public Map<String, PlayerProgress> players = new LinkedHashMap<>();
    /** Bounded idempotency ledger for progression/history settlement per completed match. */
    public Map<String, SettlementReceipt> settledMatches = new LinkedHashMap<>();

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        LinkedHashMap<String, PlayerProgress> normalized = new LinkedHashMap<>();
        if (players != null) {
            for (Map.Entry<String, PlayerProgress> entry : players.entrySet()) {
                try {
                    UUID id = UUID.fromString(entry.getKey());
                    PlayerProgress progress = entry.getValue();
                    if (progress == null) continue;
                    progress.normalize(id);
                    normalized.put(id.toString(), progress);
                } catch (RuntimeException ignored) {
                }
            }
        }
        players = normalized;
        LinkedHashMap<String, SettlementReceipt> normalizedSettlements = new LinkedHashMap<>();
        if (settledMatches != null) {
            ArrayList<Map.Entry<String, SettlementReceipt>> valid = new ArrayList<>();
            for (Map.Entry<String, SettlementReceipt> entry : settledMatches.entrySet()) {
                try {
                    UUID.fromString(entry.getKey());
                    SettlementReceipt receipt = entry.getValue();
                    if (receipt == null) continue;
                    receipt.normalize();
                    valid.add(Map.entry(entry.getKey(), receipt));
                } catch (RuntimeException ignored) {
                }
            }
            int start = Math.max(0, valid.size() - MAX_SETTLEMENTS);
            for (int index = start; index < valid.size(); index++) {
                Map.Entry<String, SettlementReceipt> entry = valid.get(index);
                normalizedSettlements.put(entry.getKey(), entry.getValue());
            }
        }
        settledMatches = normalizedSettlements;
    }

    public boolean isSettled(UUID matchId) {
        return matchId != null && settledMatches.containsKey(matchId.toString());
    }

    public void rememberSettlement(UUID matchId, String minigameId, int participantCount) {
        if (matchId == null) throw new IllegalArgumentException("Match UUID is required.");
        SettlementReceipt receipt = new SettlementReceipt();
        receipt.minigameId = bound(minigameId, 64, "");
        receipt.participantCount = Math.max(0, Math.min(128, participantCount));
        receipt.settledAtEpochMilli = System.currentTimeMillis();
        settledMatches.remove(matchId.toString());
        settledMatches.put(matchId.toString(), receipt);
        while (settledMatches.size() > MAX_SETTLEMENTS) {
            String first = settledMatches.keySet().iterator().next();
            settledMatches.remove(first);
        }
    }

    public static final class SettlementReceipt {
        public String minigameId = "";
        public int participantCount;
        public long settledAtEpochMilli;

        public void normalize() {
            minigameId = bound(minigameId, 64, "");
            participantCount = Math.max(0, Math.min(128, participantCount));
            settledAtEpochMilli = Math.max(0L, settledAtEpochMilli);
        }
    }

    public PlayerProgress getOrCreate(UUID playerId, String name) {
        if (playerId == null) throw new IllegalArgumentException("Player UUID is required.");
        PlayerProgress progress = players.computeIfAbsent(playerId.toString(), ignored -> new PlayerProgress());
        progress.uuid = playerId.toString();
        if (name != null && !name.isBlank()) progress.lastKnownName = bound(name, 64, progress.lastKnownName);
        progress.normalize(playerId);
        return progress;
    }

    public static final class PlayerProgress {
        public String uuid = "";
        public String lastKnownName = "Player";
        public long experience;
        public int level = 1;
        public Map<String, Integer> ratings = new LinkedHashMap<>();
        public Map<String, Long> gameExperience = new LinkedHashMap<>();
        public Set<String> unlockedCosmetics = new LinkedHashSet<>();
        public String selectedTitle = "Rookie";
        /** Cosmetic-only victory effect selected from unlocked progression rewards. */
        public String selectedVictoryEffect = "none";
        public String weeklyKey = "";
        public int weeklyMatches;
        public int weeklyWins;
        public long weeklyContribution;
        public Set<String> weeklyClaimed = new LinkedHashSet<>();
        public long matchesPlayed;
        public long matchesWon;
        public long updatedAtEpochMilli;

        public void normalize(UUID fallback) {
            uuid = fallback == null ? uuid : fallback.toString();
            lastKnownName = bound(lastKnownName, 64, "Player");
            experience = Math.max(0L, experience);
            level = levelForExperience(experience);
            ratings = normalizeRatings(ratings);
            gameExperience = normalizeLongMap(gameExperience);
            unlockedCosmetics = normalizeCosmetics(unlockedCosmetics, level);
            String safeTitle = bound(selectedTitle, 48, titleForLevel(level));
            selectedTitle = unlockedCosmetics.contains("title:" + cosmeticId(safeTitle))
                    ? canonicalTitle(safeTitle) : titleForLevel(level);
            String victory = bound(selectedVictoryEffect, 24, "none").toLowerCase(java.util.Locale.ROOT);
            selectedVictoryEffect = "none".equals(victory) || unlockedCosmetics.contains("victory:" + victory)
                    ? victory : "none";
            refreshWeekly();
            weeklyMatches = Math.max(0, Math.min(10_000, weeklyMatches));
            weeklyWins = Math.max(0, Math.min(weeklyMatches, weeklyWins));
            weeklyContribution = Math.max(0L, weeklyContribution);
            weeklyClaimed = normalizeWeeklyClaimed(weeklyClaimed);
            matchesPlayed = Math.max(0L, matchesPlayed);
            matchesWon = Math.max(0L, Math.min(matchesPlayed, matchesWon));
            updatedAtEpochMilli = Math.max(0L, updatedAtEpochMilli);
        }

        public int rating(String minigameId) {
            return ratings.getOrDefault(minigameId == null ? "" : minigameId, 1000);
        }

        public List<String> badges() {
            ArrayList<String> values = new ArrayList<>();
            if (matchesWon >= 100) values.add("Centurion");
            else if (matchesWon >= 25) values.add("Veteran");
            else if (matchesWon >= 5) values.add("Winner");
            if (level >= 30) values.add("Elite");
            else if (level >= 20) values.add("Champion");
            else if (level >= 10) values.add("Seasoned");
            if (values.isEmpty()) values.add("Rookie");
            return List.copyOf(values);
        }

        /** Computes the configured weekly bonus for a finished match without mutating persisted progress. */
        public int previewWeeklyBonus(boolean won, long contribution, MinigameExperienceRules rules) {
            if (rules == null || !rules.weeklyChallengesEnabled) return 0;
            String current = currentWeekKey();
            boolean sameWeek = current.equals(weeklyKey);
            int matches = (sameWeek ? weeklyMatches : 0) + 1;
            int wins = (sameWeek ? weeklyWins : 0) + (won ? 1 : 0);
            long impact = saturatingAdd(sameWeek ? weeklyContribution : 0L, Math.max(0L, contribution));
            Set<String> claimed = sameWeek && weeklyClaimed != null ? weeklyClaimed : Set.of();
            int bonus = 0;
            if (matches >= rules.weeklyMatchesRequired && !claimed.contains("matches")) {
                bonus += rules.weeklyMatchesExperience;
            }
            if (wins >= rules.weeklyWinsRequired && !claimed.contains("wins")) {
                bonus += rules.weeklyWinsExperience;
            }
            if (impact >= rules.weeklyContributionRequired && !claimed.contains("contribution")) {
                bonus += rules.weeklyContributionExperience;
            }
            return bonus;
        }

        /** Records bounded configured weekly challenge progress and returns newly earned bonus XP. */
        public int recordWeekly(boolean won, long contribution, MinigameExperienceRules rules) {
            if (rules == null || !rules.weeklyChallengesEnabled) return 0;
            refreshWeekly();
            weeklyMatches = Math.min(10_000, weeklyMatches + 1);
            if (won) weeklyWins = Math.min(weeklyMatches, weeklyWins + 1);
            weeklyContribution = saturatingAdd(weeklyContribution, Math.max(0L, contribution));
            int bonus = 0;
            if (weeklyMatches >= rules.weeklyMatchesRequired && weeklyClaimed.add("matches")) {
                bonus += rules.weeklyMatchesExperience;
            }
            if (weeklyWins >= rules.weeklyWinsRequired && weeklyClaimed.add("wins")) {
                bonus += rules.weeklyWinsExperience;
            }
            if (weeklyContribution >= rules.weeklyContributionRequired && weeklyClaimed.add("contribution")) {
                bonus += rules.weeklyContributionExperience;
            }
            return bonus;
        }

        public List<String> unlockedTitles() {
            ArrayList<String> values = new ArrayList<>();
            for (String title : List.of("Rookie", "Contender", "Veteran", "Champion", "Elite", "Legend")) {
                if (unlockedCosmetics.contains("title:" + cosmeticId(title))) values.add(title);
            }
            if (values.isEmpty()) values.add("Rookie");
            return List.copyOf(values);
        }

        public List<String> unlockedVictoryEffects() {
            ArrayList<String> values = new ArrayList<>();
            values.add("none");
            if (unlockedCosmetics.contains("victory:spark")) values.add("spark");
            if (unlockedCosmetics.contains("victory:star")) values.add("star");
            return List.copyOf(values);
        }

        public void selectTitle(String raw) {
            String candidate = canonicalTitle(raw);
            if (!unlockedCosmetics.contains("title:" + cosmeticId(candidate))) {
                throw new IllegalArgumentException("That minigame title is not unlocked.");
            }
            selectedTitle = candidate;
        }

        public void selectVictoryEffect(String raw) {
            String candidate = bound(raw, 24, "none").toLowerCase(java.util.Locale.ROOT);
            if (!"none".equals(candidate) && !unlockedCosmetics.contains("victory:" + candidate)) {
                throw new IllegalArgumentException("That victory effect is not unlocked.");
            }
            selectedVictoryEffect = candidate;
        }

        private void refreshWeekly() {
            String current = currentWeekKey();
            if (current.equals(weeklyKey)) return;
            weeklyKey = current;
            weeklyMatches = 0;
            weeklyWins = 0;
            weeklyContribution = 0L;
            weeklyClaimed = new LinkedHashSet<>();
        }

    }

    public static int levelForExperience(long experience) {
        long safe = Math.max(0L, experience);
        int level = 1;
        long required = 100L;
        while (level < 100 && safe >= required) {
            safe -= required;
            level++;
            required = 100L + (long) (level - 1) * 35L;
        }
        return level;
    }

    public static long experienceIntoLevel(long experience) {
        long safe = Math.max(0L, experience);
        int level = 1;
        long required = 100L;
        while (level < 100 && safe >= required) {
            safe -= required;
            level++;
            required = 100L + (long) (level - 1) * 35L;
        }
        return safe;
    }

    public static long experienceForNextLevel(int level) {
        return level >= 100 ? 0L : 100L + (long) Math.max(0, level - 1) * 35L;
    }

    public static String titleForLevel(int level) {
        if (level >= 40) return "Legend";
        if (level >= 30) return "Elite";
        if (level >= 20) return "Champion";
        if (level >= 10) return "Veteran";
        if (level >= 5) return "Contender";
        return "Rookie";
    }

    private static Map<String, Integer> normalizeRatings(Map<String, Integer> raw) {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Integer> entry : raw.entrySet()) {
                String key = bound(entry.getKey(), 64, "").toLowerCase(java.util.Locale.ROOT);
                if (key.isBlank()) continue;
                values.put(key, Math.max(100, Math.min(4_000, entry.getValue() == null ? 1000 : entry.getValue())));
                if (values.size() >= 256) break;
            }
        }
        return values;
    }

    private static Map<String, Long> normalizeLongMap(Map<String, Long> raw) {
        LinkedHashMap<String, Long> values = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Long> entry : raw.entrySet()) {
                String key = bound(entry.getKey(), 64, "").toLowerCase(java.util.Locale.ROOT);
                if (key.isBlank()) continue;
                values.put(key, Math.max(0L, entry.getValue() == null ? 0L : entry.getValue()));
                if (values.size() >= 256) break;
            }
        }
        return values;
    }

    private static Set<String> normalizeCosmetics(Set<String> raw, int level) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                String safe = bound(value, 64, "").toLowerCase(java.util.Locale.ROOT);
                if (!safe.isBlank()) values.add(safe);
                if (values.size() >= 128) break;
            }
        }
        values.add("title:rookie");
        if (level >= 5) values.add("title:contender");
        if (level >= 10) values.add("title:veteran");
        if (level >= 20) values.add("title:champion");
        if (level >= 30) values.add("title:elite");
        if (level >= 40) values.add("title:legend");
        if (level >= 10) values.add("victory:spark");
        if (level >= 20) values.add("victory:star");
        if (level >= 30) values.add("trail:team_dust");
        return values;
    }

    private static Set<String> normalizeWeeklyClaimed(Set<String> raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw != null) {
            for (String value : raw) {
                String safe = bound(value, 32, "").toLowerCase(java.util.Locale.ROOT);
                if (safe.equals("matches") || safe.startsWith("play_")) values.add("matches");
                else if (safe.equals("wins") || safe.startsWith("win_")) values.add("wins");
                else if (safe.equals("contribution") || safe.startsWith("impact_")) values.add("contribution");
            }
        }
        return values;
    }

    private static String currentWeekKey() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC);
        WeekFields fields = WeekFields.ISO;
        return date.get(fields.weekBasedYear()) + "-W" + String.format(java.util.Locale.ROOT, "%02d",
                date.get(fields.weekOfWeekBasedYear()));
    }

    private static String canonicalTitle(String raw) {
        String value = bound(raw, 48, "Rookie").trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "contender" -> "Contender";
            case "veteran" -> "Veteran";
            case "champion" -> "Champion";
            case "elite" -> "Elite";
            case "legend" -> "Legend";
            default -> "Rookie";
        };
    }

    private static String cosmeticId(String value) {
        return bound(value, 48, "rookie").toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    private static long saturatingAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private static String bound(String value, int maximum, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
