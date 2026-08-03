package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Map;
import java.util.UUID;

/** Immutable runtime event envelope used by quests and other content modules. */
public record ContentEvent(
        UUID eventId,
        String type,
        UUID playerId,
        String sourceModule,
        String sourceId,
        String subject,
        long amount,
        Map<String, String> metadata,
        long createdAtEpochMilli
) {
    public ContentEvent {
        eventId = eventId == null ? UUID.randomUUID() : eventId;
        type = ContentId.require(type, "Event type");
        sourceModule = ContentId.normalize(sourceModule);
        sourceId = ContentId.normalize(sourceId);
        subject = subject == null ? "" : subject.trim();
        if (subject.length() > 512) subject = subject.substring(0, 512);
        metadata = ContentDataMap.normalize(metadata, 64, 512);
        createdAtEpochMilli = createdAtEpochMilli <= 0L ? System.currentTimeMillis() : createdAtEpochMilli;
    }

    public static ContentEvent player(
            String type, UUID playerId, String sourceModule, String sourceId,
            String subject, long amount, Map<String, String> metadata) {
        return new ContentEvent(UUID.randomUUID(), type, playerId, sourceModule, sourceId,
                subject, amount, metadata, System.currentTimeMillis());
    }
}
