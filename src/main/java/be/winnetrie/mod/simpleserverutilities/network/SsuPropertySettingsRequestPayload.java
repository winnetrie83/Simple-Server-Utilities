package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuPropertySettingsRequestPayload(String kind, String target, long requestId)
        implements CustomPacketPayload {
    public static final Type<SsuPropertySettingsRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "property_settings_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPropertySettingsRequestPayload> STREAM_CODEC =
            StreamCodec.of(SsuPropertySettingsRequestPayload::encode, SsuPropertySettingsRequestPayload::decode);

    public SsuPropertySettingsRequestPayload {
        kind = PayloadBounds.string(kind, 16).trim().toLowerCase(java.util.Locale.ROOT);
        target = PayloadBounds.string(target, 64).trim();
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, SsuPropertySettingsRequestPayload p) {
        b.writeUtf(p.kind, 16); b.writeUtf(p.target, 64); b.writeVarLong(p.requestId);
    }
    private static SsuPropertySettingsRequestPayload decode(RegistryFriendlyByteBuf b) {
        return new SsuPropertySettingsRequestPayload(b.readUtf(16), b.readUtf(64), b.readVarLong());
    }
@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
