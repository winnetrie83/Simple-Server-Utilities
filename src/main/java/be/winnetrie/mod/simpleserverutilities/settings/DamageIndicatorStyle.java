package be.winnetrie.mod.simpleserverutilities.settings;

/** Personal visual style for floating damage/healing numbers. */
public enum DamageIndicatorStyle {
    FLOATING,
    HEARTS,
    COMPACT,
    POP,
    BURST,
    DROP;

    public DamageIndicatorStyle next() {
        DamageIndicatorStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
