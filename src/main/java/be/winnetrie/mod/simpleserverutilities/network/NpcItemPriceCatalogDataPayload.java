package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcItemPriceCatalogDataPayload(String query, int pageIndex, int pageCount, int totalItems,
        String currencySymbol, int decimalPlaces, List<Entry> entries, String notice, boolean error, long requestId)
        implements CustomPacketPayload {
    public static final int PAGE_SIZE = 12;
    public static final Type<NpcItemPriceCatalogDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_item_price_catalog_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcItemPriceCatalogDataPayload> STREAM_CODEC =
            StreamCodec.of(NpcItemPriceCatalogDataPayload::encode, NpcItemPriceCatalogDataPayload::decode);

    public NpcItemPriceCatalogDataPayload {
        query = PayloadBounds.string(query, 96);
        pageIndex = Math.max(0, pageIndex);
        pageCount = Math.max(1, pageCount);
        totalItems = Math.max(0, totalItems);
        currencySymbol = PayloadBounds.string(currencySymbol, 16);
        decimalPlaces = Math.max(0, Math.min(6, decimalPlaces));
        entries = entries == null ? List.of() : entries.stream().filter(java.util.Objects::nonNull).limit(PAGE_SIZE).toList();
        notice = PayloadBounds.string(notice, 256);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcItemPriceCatalogDataPayload payload) {
        buffer.writeUtf(payload.query, 96);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarInt(payload.pageCount);
        buffer.writeVarInt(payload.totalItems);
        buffer.writeUtf(payload.currencySymbol, 16);
        buffer.writeVarInt(payload.decimalPlaces);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeUtf(entry.itemId, 160);
            buffer.writeUtf(entry.displayName, 128);
            buffer.writeVarLong(entry.buyPriceMinor);
            buffer.writeVarLong(entry.sellPriceMinor);
            buffer.writeVarInt(entry.maxStackSize);
        }
        buffer.writeUtf(payload.notice, 256);
        buffer.writeBoolean(payload.error);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcItemPriceCatalogDataPayload decode(RegistryFriendlyByteBuf buffer) {
        String query = buffer.readUtf(96);
        int page = buffer.readVarInt();
        int pages = buffer.readVarInt();
        int total = buffer.readVarInt();
        String symbol = buffer.readUtf(16);
        int decimals = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Invalid item price page size");
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readUtf(160), buffer.readUtf(128), buffer.readVarLong(),
                    buffer.readVarLong(), buffer.readVarInt()));
        }
        return new NpcItemPriceCatalogDataPayload(query, page, pages, total, symbol, decimals, entries,
                buffer.readUtf(256), buffer.readBoolean(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(String itemId, String displayName, long buyPriceMinor, long sellPriceMinor, int maxStackSize) {
        public Entry {
            itemId = PayloadBounds.string(itemId, 160);
            displayName = PayloadBounds.string(displayName, 128);
            buyPriceMinor = Math.max(0L, buyPriceMinor);
            sellPriceMinor = Math.max(0L, sellPriceMinor);
            maxStackSize = Math.max(1, maxStackSize);
        }
    }
}
