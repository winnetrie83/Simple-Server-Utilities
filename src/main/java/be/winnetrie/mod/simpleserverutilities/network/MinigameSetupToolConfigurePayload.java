package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Selects the target/action used by the server-authoritative Minigame Setup Tool. */
public record MinigameSetupToolConfigurePayload(
        String operation,
        String minigameId,
        String arenaId,
        String action,
        int team,
        int index,
        long requestId
) implements CustomPacketPayload {
    public static final Type<MinigameSetupToolConfigurePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_setup_configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameSetupToolConfigurePayload> STREAM_CODEC =
            StreamCodec.of(MinigameSetupToolConfigurePayload::encode, MinigameSetupToolConfigurePayload::decode);

    public MinigameSetupToolConfigurePayload {
        operation = PayloadBounds.string(operation, 32);
        minigameId = PayloadBounds.string(minigameId, 64);
        arenaId = PayloadBounds.string(arenaId, 64);
        action = PayloadBounds.string(action, 48);
        team = Math.max(1, Math.min(16, team));
        index = Math.max(0, Math.min(63, index));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameSetupToolConfigurePayload payload) {
        buffer.writeUtf(payload.operation, 32);
        buffer.writeUtf(payload.minigameId, 64);
        buffer.writeUtf(payload.arenaId, 64);
        buffer.writeUtf(payload.action, 48);
        buffer.writeVarInt(payload.team);
        buffer.writeVarInt(payload.index);
        buffer.writeVarLong(payload.requestId);
    }

    private static MinigameSetupToolConfigurePayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameSetupToolConfigurePayload(buffer.readUtf(32), buffer.readUtf(64), buffer.readUtf(64),
                buffer.readUtf(48), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
