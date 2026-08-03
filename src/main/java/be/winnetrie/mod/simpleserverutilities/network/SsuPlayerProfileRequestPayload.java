package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests the admin player browser and one bounded permission page. */
public record SsuPlayerProfileRequestPayload(
        String selectedPlayer,
        String playerQuery,
        int permissionPageIndex,
        int permissionPageSize,
        long requestId
) implements CustomPacketPayload {

    public static final int MAX_PERMISSION_PAGE_SIZE = 20;
    public static final Type<SsuPlayerProfileRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "player_profile_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPlayerProfileRequestPayload> STREAM_CODEC =
            StreamCodec.of(SsuPlayerProfileRequestPayload::encode, SsuPlayerProfileRequestPayload::decode);

    public SsuPlayerProfileRequestPayload {
        selectedPlayer = PayloadBounds.string(selectedPlayer, 64).trim();
        playerQuery = PayloadBounds.string(playerQuery, 64).trim();
        permissionPageIndex = Math.max(0, permissionPageIndex);
        permissionPageSize = Math.max(1, Math.min(MAX_PERMISSION_PAGE_SIZE, permissionPageSize));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuPlayerProfileRequestPayload payload) {
        buffer.writeUtf(payload.selectedPlayer, 64);
        buffer.writeUtf(payload.playerQuery, 64);
        buffer.writeVarInt(payload.permissionPageIndex);
        buffer.writeVarInt(payload.permissionPageSize);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuPlayerProfileRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuPlayerProfileRequestPayload(
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong()
        );
    }
@Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
