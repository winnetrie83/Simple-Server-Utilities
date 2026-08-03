package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Reusable data-driven quest definition, independent from NPC Core. */
public final class QuestDefinition {
    public static final int STORAGE_SCHEMA = 1;
    public static final int MAX_OBJECTIVES = 32;
    public static final int MAX_REWARDS = 32;

    public int schema = STORAGE_SCHEMA;
    public String id = "new_quest";
    public String title = "New Quest";
    public String category = "General";
    public String description = "Describe this quest.";
    public String iconItem = "minecraft:book";
    public boolean enabled = true;
    public boolean hiddenUntilAvailable;
    public boolean repeatable;
    public long cooldownSeconds;
    public boolean allowAbandon = true;
    public boolean requireTurnIn;
    public ContentCondition prerequisites = new ContentCondition("always", Map.of(), List.of());
    public List<QuestObjectiveDefinition> objectives = new ArrayList<>();
    public List<ContentAction> rewards = new ArrayList<>();

    public QuestDefinition normalize() {
        schema = STORAGE_SCHEMA;
        id = ContentId.require(id, "Quest ID");
        title = limit(title == null || title.isBlank() ? id : title.trim(), 128);
        category = limit(category == null || category.isBlank() ? "General" : category.trim(), 64);
        description = limit(description == null ? "" : description.trim(), 8_192);
        iconItem = limit(iconItem == null || iconItem.isBlank() ? "minecraft:book" : iconItem.trim(), 128);
        cooldownSeconds = Math.max(0L, Math.min(31_536_000L, cooldownSeconds));
        prerequisites = prerequisites == null
                ? new ContentCondition("always", Map.of(), List.of()) : prerequisites.normalize();
        if (objectives == null || objectives.isEmpty()) {
            QuestObjectiveDefinition objective = new QuestObjectiveDefinition();
            objective.id = "objective_1";
            objectives = new ArrayList<>(List.of(objective));
        }
        if (objectives.size() > MAX_OBJECTIVES) {
            throw new IllegalArgumentException("Quest '" + id + "' exceeds " + MAX_OBJECTIVES + " objectives.");
        }
        ArrayList<QuestObjectiveDefinition> normalizedObjectives = new ArrayList<>();
        Set<String> objectiveIds = new LinkedHashSet<>();
        for (QuestObjectiveDefinition objective : objectives) {
            if (objective == null) continue;
            objective.normalize();
            if (!objectiveIds.add(objective.id)) {
                throw new IllegalArgumentException("Duplicate quest objective ID: " + objective.id);
            }
            normalizedObjectives.add(objective);
        }
        if (normalizedObjectives.isEmpty()) throw new IllegalArgumentException("A quest needs at least one objective.");
        objectives = normalizedObjectives;

        if (rewards != null && rewards.size() > MAX_REWARDS) {
            throw new IllegalArgumentException("Quest '" + id + "' exceeds " + MAX_REWARDS + " rewards.");
        }
        ArrayList<ContentAction> normalizedRewards = new ArrayList<>();
        if (rewards != null) {
            for (ContentAction reward : rewards) {
                if (reward != null) normalizedRewards.add(reward.normalize());
            }
        }
        rewards = normalizedRewards;
        return this;
    }

    public QuestDefinition copy() {
        QuestDefinition copy = new QuestDefinition();
        copy.schema = schema;
        copy.id = id;
        copy.title = title;
        copy.category = category;
        copy.description = description;
        copy.iconItem = iconItem;
        copy.enabled = enabled;
        copy.hiddenUntilAvailable = hiddenUntilAvailable;
        copy.repeatable = repeatable;
        copy.cooldownSeconds = cooldownSeconds;
        copy.allowAbandon = allowAbandon;
        copy.requireTurnIn = requireTurnIn;
        copy.prerequisites = prerequisites;
        copy.objectives = objectives.stream().map(QuestObjectiveDefinition::copy).toList();
        copy.rewards = new ArrayList<>(rewards);
        return copy;
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
