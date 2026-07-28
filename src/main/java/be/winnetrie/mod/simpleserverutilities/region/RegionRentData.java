package be.winnetrie.mod.simpleserverutilities.region;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.economy.EconomySettings;

/**
 * Persistent rental state for one server region.
 *
 * <p>The legacy {@code amount} field remains available so existing region JSON
 * files keep their old meaning: a whole major-currency amount. New builds also
 * persist {@code priceMinor}, which is the exact price in economy minor units.</p>
 */
public class RegionRentData {

    private boolean rentable = false;

    /** Legacy whole-unit price kept for backwards-compatible JSON. */
    private int amount = 0;

    /** Exact economy price. A negative value means: migrate from {@link #amount}. */
    private long priceMinor = -1L;

    private int periodDays = -1;
    private UUID renter;
    private String renterName = "";
    private long rentEndTime = -1L;
    private boolean rentPaused = false;
    private long pausedRemainingMillis = -1L;
    private boolean resetOnExpire = true;
    private boolean resetOnUnrent = true;

    /* Economy/rental reconciliation metadata introduced in 1.2.0-dev2. */
    private long rentalSequence = 0L;
    private long currentTermPaidMinor = 0L;
    private long totalPaidMinor = 0L;
    private long refundableAmountMinor = 0L;
    private long refundableWindowStartTime = -1L;
    private long refundableWindowEndTime = -1L;
    private UUID lastPaymentTransactionId;

    public boolean isRentable() {
        return rentable;
    }

    public void setRentable(boolean rentable) {
        this.rentable = rentable;
    }

    public int getAmount() {
        return amount;
    }

