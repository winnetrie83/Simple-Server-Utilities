package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record AuctionHouseDataPayload(
        boolean accessAllowed,
        String mode,
        String category,
        String search,
        String sort,
        int pageIndex,
        int pageSize,
        int totalEntries,
        String formattedBalance,
        String currencySymbol,
        int currencyDecimalPlaces,
        int activeAuctions,
        int maxAuctions,
        boolean canCreate,
        boolean administrator,
        int taxPermille,
        int defaultDurationHours,
        long requestId,
        String notice,
        boolean error,
        List<Entry> entries
) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 12;
    public static final Type<AuctionHouseDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "auction_house_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionHouseDataPayload> STREAM_CODEC =
            StreamCodec.of(AuctionHouseDataPayload::encode, AuctionHouseDataPayload::decode);

    public AuctionHouseDataPayload {
        mode = mode == null ? "browse" : mode;
        category = category == null ? "all" : category;
        search = search == null ? "" : search;
        sort = sort == null ? "name_asc" : sort;
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_ENTRIES, pageSize));
        totalEntries = Math.max(0, totalEntries);
        formattedBalance = formattedBalance == null ? "" : formattedBalance;
        currencySymbol = currencySymbol == null ? "" : currencySymbol;
        currencyDecimalPlaces = Math.max(0, Math.min(4, currencyDecimalPlaces));
        activeAuctions = Math.max(0, activeAuctions);
        maxAuctions = Math.max(0, maxAuctions);
        taxPermille = Math.max(0, Math.min(1_000, taxPermille));
        defaultDurationHours = defaultDurationHours <= 12 ? 12 : defaultDurationHours <= 24 ? 24 : 48;
        requestId = Math.max(0L, requestId);
        notice = notice == null ? "" : notice;
        entries = entries == null ? List.of() : entries.stream().limit(MAX_ENTRIES).toList();
    }

    public static AuctionHouseDataPayload denied(long requestId, String message) {
        return new AuctionHouseDataPayload(false, "browse", "all", "", "name_asc", 0, 8, 0,
                "", "", 2, 0, 0, false, false, 0, 48, requestId, message, true, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf b, AuctionHouseDataPayload p) {
        b.writeBoolean(p.accessAllowed());
        b.writeUtf(p.mode(), 16);
        b.writeUtf(p.category(), 32);
        b.writeUtf(p.search(), 96);
        b.writeUtf(p.sort(), 32);
        b.writeVarInt(p.pageIndex());
        b.writeVarInt(p.pageSize());
        b.writeVarInt(p.totalEntries());
        b.writeUtf(p.formattedBalance(), 128);
        b.writeUtf(p.currencySymbol(), 16);
        b.writeVarInt(p.currencyDecimalPlaces());
        b.writeVarInt(p.activeAuctions());
        b.writeVarInt(p.maxAuctions());
        b.writeBoolean(p.canCreate());
        b.writeBoolean(p.administrator());
        b.writeVarInt(p.taxPermille());
        b.writeVarInt(p.defaultDurationHours());
        b.writeVarLong(p.requestId());
        b.writeUtf(p.notice(), 256);
        b.writeBoolean(p.error());
        b.writeVarInt(p.entries().size());
        for (Entry entry : p.entries()) {
            b.writeUtf(entry.id(), 256);
            ItemStack.STREAM_CODEC.encode(b, entry.item());
            b.writeUtf(entry.name(), 128);
            b.writeUtf(entry.formattedUnitPrice(), 128);
            b.writeVarLong(entry.unitPriceMinor());
            b.writeVarInt(entry.quantity());
            b.writeUtf(entry.seller(), 64);
            b.writeVarLong(entry.createdAtEpochMilli());
            b.writeVarLong(entry.expiresAtEpochMilli());
            b.writeUtf(entry.category(), 32);
            b.writeBoolean(entry.ownAuction());
        }
    }

    private static AuctionHouseDataPayload decode(RegistryFriendlyByteBuf b) {
        boolean access = b.readBoolean();
        String mode = b.readUtf(16);
        String category = b.readUtf(32);
        String search = b.readUtf(96);
        String sort = b.readUtf(32);
        int page = b.readVarInt();
        int pageSize = b.readVarInt();
        int total = b.readVarInt();
        String balance = b.readUtf(128);
        String currencySymbol = b.readUtf(16);
        int currencyDecimalPlaces = b.readVarInt();
        int active = b.readVarInt();
        int max = b.readVarInt();
        boolean canCreate = b.readBoolean();
        boolean admin = b.readBoolean();
        int tax = b.readVarInt();
        int duration = b.readVarInt();
        long requestId = b.readVarLong();
        String notice = b.readUtf(256);
        boolean error = b.readBoolean();
        int count = b.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid auction entry count: " + count);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(
                    b.readUtf(256), ItemStack.STREAM_CODEC.decode(b), b.readUtf(128), b.readUtf(128),
                    b.readVarLong(), b.readVarInt(), b.readUtf(64), b.readVarLong(), b.readVarLong(),
                    b.readUtf(32), b.readBoolean()));
        }
        return new AuctionHouseDataPayload(access, mode, category, search, sort, page, pageSize, total, balance,
                currencySymbol, currencyDecimalPlaces, active, max, canCreate, admin, tax, duration,
                requestId, notice, error, entries);
    }

    public record Entry(
            String id,
            ItemStack item,
            String name,
            String formattedUnitPrice,
            long unitPriceMinor,
            int quantity,
            String seller,
            long createdAtEpochMilli,
            long expiresAtEpochMilli,
            String category,
            boolean ownAuction
    ) {
        public Entry {
            id = id == null ? "" : id;
            item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
            name = name == null ? "" : name;
            formattedUnitPrice = formattedUnitPrice == null ? "" : formattedUnitPrice;
            unitPriceMinor = Math.max(0L, unitPriceMinor);
            quantity = Math.max(0, quantity);
            seller = seller == null ? "" : seller;
            createdAtEpochMilli = Math.max(0L, createdAtEpochMilli);
            expiresAtEpochMilli = Math.max(0L, expiresAtEpochMilli);
            category = category == null ? "miscellaneous" : category;
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
