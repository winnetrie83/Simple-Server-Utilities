package be.winnetrie.mod.simpleserverutilities.region;

import java.util.UUID;

public class RegionRentData {

    private boolean rentable = false;
    private int amount = 0;
    private int periodDays = -1;
    private UUID renter;
    private String renterName = "";
    private long rentEndTime = -1L;
    private boolean rentPaused = false;
    private long pausedRemainingMillis = -1L;
    private boolean resetOnExpire = true;
    private boolean resetOnUnrent = true;

    public boolean isRentable() {
        return rentable;
    }

    public void setRentable(boolean rentable) {
        this.rentable = rentable;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
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

        if (renter != null) {
            return renter.toString();
        }

        return "none";
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

    public boolean pause(long now) {
        if (!isRented() || isPermanent() || rentPaused) {
            return false;
        }

        pausedRemainingMillis = Math.max(0L, rentEndTime - now);
        rentEndTime = -1L;
        rentPaused = true;
        return true;
    }

    public boolean resume(long now) {
        if (!isRented() || isPermanent() || !rentPaused) {
            return false;
        }

        rentEndTime = now + Math.max(0L, pausedRemainingMillis);
        pausedRemainingMillis = -1L;
        rentPaused = false;
        return true;
    }

    public void clearRental() {
        renter = null;
        renterName = "";
        rentEndTime = -1L;
        rentPaused = false;
        pausedRemainingMillis = -1L;
    }
}
