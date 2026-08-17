package be.winnetrie.mod.simpleserverutilities.npc;

/** One ordered, condition-aware action in an NPC combat pattern. */
public final class NpcAttackPatternStep {
    public static final int MAX_STEPS = 24;

    public boolean enabled = true;
    public String action = NpcAttackPatternAction.MELEE.id();
    /** Required only for Ability actions. */
    public String abilityId = "";
    /** Blank means every phase. */
    public String phaseId = "";
    public double minRange = 0.0D;
    public double maxRange = 4.0D;
    /** Step may run only while the NPC's own health percentage is inside this interval. */
    public double minHealthPercent = 0.0D;
    public double maxHealthPercent = 100.0D;

    public NpcAttackPatternStep normalize() {
        action = NpcAttackPatternAction.parse(action).id();
        abilityId = sanitizeOptionalId(abilityId, 48);
        phaseId = sanitizeOptionalId(phaseId, 48);
        minRange = finiteClamp(minRange, 0.0D, 128.0D, 0.0D);
        maxRange = finiteClamp(maxRange, minRange, 128.0D,
                NpcAttackPatternAction.parse(action) == NpcAttackPatternAction.MELEE ? Math.max(4.0D, minRange) : 128.0D);
        minHealthPercent = finiteClamp(minHealthPercent, 0.0D, 100.0D, 0.0D);
        maxHealthPercent = finiteClamp(maxHealthPercent, minHealthPercent, 100.0D, 100.0D);
        if (NpcAttackPatternAction.parse(action) == NpcAttackPatternAction.MELEE) abilityId = "";
        return this;
    }

    public NpcAttackPatternStep copy() {
        NpcAttackPatternStep copy = new NpcAttackPatternStep();
        copy.enabled = enabled;
        copy.action = action;
        copy.abilityId = abilityId;
        copy.phaseId = phaseId;
        copy.minRange = minRange;
        copy.maxRange = maxRange;
        copy.minHealthPercent = minHealthPercent;
        copy.maxHealthPercent = maxHealthPercent;
        return copy.normalize();
    }

    public NpcAttackPatternAction actionType() { return NpcAttackPatternAction.parse(action); }

    public boolean matches(NpcBossPhase phase, double distance, double healthPercent) {
        if (!enabled) return false;
        if (!phaseId.isBlank() && (phase == null || !phaseId.equals(phase.id))) return false;
        return distance + 1.0E-6D >= minRange && distance <= maxRange + 1.0E-6D
                && healthPercent + 1.0E-6D >= minHealthPercent
                && healthPercent <= maxHealthPercent + 1.0E-6D;
    }

    private static String sanitizeOptionalId(String raw, int max) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
