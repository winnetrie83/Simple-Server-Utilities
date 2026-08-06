package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client request for previous/next living participant while spectating. */
public record MinigameSpectatorActionPayload(String action) implements CustomPacketPayload {
    public static final Type<MinigameSpectatorActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_spectator_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameSpectatorActionPayload> STREAM_CODEC =
            StreamCodec.of(MinigameSpectatorActionPayload::encode, MinigameSpectatorActionPayload::decode);

    public MinigameSpectatorActionPayload {
        action = PayloadBounds.trimmedString(action, 16);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameSpectatorActionPayload payload) {
        buffer.writeUtf(payload.action, 16);
    }

    private static MinigameSpectatorActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameSpectatorActionPayload(buffer.readUtf(16));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
