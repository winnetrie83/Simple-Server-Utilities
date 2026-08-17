package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MinigameSelectionCreateResultPayload(boolean successful, String message, long requestId)
        implements CustomPacketPayload {
    public static final Type<MinigameSelectionCreateResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_selection_create_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameSelectionCreateResultPayload> STREAM_CODEC =
            StreamCodec.of(MinigameSelectionCreateResultPayload::encode, MinigameSelectionCreateResultPayload::decode);

    public MinigameSelectionCreateResultPayload {
        message = PayloadBounds.string(message, 512);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, MinigameSelectionCreateResultPayload p) {
        b.writeBoolean(p.successful);
        b.writeUtf(p.message, 512);
        b.writeVarLong(p.requestId);
    }

    private static MinigameSelectionCreateResultPayload decode(RegistryFriendlyByteBuf b) {
        return new MinigameSelectionCreateResultPayload(b.readBoolean(), b.readUtf(512), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
