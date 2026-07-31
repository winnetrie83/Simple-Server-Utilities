package be.winnetrie.mod.simpleserverutilities.network;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Create, edit or remotely delete one personal marker. */
public record MapMarkerActionPayload(
        String action,
        UUID markerId,
        String name,
        String dimension,
        int x,
        int y,
        int z,
        int colorArgb,
        boolean resolveSurfaceHeight
) implements CustomPacketPayload {
    public static final Type<MapMarkerActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "map_marker_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapMarkerActionPayload> STREAM_CODEC =
            StreamCodec.of(MapMarkerActionPayload::encode, MapMarkerActionPayload::decode);

    public MapMarkerActionPayload {
        action = limit(action == null ? "" : action.trim().toLowerCase(java.util.Locale.ROOT), 16);
        markerId = markerId == null ? new UUID(0L, 0L) : markerId;
        name = limit(name == null ? "" : name.trim(), 40);
        dimension = limit(dimension == null ? "" : dimension.trim(), 128);
        colorArgb = 0xFF000000 | (colorArgb & 0x00FFFFFF);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MapMarkerActionPayload payload) {
        buffer.writeUtf(payload.action(), 16);
        buffer.writeUUID(payload.markerId());
        buffer.writeUtf(payload.name(), 40);
        buffer.writeUtf(payload.dimension(), 128);
        buffer.writeInt(payload.x());
        buffer.writeInt(payload.y());
        buffer.writeInt(payload.z());
        buffer.writeInt(payload.colorArgb());
        buffer.writeBoolean(payload.resolveSurfaceHeight());
    }

    private static MapMarkerActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MapMarkerActionPayload(
                buffer.readUtf(16), buffer.readUUID(), buffer.readUtf(40), buffer.readUtf(128),
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readBoolean());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
