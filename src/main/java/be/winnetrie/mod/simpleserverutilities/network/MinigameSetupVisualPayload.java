package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Administrator-only in-world previews for minigame setup locations. */
public record MinigameSetupVisualPayload(boolean visible, List<Entry> markers)
        implements CustomPacketPayload {
    public static final int MAX_MARKERS = 96;
    public static final Type<MinigameSetupVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_setup_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameSetupVisualPayload> STREAM_CODEC =
            StreamCodec.of(MinigameSetupVisualPayload::encode, MinigameSetupVisualPayload::decode);

    public MinigameSetupVisualPayload {
        ArrayList<Entry> safe = new ArrayList<>();
        if (markers != null) {
            for (Entry entry : markers) {
                if (entry == null) continue;
                safe.add(entry.normalized());
                if (safe.size() >= MAX_MARKERS) break;
            }
        }
        markers = List.copyOf(safe);
    }

    public static MinigameSetupVisualPayload clear() {
        return new MinigameSetupVisualPayload(false, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameSetupVisualPayload payload) {
        buffer.writeBoolean(payload.visible);
        buffer.writeVarInt(payload.markers.size());
        for (Entry entry : payload.markers) {
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeDouble(entry.x);
            buffer.writeDouble(entry.y);
            buffer.writeDouble(entry.z);
            buffer.writeUtf(entry.label, 96);
            buffer.writeInt(entry.color);
            buffer.writeByte(entry.kind);
        }
    }

    private static MinigameSetupVisualPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        int count = Math.max(0, Math.min(MAX_MARKERS, buffer.readVarInt()));
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readUtf(128), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readUtf(96), buffer.readInt(), buffer.readByte()));
        }
        return new MinigameSetupVisualPayload(visible, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String dimension, double x, double y, double z, String label, int color, byte kind) {
        public static final byte LOBBY = 0;
        public static final byte SPECTATOR = 1;
        public static final byte SPAWN = 2;
        public static final byte FLAG = 3;
        public static final byte NODE = 4;
        public static final byte NODE_SPAWN = 5;

        private Entry normalized() {
            return new Entry(PayloadBounds.string(dimension, 128), x, y, z,
                    PayloadBounds.string(label, 96), color & 0x00FFFFFF,
                    (byte) Math.max(LOBBY, Math.min(NODE_SPAWN, kind)));
        }
    }
}
