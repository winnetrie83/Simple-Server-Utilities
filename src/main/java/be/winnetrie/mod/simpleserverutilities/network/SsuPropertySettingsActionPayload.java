package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuPropertySettingsActionPayload(String kind, String target, String key, String value, long requestId)
        implements CustomPacketPayload {
    public static final Type<SsuPropertySettingsActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "property_settings_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPropertySettingsActionPayload> STREAM_CODEC =
            StreamCodec.of(SsuPropertySettingsActionPayload::encode, SsuPropertySettingsActionPayload::decode);

    public SsuPropertySettingsActionPayload {
        kind = bounded(kind, 16).trim().toLowerCase(java.util.Locale.ROOT);
        target = bounded(target, 64).trim();
        key = bounded(key, 64).trim().toLowerCase(java.util.Locale.ROOT);
        value = bounded(value, 256).trim();
        requestId = Math.max(0L, requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b, SsuPropertySettingsActionPayload p) {
        b.writeUtf(p.kind, 16); b.writeUtf(p.target, 64); b.writeUtf(p.key, 64); b.writeUtf(p.value, 256); b.writeVarLong(p.requestId);
    }
    private static SsuPropertySettingsActionPayload decode(RegistryFriendlyByteBuf b) {
        return new SsuPropertySettingsActionPayload(b.readUtf(16), b.readUtf(64), b.readUtf(64), b.readUtf(256), b.readVarLong());
    }
    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
