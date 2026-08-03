package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.LinkedHashMap;
import java.util.Map;

/** Durable progress for one quest in one player's journal. */
public final class QuestProgress {
    public String questId = "";
    public String status = QuestStatus.ACTIVE.serializedName();
    public Map<String, Long> objectiveProgress = new LinkedHashMap<>();
    public long startedAtEpochMilli;
    public long updatedAtEpochMilli;
    public long completedAtEpochMilli;
    public int completionCount;

    public QuestProgress normalize(String fallbackQuestId) {
        questId = be.winnetrie.mod.simpleserverutilities.content.ContentId.require(
                questId == null || questId.isBlank() ? fallbackQuestId : questId, "Quest progress ID");
        status = QuestStatus.parse(status).serializedName();
        LinkedHashMap<String, Long> normalized = new LinkedHashMap<>();
        if (objectiveProgress != null) {
            for (Map.Entry<String, Long> entry : objectiveProgress.entrySet()) {
                String key = be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(entry.getKey());
                if (!key.isBlank()) normalized.put(key, Math.max(0L, entry.getValue() == null ? 0L : entry.getValue()));
            }
        }
        objectiveProgress = normalized;
        startedAtEpochMilli = Math.max(0L, startedAtEpochMilli);
        updatedAtEpochMilli = Math.max(startedAtEpochMilli, updatedAtEpochMilli);
        completedAtEpochMilli = Math.max(0L, completedAtEpochMilli);
        completionCount = Math.max(0, completionCount);
        return this;
    }

    public QuestStatus statusValue() { return QuestStatus.parse(status); }
    public void setStatus(QuestStatus value) { status = value.serializedName(); }
    public long amount(String objectiveId) { return objectiveProgress.getOrDefault(objectiveId, 0L); }
}
