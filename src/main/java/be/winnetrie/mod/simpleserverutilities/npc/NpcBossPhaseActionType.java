package be.winnetrie.mod.simpleserverutilities.npc;

/** One-shot actions executed when a boss encounter enters a phase. */
public enum NpcBossPhaseActionType {
    ANNOUNCE("announce", "Announce", true, false),
    TRIGGER_ABILITY("trigger_ability", "Trigger ability", true, false),
    SPAWN_ADDS("spawn_adds", "Spawn adds", true, true),
    HEAL_PERCENT("heal_percent", "Heal %", false, true),
    THREAT_RESET("threat_reset", "Reset threat", false, false),
    FIXATE_RANDOM("fixate_random", "Fixate random player", false, true),
    DESPAWN_ADDS("despawn_adds", "Despawn adds", false, false);

    private final String id;
    private final String label;
    private final boolean usesValue;
    private final boolean usesAmount;

    NpcBossPhaseActionType(String id, String label, boolean usesValue, boolean usesAmount) {
        this.id = id; this.label = label; this.usesValue = usesValue; this.usesAmount = usesAmount;
    }

    public String id() { return id; }
    public String label() { return label; }
    public boolean usesValue() { return usesValue; }
    public boolean usesAmount() { return usesAmount; }
    public NpcBossPhaseActionType next() { return values()[(ordinal() + 1) % values().length]; }

    public static NpcBossPhaseActionType parse(String raw) {
        if (raw != null) for (NpcBossPhaseActionType value : values()) if (value.id.equalsIgnoreCase(raw)) return value;
        return ANNOUNCE;
    }
}
