package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.content.ContentDataMap;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;
import be.winnetrie.mod.simpleserverutilities.content.objective.ContentObjectiveDefinition;
import be.winnetrie.mod.simpleserverutilities.content.objective.ContentObjectiveMatcher;

/** One event-driven, independently tracked quest objective. */
public final class QuestObjectiveDefinition {
    public String id = "objective";
    public String description = "Complete the objective";
    public String eventType = "player_login";
    public String subject = "*";
    public long targetAmount = 1L;
    public Map<String, String> metadata = new LinkedHashMap<>();
    public boolean optional;

    public QuestObjectiveDefinition normalize() {
        id = ContentId.require(id, "Quest objective ID");
        description = limit(description == null || description.isBlank() ? id : description.trim(), 256);
        eventType = ContentId.require(eventType, "Quest objective event type");
        subject = limit(subject == null || subject.isBlank() ? "*" : subject.trim(), 512);
        targetAmount = Math.max(1L, Math.min(1_000_000_000L, targetAmount));
        metadata = new LinkedHashMap<>(ContentDataMap.normalize(metadata, 32, 256));
        return this;
    }

    /** Quest compatibility facade over the shared Content objective matcher. */
    public boolean matches(ContentEvent event) {
        return ContentObjectiveMatcher.matches(toContentObjective(), event);
    }

    public long increment(ContentEvent event) {
        if (!matches(event)) return 0L;
        long contribution = ContentObjectiveMatcher.contribution(toContentObjective(), event);
        // Preserve the legacy Quest behavior: zero/negative event amounts count as one occurrence.
        return contribution <= 0L ? 1L : contribution;
    }

    public ContentObjectiveDefinition toContentObjective() {
        ContentObjectiveDefinition objective = new ContentObjectiveDefinition();
        objective.id = id;
        objective.description = description;
        objective.eventType = eventType;
        objective.targetMode = "*".equals(subject) ? ContentObjectiveDefinition.TargetMode.ANY : ContentObjectiveDefinition.TargetMode.EXACT;
        objective.targets = "*".equals(subject) ? java.util.List.of() : java.util.List.of(subject);
        objective.aggregator = ContentObjectiveDefinition.Aggregator.SUM;
        objective.targetAmount = targetAmount;
        objective.metadata = new LinkedHashMap<>(metadata);
        objective.optional = optional;
        return objective.normalize();
    }

    public QuestObjectiveDefinition copy() {
        QuestObjectiveDefinition copy = new QuestObjectiveDefinition();
        copy.id = id;
        copy.description = description;
        copy.eventType = eventType;
        copy.subject = subject;
        copy.targetAmount = targetAmount;
        copy.metadata = new LinkedHashMap<>(metadata);
        copy.optional = optional;
        return copy;
    }

    public String summary() {
        String target = "*".equals(subject) ? "" : " [" + subject.toLowerCase(Locale.ROOT) + "]";
        return description + target + " × " + targetAmount;
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
