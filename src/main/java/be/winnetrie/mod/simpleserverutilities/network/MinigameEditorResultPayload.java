package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MinigameEditorResultPayload(boolean successful, String message, String minigameId, long requestId)
        implements CustomPacketPayload {
    public static final Type<MinigameEditorResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameEditorResultPayload> STREAM_CODEC =
            StreamCodec.of(MinigameEditorResultPayload::encode, MinigameEditorResultPayload::decode);

    public MinigameEditorResultPayload {
        message = PayloadBounds.string(message, 512);
        minigameId = PayloadBounds.string(minigameId, 64);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameEditorResultPayload payload) {
        buffer.writeBoolean(payload.successful);
        buffer.writeUtf(payload.message, 512);
        buffer.writeUtf(payload.minigameId, 64);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameEditorResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameEditorResultPayload(buffer.readBoolean(), buffer.readUtf(512),
                buffer.readUtf(64), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