    /**
     * Sets the legacy whole-unit value and marks the exact price for migration.
     */
    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
        this.priceMinor = -1L;
    }

    public long getStoredPriceMinor() {
        return priceMinor;
    }

    public long getPriceMinor(EconomySettings settings) {
        if (priceMinor >= 0L) {
            return priceMinor;
        }

        long scale = 1L;
        int decimals = settings == null ? 2 : settings.getDecimalPlaces();
        for (int i = 0; i < decimals; i++) {
            scale = Math.multiplyExact(scale, 10L);
        }

        try {
            return Math.multiplyExact((long) amount, scale);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public void setPriceMinor(long priceMinor, EconomySettings settings) {
        this.priceMinor = Math.max(0L, priceMinor);

        int decimals = settings == null ? 2 : settings.getDecimalPlaces();
        long scale = 1L;
        for (int i = 0; i < decimals; i++) {
            scale = Math.multiplyExact(scale, 10L);
        }
        long whole = this.priceMinor / Math.max(1L, scale);
        this.amount = (int) Math.min(Integer.MAX_VALUE, whole);
    }

    /** Used while loading JSON that already contains an exact minor-unit price. */
    public void loadPriceMinor(long priceMinor) {
        this.priceMinor = Math.max(0L, priceMinor);
    }

    public int getPeriodDays() {
        return periodDays;
    }

    public void setPeriodDays(int periodDays) {
        this.periodDays = periodDays;
    }

    public UUID getRenter() {
        return renter;
    }

    public void setRenter(UUID renter) {
        this.renter = renter;
    }

    public String getRenterName() {
        return renterName;
    }

    public void setRenterName(String renterName) {
        this.renterName = renterName == null ? "" : renterName;
    }

    public String getDisplayRenterName() {
        if (renterName != null && !renterName.isBlank()) {
            return renterName;
        }
        return renter == null ? "none" : renter.toString();
    }

    public long getRentEndTime() {
        return rentEndTime;
    }

    public void setRentEndTime(long rentEndTime) {
        this.rentEndTime = rentEndTime;
    }

    public boolean isRentPaused() {
        return rentPaused;
    }

    public void setRentPaused(boolean rentPaused) {
        this.rentPaused = rentPaused;
    }

    public long getPausedRemainingMillis() {
        return pausedRemainingMillis;
    }

    public void setPausedRemainingMillis(long pausedRemainingMillis) {
        this.pausedRemainingMillis = pausedRemainingMillis;
    }

    public boolean isResetOnExpire() {
        return resetOnExpire;
    }

    public void setResetOnExpire(boolean resetOnExpire) {
        this.resetOnExpire = resetOnExpire;
    }

    public boolean isResetOnUnrent() {
        return resetOnUnrent;
    }

    public void setResetOnUnrent(boolean resetOnUnrent) {
        this.resetOnUnrent = resetOnUnrent;
    }

    public boolean isRented() {
        return renter != null;
    }

    public boolean isPermanent() {
        return periodDays == -1;
    }

    public long getPeriodMillis() {
        if (isPermanent()) {
            return -1L;
        }
        return periodDays * 24L * 60L * 60L * 1000L;
    }

    public long getRentalSequence() {
        return rentalSequence;
    }

    public void setRentalSequence(long rentalSequence) {
        this.rentalSequence = Math.max(0L, rentalSequence);
    }

    public long getCurrentTermPaidMinor() {
        return currentTermPaidMinor;
    }

    public void setCurrentTermPaidMinor(long value) {
        currentTermPaidMinor = Math.max(0L, value);
    }

    public long getTotalPaidMinor() {
        return totalPaidMinor;
    }

    public void setTotalPaidMinor(long value) {
        totalPaidMinor = Math.max(0L, value);
    }

    public long getRefundableAmountMinor() {
        return refundableAmountMinor;
    }

    public void setRefundableAmountMinor(long value) {
        refundableAmountMinor = Math.max(0L, value);
    }

    public long getRefundableWindowStartTime() {
        return refundableWindowStartTime;
    }

    public void setRefundableWindowStartTime(long value) {
        refundableWindowStartTime = value;
    }

    public long getRefundableWindowEndTime() {
        return refundableWindowEndTime;
    }

    public void setRefundableWindowEndTime(long value) {
        refundableWindowEndTime = value;
    }

    public UUID getLastPaymentTransactionId() {
        return lastPaymentTransactionId;
    }

    public void setLastPaymentTransactionId(UUID transactionId) {
        this.lastPaymentTransactionId = transactionId;
    }

    public boolean pause(long now) {
        if (!isRented() || isPermanent() || rentPaused) {
            return false;
        }

        /* Freeze both time and refundable value at the pause moment. */
        refundableAmountMinor = calculateRefundMinor(now, 1_000);
        pausedRemainingMillis = Math.max(0L, rentEndTime - now);
        rentEndTime = -1L;
        rentPaused = true;
        refundableWindowStartTime = now;
        refundableWindowEndTime = safeAdd(now, pausedRemainingMillis);
        return true;
    }

    public boolean resume(long now) {
        if (!isRented() || isPermanent() || !rentPaused) {
            return false;
        }

        rentEndTime = now + Math.max(0L, pausedRemainingMillis);
        refundableWindowStartTime = now;
        refundableWindowEndTime = rentEndTime;
        pausedRemainingMillis = -1L;
        rentPaused = false;
        return true;
    }

    /**
     * Records a successful rent/renew payment and rolls any remaining refundable
     * value into a fresh time window. This keeps pro-rata refunds fair even when
     * a player renews before the previous period ends or the price changes.
     */
    public void recordPayment(long now, long amountMinor, long effectiveEndTime, UUID transactionId) {
        long remainingValue = calculateRefundMinor(now, 1_000);
        refundableAmountMinor = safeAdd(remainingValue, Math.max(0L, amountMinor));
        refundableWindowStartTime = now;
        refundableWindowEndTime = isPermanent() ? -1L : effectiveEndTime;
        currentTermPaidMinor = Math.max(0L, amountMinor);
        totalPaidMinor = safeAdd(totalPaidMinor, Math.max(0L, amountMinor));
        lastPaymentTransactionId = transactionId;
        rentalSequence = safeAdd(rentalSequence, 1L);
    }

    /** Returns a pro-rata refund using permille (0..1000). */
    public long calculateRefundMinor(long now, int refundPermille) {
        int boundedPermille = Math.max(0, Math.min(1_000, refundPermille));
        if (boundedPermille == 0 || refundableAmountMinor <= 0L) {
            return 0L;
        }

        long eligible = refundableAmountMinor;
        if (rentPaused) {
            return multiplyDivideFloor(eligible, boundedPermille, 1_000L);
        }
        if (!isPermanent() && refundableWindowEndTime > 0L && refundableWindowStartTime >= 0L) {
            long totalWindow = Math.max(1L, refundableWindowEndTime - refundableWindowStartTime);
            long remaining = Math.max(0L, refundableWindowEndTime - now);
            if (remaining <= 0L) {
                eligible = 0L;
            } else {
                eligible = multiplyDivideFloor(refundableAmountMinor, Math.min(remaining, totalWindow), totalWindow);
            }
        }

        return multiplyDivideFloor(eligible, boundedPermille, 1_000L);
    }

    public void clearRental() {
        renter = null;
        renterName = "";
        rentEndTime = -1L;
        rentPaused = false;
        pausedRemainingMillis = -1L;
        currentTermPaidMinor = 0L;
        refundableAmountMinor = 0L;
        refundableWindowStartTime = -1L;
        refundableWindowEndTime = -1L;
        lastPaymentTransactionId = null;
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long multiplyDivideFloor(long value, long multiplier, long divisor) {
        if (value <= 0L || multiplier <= 0L || divisor <= 0L) {
            return 0L;
        }
        java.math.BigInteger result = java.math.BigInteger.valueOf(value)
                .multiply(java.math.BigInteger.valueOf(multiplier))
                .divide(java.math.BigInteger.valueOf(divisor));
        return result.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
}
