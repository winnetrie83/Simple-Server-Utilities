package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persisted return points protect players after disconnects or server crashes. */
public final class MinigameRecoveryData {
    public static final int SCHEMA_VERSION = 4;
    public int schemaVersion = SCHEMA_VERSION;
    public List<Entry> players = new ArrayList<>();
    /** Arena keys that require a successful reset or explicit administrator release after interruption. */
    public List<String> unsafeArenas = new ArrayList<>();

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        ArrayList<Entry> normalized = new ArrayList<>();
        if (players != null) {
            for (Entry entry : players) {
                if (entry == null || entry.playerId == null) continue;
                if (entry.returnLocation == null) entry.returnLocation = new MinigameLocation();
                entry.returnLocation.normalize();
                if (entry.stateCaptured && entry.playerState != null) {
                    entry.playerState.normalize();
                } else {
                    // Schema-1 recovery files did not contain player state. Never
                    // manufacture an empty inventory during migration: returning the
                    // player without clearing their live state is the only safe choice.
                    entry.stateCaptured = false;
                    entry.playerState = null;
                }
                entry.minigameId = entry.minigameId == null ? "" : entry.minigameId.trim();
                entry.matchId = entry.matchId == null ? "" : entry.matchId.trim();
                normalized.add(entry);
                if (normalized.size() >= 10_000) break;
            }
        }
        players = normalized;
        java.util.LinkedHashSet<String> normalizedArenas = new java.util.LinkedHashSet<>();
        if (unsafeArenas != null) {
            for (String raw : unsafeArenas) {
                String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (!value.isBlank() && value.length() <= 160) normalizedArenas.add(value);
                if (normalizedArenas.size() >= 1_024) break;
            }
        }
        unsafeArenas = new ArrayList<>(normalizedArenas);
    }

    public static final class Entry {
        public UUID playerId;
        public String minigameId = "";
        public String matchId = "";
        public MinigameLocation returnLocation = new MinigameLocation();
        /** False for migrated schema-1 entries that only contain a return location. */
        public boolean stateCaptured;
        public MinigamePlayerState playerState;

        public Entry() {
        }

        public Entry(UUID playerId, String minigameId, String matchId, MinigameLocation returnLocation) {
            this(playerId, minigameId, matchId, returnLocation, null);
        }

        public Entry(UUID playerId, String minigameId, String matchId, MinigameLocation returnLocation, MinigamePlayerState playerState) {
            this.playerId = playerId;
            this.minigameId = minigameId;
            this.matchId = matchId;
            this.returnLocation = returnLocation;
            this.stateCaptured = playerState != null;
            this.playerState = playerState;
        }
    }
}
