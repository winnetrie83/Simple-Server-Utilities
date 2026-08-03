package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Explicit administrator score adjustment request used by the Minigame Lobby GUI. */
public record MinigameScoreActionPayload(String mode, String playerName, long amount, long requestId)
        implements CustomPacketPayload {
    public static final Type<MinigameScoreActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_score_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameScoreActionPayload> STREAM_CODEC =
            StreamCodec.of(MinigameScoreActionPayload::encode, MinigameScoreActionPayload::decode);

    public MinigameScoreActionPayload {
        mode = PayloadBounds.trimmedString(mode, 8);
        playerName = PayloadBounds.trimmedString(playerName, 64);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameScoreActionPayload payload) {
        buffer.writeUtf(payload.mode, 8);
        buffer.writeUtf(payload.playerName, 64);
        buffer.writeLong(payload.amount);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameScoreActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameScoreActionPayload(
                buffer.readUtf(8), buffer.readUtf(64), buffer.readLong(), buffer.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
