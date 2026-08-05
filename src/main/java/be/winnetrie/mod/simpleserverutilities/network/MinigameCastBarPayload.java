package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-only progress bar for interruptible minigame actions. */
public record MinigameCastBarPayload(boolean visible, String label, float progress, int color)
        implements CustomPacketPayload {
    public static final Type<MinigameCastBarPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_cast_bar"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameCastBarPayload> STREAM_CODEC =
            StreamCodec.of(MinigameCastBarPayload::encode, MinigameCastBarPayload::decode);

    public MinigameCastBarPayload {
        label = PayloadBounds.string(label, 96);
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        color &= 0x00FFFFFF;
    }

    public static MinigameCastBarPayload clear() {
        return new MinigameCastBarPayload(false, "", 0.0F, 0xFFFFFF);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameCastBarPayload payload) {
        buffer.writeBoolean(payload.visible);
        buffer.writeUtf(payload.label, 96);
        buffer.writeFloat(payload.progress);
        buffer.writeInt(payload.color);
    }

    private static MinigameCastBarPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameCastBarPayload(buffer.readBoolean(), buffer.readUtf(96),
                buffer.readFloat(), buffer.readInt());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
