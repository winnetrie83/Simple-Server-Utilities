package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClaimMapRequestPayload(
        int centerChunkX,
        int centerChunkZ,
        int radius,
        String selectedClaimGroup,
        boolean centerOnSelectedClaim
) implements CustomPacketPayload {

    public static final Type<ClaimMapRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "claim_map_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimMapRequestPayload> STREAM_CODEC =
            StreamCodec.of(ClaimMapRequestPayload::encode, ClaimMapRequestPayload::decode);

    public ClaimMapRequestPayload {
        radius = Math.max(2, Math.min(radius, 12));
        selectedClaimGroup = selectedClaimGroup == null ? "" : selectedClaimGroup;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClaimMapRequestPayload payload) {
        buffer.writeVarInt(payload.centerChunkX);
        buffer.writeVarInt(payload.centerChunkZ);
        buffer.writeVarInt(payload.radius);
        buffer.writeUtf(payload.selectedClaimGroup, 64);
        buffer.writeBoolean(payload.centerOnSelectedClaim);
    }

    private static ClaimMapRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClaimMapRequestPayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
