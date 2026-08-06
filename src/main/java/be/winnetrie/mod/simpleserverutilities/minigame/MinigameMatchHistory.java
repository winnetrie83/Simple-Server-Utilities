package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded persisted audit/history used by diagnostics, results and future leaderboards. */
public final class MinigameMatchHistory {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_MATCHES = 500;
    public int schemaVersion = SCHEMA_VERSION;
    public List<Entry> matches = new ArrayList<>();

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        ArrayList<Entry> normalized = new ArrayList<>();
        if (matches != null) {
            int start = Math.max(0, matches.size() - MAX_MATCHES);
            for (int index = start; index < matches.size(); index++) {
                Entry entry = matches.get(index);
                if (entry == null) continue;
                entry.normalize();
                normalized.add(entry);
            }
        }
        matches = normalized;
    }

    public static final class Entry {
        public String matchId = "";
        public String minigameId = "";
        public String displayName = "";
        public String arenaId = "";
        public long startedAtEpochMilli;
        public long finishedAtEpochMilli;
        public String finishReason = "";
        public List<Integer> winningTeams = new ArrayList<>();
        public Map<String, PlayerEntry> players = new LinkedHashMap<>();

        public void normalize() {
            matchId = bound(matchId, 64);
            minigameId = bound(minigameId, 64);
            displayName = bound(displayName, 128);
            arenaId = bound(arenaId, 64);
            startedAtEpochMilli = Math.max(0L, startedAtEpochMilli);
            finishedAtEpochMilli = Math.max(startedAtEpochMilli, finishedAtEpochMilli);
            finishReason = bound(finishReason, 512);
            ArrayList<Integer> teams = new ArrayList<>();
            if (winningTeams != null) for (Integer team : winningTeams) {
                if (team != null && team > 0 && !teams.contains(team)) teams.add(team);
                if (teams.size() >= 16) break;
            }
            winningTeams = teams;
            LinkedHashMap<String, PlayerEntry> normalized = new LinkedHashMap<>();
            if (players != null) {
                for (Map.Entry<String, PlayerEntry> entry : players.entrySet()) {
                    if (entry.getValue() == null) continue;
                    entry.getValue().normalize();
                    normalized.put(bound(entry.getKey(), 64), entry.getValue());
                    if (normalized.size() >= 128) break;
                }
            }
            players = normalized;
        }
    }

    public static final class PlayerEntry {
        public String name = "Player";
        public int team;
        public String role = "dps";
        public boolean won;
        public long score;
        public MinigamePerformance performance = new MinigamePerformance();
        public int experienceGained;
        public int resultingLevel = 1;

        public void normalize() {
            name = bound(name, 64);
            team = Math.max(0, Math.min(16, team));
            role = bound(role, 16);
            score = Math.max(0L, score);
            if (performance == null) performance = new MinigamePerformance();
            experienceGained = Math.max(0, Math.min(1_000_000, experienceGained));
            resultingLevel = Math.max(1, Math.min(100, resultingLevel));
        }
    }

    private static String bound(String value, int maximum) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
