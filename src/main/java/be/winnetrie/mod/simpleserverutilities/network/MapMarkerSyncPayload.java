package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Personal marker snapshot plus effective display settings. */
public record MapMarkerSyncPayload(
        boolean showOnWorldMap,
        boolean showInWorld,
        boolean showOnMinimap,
        boolean showBeams,
        int beamDistance,
        List<Entry> markers
) implements CustomPacketPayload {
    private static final int MAX_MARKERS = 256;

    public static final Type<MapMarkerSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "map_marker_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapMarkerSyncPayload> STREAM_CODEC =
            StreamCodec.of(MapMarkerSyncPayload::encode, MapMarkerSyncPayload::decode);

    public MapMarkerSyncPayload {
        beamDistance = Math.max(16, Math.min(512, beamDistance));
        markers = markers == null ? List.of() : List.copyOf(markers);
        if (markers.size() > MAX_MARKERS) throw new IllegalArgumentException("Too many map markers.");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MapMarkerSyncPayload payload) {
        buffer.writeBoolean(payload.showOnWorldMap());
        buffer.writeBoolean(payload.showInWorld());
        buffer.writeBoolean(payload.showOnMinimap());
        buffer.writeBoolean(payload.showBeams());
        buffer.writeVarInt(payload.beamDistance());
        buffer.writeVarInt(payload.markers().size());
        for (Entry marker : payload.markers()) {
            buffer.writeUUID(marker.id());
            buffer.writeUtf(marker.name(), 40);
            buffer.writeUtf(marker.dimension(), 128);
            buffer.writeInt(marker.x());
            buffer.writeInt(marker.y());
            buffer.writeInt(marker.z());
            buffer.writeInt(marker.colorArgb());
        }
    }

    private static MapMarkerSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean showOnWorldMap = buffer.readBoolean();
        boolean showInWorld = buffer.readBoolean();
        boolean showOnMinimap = buffer.readBoolean();
        boolean showBeams = buffer.readBoolean();
        int beamDistance = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_MARKERS) throw new IllegalArgumentException("Invalid map marker count " + size);
        List<Entry> markers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            markers.add(new Entry(
                    buffer.readUUID(), buffer.readUtf(40), buffer.readUtf(128),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt()));
        }
        return new MapMarkerSyncPayload(showOnWorldMap, showInWorld, showOnMinimap, showBeams, beamDistance, markers);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(UUID id, String name, String dimension, int x, int y, int z, int colorArgb) {
        public Entry {
            id = id == null ? new UUID(0L, 0L) : id;
            name = limit(name == null || name.isBlank() ? "Marker" : name, 40);
            dimension = limit(dimension == null ? "" : dimension, 128);
            colorArgb = 0xFF000000 | (colorArgb & 0x00FFFFFF);
        }
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
