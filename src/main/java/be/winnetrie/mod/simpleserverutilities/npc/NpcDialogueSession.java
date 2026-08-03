package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.UUID;

/** Short-lived server-authoritative dialogue session with replay protection. */
public record NpcDialogueSession(
        UUID sessionId,
        UUID playerId,
        UUID npcInstanceId,
        String dialogueId,
        String currentNode,
        long expiresAtTick,
        long sequence,
        long lastRequestId
) {
    public NpcDialogueSession advance(String node, long expiry, long requestId) {
        return new NpcDialogueSession(sessionId, playerId, npcInstanceId, dialogueId, node, expiry,
                sequence + 1L, Math.max(lastRequestId, requestId));
    }
}
