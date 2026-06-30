package be.winnetrie.mod.simpleserverutilities.region;

import java.util.UUID;

public class RegionRentData {

    private boolean rentable = false;
    private int amount = 0;
    private int periodDays = -1;
    private UUID renter;
    private long rentEndTime = -1L;

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
        this.amount = amount;
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

    public long getRentEndTime() {
        return rentEndTime;
    }

    public void setRentEndTime(long rentEndTime) {
        this.rentEndTime = rentEndTime;
    }

    public boolean isRented() {
        return renter != null;
    }

    public boolean isPermanent() {
        return periodDays == -1;
    }
}