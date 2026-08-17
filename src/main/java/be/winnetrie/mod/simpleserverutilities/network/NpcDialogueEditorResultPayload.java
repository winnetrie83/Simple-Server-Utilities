package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Result of a server-validated dialogue editor save. */
public record NpcDialogueEditorResultPayload(
        boolean successful,
        String message,
        String dialogueId,
        long requestId
) implements CustomPacketPayload {
    public static final Type<NpcDialogueEditorResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_dialogue_editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueEditorResultPayload> STREAM_CODEC =
            StreamCodec.of(NpcDialogueEditorResultPayload::encode, NpcDialogueEditorResultPayload::decode);

    public NpcDialogueEditorResultPayload {
        message = PayloadBounds.string(message, 512);
        dialogueId = PayloadBounds.string(dialogueId, 64);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcDialogueEditorResultPayload payload) {
        buffer.writeBoolean(payload.successful);
        buffer.writeUtf(payload.message, 512);
        buffer.writeUtf(payload.dialogueId, 64);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcDialogueEditorResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcDialogueEditorResultPayload(
                buffer.readBoolean(), buffer.readUtf(512), buffer.readUtf(64), buffer.readVarLong());
    }



    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
