package be.winnetrie.mod.simpleserverutilities.npc;

/** One editable/data-driven NPC ability. Presets are only starting points; every field stays editable. */
public final class NpcAbilityDefinition {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ABILITIES = 24;

    public int schemaVersion = SCHEMA_VERSION;

    public String id = "ability";
    public String displayName = "Ability";
    public String type = NpcAbilityType.POWER_STRIKE.id();
    public boolean enabled = true;
    /** Legacy/UI bridge only. In schema 19+ phase gating belongs to NpcAbilityAssignment. */
    public String phaseId = "";
    /** Melee/ranged/magic permission channel. */
    public String attackKind = NpcAttackKind.MELEE.id();
    /** Single target, around caster, around target or a cone in front of the caster. */
    public String shape = NpcAbilityShape.SINGLE.id();
    /** Semantic damage type. Magical schools respect the defender's magic resistance. */
    public String damageSchool = NpcDamageSchool.PHYSICAL.id();

    public int cooldownTicks = 100;
    public int windupTicks = 12;
    public int recoveryTicks = 8;
    public double minRange = 0.0D;
    public double maxRange = 4.0D;
    public double chance = 1.0D;

    /**
     * Direct damage. When damageUsesEquipment=true this is a multiplier of the equipped weapon's
     * computed attack value (1.0 = normal weapon hit, 0.5 = half weapon damage).
     */
    public double damage = 8.0D;
    public boolean damageUsesEquipment;
    public double radius = 4.0D;
    public double coneAngleDegrees = 90.0D;
    public double knockback = 0.8D;
    public double healAmount = 0.0D;

    /** Repeated/channelled pulse configuration; used by Slash, Arcane Missiles, Bladestorm and custom spells. */
    public int hitCount = 1;
    public int pulseIntervalTicks = 5;
    public boolean channeling;
    public boolean interruptOnDamage;
    public boolean interruptOnMove;
    /** AI preparation rule: stop navigation and hold position before/during the cast. */
    public boolean requiresStationary;
    /** Minimum valid hostile targets required in the configured shape before AI may choose this ability. */
    public int minTargets = 1;

    /** Crowd-control/effect options. */
    public int stunTicks;
    public int slowTicks;
    public int slowAmplifier;
    /** Optional arbitrary Minecraft/modded mob-effect registry ID applied to affected targets. */
    public String debuffEffect = "";
    public int debuffDurationTicks;
    public int debuffAmplifier;
    public double bleedDamage;
    public int bleedDurationTicks;
    public int bleedIntervalTicks = 20;
    public double dotDamage;
    public int dotDurationTicks;
    public int dotIntervalTicks = 20;
    public double hotAmount;
    public int hotDurationTicks;
    public int hotIntervalTicks = 20;
    /** Charge/path burst speed multiplier. */
    public double chargeSpeed = 2.0D;

