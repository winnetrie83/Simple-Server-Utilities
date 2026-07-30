package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests server-authoritative overlays for a world-map viewport. */
public record WorldMapRequestPayload(
        int centerChunkX,
        int centerChunkZ,
        int radius
) implements CustomPacketPayload {

    public static final Type<WorldMapRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "world_map_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldMapRequestPayload> STREAM_CODEC =
            StreamCodec.of(WorldMapRequestPayload::encode, WorldMapRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, WorldMapRequestPayload payload) {
        buffer.writeVarInt(payload.centerChunkX());
        buffer.writeVarInt(payload.centerChunkZ());
        buffer.writeVarInt(payload.radius());
    }

    private static WorldMapRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new WorldMapRequestPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
