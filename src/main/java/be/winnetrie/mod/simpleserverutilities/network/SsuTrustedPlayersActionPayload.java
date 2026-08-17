package be.winnetrie.mod.simpleserverutilities.network;

import java.util.Locale;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Adds or removes one trusted player from one owned claim. */
public record SsuTrustedPlayersActionPayload(
        String claim,
        String action,
        UUID playerId,
        String search,
        long requestId
) implements CustomPacketPayload {
    public static final Type<SsuTrustedPlayersActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "trusted_players_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuTrustedPlayersActionPayload> STREAM_CODEC =
            StreamCodec.of(SsuTrustedPlayersActionPayload::encode, SsuTrustedPlayersActionPayload::decode);

    public SsuTrustedPlayersActionPayload {
        claim = PayloadBounds.string(claim, 64).trim();
        action = PayloadBounds.string(action, 16).trim().toLowerCase(Locale.ROOT);
        playerId = playerId == null ? new UUID(0L, 0L) : playerId;
        search = PayloadBounds.string(search, 64).trim();
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuTrustedPlayersActionPayload payload) {
        buffer.writeUtf(payload.claim, 64);
        buffer.writeUtf(payload.action, 16);
        buffer.writeUUID(payload.playerId);
        buffer.writeUtf(payload.search, 64);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuTrustedPlayersActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuTrustedPlayersActionPayload(
                buffer.readUtf(64), buffer.readUtf(16), buffer.readUUID(), buffer.readUtf(64), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
