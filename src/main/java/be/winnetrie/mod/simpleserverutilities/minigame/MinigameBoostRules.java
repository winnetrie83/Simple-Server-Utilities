package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared server-authoritative boost configuration for CTF and Domination. */
public final class MinigameBoostRules {
    public boolean enabled;
    /** manual uses arena.boostSpawns; automatic derives safe positions at match start. */
    public String placementMode = "manual";
    public int maximumActive = 2;
    public int initialSpawnDelaySeconds = 20;
    public int respawnMinSeconds = 20;
    public int respawnMaxSeconds = 40;
    /** Minimum Euclidean distance between simultaneously active boosts. */
    public double minimumSpacing = 4.0D;

    public boolean speedEnabled = true;
    public int speedDurationSeconds = 10;
    public int speedColor = 0x29B6F6;

    public boolean regenerationEnabled = true;
    public int regenerationDurationSeconds = 8;
    public int regenerationColor = 0xEC407A;

    public boolean armorEnabled = true;
    public int armorDurationSeconds = 12;
    public int armorColor = 0xB0BEC5;
    /** Vanilla armor points added to the player's base armor attribute. */
    public double armorPoints = 6.0D;

    public boolean jumpEnabled = true;
    public int jumpDurationSeconds = 10;
    public int jumpColor = 0x66BB6A;

    public void normalize() {
        placementMode = normalizeMode(placementMode);
        maximumActive = Math.max(1, Math.min(16, maximumActive));
        initialSpawnDelaySeconds = Math.max(0, Math.min(3_600, initialSpawnDelaySeconds));
        respawnMinSeconds = Math.max(1, Math.min(3_600, respawnMinSeconds));
        respawnMaxSeconds = Math.max(respawnMinSeconds, Math.min(3_600, respawnMaxSeconds));
        if (!Double.isFinite(minimumSpacing)) minimumSpacing = 4.0D;
        minimumSpacing = Math.max(1.0D, Math.min(32.0D, minimumSpacing));
        speedDurationSeconds = duration(speedDurationSeconds);
        regenerationDurationSeconds = duration(regenerationDurationSeconds);
        armorDurationSeconds = duration(armorDurationSeconds);
        jumpDurationSeconds = duration(jumpDurationSeconds);
        speedColor = color(speedColor, 0x29B6F6);
        regenerationColor = color(regenerationColor, 0xEC407A);
        armorColor = color(armorColor, 0xB0BEC5);
        jumpColor = color(jumpColor, 0x66BB6A);
        if (!Double.isFinite(armorPoints)) armorPoints = 6.0D;
        armorPoints = Math.max(1.0D, Math.min(20.0D, armorPoints));
    }

    public boolean automatic() { return "automatic".equals(placementMode); }

    public List<MinigameBoostType> enabledTypes() {
        ArrayList<MinigameBoostType> result = new ArrayList<>();
        if (speedEnabled) result.add(MinigameBoostType.SPEED);
        if (regenerationEnabled) result.add(MinigameBoostType.REGENERATION);
        if (armorEnabled) result.add(MinigameBoostType.ARMOR);
        if (jumpEnabled) result.add(MinigameBoostType.JUMP);
        return List.copyOf(result);
    }

    public int durationSeconds(MinigameBoostType type) {
        return switch (type) {
            case SPEED -> speedDurationSeconds;
            case REGENERATION -> regenerationDurationSeconds;
            case ARMOR -> armorDurationSeconds;
            case JUMP -> jumpDurationSeconds;
        };
    }

    public int color(MinigameBoostType type) {
        return switch (type) {
            case SPEED -> speedColor;
            case REGENERATION -> regenerationColor;
            case ARMOR -> armorColor;
            case JUMP -> jumpColor;
        };
    }

    private static String normalizeMode(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "automatic".equals(value) || "auto".equals(value) ? "automatic" : "manual";
    }

    private static int duration(int value) { return Math.max(1, Math.min(600, value)); }
    private static int color(int value, int fallback) {
        int normalized = value & 0x00FFFFFF;
        return normalized == 0 ? fallback : normalized;
    }
}
