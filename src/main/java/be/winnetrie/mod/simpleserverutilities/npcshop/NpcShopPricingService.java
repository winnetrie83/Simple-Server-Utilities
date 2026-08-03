package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.util.Collection;

/**
 * Central price-quote pipeline. Every discount is calculated from the unchanged
 * catalog base price, so future reputation and server-event discounts never
 * compound on top of one another.
 */
public final class NpcShopPricingService {
    public static final int BASIS_POINTS = 10_000;

    private NpcShopPricingService() {}

    public static Quote purchaseQuote(long basePriceMinor, Collection<Discount> discounts) {
        long base = Math.max(0L, basePriceMinor);
        int totalBasisPoints = 0;
        if (discounts != null) {
            for (Discount discount : discounts) {
                if (discount == null) continue;
                totalBasisPoints = Math.min(BASIS_POINTS,
                        totalBasisPoints + Math.max(0, Math.min(BASIS_POINTS, discount.basisPoints())));
            }
        }
        long discountMinor = multiplyBasisPoints(base, totalBasisPoints);
        return new Quote(base, totalBasisPoints, discountMinor, Math.max(0L, base - discountMinor));
    }

    private static long multiplyBasisPoints(long value, int basisPoints) {
        if (value <= 0L || basisPoints <= 0) return 0L;
        long whole = value / BASIS_POINTS;
        long remainder = value % BASIS_POINTS;
        return Math.addExact(Math.multiplyExact(whole, (long) basisPoints),
                Math.multiplyExact(remainder, (long) basisPoints) / BASIS_POINTS);
    }

    /** A named future discount source, e.g. faction reputation or a server event. */
    public record Discount(String source, int basisPoints) {
        public Discount {
            source = source == null ? "" : source.trim();
            basisPoints = Math.max(0, Math.min(BASIS_POINTS, basisPoints));
        }
    }

    public record Quote(long basePriceMinor, int totalDiscountBasisPoints,
                        long discountMinor, long effectivePriceMinor) {}
}
