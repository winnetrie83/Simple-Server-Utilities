package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** High-level reusable behaviour preset for an NPC definition. */
public enum NpcBehaviorMode {
    NATIVE("native", "Native AI"),
    STATIONARY("stationary", "Stationary"),
    LOOK_AT_PLAYERS("look_at_players", "Look at players"),
    WANDER("wander", "Wander"),
    PATROL("patrol", "Patrol");

    private final String id;
    private final String label;

    NpcBehaviorMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public NpcBehaviorMode next() {
        NpcBehaviorMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public NpcBehaviorMode previous() {
        NpcBehaviorMode[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public static NpcBehaviorMode parse(String raw) {
        if (raw == null) return STATIONARY;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcBehaviorMode value : values()) if (value.id.equals(normalized)) return value;
        return STATIONARY;
    }
}
