package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One short-lived kill/objective feed line. */
public record MinigameKillFeedPayload(String text, int color, int lifetimeTicks) implements CustomPacketPayload {
    public static final Type<MinigameKillFeedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_kill_feed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameKillFeedPayload> STREAM_CODEC =
            StreamCodec.of(MinigameKillFeedPayload::encode, MinigameKillFeedPayload::decode);

    public MinigameKillFeedPayload {
        text = PayloadBounds.string(text, 160);
        lifetimeTicks = Math.max(20, Math.min(400, lifetimeTicks));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameKillFeedPayload payload) {
        buffer.writeUtf(payload.text, 160);
        buffer.writeInt(payload.color);
        buffer.writeVarInt(payload.lifetimeTicks);
    }

    private static MinigameKillFeedPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameKillFeedPayload(buffer.readUtf(160), buffer.readInt(), buffer.readVarInt());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
