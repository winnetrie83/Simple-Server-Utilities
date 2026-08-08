package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;
import net.minecraft.resources.Identifier;

/** Two-team King of the Hill objective rules. */
public final class KingOfTheHillRules {
    public int scoreToWin = 120;
    public double hillRadius = 6.0D;
    public int scoreIntervalSeconds = 1;
    public int pointsPerInterval = 1;
    /** STATIC: approximate seconds for a one-player advantage to push the marker from center to the edge of the 20% neutral zone. */
    public int controlSweepSeconds = 8;
    /** ROTATING: seconds each authored hill point remains active. */
    public int rotationIntervalSeconds = 60;
    /** ROTATING: advance warning before the active hill switches. */
    public int rotationWarningSeconds = 10;
    public String weaponItem = "minecraft:iron_sword";
    public boolean allowFriendlyFire;
    public String team1Name = "Red Team";
    public String team2Name = "Blue Team";
    public int team1Color = 0xE53935;
    public int team2Color = 0x3F51B5;

    public void normalize() {
        scoreToWin = Math.max(10, Math.min(1_000_000, scoreToWin));
        hillRadius = Math.max(1.5D, Math.min(32.0D, hillRadius));
        scoreIntervalSeconds = Math.max(1, Math.min(60, scoreIntervalSeconds));
        pointsPerInterval = Math.max(1, Math.min(10_000, pointsPerInterval));
        controlSweepSeconds = Math.max(2, Math.min(60, controlSweepSeconds));
        rotationIntervalSeconds = Math.max(15, Math.min(900, rotationIntervalSeconds));
        rotationWarningSeconds = Math.max(0, Math.min(rotationIntervalSeconds - 1, rotationWarningSeconds));
        weaponItem = identifier(weaponItem, "minecraft:iron_sword");
        team1Name = bound(team1Name, 32, "Red Team");
        team2Name = bound(team2Name, 32, "Blue Team");
        team1Color &= 0xFFFFFF;
        team2Color &= 0xFFFFFF;
    }

    public String teamName(int team) { return team == 2 ? team2Name : team1Name; }
    public int color(int team) { return team == 2 ? team2Color : team1Color; }

    private static String identifier(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim().toLowerCase(Locale.ROOT);
        Identifier.parse(value);
        return value;
    }
    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
