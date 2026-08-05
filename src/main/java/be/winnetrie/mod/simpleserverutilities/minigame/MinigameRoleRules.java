package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;

import net.minecraft.resources.Identifier;

/** Optional tactical role composition and ability tuning for two-team modes. */
public final class MinigameRoleRules {
    public boolean enabled;

    public MinigameRoleProfile dps = new MinigameRoleProfile(0, 64, 20.0D, 4.0D, 0.0D);
    public MinigameRoleProfile tank = new MinigameRoleProfile(0, 64, 28.0D, 14.0D, 6.0D);
    public MinigameRoleProfile healer = new MinigameRoleProfile(0, 64, 22.0D, 6.0D, 2.0D);

    /** Radius and timing for the Tank enemy-only defensive field. */
    public double tankSlowRadius = 3.0D;
    /** Horizontal enemy knockback applied by the same defensive field. Zero disables knockback. */
    public double tankKnockbackStrength = 1.0D;
    public int tankSlowDurationSeconds = 4;
    public int tankSlowCooldownSeconds = 18;

    /** Health values are raw health points; two points equal one vanilla heart. */
    public double healerSingleHealAmount = 8.0D;
    public int healerSingleHealCooldownSeconds = 8;
    public double healerAoeHealAmount = 4.0D;
    public double healerAoeHealRadius = 3.0D;
    public int healerAoeHealCooldownSeconds = 14;
    public int healerSelfHealCooldownSeconds = 30;

    /** DPS arrows are replenished and apply this effect on enemy impact. Amplifier 0 is level I. */
    public String dpsArrowEffect = "minecraft:poison";
    public int dpsArrowEffectAmplifier;
    public int dpsArrowEffectDurationSeconds = 5;

    public void normalize() {
        if (dps == null) dps = new MinigameRoleProfile(0, 64, 20.0D, 4.0D, 0.0D);
        if (tank == null) tank = new MinigameRoleProfile(0, 64, 28.0D, 14.0D, 6.0D);
        if (healer == null) healer = new MinigameRoleProfile(0, 64, 22.0D, 6.0D, 2.0D);
        dps.normalize();
        tank.normalize();
        healer.normalize();
        if (!Double.isFinite(tankSlowRadius)) tankSlowRadius = 3.0D;
        tankSlowRadius = Math.max(1.0D, Math.min(16.0D, tankSlowRadius));
        if (!Double.isFinite(tankKnockbackStrength)) tankKnockbackStrength = 1.0D;
        tankKnockbackStrength = Math.max(0.0D, Math.min(5.0D, tankKnockbackStrength));
        tankSlowDurationSeconds = Math.max(1, Math.min(60, tankSlowDurationSeconds));
        tankSlowCooldownSeconds = Math.max(1, Math.min(600, tankSlowCooldownSeconds));
        if (!Double.isFinite(healerSingleHealAmount)) healerSingleHealAmount = 8.0D;
        if (!Double.isFinite(healerAoeHealAmount)) healerAoeHealAmount = 4.0D;
        healerSingleHealAmount = Math.max(1.0D, Math.min(100.0D, healerSingleHealAmount));
        healerAoeHealAmount = Math.max(0.5D,
                Math.min(healerSingleHealAmount - 0.5D, healerAoeHealAmount));
        if (!Double.isFinite(healerAoeHealRadius)) healerAoeHealRadius = 3.0D;
        healerAoeHealRadius = Math.max(1.0D, Math.min(16.0D, healerAoeHealRadius));
        healerSingleHealCooldownSeconds = Math.max(1, Math.min(600, healerSingleHealCooldownSeconds));
        healerAoeHealCooldownSeconds = Math.max(1, Math.min(600, healerAoeHealCooldownSeconds));
        healerSelfHealCooldownSeconds = Math.max(1, Math.min(600, healerSelfHealCooldownSeconds));
        String effect = dpsArrowEffect == null || dpsArrowEffect.isBlank()
                ? "minecraft:poison" : dpsArrowEffect.trim().toLowerCase(Locale.ROOT);
        try {
            Identifier.parse(effect);
        } catch (RuntimeException ignored) {
            effect = "minecraft:poison";
        }
        dpsArrowEffect = effect;
        dpsArrowEffectAmplifier = Math.max(0, Math.min(9, dpsArrowEffectAmplifier));
        dpsArrowEffectDurationSeconds = Math.max(1, Math.min(600, dpsArrowEffectDurationSeconds));
    }

    public MinigameRoleProfile profile(MinigameRole role) {
        return switch (role == null ? MinigameRole.DPS : role) {
            case DPS -> dps;
            case TANK -> tank;
            case HEALER -> healer;
        };
    }

    public int minimum(MinigameRole role) { return profile(role).minimumPerTeam; }
    public int maximum(MinigameRole role) { return profile(role).maximumPerTeam; }
    public int minimumTotalPerTeam() { return dps.minimumPerTeam + tank.minimumPerTeam + healer.minimumPerTeam; }
    public int maximumTotalPerTeam() { return dps.maximumPerTeam + tank.maximumPerTeam + healer.maximumPerTeam; }
}
