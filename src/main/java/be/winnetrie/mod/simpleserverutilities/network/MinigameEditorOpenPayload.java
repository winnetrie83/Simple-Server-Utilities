package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MinigameEditorOpenPayload(String originalMinigameId, String definitionJson, long requestId)
        implements CustomPacketPayload {
    public static final Type<MinigameEditorOpenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(MinigameEditorOpenPayload::encode, MinigameEditorOpenPayload::decode);

    public MinigameEditorOpenPayload {
        originalMinigameId = PayloadBounds.string(originalMinigameId, 64);
        definitionJson = PayloadBounds.string(definitionJson, 65_535);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameEditorOpenPayload payload) {
        buffer.writeUtf(payload.originalMinigameId, 64);
        buffer.writeUtf(payload.definitionJson, 65_535);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameEditorOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameEditorOpenPayload(buffer.readUtf(64), buffer.readUtf(65_535), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
