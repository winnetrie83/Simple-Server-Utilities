package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.network.NpcLabelSyncPayload;

/** Client-only immutable snapshot used by NPC labels and per-entity appearance lookups. */
public final class NpcLabelClientState {
    private static volatile List<NpcLabelSyncPayload.Entry> entries = List.of();
    private static volatile Map<Integer, String> definitionIds = Map.of();

    private NpcLabelClientState() {}

    public static void apply(NpcLabelSyncPayload payload) {
        List<NpcLabelSyncPayload.Entry> next = payload == null ? List.of() : payload.entries();
        HashMap<Integer, String> ids = new HashMap<>(Math.max(16, next.size() * 2));
        for (NpcLabelSyncPayload.Entry entry : next) ids.put(entry.entityId(), entry.definitionId());
        entries = next;
        definitionIds = Map.copyOf(ids);
    }

    public static List<NpcLabelSyncPayload.Entry> entries() { return entries; }

    /** Hot render-path lookup; kept O(1) because this is queried for every living render state. */
    public static String definitionIdForEntity(int entityId) {
        return definitionIds.getOrDefault(entityId, "");
    }

    public static void clear() {
        entries = List.of();
        definitionIds = Map.of();
    }
}
