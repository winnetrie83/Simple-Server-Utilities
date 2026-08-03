package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One schema-versioned player quest journal. */
public final class PlayerQuestJournal {
    public static final int STORAGE_SCHEMA = 1;
    public int schema = STORAGE_SCHEMA;
    public String playerId = "";
    public String trackedQuestId = "";
    public Map<String, QuestProgress> quests = new LinkedHashMap<>();

    public PlayerQuestJournal normalize(UUID fallbackPlayerId) {
        schema = STORAGE_SCHEMA;
        UUID id;
        try { id = UUID.fromString(playerId); }
        catch (Exception ignored) { id = fallbackPlayerId; }
        if (id == null) throw new IllegalArgumentException("Quest journal player UUID is missing.");
        playerId = id.toString();
        trackedQuestId = be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(trackedQuestId);
        LinkedHashMap<String, QuestProgress> normalized = new LinkedHashMap<>();
        if (quests != null) {
            for (Map.Entry<String, QuestProgress> entry : quests.entrySet()) {
                if (entry.getValue() == null) continue;
                QuestProgress progress = entry.getValue().normalize(entry.getKey());
                normalized.put(progress.questId, progress);
            }
        }
        quests = normalized;
        if (!trackedQuestId.isBlank() && !quests.containsKey(trackedQuestId)) trackedQuestId = "";
        return this;
    }

    public UUID uuid() { return UUID.fromString(playerId); }
}
