package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;

import net.minecraft.resources.Identifier;

/** Server-authoritative rules for the two-team Capture the Flag mode. */
public final class CaptureTheFlagRules {
    public int scoreToWin = 3;
    public double captureRadius = 2.5D;
    /** Seconds a player must remain still while taking the enemy flag. */
    public int flagTakeCastSeconds = 5;
    public boolean allowFriendlyFire;
    public String weaponItem = "minecraft:iron_sword";
    public String team1Name = "Red";
    public String team2Name = "Blue";
    public String team1FlagBlock = "minecraft:red_banner";
    public String team2FlagBlock = "minecraft:blue_banner";
    public int team1Color = 0xE53935;
    public int team2Color = 0x1E88E5;
    public MinigameBoostRules boosts = new MinigameBoostRules();
    public MinigameRoleRules roles = new MinigameRoleRules();

    public void normalize() {
        scoreToWin = Math.max(1, Math.min(100, scoreToWin));
        if (!Double.isFinite(captureRadius)) captureRadius = 2.5D;
        captureRadius = Math.max(0.75D, Math.min(12.0D, captureRadius));
        flagTakeCastSeconds = Math.max(1, Math.min(60, flagTakeCastSeconds));
        weaponItem = identifier(weaponItem, "minecraft:iron_sword");
        team1Name = bound(team1Name, 32, "Red");
        team2Name = bound(team2Name, 32, "Blue");
        team1FlagBlock = identifier(team1FlagBlock, "minecraft:red_banner");
        team2FlagBlock = identifier(team2FlagBlock, "minecraft:blue_banner");
        team1Color &= 0x00FFFFFF;
        team2Color &= 0x00FFFFFF;
        if (team1Color == 0) team1Color = 0xE53935;
        if (team2Color == 0) team2Color = 0x1E88E5;
        if (boosts == null) boosts = new MinigameBoostRules();
        boosts.normalize();
        if (roles == null) roles = new MinigameRoleRules();
        roles.normalize();
    }

    public String teamName(int team) { return team == 2 ? team2Name : team1Name; }
    public String flagBlock(int team) { return team == 2 ? team2FlagBlock : team1FlagBlock; }
    public int color(int team) { return team == 2 ? team2Color : team1Color; }

    private static String identifier(String value, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
        Identifier.parse(safe);
        return safe;
    }

    private static String bound(String value, int maximum, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
