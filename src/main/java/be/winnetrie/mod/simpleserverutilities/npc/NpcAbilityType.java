package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Built-in executor families. Every definition remains editable/data-driven in the Ability Workshop. */
public enum NpcAbilityType {
    POWER_STRIKE("power_strike", "Power strike", true, true),
    RANGED_BLAST("ranged_blast", "Ranged blast", true, true),
    SHOCKWAVE("shockwave", "Shockwave", true, true),
    SELF_HEAL("self_heal", "Self heal", false, true),
    LEAP("leap", "Leap", true, true),
    WEAPON_MELEE("weapon_melee", "Weapon melee", true, false),
    WEAPON_RANGED("weapon_ranged", "Weapon ranged", true, false),
    CHARGE("charge", "Charge", true, true),
    THUNDERCLAP("thunderclap", "Thunderclap", true, true),
    SLASH("slash", "Slash", true, true),
    ARCANE_MISSILES("arcane_missiles", "Arcane missiles", true, true),
    ARROW_VOLLEY("arrow_volley", "Arrow volley", true, true),
    FIREBALL("fireball", "Fireball", true, true),
    ICE_BALL("ice_ball", "Ice ball", true, true),
    MORTAL_STRIKE("mortal_strike", "Mortal strike", true, true),
    BLADESTORM("bladestorm", "Bladestorm", true, true),
    CUSTOM("custom", "Custom", true, false);

    private final String id;
    private final String label;
    private final boolean targetRequired;
    private final boolean locksMovement;

    NpcAbilityType(String id, String label, boolean targetRequired, boolean locksMovement) {
        this.id = id;
        this.label = label;
        this.targetRequired = targetRequired;
        this.locksMovement = locksMovement;
    }

    public String id() { return id; }
    public String label() { return label; }
    public boolean targetRequired() { return targetRequired; }
    public boolean locksMovement() { return locksMovement; }
    public NpcAbilityType next() { NpcAbilityType[] values = values(); return values[(ordinal() + 1) % values.length]; }

    public static NpcAbilityType parse(String raw) {
        if (raw == null) return POWER_STRIKE;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcAbilityType value : values()) if (value.id.equals(normalized)) return value;
        return POWER_STRIKE;
    }
}
