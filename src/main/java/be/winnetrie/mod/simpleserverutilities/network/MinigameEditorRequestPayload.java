package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MinigameEditorRequestPayload(String minigameId, long requestId) implements CustomPacketPayload {
    public static final Type<MinigameEditorRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_editor_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameEditorRequestPayload> STREAM_CODEC =
            StreamCodec.of(MinigameEditorRequestPayload::encode, MinigameEditorRequestPayload::decode);

    public MinigameEditorRequestPayload {
        minigameId = PayloadBounds.trimmedString(minigameId, 64);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameEditorRequestPayload payload) {
        buffer.writeUtf(payload.minigameId, 64);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameEditorRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameEditorRequestPayload(buffer.readUtf(64), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
