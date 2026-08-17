package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** Action families supported by the data-driven SSU combat pattern sequencer. */
public enum NpcAttackPatternAction {
    MELEE("melee", "Melee"),
    ABILITY("ability", "Ability");

    private final String id;
    private final String label;

    NpcAttackPatternAction(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }
    public NpcAttackPatternAction next() {
        NpcAttackPatternAction[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static NpcAttackPatternAction parse(String raw) {
        if (raw == null) return MELEE;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (NpcAttackPatternAction action : values()) if (action.id.equals(normalized)) return action;
        return MELEE;
    }
}
