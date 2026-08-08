package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.NpcLabelSyncPayload;

/** Client-only immutable snapshot used by the NPC overhead label renderer. */
public final class NpcLabelClientState {
    private static volatile List<NpcLabelSyncPayload.Entry> entries = List.of();

    private NpcLabelClientState() {}

    public static void apply(NpcLabelSyncPayload payload) {
        entries = payload == null ? List.of() : payload.entries();
    }

    public static List<NpcLabelSyncPayload.Entry> entries() { return entries; }

    public static String definitionIdForEntity(int entityId) {
        for (NpcLabelSyncPayload.Entry entry : entries) if (entry.entityId() == entityId) return entry.definitionId();
        return "";
    }

    public static void clear() { entries = List.of(); }
}