    public NpcAbilityDefinition normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = sanitize(id, "ability", 48);
        displayName = limit(displayName == null || displayName.isBlank()
                ? NpcAbilityType.parse(type).label() : displayName.trim(), 64);
        type = NpcAbilityType.parse(type).id();
        phaseId = phaseId == null || phaseId.isBlank() ? "" : sanitize(phaseId, "phase", 48);
        attackKind = NpcAttackKind.parse(attackKind).id();
        shape = NpcAbilityShape.parse(shape).id();
        damageSchool = NpcDamageSchool.parse(damageSchool).id();
        cooldownTicks = clamp(cooldownTicks, 4, 72_000);
        windupTicks = clamp(windupTicks, 0, 400);
        recoveryTicks = clamp(recoveryTicks, 0, 400);
        minRange = finiteClamp(minRange, 0.0D, 64.0D, 0.0D);
        maxRange = finiteClamp(maxRange, minRange, 128.0D, Math.max(4.0D, minRange));
        chance = finiteClamp(chance, 0.0D, 1.0D, 1.0D);
        damage = finiteClamp(damage, 0.0D, 2_048.0D, 8.0D);
        radius = finiteClamp(radius, 0.25D, 32.0D, 4.0D);
        coneAngleDegrees = finiteClamp(coneAngleDegrees, 5.0D, 180.0D, 90.0D);
        knockback = finiteClamp(knockback, 0.0D, 8.0D, 0.8D);
        healAmount = finiteClamp(healAmount, 0.0D, 2_048.0D, 0.0D);
        hitCount = clamp(hitCount, 1, 32);
        pulseIntervalTicks = clamp(pulseIntervalTicks, 1, 200);
        minTargets = clamp(minTargets, 0, 64);
        stunTicks = clamp(stunTicks, 0, 1_200);
        slowTicks = clamp(slowTicks, 0, 7_200);
        slowAmplifier = clamp(slowAmplifier, 0, 10);
        debuffEffect = normalizeOptionalRegistryId(debuffEffect, 128);
        debuffDurationTicks = clamp(debuffDurationTicks, 0, 72_000);
        debuffAmplifier = clamp(debuffAmplifier, 0, 10);
        bleedDamage = finiteClamp(bleedDamage, 0.0D, 2_048.0D, 0.0D);
        bleedDurationTicks = clamp(bleedDurationTicks, 0, 72_000);
        bleedIntervalTicks = clamp(bleedIntervalTicks, 1, 1_200);
        dotDamage = finiteClamp(dotDamage, 0.0D, 2_048.0D, 0.0D);
        dotDurationTicks = clamp(dotDurationTicks, 0, 72_000);
        dotIntervalTicks = clamp(dotIntervalTicks, 1, 1_200);
        hotAmount = finiteClamp(hotAmount, 0.0D, 2_048.0D, 0.0D);
        hotDurationTicks = clamp(hotDurationTicks, 0, 72_000);
        hotIntervalTicks = clamp(hotIntervalTicks, 1, 1_200);
        chargeSpeed = finiteClamp(chargeSpeed, 0.2D, 8.0D, 2.0D);
        return this;
    }

    public NpcAbilityDefinition copy() {
        NpcAbilityDefinition copy = new NpcAbilityDefinition();
        copy.id = id; copy.displayName = displayName; copy.type = type; copy.enabled = enabled; copy.phaseId = phaseId;
        copy.attackKind = attackKind; copy.shape = shape; copy.damageSchool = damageSchool;
        copy.cooldownTicks = cooldownTicks; copy.windupTicks = windupTicks; copy.recoveryTicks = recoveryTicks;
        copy.minRange = minRange; copy.maxRange = maxRange; copy.chance = chance; copy.damage = damage;
        copy.damageUsesEquipment = damageUsesEquipment; copy.radius = radius; copy.coneAngleDegrees = coneAngleDegrees;
        copy.knockback = knockback; copy.healAmount = healAmount; copy.hitCount = hitCount; copy.pulseIntervalTicks = pulseIntervalTicks;
        copy.channeling = channeling; copy.interruptOnDamage = interruptOnDamage; copy.interruptOnMove = interruptOnMove;
        copy.requiresStationary = requiresStationary; copy.minTargets = minTargets;
        copy.stunTicks = stunTicks; copy.slowTicks = slowTicks; copy.slowAmplifier = slowAmplifier;
        copy.debuffEffect = debuffEffect; copy.debuffDurationTicks = debuffDurationTicks; copy.debuffAmplifier = debuffAmplifier;
        copy.bleedDamage = bleedDamage; copy.bleedDurationTicks = bleedDurationTicks; copy.bleedIntervalTicks = bleedIntervalTicks;
        copy.dotDamage = dotDamage; copy.dotDurationTicks = dotDurationTicks; copy.dotIntervalTicks = dotIntervalTicks;
        copy.hotAmount = hotAmount; copy.hotDurationTicks = hotDurationTicks; copy.hotIntervalTicks = hotIntervalTicks;
        copy.chargeSpeed = chargeSpeed;
        return copy.normalize();
    }

    public NpcAbilityType abilityType() { return NpcAbilityType.parse(type); }
    public NpcAttackKind attackKind() { return NpcAttackKind.parse(attackKind); }
    public NpcAbilityShape abilityShape() { return NpcAbilityShape.parse(shape); }
    public NpcDamageSchool damageSchool() { return NpcDamageSchool.parse(damageSchool); }

    private static String sanitize(String raw, String fallback, int max) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
        if (value.isBlank()) value = fallback;
        return limit(value, max);
    }

    private static String normalizeOptionalRegistryId(String raw, int max) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isBlank()) return "";
        if (!value.contains(":")) value = "minecraft:" + value;
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return "";
        return limit(value, max);
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
