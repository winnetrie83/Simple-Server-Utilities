package be.winnetrie.mod.simpleserverutilities.utilitymining;

public enum MiningActivationMode {
    KEYBIND,
    SNEAK;

    public MiningActivationMode next() {
        return this == KEYBIND ? SNEAK : KEYBIND;
    }
}
