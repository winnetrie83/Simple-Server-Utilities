package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Semantic damage school. Physical is armor-driven; magical schools respect NPC magic resistance. */
public enum NpcDamageSchool {
    PHYSICAL("physical", "Physical", false),
    FIRE("fire", "Fire", true),
    ARCANE("arcane", "Arcane", true),
    ICE("ice", "Ice", true),
    NATURE("nature", "Nature", true),
    SHADOW("shadow", "Shadow", true);

    private final String id;
    private final String label;
    private final boolean magical;
    NpcDamageSchool(String id, String label, boolean magical) { this.id = id; this.label = label; this.magical = magical; }
    public String id() { return id; }
    public String label() { return label; }
    public boolean magical() { return magical; }
    public NpcDamageSchool next() { NpcDamageSchool[] values = values(); return values[(ordinal() + 1) % values.length]; }
    public static NpcDamageSchool parse(String raw) {
        if (raw == null) return PHYSICAL;
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcDamageSchool school : values()) if (school.id.equals(value)) return school;
        return PHYSICAL;
    }
}
