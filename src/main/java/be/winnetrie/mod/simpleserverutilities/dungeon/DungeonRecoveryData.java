package be.winnetrie.mod.simpleserverutilities.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persisted return points and unsafe arena markers for interrupted dungeon runs. */
public final class DungeonRecoveryData {
    public static final int SCHEMA_VERSION = 1;
    public int schemaVersion = SCHEMA_VERSION;
    public List<Entry> players = new ArrayList<>();
    public List<String> unsafeArenas = new ArrayList<>();

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        ArrayList<Entry> normalized = new ArrayList<>();
        if (players != null) for (Entry entry : players) {
            if (entry == null || entry.playerId == null) continue;
            if (entry.returnLocation == null) entry.returnLocation = new DungeonLocation();
            entry.returnLocation.normalize();
            entry.dungeonId = entry.dungeonId == null ? "" : entry.dungeonId.trim();
            entry.runId = entry.runId == null ? "" : entry.runId.trim();
            normalized.add(entry);
            if (normalized.size() >= 10_000) break;
        }
        players = normalized;
        java.util.LinkedHashSet<String> arenas = new java.util.LinkedHashSet<>();
        if (unsafeArenas != null) for (String raw : unsafeArenas) {
            String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (!value.isBlank() && value.length() <= 160) arenas.add(value);
            if (arenas.size() >= 1_024) break;
        }
        unsafeArenas = new ArrayList<>(arenas);
    }

    public static final class Entry {
        public UUID playerId;
        public String dungeonId = "";
        public String runId = "";
        public DungeonLocation returnLocation = new DungeonLocation();
        public Entry() {}
        public Entry(UUID playerId, String dungeonId, String runId, DungeonLocation returnLocation) {
            this.playerId = playerId; this.dungeonId = dungeonId; this.runId = runId; this.returnLocation = returnLocation;
        }
    }
}
