package be.winnetrie.mod.simpleserverutilities.minigame;

/** Safe built-in spell component. No commands or arbitrary scripts are supported. */
public record MinigameAbilityEffect(Type type, double amount, int durationTicks, int amplifier) {
    public enum Type { HEAL, SLOW, KNOCKBACK }

    public MinigameAbilityEffect {
        type = type == null ? Type.HEAL : type;
        if (!Double.isFinite(amount)) amount = 0.0D;
        amount = Math.max(0.0D, Math.min(1024.0D, amount));
        durationTicks = Math.max(0, Math.min(72_000, durationTicks));
        amplifier = Math.max(0, Math.min(255, amplifier));
    }
}
