package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative current dialogue node and already-filtered choices. */
public record NpcDialogueViewPayload(
        boolean closed,
        String sessionId,
        String instanceId,
        String dialogueId,
        String nodeId,
        String npcName,
        String speaker,
        String text,
        String notice,
        boolean error,
        List<ChoiceEntry> choices
) implements CustomPacketPayload {
    public static final Type<NpcDialogueViewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_dialogue_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueViewPayload> STREAM_CODEC =
            StreamCodec.of(NpcDialogueViewPayload::encode, NpcDialogueViewPayload::decode);

    public NpcDialogueViewPayload {
        sessionId = PayloadBounds.string(sessionId, 36); instanceId = PayloadBounds.string(instanceId, 36);
        dialogueId = PayloadBounds.string(dialogueId, 64); nodeId = PayloadBounds.string(nodeId, 64);
        npcName = PayloadBounds.string(npcName, 64); speaker = PayloadBounds.string(speaker, 64);
        text = PayloadBounds.string(text, 4_096); notice = PayloadBounds.string(notice, 512);
        choices = choices == null ? List.of() : List.copyOf(choices.subList(0, Math.min(8, choices.size())));
    }

    public static NpcDialogueViewPayload closed(String sessionId, String message, boolean error) {
        return new NpcDialogueViewPayload(true, sessionId, "", "", "", "", "", "", message, error, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf b, NpcDialogueViewPayload p) {
        b.writeBoolean(p.closed); b.writeUtf(p.sessionId, 36); b.writeUtf(p.instanceId, 36);
        b.writeUtf(p.dialogueId, 64); b.writeUtf(p.nodeId, 64); b.writeUtf(p.npcName, 64);
        b.writeUtf(p.speaker, 64); b.writeUtf(p.text, 4_096); b.writeUtf(p.notice, 512); b.writeBoolean(p.error);
        b.writeVarInt(p.choices.size());
        for (ChoiceEntry choice : p.choices) {
            b.writeUtf(choice.id, 64); b.writeUtf(choice.text, 256);
            b.writeBoolean(choice.enabled); b.writeUtf(choice.lockReason, 256);
        }
    }

    private static NpcDialogueViewPayload decode(RegistryFriendlyByteBuf b) {
        boolean closed = b.readBoolean(); String session = b.readUtf(36); String instance = b.readUtf(36);
        String dialogue = b.readUtf(64); String node = b.readUtf(64); String npc = b.readUtf(64);
        String speaker = b.readUtf(64); String text = b.readUtf(4_096); String notice = b.readUtf(512); boolean error = b.readBoolean();
        int size = b.readVarInt();
        if (size < 0 || size > 8) throw new IllegalArgumentException("Invalid NPC dialogue choice count: " + size);
        ArrayList<ChoiceEntry> choices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) choices.add(new ChoiceEntry(b.readUtf(64), b.readUtf(256), b.readBoolean(), b.readUtf(256)));
        return new NpcDialogueViewPayload(closed, session, instance, dialogue, node, npc, speaker, text, notice, error, choices);
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record ChoiceEntry(String id, String text, boolean enabled, String lockReason) {
        public ChoiceEntry {
            id = PayloadBounds.string(id, 64); text = PayloadBounds.string(text, 256); lockReason = PayloadBounds.string(lockReason, 256);
        }
    }
}
