package be.winnetrie.mod.simpleserverutilities.warp;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.core.location.WorldPositionValues;

/** Persistent server or player-rented warp location. */
public class Warp {
    private String name;
    private String dimension;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private UUID createdBy;
    private long createdAt;
    private long updatedAt;

    // Schema 2 rental metadata. Null renterId means a traditional server warp.
    private UUID renterId;
    private String renterName = "";
    private boolean publicWarp;
    private long paidUntil;

    public Warp() {}

    public Warp(String name, String dimension, double x, double y, double z, float yaw, float pitch,
                UUID createdBy, long timestamp) {
        this.name = name;
        applyPosition(dimension, x, y, z, yaw, pitch);
        this.createdBy = createdBy;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    public static Warp rented(String name, String dimension, double x, double y, double z, float yaw, float pitch,
                              UUID renterId, String renterName, long timestamp, long paidUntil) {
        Warp warp = new Warp(name, dimension, x, y, z, yaw, pitch, renterId, timestamp);
        warp.renterId = renterId;
        warp.renterName = renterName == null ? "" : renterName;
        warp.publicWarp = false;
        warp.paidUntil = Math.max(timestamp, paidUntil);
        return warp;
    }

    public void ensureDefaults() {
        if (name == null) name = "warp";
        if (dimension == null || dimension.isBlank()) dimension = "minecraft:overworld";
        if (renterName == null) renterName = "";
        createdAt = Math.max(0L, createdAt);
        updatedAt = Math.max(createdAt, updatedAt);
        paidUntil = Math.max(0L, paidUntil);
        if (renterId == null) {
            renterName = "";
            publicWarp = true; // Legacy/server warps remain visible to everyone with use permission.
            paidUntil = 0L;
        }
    }

    private void applyPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
        WorldPositionValues position = WorldPositionValues.normalize(dimension, x, y, z, yaw, pitch);
        this.dimension = position.dimension();
        this.x = position.x(); this.y = position.y(); this.z = position.z();
        this.yaw = position.yaw(); this.pitch = position.pitch();
    }

    public String getName() { return name; }
    public String getDisplayName() { return name == null ? "warp" : name; }
    public String getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public UUID getCreatedBy() { return createdBy; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public UUID getRenterId() { return renterId; }
    public String getRenterName() { return renterName == null ? "" : renterName; }
    public boolean isPublicWarp() { return !isPlayerRental() || publicWarp; }
    public long getPaidUntil() { return Math.max(0L, paidUntil); }
    public boolean isPlayerRental() { return renterId != null; }
    public boolean isRentedBy(UUID playerId) { return playerId != null && playerId.equals(renterId); }

    public void update(String dimension, double x, double y, double z, float yaw, float pitch,
                       UUID updatedBy, long timestamp) {
        applyPosition(dimension, x, y, z, yaw, pitch);
        if (createdBy == null) createdBy = updatedBy;
        if (createdAt <= 0) createdAt = timestamp;
        updatedAt = timestamp;
    }

    public void setPublicWarp(boolean value, long timestamp) {
        if (!isPlayerRental()) return;
        publicWarp = value;
        updatedAt = Math.max(updatedAt, timestamp);
    }

    public void updateRenterName(String value) {
        if (isPlayerRental() && value != null && !value.isBlank()) renterName = value.trim();
    }

    public void renewUntil(long timestamp) {
        if (isPlayerRental()) paidUntil = Math.max(paidUntil, timestamp);
    }
}
