package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SsuDimensionManagerRequestPayload(String selectedId, long requestId) implements CustomPacketPayload {
    public static final Type<SsuDimensionManagerRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "dimension_manager_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuDimensionManagerRequestPayload> STREAM_CODEC =
            StreamCodec.of(SsuDimensionManagerRequestPayload::encode, SsuDimensionManagerRequestPayload::decode);

    public SsuDimensionManagerRequestPayload {
        selectedId = PayloadBounds.string(selectedId, 128).trim();
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuDimensionManagerRequestPayload payload) {
        buffer.writeUtf(payload.selectedId, 128);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuDimensionManagerRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuDimensionManagerRequestPayload(buffer.readUtf(128), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
