package be.winnetrie.mod.simpleserverutilities.achievement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;
import be.winnetrie.mod.simpleserverutilities.content.objective.ContentObjectiveDefinition;

/** Administrator-defined, event-driven achievement. */
public final class AchievementDefinition {
    public static final int STORAGE_SCHEMA = 1;
    public static final int MAX_OBJECTIVES = 32;
    public static final int MAX_REWARDS = 32;

    public int schema = STORAGE_SCHEMA;
    public String id = "new_achievement";
    /** SSU rich-text encoded. */
    public String title = "New Achievement";
    /** SSU rich-text encoded. */
    public String info = "Describe this achievement.";
    public String category = "General";
    public String iconItem = "minecraft:nether_star";
    public boolean enabled = true;
    public boolean hidden;
    public boolean announce = true;
    public int sortWeight;
    public List<ContentObjectiveDefinition> objectives = new ArrayList<>();
    public List<ContentAction> rewards = new ArrayList<>();
    public long createdAtEpochMilli;
    public long updatedAtEpochMilli;

    public AchievementDefinition normalize() {
        if (schema > STORAGE_SCHEMA) throw new IllegalArgumentException("Achievement schema " + schema + " is newer than supported schema " + STORAGE_SCHEMA + ".");
        schema = STORAGE_SCHEMA;
        id = ContentId.require(id, "Achievement ID");
        title = AchievementRichText.normalizeTitle(title);
        if (AchievementRichText.plain(title).isBlank()) title = id;
        info = AchievementRichText.normalizeInfo(info);
        category = bound(category == null || category.isBlank() ? "General" : category.trim(), 64);
        iconItem = bound(iconItem == null || iconItem.isBlank() ? "minecraft:nether_star" : iconItem.trim().toLowerCase(Locale.ROOT), 128);
        sortWeight = Math.max(-1_000_000, Math.min(1_000_000, sortWeight));
        if (objectives == null || objectives.isEmpty()) {
            ContentObjectiveDefinition objective = new ContentObjectiveDefinition();
            objective.id = "objective_1";
            objectives = new ArrayList<>(List.of(objective));
        }
        if (objectives.size() > MAX_OBJECTIVES) throw new IllegalArgumentException("Achievement exceeds " + MAX_OBJECTIVES + " objectives.");
        ArrayList<ContentObjectiveDefinition> normalized = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ContentObjectiveDefinition objective : objectives) {
            if (objective == null) continue;
            objective.normalize();
            if (!ids.add(objective.id)) throw new IllegalArgumentException("Duplicate objective ID: " + objective.id);
            normalized.add(objective);
        }
        if (normalized.isEmpty()) throw new IllegalArgumentException("An achievement needs at least one objective.");
        objectives = normalized;
        if (rewards == null) rewards = new ArrayList<>();
        if (rewards.size() > MAX_REWARDS) throw new IllegalArgumentException("Achievement exceeds " + MAX_REWARDS + " rewards.");
        ArrayList<ContentAction> normalizedRewards = new ArrayList<>();
        for (ContentAction reward : rewards) if (reward != null) normalizedRewards.add(reward.normalize());
        rewards = normalizedRewards;
        long now = System.currentTimeMillis();
        if (createdAtEpochMilli <= 0L) createdAtEpochMilli = now;
        if (updatedAtEpochMilli <= 0L) updatedAtEpochMilli = createdAtEpochMilli;
        return this;
    }

    public AchievementDefinition copy() {
        AchievementDefinition c = new AchievementDefinition();
        c.schema=schema;c.id=id;c.title=title;c.info=info;c.category=category;c.iconItem=iconItem;c.enabled=enabled;c.hidden=hidden;c.announce=announce;c.sortWeight=sortWeight;
        c.objectives=objectives==null?new ArrayList<>():new ArrayList<>(objectives.stream().map(ContentObjectiveDefinition::copy).toList());
        c.rewards=rewards==null?new ArrayList<>():new ArrayList<>(rewards);
        c.createdAtEpochMilli=createdAtEpochMilli;c.updatedAtEpochMilli=updatedAtEpochMilli;return c;
    }
    private static String bound(String value,int max){return value.length()<=max?value:value.substring(0,max);}
}
