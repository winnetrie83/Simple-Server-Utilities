package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Bounded player-facing snapshot of a click-driven NPC shop. */
public record NpcShopDataPayload(boolean accessAllowed, String instanceId, String shopId, String npcName,
        String shopName, String formattedBalance, long balanceMinor, String gameTime, int pageIndex, int pageSize, int totalEntries,
        long requestId, String notice, boolean error, List<Entry> entries,
        List<BuybackEntry> buybackEntries, List<InventorySaleQuote> inventorySaleQuotes) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 18;
    public static final int MAX_BUYBACK_ENTRIES = 9;
    public static final int MAX_INVENTORY_SALE_QUOTES = 36;
    public static final Type<NpcShopDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopDataPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopDataPayload::encode, NpcShopDataPayload::decode);

    public NpcShopDataPayload {
        instanceId = PayloadBounds.string(instanceId, 36);
        shopId = PayloadBounds.string(shopId, 64);
        npcName = PayloadBounds.string(npcName, 64);
        shopName = PayloadBounds.string(shopName, 64);
        formattedBalance = PayloadBounds.string(formattedBalance, 128);
        balanceMinor = Math.max(0L, balanceMinor);
        gameTime = PayloadBounds.string(gameTime, 64);
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_ENTRIES, pageSize));
        totalEntries = Math.max(0, totalEntries);
        requestId = Math.max(0L, requestId);
        notice = PayloadBounds.string(notice, 256);
        entries = entries == null ? List.of()
                : entries.stream().filter(java.util.Objects::nonNull).limit(MAX_ENTRIES).toList();
        buybackEntries = buybackEntries == null ? List.of()
                : buybackEntries.stream().filter(java.util.Objects::nonNull).limit(MAX_BUYBACK_ENTRIES).toList();
        inventorySaleQuotes = inventorySaleQuotes == null ? List.of()
                : inventorySaleQuotes.stream().filter(java.util.Objects::nonNull).limit(MAX_INVENTORY_SALE_QUOTES).toList();
    }

    public static NpcShopDataPayload denied(String instance, String shop, long request, String message) {
        return new NpcShopDataPayload(false, instance, shop, "", "", "", 0L, "", 0, MAX_ENTRIES, 0,
                request, message, true, List.of(), List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcShopDataPayload payload) {
        buffer.writeBoolean(payload.accessAllowed);
        buffer.writeUtf(payload.instanceId, 36);
        buffer.writeUtf(payload.shopId, 64);
        buffer.writeUtf(payload.npcName, 64);
        buffer.writeUtf(payload.shopName, 64);
        buffer.writeUtf(payload.formattedBalance, 128);
        buffer.writeVarLong(payload.balanceMinor);
        buffer.writeUtf(payload.gameTime, 64);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarInt(payload.pageSize);
        buffer.writeVarInt(payload.totalEntries);
        buffer.writeVarLong(payload.requestId);
        buffer.writeUtf(payload.notice, 256);
        buffer.writeBoolean(payload.error);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeUtf(entry.id, 64);
            ItemStack.STREAM_CODEC.encode(buffer, entry.item);
            buffer.writeUtf(entry.name, 128);
            buffer.writeUtf(entry.formattedBuyPrice, 128);
            buffer.writeUtf(entry.formattedStackBuyPrice, 128);
            buffer.writeUtf(entry.formattedSellPrice, 128);
            buffer.writeVarLong(entry.buyPriceMinor);
            buffer.writeVarLong(entry.sellPriceMinor);
            buffer.writeVarInt(entry.stock);
            buffer.writeVarInt(entry.maxStock);
            buffer.writeBoolean(entry.canBuy);
            buffer.writeBoolean(entry.canSell);
        }
        buffer.writeVarInt(payload.buybackEntries.size());
        for (BuybackEntry entry : payload.buybackEntries) {
            buffer.writeUtf(entry.id, 64);
            ItemStack.STREAM_CODEC.encode(buffer, entry.item);
            buffer.writeUtf(entry.name, 128);
            buffer.writeUtf(entry.formattedUnitPrice, 128);
            buffer.writeVarLong(entry.unitPriceMinor);
            buffer.writeVarLong(entry.expiresAtEpochMilli);
        }
        buffer.writeVarInt(payload.inventorySaleQuotes.size());
        for (InventorySaleQuote quote : payload.inventorySaleQuotes) {
            buffer.writeVarInt(quote.inventorySlot);
            buffer.writeUtf(quote.formattedUnitPrice, 128);
            buffer.writeVarLong(quote.unitPriceMinor);
            buffer.writeBoolean(quote.canSell);
            buffer.writeUtf(quote.reason, 160);
        }
    }

    private static NpcShopDataPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean access = buffer.readBoolean();
        String instance = buffer.readUtf(36);
        String shop = buffer.readUtf(64);
        String npc = buffer.readUtf(64);
        String name = buffer.readUtf(64);
        String balance = buffer.readUtf(128);
        long balanceMinor = buffer.readVarLong();
        String gameTime = buffer.readUtf(64);
        int page = buffer.readVarInt();
        int size = buffer.readVarInt();
        int total = buffer.readVarInt();
        long request = buffer.readVarLong();
        String notice = buffer.readUtf(256);
        boolean error = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid NPC shop entry count");
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readUtf(64), ItemStack.STREAM_CODEC.decode(buffer),
                    buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(128),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean()));
        }
        int buybackCount = buffer.readVarInt();
        if (buybackCount < 0 || buybackCount > MAX_BUYBACK_ENTRIES) {
            throw new IllegalArgumentException("Invalid NPC shop buy-back entry count");
        }
        ArrayList<BuybackEntry> buybacks = new ArrayList<>(buybackCount);
        for (int index = 0; index < buybackCount; index++) {
            buybacks.add(new BuybackEntry(buffer.readUtf(64), ItemStack.STREAM_CODEC.decode(buffer),
                    buffer.readUtf(128), buffer.readUtf(128), buffer.readVarLong(), buffer.readVarLong()));
        }
        int quoteCount = buffer.readVarInt();
        if (quoteCount < 0 || quoteCount > MAX_INVENTORY_SALE_QUOTES) {
            throw new IllegalArgumentException("Invalid NPC shop inventory sale quote count");
        }
        ArrayList<InventorySaleQuote> quotes = new ArrayList<>(quoteCount);
        for (int index = 0; index < quoteCount; index++) {
            quotes.add(new InventorySaleQuote(buffer.readVarInt(), buffer.readUtf(128), buffer.readVarLong(),
                    buffer.readBoolean(), buffer.readUtf(160)));
        }
        return new NpcShopDataPayload(access, instance, shop, npc, name, balance, balanceMinor, gameTime, page, size, total,
                request, notice, error, entries, buybacks, quotes);
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(String id, ItemStack item, String name, String formattedBuyPrice,
            String formattedStackBuyPrice, String formattedSellPrice, long buyPriceMinor, long sellPriceMinor, int stock,
            int maxStock, boolean canBuy, boolean canSell) {
        public Entry {
            id = PayloadBounds.string(id, 64);
            item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
            name = PayloadBounds.string(name, 128);
            formattedBuyPrice = PayloadBounds.string(formattedBuyPrice, 128);
            formattedStackBuyPrice = PayloadBounds.string(formattedStackBuyPrice, 128);
            formattedSellPrice = PayloadBounds.string(formattedSellPrice, 128);
            buyPriceMinor = Math.max(0L, buyPriceMinor);
            sellPriceMinor = Math.max(0L, sellPriceMinor);
            stock = Math.max(-1, stock);
            maxStock = Math.max(-1, maxStock);
        }
    }

    public record BuybackEntry(String id, ItemStack item, String name, String formattedUnitPrice,
            long unitPriceMinor, long expiresAtEpochMilli) {
        public BuybackEntry {
            id = PayloadBounds.string(id, 64);
            item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
            name = PayloadBounds.string(name, 128);
            formattedUnitPrice = PayloadBounds.string(formattedUnitPrice, 128);
            unitPriceMinor = Math.max(0L, unitPriceMinor);
            expiresAtEpochMilli = Math.max(0L, expiresAtEpochMilli);
        }
    }

    public record InventorySaleQuote(int inventorySlot, String formattedUnitPrice, long unitPriceMinor,
                                     boolean canSell, String reason) {
        public InventorySaleQuote {
            inventorySlot = Math.max(0, Math.min(35, inventorySlot));
            formattedUnitPrice = PayloadBounds.string(formattedUnitPrice, 128);
            unitPriceMinor = Math.max(0L, unitPriceMinor);
            reason = PayloadBounds.string(reason, 160);
        }
    }
}
