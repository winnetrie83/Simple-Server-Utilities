package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Optional day/night restriction for natural dynamic spawning. */
public enum NpcSpawnTime {
    ANY("any", "Any time"),
    DAY("day", "Day"),
    NIGHT("night", "Night");

    private final String id;
    private final String label;

    NpcSpawnTime(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public static NpcSpawnTime parse(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (NpcSpawnTime value : values()) if (value.id.equals(normalized)) return value;
        return ANY;
    }
}
