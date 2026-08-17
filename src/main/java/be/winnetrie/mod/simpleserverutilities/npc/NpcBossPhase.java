package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.List;

/** Health-threshold phase for boss NPCs. The lowest crossed health threshold is active. */
public final class NpcBossPhase {
    public static final int MAX_PHASES = 8;

    public String id = "phase_1";
    public String displayName = "Phase 1";
    /** Phase becomes active when health percentage is <= this value. */
    public double healthThresholdPercent = 100.0D;
    public double movementSpeedMultiplier = 1.0D;
    public double cooldownMultiplier = 1.0D;
    public double abilityDamageMultiplier = 1.0D;
    /** External taunts are ignored while this phase is active; scripted fixate actions still work. */
    public boolean tauntImmune;
    /** One-shot actions executed when this phase is entered during an active encounter. */
    public List<NpcBossPhaseAction> actions = new ArrayList<>();

    public NpcBossPhase normalize() {
        id = sanitize(id, "phase_1", 48);
        displayName = limit(displayName == null || displayName.isBlank() ? humanize(id) : displayName.trim(), 64);
        healthThresholdPercent = finiteClamp(healthThresholdPercent, 0.1D, 100.0D, 100.0D);
        movementSpeedMultiplier = finiteClamp(movementSpeedMultiplier, 0.1D, 4.0D, 1.0D);
        cooldownMultiplier = finiteClamp(cooldownMultiplier, 0.1D, 5.0D, 1.0D);
        abilityDamageMultiplier = finiteClamp(abilityDamageMultiplier, 0.0D, 8.0D, 1.0D);
        if (actions == null) actions = new ArrayList<>();
        List<NpcBossPhaseAction> normalizedActions = new ArrayList<>();
        for (NpcBossPhaseAction action : actions) {
            if (action == null) continue;
            normalizedActions.add(action.copy());
            if (normalizedActions.size() >= NpcBossPhaseAction.MAX_ACTIONS_PER_PHASE) break;
        }
        actions = normalizedActions;
        return this;
    }

    public NpcBossPhase copy() {
        NpcBossPhase copy = new NpcBossPhase();
        copy.id = id; copy.displayName = displayName; copy.healthThresholdPercent = healthThresholdPercent;
        copy.movementSpeedMultiplier = movementSpeedMultiplier; copy.cooldownMultiplier = cooldownMultiplier;
        copy.abilityDamageMultiplier = abilityDamageMultiplier;
        copy.tauntImmune = tauntImmune;
        copy.actions = new ArrayList<>();
        for (NpcBossPhaseAction action : actions) copy.actions.add(action.copy());
        return copy.normalize();
    }

    public static NpcBossPhase phaseOne() { return new NpcBossPhase().normalize(); }

    private static String sanitize(String raw, String fallback, int max) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
        if (value.isBlank()) value = fallback;
        return limit(value, max);
    }
    private static String humanize(String value) {
        StringBuilder out = new StringBuilder(); boolean upper = true;
        for (char c : value.replace('_', ' ').replace('-', ' ').toCharArray()) {
            if (Character.isWhitespace(c)) { out.append(c); upper = true; }
            else { out.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return out.toString();
    }
    private static double finiteClamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
