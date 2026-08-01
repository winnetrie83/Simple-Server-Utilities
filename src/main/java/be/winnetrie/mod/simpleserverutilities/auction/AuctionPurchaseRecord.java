package be.winnetrie.mod.simpleserverutilities.auction;

import java.util.UUID;

import com.google.gson.JsonElement;

public final class AuctionPurchaseRecord {
    public static final int SCHEMA_VERSION = 1;

    public enum Status {
        PREPARED,
        LISTING_RESERVED,
        FUNDS_CAPTURED,
        PAYOUT_FUNDED,
        SELLER_MAILED,
        BUYER_MAILED,
        COMMITTED,
        ROLLBACK_PENDING,
        ROLLED_BACK
    }

    private int schemaVersion = SCHEMA_VERSION;
    private UUID id = UUID.randomUUID();
    private UUID listingId;
    private UUID buyerId;
    private String buyerName = "";
    private UUID sellerId;
    private String sellerName = "";
    private JsonElement item;
    private String itemName = "";
    private int quantity;
    private int listingRemainingBefore;
    private long unitPriceMinor;
    private long grossMinor;
    private int taxPermille;
    private long taxMinor;
    private long netMinor;
    private long createdAtEpochMilli = System.currentTimeMillis();
    private Status status = Status.PREPARED;

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public UUID getBuyerId() { return buyerId; }
    public String getBuyerName() { return buyerName == null ? "" : buyerName; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName == null ? "" : sellerName; }
    public JsonElement getItem() { return item == null ? null : item.deepCopy(); }
    public String getItemName() { return itemName == null ? "" : itemName; }
    public int getQuantity() { return Math.max(0, quantity); }
    public int getListingRemainingBefore() { return Math.max(0, listingRemainingBefore); }
    public long getUnitPriceMinor() { return Math.max(0L, unitPriceMinor); }
    public long getGrossMinor() { return Math.max(0L, grossMinor); }
    public int getTaxPermille() { return Math.max(0, Math.min(1_000, taxPermille)); }
    public long getTaxMinor() { return Math.max(0L, taxMinor); }
    public long getNetMinor() { return Math.max(0L, netMinor); }
    public long getCreatedAtEpochMilli() { return Math.max(0L, createdAtEpochMilli); }
    public Status getStatus() { return status == null ? Status.PREPARED : status; }
    public void setStatus(Status status) { this.status = status == null ? Status.PREPARED : status; }

    public static AuctionPurchaseRecord create(AuctionListing listing, JsonElement item, String itemName,
            UUID buyerId, String buyerName, int quantity, long gross, int taxPermille, long tax, long net) {
        AuctionPurchaseRecord record = new AuctionPurchaseRecord();
        record.id = UUID.randomUUID();
        record.listingId = listing.getId();
        record.buyerId = buyerId;
        record.buyerName = buyerName == null ? "" : buyerName;
        record.sellerId = listing.getSellerId();
        record.sellerName = listing.getSellerName();
        record.item = item == null ? null : item.deepCopy();
        record.itemName = itemName == null ? "" : itemName;
        record.quantity = Math.max(1, quantity);
        record.listingRemainingBefore = listing.getRemainingQuantity();
        record.unitPriceMinor = listing.getPricePerUnitMinor();
        record.grossMinor = Math.max(0L, gross);
        record.taxPermille = Math.max(0, Math.min(1_000, taxPermille));
        record.taxMinor = Math.max(0L, tax);
        record.netMinor = Math.max(0L, net);
        record.createdAtEpochMilli = System.currentTimeMillis();
        record.status = Status.PREPARED;
        return record;
    }

    public void normalize(UUID fallbackId) {
        schemaVersion = SCHEMA_VERSION;
        if (id == null) id = fallbackId == null ? UUID.randomUUID() : fallbackId;
        buyerName = buyerName == null ? "" : buyerName;
        sellerName = sellerName == null ? "" : sellerName;
        itemName = itemName == null ? "" : itemName;
        quantity = Math.max(0, quantity);
        listingRemainingBefore = Math.max(quantity, listingRemainingBefore);
        unitPriceMinor = Math.max(0L, unitPriceMinor);
        grossMinor = Math.max(0L, grossMinor);
        taxPermille = Math.max(0, Math.min(1_000, taxPermille));
        taxMinor = Math.max(0L, Math.min(grossMinor, taxMinor));
        netMinor = Math.max(0L, Math.min(grossMinor, netMinor));
        createdAtEpochMilli = Math.max(0L, createdAtEpochMilli);
        status = status == null ? Status.PREPARED : status;
    }
}
