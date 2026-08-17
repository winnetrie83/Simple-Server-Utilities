package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact server-authoritative in-match scoreboard overlay. */
public record MinigameHudPayload(boolean visible, String title, List<String> lines)
        implements CustomPacketPayload {
    public static final int MAX_LINES = 10;
    public static final Type<MinigameHudPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameHudPayload> STREAM_CODEC =
            StreamCodec.of(MinigameHudPayload::encode, MinigameHudPayload::decode);

    public MinigameHudPayload {
        title = PayloadBounds.string(title, 96);
        lines = lines == null ? List.of() : lines.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> PayloadBounds.string(value, 128))
                .limit(MAX_LINES)
                .toList();
    }

    public static MinigameHudPayload clear() { return new MinigameHudPayload(false, "", List.of()); }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameHudPayload payload) {
        buffer.writeBoolean(payload.visible);
        buffer.writeUtf(payload.title, 96);
        buffer.writeVarInt(payload.lines.size());
        for (String line : payload.lines) buffer.writeUtf(line, 128);
    }

    private static MinigameHudPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        String title = buffer.readUtf(96);
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_LINES) throw new IllegalArgumentException("Invalid minigame HUD line count: " + size);
        ArrayList<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) lines.add(buffer.readUtf(128));
        return new MinigameHudPayload(visible, title, lines);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
