package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcItemPriceCatalogActionPayload(String itemId, long buyPriceMinor, long sellPriceMinor,
        String query, int pageIndex, long requestId) implements CustomPacketPayload {
    public static final Type<NpcItemPriceCatalogActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_item_price_catalog_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcItemPriceCatalogActionPayload> STREAM_CODEC =
            StreamCodec.of(NpcItemPriceCatalogActionPayload::encode, NpcItemPriceCatalogActionPayload::decode);

    public NpcItemPriceCatalogActionPayload {
        itemId = PayloadBounds.string(itemId, 160);
        buyPriceMinor = Math.max(0L, buyPriceMinor);
        sellPriceMinor = Math.max(0L, sellPriceMinor);
        query = PayloadBounds.string(query, 96);
        pageIndex = Math.max(0, pageIndex);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcItemPriceCatalogActionPayload payload) {
        buffer.writeUtf(payload.itemId, 160);
        buffer.writeVarLong(payload.buyPriceMinor);
        buffer.writeVarLong(payload.sellPriceMinor);
        buffer.writeUtf(payload.query, 96);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcItemPriceCatalogActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcItemPriceCatalogActionPayload(buffer.readUtf(160), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readUtf(96), buffer.readVarInt(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
