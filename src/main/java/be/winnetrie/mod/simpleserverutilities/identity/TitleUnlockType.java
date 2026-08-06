package be.winnetrie.mod.simpleserverutilities.identity;

/** Server-authoritative ways in which a player can unlock a visible title. */
public enum TitleUnlockType {
    FREE,
    MINIGAME_LEVEL,
    MINIGAME_WINS,
    RANK,
    PERMISSION,
    MANUAL;

    public TitleUnlockType next() {
        TitleUnlockType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
