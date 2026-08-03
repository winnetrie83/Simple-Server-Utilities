package be.winnetrie.mod.simpleserverutilities.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Data-driven region-based dungeon definition. */
public final class DungeonDefinition {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ARENAS = 32;
    public static final int MAX_STAGES = 128;
    public static final int MAX_REWARDS = 64;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "new_dungeon";
    public String displayName = "New Dungeon";
    public String description = "A new customized SSU dungeon.";
    public String iconItem = "minecraft:trial_key";
    public boolean enabled = true;
    public int minPlayers = 1;
    public int maxPlayers = 4;
    public int countdownSeconds = 10;
    public int timeLimitSeconds = 1_800;
    public int postRunSeconds = 8;
    public int livesPerPlayer = 3;
    public boolean automaticStart = true;
    public boolean allowLateJoin;
    public ContentCondition prerequisites = new ContentCondition();
    public List<ContentAction> participationRewards = new ArrayList<>();
    public List<ContentAction> completionRewards = new ArrayList<>();
    public List<ContentAction> failureRewards = new ArrayList<>();
    public List<DungeonStageDefinition> stages = new ArrayList<>();
    public List<DungeonArenaDefinition> arenas = new ArrayList<>();

    public DungeonDefinition() {
        stages.add(new DungeonStageDefinition());
        arenas.add(new DungeonArenaDefinition());
    }

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = ContentId.require(id, "Dungeon ID");
        displayName = bound(displayName, 128, id);
        description = bound(description, 8_192, "");
        iconItem = bound(iconItem, 128, "minecraft:trial_key").toLowerCase(Locale.ROOT);
        minPlayers = Math.max(1, Math.min(64, minPlayers));
        maxPlayers = Math.max(minPlayers, Math.min(64, maxPlayers));
        countdownSeconds = Math.max(0, Math.min(600, countdownSeconds));
        timeLimitSeconds = Math.max(0, Math.min(86_400, timeLimitSeconds));
        postRunSeconds = Math.max(0, Math.min(600, postRunSeconds));
        livesPerPlayer = Math.max(0, Math.min(100, livesPerPlayer));
        if (prerequisites == null) prerequisites = new ContentCondition();
        prerequisites.normalize();
        participationRewards = normalizeActions(participationRewards);
        completionRewards = normalizeActions(completionRewards);
        failureRewards = normalizeActions(failureRewards);
        ArrayList<DungeonStageDefinition> normalizedStages = new ArrayList<>();
        if (stages != null) {
            for (DungeonStageDefinition stage : stages) {
                if (stage == null) continue;
                stage.normalize(); normalizedStages.add(stage);
                if (normalizedStages.size() >= MAX_STAGES) break;
            }
        }
        stages = normalizedStages;
        ArrayList<DungeonArenaDefinition> normalizedArenas = new ArrayList<>();
        if (arenas != null) {
            for (DungeonArenaDefinition arena : arenas) {
                if (arena == null) continue;
                arena.normalize(); normalizedArenas.add(arena);
                if (normalizedArenas.size() >= MAX_ARENAS) break;
            }
        }
        arenas = normalizedArenas;
    }

    private static List<ContentAction> normalizeActions(List<ContentAction> raw) {
        ArrayList<ContentAction> values = new ArrayList<>();
        if (raw != null) for (ContentAction action : raw) {
            if (action == null) continue;
            values.add(action.normalize());
            if (values.size() >= MAX_REWARDS) break;
        }
        return values;
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
