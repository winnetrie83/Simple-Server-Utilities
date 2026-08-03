package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MinigameEditorSubmitPayload(String originalMinigameId, String definitionJson, long requestId)
        implements CustomPacketPayload {
    public static final Type<MinigameEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(MinigameEditorSubmitPayload::encode, MinigameEditorSubmitPayload::decode);

    public MinigameEditorSubmitPayload {
        originalMinigameId = PayloadBounds.string(originalMinigameId, 64);
        definitionJson = PayloadBounds.string(definitionJson, 65_535);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameEditorSubmitPayload payload) {
        buffer.writeUtf(payload.originalMinigameId, 64);
        buffer.writeUtf(payload.definitionJson, 65_535);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameEditorSubmitPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameEditorSubmitPayload(buffer.readUtf(64), buffer.readUtf(65_535), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
