package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Editable presets used by the Ability Workshop. The returned definition is always a normal editable ability. */
public enum NpcAbilityPreset {
    CUSTOM("custom", "Custom"),
    REGULAR_MELEE("regular_melee", "Regular melee hit"),
    REGULAR_RANGED("regular_ranged", "Regular ranged hit"),
    CHARGE("charge", "Charge"),
    THUNDERCLAP("thunderclap", "Thunderclap"),
    SLASH("slash", "Slash (3 hits)"),
    ARCANE_MISSILES("arcane_missiles", "Arcane missiles"),
    ARROW_VOLLEY("arrow_volley", "Arrow volley"),
    FIREBALL("fireball", "Fireball"),
    ICE_BALL("ice_ball", "Ice ball"),
    LEAP("leap", "Leap"),
    MORTAL_STRIKE("mortal_strike", "Mortal strike"),
    BLADESTORM("bladestorm", "Bladestorm"),
    SELF_HEAL("self_heal", "Self heal");

    private final String id;
    private final String label;
    NpcAbilityPreset(String id, String label) { this.id = id; this.label = label; }
    public String id() { return id; }
    public String label() { return label; }
    public NpcAbilityPreset next() { NpcAbilityPreset[] values = values(); return values[(ordinal() + 1) % values.length]; }
    public static NpcAbilityPreset parse(String raw) {
        if (raw == null) return CUSTOM;
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcAbilityPreset preset : values()) if (preset.id.equals(value)) return preset;
        return CUSTOM;
    }

    public NpcAbilityDefinition create(int ordinal) {
        NpcAbilityDefinition a = new NpcAbilityDefinition();
        a.id = id.equals("custom") ? "custom_" + Math.max(1, ordinal) : id + "_" + Math.max(1, ordinal);
        a.displayName = label;
        switch (this) {
            case CUSTOM -> { a.type = NpcAbilityType.CUSTOM.id(); a.attackKind = NpcAttackKind.MAGIC.id(); a.damage = 0.0D; a.healAmount = 0.0D; }
            case REGULAR_MELEE -> { a.type = NpcAbilityType.WEAPON_MELEE.id(); a.attackKind = NpcAttackKind.MELEE.id(); a.damage = 1.0D; a.damageUsesEquipment = true; a.maxRange = 4.0D; }
            case REGULAR_RANGED -> { a.type = NpcAbilityType.WEAPON_RANGED.id(); a.attackKind = NpcAttackKind.RANGED.id(); a.damage = 1.0D; a.damageUsesEquipment = true; a.minRange = 3.0D; a.maxRange = 28.0D; a.requiresStationary = true; }
            case CHARGE -> { a.type = NpcAbilityType.CHARGE.id(); a.attackKind = NpcAttackKind.MELEE.id(); a.damageUsesEquipment = true; a.damage = 1.0D; a.maxRange = 18.0D; a.chargeSpeed = 2.2D; a.stunTicks = 16; a.windupTicks = 4; a.recoveryTicks = 10; }
            case THUNDERCLAP -> { a.type = NpcAbilityType.THUNDERCLAP.id(); a.attackKind = NpcAttackKind.MELEE.id(); a.shape = NpcAbilityShape.AROUND_SELF.id(); a.damageUsesEquipment = true; a.damage = 0.45D; a.radius = 5.0D; a.knockback = 1.2D; a.slowTicks = 60; a.slowAmplifier = 1; a.requiresStationary = true; a.minTargets = 1; }
            case SLASH -> { a.type = NpcAbilityType.SLASH.id(); a.attackKind = NpcAttackKind.MELEE.id(); a.damageUsesEquipment = true; a.damage = 0.5D; a.hitCount = 3; a.pulseIntervalTicks = 5; a.maxRange = 4.0D; a.recoveryTicks = 8; a.requiresStationary = true; }
            case ARCANE_MISSILES -> { a.type = NpcAbilityType.ARCANE_MISSILES.id(); a.attackKind = NpcAttackKind.MAGIC.id(); a.damageSchool = NpcDamageSchool.ARCANE.id(); a.damage = 4.0D; a.hitCount = 3; a.pulseIntervalTicks = 10; a.channeling = true; a.interruptOnDamage = true; a.interruptOnMove = true; a.requiresStationary = true; a.minRange = 2.0D; a.maxRange = 28.0D; a.windupTicks = 4; a.recoveryTicks = 6; }
            case ARROW_VOLLEY -> { a.type = NpcAbilityType.ARROW_VOLLEY.id(); a.attackKind = NpcAttackKind.RANGED.id(); a.shape = NpcAbilityShape.AROUND_TARGET.id(); a.damage = 3.0D; a.hitCount = 5; a.pulseIntervalTicks = 5; a.radius = 4.0D; a.minRange = 5.0D; a.maxRange = 30.0D; a.requiresStationary = true; }
            case FIREBALL -> { a.type = NpcAbilityType.FIREBALL.id(); a.attackKind = NpcAttackKind.MAGIC.id(); a.damageSchool = NpcDamageSchool.FIRE.id(); a.damage = 8.0D; a.minRange = 3.0D; a.maxRange = 30.0D; a.dotDamage = 1.5D; a.dotDurationTicks = 80; a.dotIntervalTicks = 20; a.requiresStationary = true; }
            case ICE_BALL -> { a.type = NpcAbilityType.ICE_BALL.id(); a.attackKind = NpcAttackKind.MAGIC.id(); a.damageSchool = NpcDamageSchool.ICE.id(); a.damage = 7.0D; a.minRange = 3.0D; a.maxRange = 30.0D; a.slowTicks = 80; a.slowAmplifier = 1; a.requiresStationary = true; }
            case LEAP -> { a.type = NpcAbilityType.LEAP.id(); a.attackKind = NpcAttackKind.MELEE.id(); a.damageUsesEquipment = true; a.damage = 1.0D; a.maxRange = 16.0D; a.knockback = 1.0D; }
            case MORTAL_STRIKE -> { a.type = NpcAbilityType.MORTAL_STRIKE.id(); a.attackKind = NpcAttackKind.MELEE.id(); a.damageUsesEquipment = true; a.damage = 1.5D; a.maxRange = 4.0D; a.cooldownTicks = 120; }
            case BLADESTORM -> { a.type = NpcAbilityType.BLADESTORM.id(); a.attackKind = NpcAttackKind.MELEE.id(); a.shape = NpcAbilityShape.AROUND_SELF.id(); a.damageUsesEquipment = true; a.damage = 0.55D; a.radius = 4.0D; a.hitCount = 6; a.pulseIntervalTicks = 5; a.channeling = true; a.interruptOnDamage = false; a.interruptOnMove = false; }
            case SELF_HEAL -> { a.type = NpcAbilityType.SELF_HEAL.id(); a.attackKind = NpcAttackKind.MAGIC.id(); a.damage = 0.0D; a.healAmount = 10.0D; a.maxRange = 0.0D; a.minTargets = 0; a.requiresStationary = true; }
        }
        return a.normalize();
    }
}
