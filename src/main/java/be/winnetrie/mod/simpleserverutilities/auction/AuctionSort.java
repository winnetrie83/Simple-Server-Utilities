package be.winnetrie.mod.simpleserverutilities.auction;

import java.util.Comparator;
import java.util.Locale;

public enum AuctionSort {
    NAME_ASC("name_asc"), NAME_DESC("name_desc"),
    QUANTITY_ASC("quantity_asc"), QUANTITY_DESC("quantity_desc"),
    PRICE_ASC("price_asc"), PRICE_DESC("price_desc"),
    TIME_ASC("time_asc"), TIME_DESC("time_desc");

    private final String id;
    AuctionSort(String id) { this.id = id; }
    public String id() { return id; }

    public static AuctionSort byId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (AuctionSort sort : values()) if (sort.id.equals(value)) return sort;
        return NAME_ASC;
    }

    public Comparator<AuctionListingView> comparator() {
        Comparator<AuctionListingView> byId = Comparator.comparing((AuctionListingView v) -> v.listing().getId().toString());
        return switch (this) {
            case NAME_ASC -> Comparator.comparing(AuctionListingView::displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(byId);
            case NAME_DESC -> Comparator.comparing(AuctionListingView::displayName, String.CASE_INSENSITIVE_ORDER).reversed().thenComparing(byId);
            case QUANTITY_ASC -> Comparator.comparingInt((AuctionListingView v) -> v.listing().getRemainingQuantity()).thenComparing(byId);
            case QUANTITY_DESC -> Comparator.comparingInt((AuctionListingView v) -> v.listing().getRemainingQuantity()).reversed().thenComparing(byId);
            case PRICE_ASC -> Comparator.comparingLong((AuctionListingView v) -> v.listing().getPricePerUnitMinor()).thenComparing(byId);
            case PRICE_DESC -> Comparator.comparingLong((AuctionListingView v) -> v.listing().getPricePerUnitMinor()).reversed().thenComparing(byId);
            case TIME_ASC -> Comparator.comparingLong((AuctionListingView v) -> v.listing().getExpiresAtEpochMilli()).thenComparing(byId);
            case TIME_DESC -> Comparator.comparingLong((AuctionListingView v) -> v.listing().getExpiresAtEpochMilli()).reversed().thenComparing(byId);
        };
    }
}
