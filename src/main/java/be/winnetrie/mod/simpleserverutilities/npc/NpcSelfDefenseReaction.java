package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Reaction used when this NPC is directly attacked. */
public enum NpcSelfDefenseReaction {
    IGNORE("ignore", "Ignore"),
    FLEE("flee", "Flee"),
    FIGHT("fight", "Fight back"),
    CALL_ALLIES("call_allies", "Fight + call allies");

    private final String id;
    private final String label;

    NpcSelfDefenseReaction(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public NpcSelfDefenseReaction next() { NpcSelfDefenseReaction[] v = values(); return v[(ordinal()+1)%v.length]; }

    public static NpcSelfDefenseReaction parse(String raw) {
        if (raw == null) return FIGHT;
        String n = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcSelfDefenseReaction v : values()) if (v.id.equals(n)) return v;
        return FIGHT;
    }
}
