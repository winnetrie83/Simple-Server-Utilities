package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens the initial region creation/settings editor for the current tool selection. */
public record RegionEditorOpenPayload(String dimension, long point1, long point2)
        implements CustomPacketPayload {
    public static final Type<RegionEditorOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_editor_open")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(RegionEditorOpenPayload::encode, RegionEditorOpenPayload::decode);

    public RegionEditorOpenPayload {
        dimension = dimension == null ? "" : limit(dimension, 256);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RegionEditorOpenPayload payload) {
        buffer.writeUtf(payload.dimension(), 256);
        buffer.writeLong(payload.point1());
        buffer.writeLong(payload.point2());
    }

    private static RegionEditorOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        return new RegionEditorOpenPayload(buffer.readUtf(256), buffer.readLong(), buffer.readLong());
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
