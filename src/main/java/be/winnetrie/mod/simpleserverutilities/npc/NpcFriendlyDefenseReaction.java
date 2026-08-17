package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Reaction used when an allied/friendly managed NPC is attacked nearby. */
public enum NpcFriendlyDefenseReaction {
    IGNORE("ignore", "Ignore"),
    ASSIST("assist", "Assist"),
    CALL_ALLIES("call_allies", "Assist + call allies");

    private final String id;
    private final String label;

    NpcFriendlyDefenseReaction(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public NpcFriendlyDefenseReaction next() { NpcFriendlyDefenseReaction[] v = values(); return v[(ordinal()+1)%v.length]; }

    public static NpcFriendlyDefenseReaction parse(String raw) {
        if (raw == null) return ASSIST;
        String n = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcFriendlyDefenseReaction v : values()) if (v.id.equals(n)) return v;
        return ASSIST;
    }
}
