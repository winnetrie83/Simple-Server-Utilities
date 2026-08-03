package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests the trusted-player manager for one owned claim. */
public record SsuTrustedPlayersRequestPayload(String claim, String search, long requestId)
        implements CustomPacketPayload {
    public static final Type<SsuTrustedPlayersRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "trusted_players_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuTrustedPlayersRequestPayload> STREAM_CODEC =
            StreamCodec.of(SsuTrustedPlayersRequestPayload::encode, SsuTrustedPlayersRequestPayload::decode);

    public SsuTrustedPlayersRequestPayload {
        claim = PayloadBounds.string(claim, 64).trim();
        search = PayloadBounds.string(search, 64).trim();
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuTrustedPlayersRequestPayload payload) {
        buffer.writeUtf(payload.claim, 64);
        buffer.writeUtf(payload.search, 64);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuTrustedPlayersRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuTrustedPlayersRequestPayload(buffer.readUtf(64), buffer.readUtf(64), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
