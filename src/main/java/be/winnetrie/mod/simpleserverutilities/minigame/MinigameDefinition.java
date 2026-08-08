package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Data-driven minigame definition. Actual game rules can build on this lifecycle. */
public final class MinigameDefinition {
    public static final int SCHEMA_VERSION = 21;
    public static final int MAX_ARENAS = 32;
    public static final int MAX_REWARDS = 64;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "new_minigame";
    public String displayName = "New Minigame";
    public String description = "A new SSU minigame.";
    public String iconItem = "minecraft:diamond_sword";
    public String gameType = MinigameGameType.GENERIC.id();
    public boolean enabled = true;
    public int minPlayers = 2;
    public int maxPlayers = 8;
    public int teamCount = 2;
    public int countdownSeconds = 10;
    public int matchDurationSeconds = 300;
    public int postGameSeconds = 8;
    /** Delay before CTF/Domination players return after being defeated. */
    public int respawnDelaySeconds = 5;
    public boolean automaticStart = true;
    /** Server-owned match loadouts are immutable unless an administrator explicitly disables this rule. */
    public boolean lockInventory = true;
    public boolean allowLateJoin;
    public String victoryMode = "highest_score";
    public ContentCondition prerequisites = new ContentCondition();
    /** Legacy dev1 action lists. They migrate into the structured reward sets on load. */
    public List<ContentAction> participationRewards = new ArrayList<>();
    public List<ContentAction> winnerRewards = new ArrayList<>();
    public MinigameRewardSet participationReward = new MinigameRewardSet();
    public MinigameRewardSet winnerReward = new MinigameRewardSet();
    public List<MinigameArenaDefinition> arenas = new ArrayList<>();
    public SpleefRules spleef = new SpleefRules();
    public CaptureTheFlagRules captureTheFlag = new CaptureTheFlagRules();
    public DominationRules domination = new DominationRules();
    public KingOfTheHillRules kingOfTheHill = new KingOfTheHillRules();
    public BlockPartyRules blockParty = new BlockPartyRules();
    public MinigameExperienceRules experience = new MinigameExperienceRules();

    public MinigameDefinition() {
        arenas.add(new MinigameArenaDefinition());
    }

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = ContentId.require(id, "Minigame ID");
        displayName = bound(displayName, 128, id);
        description = bound(description, 8_192, "");
        iconItem = bound(iconItem, 128, "minecraft:diamond_sword").toLowerCase(Locale.ROOT);
        MinigameGameType type = MinigameGameType.parse(gameType);
        gameType = type.id();
        minPlayers = Math.max(1, Math.min(64, minPlayers));
        maxPlayers = Math.max(minPlayers, Math.min(128, maxPlayers));
        teamCount = Math.max(1, Math.min(16, teamCount));
        if (type == MinigameGameType.SPLEEF) {
            maxPlayers = Math.min(16, Math.max(2, maxPlayers));
            minPlayers = Math.min(maxPlayers, Math.max(2, minPlayers));
            teamCount = maxPlayers;
            victoryMode = "last_team_standing";
            allowLateJoin = false;
        } else if (type == MinigameGameType.CAPTURE_THE_FLAG || type == MinigameGameType.DOMINATION
                || type == MinigameGameType.KING_OF_THE_HILL) {
            maxPlayers = Math.min(64, Math.max(2, maxPlayers));
            minPlayers = Math.min(maxPlayers, Math.max(2, minPlayers));
            teamCount = 2;
            victoryMode = "highest_score";
            allowLateJoin = false;
        } else if (type == MinigameGameType.BLOCK_PARTY) {
            maxPlayers = Math.min(32, Math.max(2, maxPlayers));
            minPlayers = Math.min(maxPlayers, Math.max(2, minPlayers));
            teamCount = maxPlayers;
            victoryMode = "last_team_standing";
            allowLateJoin = false;
        }
        countdownSeconds = Math.max(0, Math.min(600, countdownSeconds));
        matchDurationSeconds = Math.max(0, Math.min(86_400, matchDurationSeconds));
        postGameSeconds = Math.max(0, Math.min(600, postGameSeconds));
        respawnDelaySeconds = Math.max(1, Math.min(300, respawnDelaySeconds));
        victoryMode = normalizeVictoryMode(victoryMode);
        if (spleef == null) spleef = new SpleefRules();
        spleef.normalize();
        if (captureTheFlag == null) captureTheFlag = new CaptureTheFlagRules();
        captureTheFlag.normalize();
        if (domination == null) domination = new DominationRules();
        domination.normalize();
        if (kingOfTheHill == null) kingOfTheHill = new KingOfTheHillRules();
        kingOfTheHill.normalize();
        if (blockParty == null) blockParty = new BlockPartyRules();
        blockParty.normalize();
        if (experience == null) experience = new MinigameExperienceRules();
        experience.normalize();
        if (prerequisites == null) prerequisites = new ContentCondition();
        prerequisites.normalize();
        participationRewards = normalizeActions(participationRewards);
        winnerRewards = normalizeActions(winnerRewards);
        if (participationReward == null) participationReward = new MinigameRewardSet();
        if (winnerReward == null) winnerReward = new MinigameRewardSet();
        // dev1 stored every reward as a raw ContentAction. Preserve those definitions
        // by migrating them to the immediate-action part of the new mail-backed package.
        if (!participationRewards.isEmpty()) participationReward.directActions.addAll(participationRewards);
        if (!winnerRewards.isEmpty()) winnerReward.directActions.addAll(winnerRewards);
        participationRewards = new ArrayList<>();
        winnerRewards = new ArrayList<>();
        participationReward.normalize();
        winnerReward.normalize();
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
