package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Route traversal strategy for one placed NPC's patrol points. */
public enum NpcPatrolMode {
    LOOP("loop", "Loop"),
    PING_PONG("ping_pong", "Ping-pong"),
    RANDOM("random", "Random");

    private final String id;
    private final String label;

    NpcPatrolMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public NpcPatrolMode next() {
        NpcPatrolMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static NpcPatrolMode parse(String raw) {
        if (raw == null) return LOOP;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcPatrolMode value : values()) if (value.id.equals(normalized)) return value;
        return LOOP;
    }
}
