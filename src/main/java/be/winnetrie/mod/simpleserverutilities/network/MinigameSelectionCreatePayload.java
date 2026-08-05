package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Creates the first arena of a minigame from the administrator's active region selection. */
public record MinigameSelectionCreatePayload(
        String minigameId,
        String displayName,
        String gameType,
        int minPlayers,
        int maxPlayers,
        long requestId
) implements CustomPacketPayload {
    public static final Type<MinigameSelectionCreatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_selection_create"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameSelectionCreatePayload> STREAM_CODEC =
            StreamCodec.of(MinigameSelectionCreatePayload::encode, MinigameSelectionCreatePayload::decode);

    public MinigameSelectionCreatePayload {
        minigameId = PayloadBounds.trimmedString(minigameId, 64);
        displayName = PayloadBounds.trimmedString(displayName, 128);
        gameType = PayloadBounds.trimmedString(gameType, 32);
        minPlayers = Math.max(1, Math.min(64, minPlayers));
        maxPlayers = Math.max(minPlayers, Math.min(64, maxPlayers));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, MinigameSelectionCreatePayload p) {
        b.writeUtf(p.minigameId, 64);
        b.writeUtf(p.displayName, 128);
        b.writeUtf(p.gameType, 32);
        b.writeVarInt(p.minPlayers);
        b.writeVarInt(p.maxPlayers);
        b.writeVarLong(p.requestId);
    }

    private static MinigameSelectionCreatePayload decode(RegistryFriendlyByteBuf b) {
        return new MinigameSelectionCreatePayload(b.readUtf(64), b.readUtf(128), b.readUtf(32),
                b.readVarInt(), b.readVarInt(), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
