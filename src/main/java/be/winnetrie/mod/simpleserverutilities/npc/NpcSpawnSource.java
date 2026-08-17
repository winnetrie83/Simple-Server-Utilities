package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Origin of a dynamic NPC. */
public enum NpcSpawnSource {
    NATURAL("natural", "Natural"),
    SPAWNER("spawner", "Spawner");

    private final String id;
    private final String label;

    NpcSpawnSource(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public static NpcSpawnSource parse(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (NpcSpawnSource value : values()) if (value.id.equals(normalized)) return value;
        return NATURAL;
    }
}
