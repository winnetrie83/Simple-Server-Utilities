package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Data-driven minigame definition. Actual game rules can build on this lifecycle. */
public final class MinigameDefinition {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ARENAS = 32;
    public static final int MAX_REWARDS = 64;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "new_minigame";
    public String displayName = "New Minigame";
    public String description = "A new SSU minigame.";
    public String iconItem = "minecraft:diamond_sword";
    public boolean enabled = true;
    public int minPlayers = 2;
    public int maxPlayers = 8;
    public int teamCount = 2;
    public int countdownSeconds = 10;
    public int matchDurationSeconds = 300;
    public int postGameSeconds = 8;
    public boolean automaticStart = true;
    public boolean allowLateJoin;
    public String victoryMode = "highest_score";
    public ContentCondition prerequisites = new ContentCondition();
    public List<ContentAction> participationRewards = new ArrayList<>();
    public List<ContentAction> winnerRewards = new ArrayList<>();
    public List<MinigameArenaDefinition> arenas = new ArrayList<>();

    public MinigameDefinition() {
        arenas.add(new MinigameArenaDefinition());
    }

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = ContentId.require(id, "Minigame ID");
        displayName = bound(displayName, 128, id);
        description = bound(description, 8_192, "");
        iconItem = bound(iconItem, 128, "minecraft:diamond_sword").toLowerCase(Locale.ROOT);
        minPlayers = Math.max(1, Math.min(64, minPlayers));
        maxPlayers = Math.max(minPlayers, Math.min(128, maxPlayers));
        teamCount = Math.max(1, Math.min(16, teamCount));
        countdownSeconds = Math.max(0, Math.min(600, countdownSeconds));
        matchDurationSeconds = Math.max(0, Math.min(86_400, matchDurationSeconds));
        postGameSeconds = Math.max(0, Math.min(600, postGameSeconds));
        victoryMode = normalizeVictoryMode(victoryMode);
        if (prerequisites == null) prerequisites = new ContentCondition();
        prerequisites.normalize();
        participationRewards = normalizeActions(participationRewards);
        winnerRewards = normalizeActions(winnerRewards);
        ArrayList<MinigameArenaDefinition> normalizedArenas = new ArrayList<>();
        if (arenas != null) {
            for (MinigameArenaDefinition arena : arenas) {
                if (arena == null) continue;
                arena.normalize();
                normalizedArenas.add(arena);
                if (normalizedArenas.size() >= MAX_ARENAS) break;
            }
        }
        arenas = normalizedArenas;
    }

    private static List<ContentAction> normalizeActions(List<ContentAction> raw) {
        ArrayList<ContentAction> values = new ArrayList<>();
        if (raw != null) {
            for (ContentAction action : raw) {
                if (action == null) continue;
                values.add(action.normalize());
                if (values.size() >= MAX_REWARDS) break;
            }
        }
        return values;
    }

    public static String normalizeVictoryMode(String raw) {
        String mode = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (mode) {
            case "last_team_standing", "manual" -> mode;
            default -> "highest_score";
        };
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
