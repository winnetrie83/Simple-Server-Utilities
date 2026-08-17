package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionSchematicManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sends a completed template export to the requesting client for local storage. */
public record RegionSelectionClientTemplatePayload(String name, byte[] data, long requestId)
        implements CustomPacketPayload {
    public static final Type<RegionSelectionClientTemplatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_selection_client_template")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionSelectionClientTemplatePayload> STREAM_CODEC =
            StreamCodec.of(RegionSelectionClientTemplatePayload::encode, RegionSelectionClientTemplatePayload::decode);

    public RegionSelectionClientTemplatePayload {
        name = PayloadBounds.trimmedString(name, 64);
        data = data == null ? new byte[0] : data.clone();
        if (data.length > RegionSelectionSchematicManager.MAX_TRANSFER_BYTES) {
            throw new IllegalArgumentException("Selection template exceeds the transfer limit.");
        }
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, RegionSelectionClientTemplatePayload p) {
        b.writeUtf(p.name(), 64);
        b.writeByteArray(p.data());
        b.writeVarLong(p.requestId());
    }

    private static RegionSelectionClientTemplatePayload decode(RegistryFriendlyByteBuf b) {
        return new RegionSelectionClientTemplatePayload(
                b.readUtf(64), b.readByteArray(RegionSelectionSchematicManager.MAX_TRANSFER_BYTES), b.readVarLong());
    }

    public byte[] data() { return data.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
