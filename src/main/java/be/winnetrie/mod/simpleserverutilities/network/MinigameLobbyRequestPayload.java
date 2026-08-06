package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded player/admin request for the minigame lobby. */
public record MinigameLobbyRequestPayload(
        String action,
        String minigameId,
        String preferredRole,
        String contextMinigameId,
        long requestId
) implements CustomPacketPayload {
    public static final Type<MinigameLobbyRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_lobby_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameLobbyRequestPayload> STREAM_CODEC =
            StreamCodec.of(MinigameLobbyRequestPayload::encode, MinigameLobbyRequestPayload::decode);

    public MinigameLobbyRequestPayload(String action, String minigameId, long requestId) {
        this(action, minigameId, "", "", requestId);
    }

    public MinigameLobbyRequestPayload(String action, String minigameId, String preferredRole, long requestId) {
        this(action, minigameId, preferredRole, "", requestId);
    }

    public MinigameLobbyRequestPayload {
        action = PayloadBounds.trimmedString(action, 32);
        minigameId = PayloadBounds.trimmedString(minigameId, 128);
        preferredRole = PayloadBounds.trimmedString(preferredRole, 16);
        contextMinigameId = PayloadBounds.trimmedString(contextMinigameId, 128);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameLobbyRequestPayload payload) {
        buffer.writeUtf(payload.action, 32);
        buffer.writeUtf(payload.minigameId, 128);
        buffer.writeUtf(payload.preferredRole, 16);
        buffer.writeUtf(payload.contextMinigameId, 128);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameLobbyRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameLobbyRequestPayload(buffer.readUtf(32), buffer.readUtf(128),
                buffer.readUtf(16), buffer.readUtf(128), buffer.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
