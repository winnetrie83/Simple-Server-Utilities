package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** High-level combat channel. NPCs may enable any combination of these channels. */
public enum NpcAttackKind {
    MELEE("melee", "Melee"),
    RANGED("ranged", "Ranged"),
    MAGIC("magic", "Magic");

    private final String id;
    private final String label;

    NpcAttackKind(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public NpcAttackKind next() { NpcAttackKind[] values = values(); return values[(ordinal() + 1) % values.length]; }

    public static NpcAttackKind parse(String raw) {
        if (raw == null) return MELEE;
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcAttackKind kind : values()) if (kind.id.equals(value)) return kind;
        return MELEE;
    }
}
