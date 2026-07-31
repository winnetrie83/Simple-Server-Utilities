package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Result shown by marker editor/management screens. */
public record MapMarkerActionResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final Type<MapMarkerActionResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "map_marker_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapMarkerActionResultPayload> STREAM_CODEC =
            StreamCodec.of(MapMarkerActionResultPayload::encode, MapMarkerActionResultPayload::decode);

    public MapMarkerActionResultPayload {
        message = message == null ? "" : (message.length() <= 160 ? message : message.substring(0, 160));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MapMarkerActionResultPayload payload) {
        buffer.writeBoolean(payload.success());
        buffer.writeUtf(payload.message(), 160);
    }

    private static MapMarkerActionResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new MapMarkerActionResultPayload(buffer.readBoolean(), buffer.readUtf(160));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
