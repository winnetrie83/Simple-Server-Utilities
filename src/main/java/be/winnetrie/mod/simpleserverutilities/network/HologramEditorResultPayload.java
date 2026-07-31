package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HologramEditorResultPayload(boolean successful, String message, long requestId)
        implements CustomPacketPayload {
    public static final Type<HologramEditorResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "hologram_editor_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HologramEditorResultPayload> STREAM_CODEC =
            StreamCodec.of(HologramEditorResultPayload::encode, HologramEditorResultPayload::decode);

    public HologramEditorResultPayload {
        message = message == null ? "" : message.length() <= 256 ? message : message.substring(0,256);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, HologramEditorResultPayload p) {
        b.writeBoolean(p.successful); b.writeUtf(p.message,256); b.writeVarLong(p.requestId);
    }

    private static HologramEditorResultPayload decode(RegistryFriendlyByteBuf b) {
        return new HologramEditorResultPayload(b.readBoolean(), b.readUtf(256), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
