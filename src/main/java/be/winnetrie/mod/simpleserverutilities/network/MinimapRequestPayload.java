package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Lightweight client request for the player's current minimap snapshot. */
public record MinimapRequestPayload() implements CustomPacketPayload {

    public static final Type<MinimapRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minimap_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MinimapRequestPayload> STREAM_CODEC =
            StreamCodec.of(MinimapRequestPayload::encode, MinimapRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, MinimapRequestPayload payload) {
        // No client-controlled coordinates are needed; the server uses the requesting player.
    }

    private static MinimapRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinimapRequestPayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
