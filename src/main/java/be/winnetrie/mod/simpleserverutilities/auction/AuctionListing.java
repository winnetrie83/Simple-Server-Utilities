package be.winnetrie.mod.simpleserverutilities.auction;

import java.util.UUID;

import com.google.gson.JsonElement;

import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

public final class AuctionListing {
    public static final int SCHEMA_VERSION = 3;

    private int schemaVersion = SCHEMA_VERSION;
    private UUID id = UUID.randomUUID();
    private UUID sellerId;
    private String sellerName = "";
    private JsonElement item;
    private String category = AuctionCategory.MISCELLANEOUS.id();
    private int remainingQuantity;
    private long pricePerUnitMinor;
    private long createdAtEpochMilli = System.currentTimeMillis();
    private long expiresAtEpochMilli;
    private UUID seizureRecipientId;
    private String seizureRecipientName = "";
    private long seizureStartedAtEpochMilli;
    private String seizureReason = "";
    private long revision;

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName == null ? "" : sellerName; }
    public String getCategory() { return category == null ? AuctionCategory.MISCELLANEOUS.id() : category; }
    public int getRemainingQuantity() { return Math.max(0, remainingQuantity); }
    public long getPricePerUnitMinor() { return Math.max(0L, pricePerUnitMinor); }
    public long getCreatedAtEpochMilli() { return Math.max(0L, createdAtEpochMilli); }
    public long getExpiresAtEpochMilli() { return Math.max(0L, expiresAtEpochMilli); }
    public UUID getSeizureRecipientId() { return seizureRecipientId; }
    public String getSeizureRecipientName() { return seizureRecipientName == null ? "" : seizureRecipientName; }
    public long getSeizureStartedAtEpochMilli() { return Math.max(0L, seizureStartedAtEpochMilli); }
    public String getSeizureReason() { return seizureReason == null ? "" : seizureReason; }
    public boolean isSeizurePending() { return seizureRecipientId != null; }
    public long getRevision() { return Math.max(0L, revision); }
    public JsonElement getItemData() { return item == null ? null : item.deepCopy(); }

    public void setRemainingQuantity(int quantity) {
        remainingQuantity = Math.max(0, quantity);
        revision = Math.max(0L, revision + 1L);
    }

    public void beginSeizure(UUID recipientId, String recipientName, String reason, long now) {
        if (recipientId == null) throw new IllegalArgumentException("Seizure recipient is missing.");
        seizureRecipientId = recipientId;
        seizureRecipientName = recipientName == null ? "" : recipientName.trim();
        seizureStartedAtEpochMilli = Math.max(1L, now);
        seizureReason = normalizeReason(reason);
        revision = Math.max(0L, revision + 1L);
    }

    public void clearSeizure() {
        seizureRecipientId = null;
        seizureRecipientName = "";
        seizureStartedAtEpochMilli = 0L;
        seizureReason = "";
        revision = Math.max(0L, revision + 1L);
    }

    private static String normalizeReason(String reason) {
        if (reason == null) return "";
        String normalized = reason.replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    public ItemStack item(HolderLookup.Provider registries) {
        ItemStack result = MailItemCodec.decode(registries, item);
        return result.isEmpty() ? ItemStack.EMPTY : result.copyWithCount(1);
    }

    public static AuctionListing create(HolderLookup.Provider registries, UUID sellerId, String sellerName,
            ItemStack template, int quantity, long pricePerUnitMinor, long now, int durationHours) {
        if (sellerId == null || template == null || template.isEmpty()) throw new IllegalArgumentException("Invalid auction item.");
        AuctionListing listing = new AuctionListing();
        listing.id = UUID.randomUUID();
        listing.sellerId = sellerId;
        listing.sellerName = sellerName == null ? "" : sellerName.trim();
        listing.item = MailItemCodec.encode(registries, template.copyWithCount(1));
        listing.category = AuctionCategory.classify(template).id();
        listing.remainingQuantity = Math.max(1, quantity);
        listing.pricePerUnitMinor = Math.max(1L, pricePerUnitMinor);
        listing.createdAtEpochMilli = Math.max(1L, now);
        listing.expiresAtEpochMilli = Math.addExact(listing.createdAtEpochMilli,
                AuctionHouseSettings.normalizeDuration(durationHours) * 60L * 60L * 1000L);
        listing.revision = 0L;
        return listing;
    }

    public void normalize(UUID fallbackId) {
        schemaVersion = SCHEMA_VERSION;
        if (id == null) id = fallbackId == null ? UUID.randomUUID() : fallbackId;
        sellerName = sellerName == null ? "" : sellerName.trim();
        category = AuctionCategory.byId(category).id();
        remainingQuantity = Math.max(0, remainingQuantity);
        pricePerUnitMinor = Math.max(0L, pricePerUnitMinor);
        createdAtEpochMilli = Math.max(0L, createdAtEpochMilli);
        expiresAtEpochMilli = Math.max(createdAtEpochMilli, expiresAtEpochMilli);
        if (seizureRecipientId == null) {
            seizureRecipientName = "";
            seizureStartedAtEpochMilli = 0L;
            seizureReason = "";
        } else {
            seizureRecipientName = seizureRecipientName == null ? "" : seizureRecipientName.trim();
            seizureStartedAtEpochMilli = Math.max(1L, seizureStartedAtEpochMilli);
            seizureReason = normalizeReason(seizureReason);
        }
        revision = Math.max(0L, revision);
    }
}
