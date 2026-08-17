package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcItemPriceCatalogRequestPayload(String query, int pageIndex, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcItemPriceCatalogRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_item_price_catalog_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcItemPriceCatalogRequestPayload> STREAM_CODEC =
            StreamCodec.of(NpcItemPriceCatalogRequestPayload::encode, NpcItemPriceCatalogRequestPayload::decode);

    public NpcItemPriceCatalogRequestPayload {
        query = PayloadBounds.string(query, 96);
        pageIndex = Math.max(0, pageIndex);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcItemPriceCatalogRequestPayload payload) {
        buffer.writeUtf(payload.query, 96);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcItemPriceCatalogRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcItemPriceCatalogRequestPayload(buffer.readUtf(96), buffer.readVarInt(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
