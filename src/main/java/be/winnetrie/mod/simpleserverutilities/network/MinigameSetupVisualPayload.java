package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Administrator-only in-world previews for minigame setup locations and configured play areas. */
public record MinigameSetupVisualPayload(boolean visible, List<Entry> markers, List<Bounds> bounds)
        implements CustomPacketPayload {
    public static final int MAX_MARKERS = 192;
    public static final int MAX_BOUNDS = 3;
    public static final Type<MinigameSetupVisualPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_setup_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameSetupVisualPayload> STREAM_CODEC =
            StreamCodec.of(MinigameSetupVisualPayload::encode, MinigameSetupVisualPayload::decode);

    public MinigameSetupVisualPayload {
        ArrayList<Entry> safeMarkers = new ArrayList<>();
        if (markers != null) {
            for (Entry entry : markers) {
                if (entry == null) continue;
                safeMarkers.add(entry.normalized());
                if (safeMarkers.size() >= MAX_MARKERS) break;
            }
        }
        markers = List.copyOf(safeMarkers);

        ArrayList<Bounds> safeBounds = new ArrayList<>();
        if (bounds != null) {
            for (Bounds entry : bounds) {
                if (entry == null) continue;
                Bounds normalized = entry.normalized();
                if (!normalized.dimension().isBlank()) safeBounds.add(normalized);
                if (safeBounds.size() >= MAX_BOUNDS) break;
            }
        }
        bounds = List.copyOf(safeBounds);
    }

    public static MinigameSetupVisualPayload clear() {
        return new MinigameSetupVisualPayload(false, List.of(), List.of());
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
        buffer.writeVarInt(payload.bounds.size());
        for (Bounds entry : payload.bounds) {
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeVarInt(entry.minX);
            buffer.writeVarInt(entry.minY);
            buffer.writeVarInt(entry.minZ);
            buffer.writeVarInt(entry.maxX);
            buffer.writeVarInt(entry.maxY);
            buffer.writeVarInt(entry.maxZ);
            buffer.writeUtf(entry.label, 96);
            buffer.writeInt(entry.color);
            buffer.writeByte(entry.kind);
        }
    }

    private static MinigameSetupVisualPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        int markerCount = readBoundedCount(buffer, MAX_MARKERS, "marker");
        ArrayList<Entry> entries = new ArrayList<>(markerCount);
        for (int index = 0; index < markerCount; index++) {
            entries.add(new Entry(buffer.readUtf(128), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readUtf(96), buffer.readInt(), buffer.readByte()));
        }
        int boundsCount = readBoundedCount(buffer, MAX_BOUNDS, "bounds");
        ArrayList<Bounds> bounds = new ArrayList<>(boundsCount);
        for (int index = 0; index < boundsCount; index++) {
            bounds.add(new Bounds(buffer.readUtf(128), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(96), buffer.readInt(), buffer.readByte()));
        }
        return new MinigameSetupVisualPayload(visible, entries, bounds);
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid minigame setup " + label + " count: " + count);
        }
        return count;
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
        public static final byte BOOST = 6;

        private Entry normalized() {
            return new Entry(PayloadBounds.string(dimension, 128), x, y, z,
                    PayloadBounds.string(label, 96), color & 0x00FFFFFF,
                    (byte) Math.max(LOBBY, Math.min(BOOST, kind)));
        }
    }

    public record Bounds(String dimension, int minX, int minY, int minZ,
                         int maxX, int maxY, int maxZ, String label, int color, byte kind) {
        public static final byte GAME = 0;
        public static final byte SPECTATOR = 1;
        public static final byte SPLEEF_FLOOR = 2;

        private Bounds normalized() {
            int x1 = Math.min(minX, maxX), x2 = Math.max(minX, maxX);
            int y1 = Math.min(minY, maxY), y2 = Math.max(minY, maxY);
            int z1 = Math.min(minZ, maxZ), z2 = Math.max(minZ, maxZ);
            return new Bounds(PayloadBounds.string(dimension, 128), x1, y1, z1, x2, y2, z2,
                    PayloadBounds.string(label, 96), color & 0x00FFFFFF,
                    (byte) Math.max(GAME, Math.min(SPLEEF_FLOOR, kind)));
        }
    }
}
