package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RegionEditorResultPayload(boolean successful, String message, long requestId)
        implements CustomPacketPayload {
    public static final Type<RegionEditorResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_editor_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionEditorResultPayload> STREAM_CODEC =
            StreamCodec.of(RegionEditorResultPayload::encode, RegionEditorResultPayload::decode);

    public RegionEditorResultPayload {
        message = message == null ? "" : message.length() <= 256 ? message : message.substring(0, 256);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, RegionEditorResultPayload p) {
        b.writeBoolean(p.successful());
        b.writeUtf(p.message(), 256);
        b.writeVarLong(p.requestId());
    }

    private static RegionEditorResultPayload decode(RegistryFriendlyByteBuf b) {
        return new RegionEditorResultPayload(b.readBoolean(), b.readUtf(256), b.readVarLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
