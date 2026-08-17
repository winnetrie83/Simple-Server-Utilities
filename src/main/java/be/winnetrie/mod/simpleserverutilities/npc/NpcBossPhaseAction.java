package be.winnetrie.mod.simpleserverutilities.npc;

/** Serializable one-shot phase-entry action. */
public final class NpcBossPhaseAction {
    public static final int MAX_ACTIONS_PER_PHASE = 8;

    public String type = NpcBossPhaseActionType.ANNOUNCE.id();
    /** Message, ability id or add NPC definition id depending on type. */
    public String value = "";
    /** Count for adds or percentage for healing. */
    public double amount = 1.0D;
    /** Spawn radius for adds. */
    public double radius = 4.0D;

    public NpcBossPhaseActionType actionType() { return NpcBossPhaseActionType.parse(type); }

    public NpcBossPhaseAction normalize() {
        type = actionType().id();
        value = limit(value == null ? "" : value.trim(), actionType() == NpcBossPhaseActionType.ANNOUNCE ? 160 : 64);
        amount = finiteClamp(amount, 0.0D, 100.0D, 1.0D);
        radius = finiteClamp(radius, 0.5D, 32.0D, 4.0D);
        if (actionType() == NpcBossPhaseActionType.SPAWN_ADDS) amount = Math.max(1.0D, Math.min(16.0D, Math.rint(amount)));
        if (actionType() == NpcBossPhaseActionType.HEAL_PERCENT) amount = Math.max(0.0D, Math.min(100.0D, amount));
        if (actionType() == NpcBossPhaseActionType.FIXATE_RANDOM) amount = Math.max(1.0D, Math.min(60.0D, Math.rint(amount)));
        return this;
    }

    public NpcBossPhaseAction copy() {
        NpcBossPhaseAction copy = new NpcBossPhaseAction();
        copy.type = type; copy.value = value; copy.amount = amount; copy.radius = radius;
        return copy.normalize();
    }

    public static NpcBossPhaseAction announce() {
        NpcBossPhaseAction action = new NpcBossPhaseAction();
        action.type = NpcBossPhaseActionType.ANNOUNCE.id();
        return action.normalize();
    }

    private static double finiteClamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
