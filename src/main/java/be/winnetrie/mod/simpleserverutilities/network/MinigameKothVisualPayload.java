package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Translucent in-world King of the Hill dome state used in setup and live matches. */
public record MinigameKothVisualPayload(
        boolean visible,
        String dimension,
        double x,
        double y,
        double z,
        double radius,
        int rgb,
        String label
) implements CustomPacketPayload {
    public static final Type<MinigameKothVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_koth_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameKothVisualPayload> STREAM_CODEC =
            StreamCodec.of(MinigameKothVisualPayload::encode, MinigameKothVisualPayload::decode);

    public MinigameKothVisualPayload {
        dimension = PayloadBounds.string(dimension, 128);
        radius = Math.max(1.0D, Math.min(64.0D, radius));
        rgb &= 0x00FFFFFF;
        label = PayloadBounds.string(label, 96);
    }

    public static MinigameKothVisualPayload clear() {
        return new MinigameKothVisualPayload(false, "", 0, 0, 0, 1, 0xFFFFFF, "");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameKothVisualPayload payload) {
        buffer.writeBoolean(payload.visible);
        buffer.writeUtf(payload.dimension, 128);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeDouble(payload.radius);
        buffer.writeInt(payload.rgb);
        buffer.writeUtf(payload.label, 96);
    }

    private static MinigameKothVisualPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MinigameKothVisualPayload(buffer.readBoolean(), buffer.readUtf(128), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readUtf(96));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
