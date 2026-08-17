package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/**
 * Activity performed after an NPC reaches the active schedule point.
 *
 * <p>The schedule entry stores the stable string id so older data and the existing
 * network payload stay compatible while new activities can be added without changing
 * the payload shape.</p>
 */
public enum NpcScheduleActivity {
    IDLE("idle", "Idle"),
    LOOK_AROUND("look_around", "Look around"),
    WORK("work", "Work / use main hand"),
    GUARD("guard", "Guard area"),
    /** Legacy id kept for existing schedules; behaviour matches WORK. */
    CHOP_TREE("chop_tree", "Chop tree (legacy)");

    private final String id;
    private final String label;

    NpcScheduleActivity(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public NpcScheduleActivity next() {
        NpcScheduleActivity[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static NpcScheduleActivity parse(String raw) {
        if (raw == null) return IDLE;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcScheduleActivity value : values()) if (value.id.equals(normalized)) return value;
        return IDLE;
    }
}
