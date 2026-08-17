package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Reaction used when an entity with HOSTILE attitude is seen. */
public enum NpcHostileSightReaction {
    IGNORE("ignore", "Ignore"),
    AVOID("avoid", "Avoid"),
    ATTACK("attack", "Attack");

    private final String id;
    private final String label;

    NpcHostileSightReaction(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public NpcHostileSightReaction next() { NpcHostileSightReaction[] v = values(); return v[(ordinal()+1)%v.length]; }

    public static NpcHostileSightReaction parse(String raw) {
        if (raw == null) return ATTACK;
        String n = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcHostileSightReaction v : values()) if (v.id.equals(n)) return v;
        return ATTACK;
    }
}
