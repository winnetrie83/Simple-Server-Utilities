package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;

import net.minecraft.resources.ResourceLocation;

/** Arathi Basin-inspired two-team capture-node rules. */
public final class DominationRules {
    public int scoreToWin = 1_000;
    /** Seconds the player must remain still while right-click claiming a node. */
    public int claimCastSeconds = 5;
    /** Seconds between a successful claim cast and actual ownership transfer. */
    public int captureDelaySeconds = 30;
    /** Legacy proximity-capture setting retained for schema migration only. */
    public int captureSeconds = 10;
    public double captureRadius = 4.5D;
    public int scoreIntervalSeconds = 2;
    public int pointsPerNode = 10;
    public String weaponItem = "minecraft:iron_sword";
    public boolean allowFriendlyFire;
    public String team1Name = "Red Team";
    public String team2Name = "Blue Team";
    public int team1Color = 0xE53935;
    public int team2Color = 0x3F51B5;
    public MinigameBoostRules boosts = new MinigameBoostRules();
    public MinigameRoleRules roles = new MinigameRoleRules();
    public String neutralBannerBlock = "minecraft:white_banner";
    public String team1BannerBlock = "minecraft:red_banner";
    public String team2BannerBlock = "minecraft:blue_banner";

    public void normalize() {
        scoreToWin = Math.max(10, Math.min(1_000_000, scoreToWin));
        claimCastSeconds = Math.max(1, Math.min(60, claimCastSeconds));
        captureDelaySeconds = Math.max(1, Math.min(900, captureDelaySeconds));
        captureSeconds = Math.max(1, Math.min(300, captureSeconds));
        captureRadius = Math.max(1.0D, Math.min(32.0D, captureRadius));
        scoreIntervalSeconds = Math.max(1, Math.min(60, scoreIntervalSeconds));
        pointsPerNode = Math.max(1, Math.min(10_000, pointsPerNode));
        weaponItem = identifier(weaponItem, "minecraft:iron_sword");
        neutralBannerBlock = identifier(neutralBannerBlock, "minecraft:white_banner");
        team1BannerBlock = identifier(team1BannerBlock, "minecraft:red_banner");
        team2BannerBlock = identifier(team2BannerBlock, "minecraft:blue_banner");
        team1Name = bound(team1Name, 32, "Red Team");
        team2Name = bound(team2Name, 32, "Blue Team");
        team1Color &= 0xFFFFFF;
        team2Color &= 0xFFFFFF;
        if (boosts == null) boosts = new MinigameBoostRules();
        boosts.normalize();
        if (roles == null) roles = new MinigameRoleRules();
        roles.normalize();
    }

    public String teamName(int team) { return team == 2 ? team2Name : team1Name; }
    public int color(int team) { return team == 2 ? team2Color : team1Color; }
    public String bannerBlock(int owner) {
        return owner == 1 ? team1BannerBlock : owner == 2 ? team2BannerBlock : neutralBannerBlock;
    }

    private static String identifier(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim().toLowerCase(Locale.ROOT);
        ResourceLocation.parse(value);
        return value;
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
