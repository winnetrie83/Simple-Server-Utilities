package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests a server-authoritative tree/vein preview for the block under the crosshair. */
public record UtilityMiningPreviewRequestPayload(long packedBlockPos) implements CustomPacketPayload {

    public static final Type<UtilityMiningPreviewRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "utility_mining_preview_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UtilityMiningPreviewRequestPayload> STREAM_CODEC =
            StreamCodec.of(UtilityMiningPreviewRequestPayload::encode, UtilityMiningPreviewRequestPayload::decode);

    public static UtilityMiningPreviewRequestPayload at(BlockPos pos) {
        return new UtilityMiningPreviewRequestPayload(pos.asLong());
    }

    public BlockPos blockPos() {
        return BlockPos.of(packedBlockPos);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, UtilityMiningPreviewRequestPayload payload) {
        buffer.writeLong(payload.packedBlockPos);
    }

    private static UtilityMiningPreviewRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new UtilityMiningPreviewRequestPayload(buffer.readLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
