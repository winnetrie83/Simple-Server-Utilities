package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Targeting shape used by custom/data-driven NPC abilities. */
public enum NpcAbilityShape {
    SINGLE("single", "Single target"),
    AROUND_SELF("around_self", "AoE around caster"),
    AROUND_TARGET("around_target", "AoE around target"),
    CONE("cone", "Cone in front");

    private final String id;
    private final String label;
    NpcAbilityShape(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public NpcAbilityShape next() { NpcAbilityShape[] values = values(); return values[(ordinal() + 1) % values.length]; }
    public static NpcAbilityShape parse(String raw) {
        if (raw == null) return SINGLE;
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcAbilityShape shape : values()) if (shape.id.equals(value)) return shape;
        return SINGLE;
    }
}
