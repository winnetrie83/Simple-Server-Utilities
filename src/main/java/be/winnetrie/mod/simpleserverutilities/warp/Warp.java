package be.winnetrie.mod.simpleserverutilities.warp;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.core.location.WorldPositionValues;

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

    public Warp() {
        // Required for Gson
    }

    public Warp(
            String name,
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            UUID createdBy,
            long timestamp
    ) {
        this.name = name;
        applyPosition(dimension, x, y, z, yaw, pitch);
        this.createdBy = createdBy;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    private void applyPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
        WorldPositionValues position = WorldPositionValues.normalize(dimension, x, y, z, yaw, pitch);
        this.dimension = position.dimension();
        this.x = position.x();
        this.y = position.y();
        this.z = position.z();
        this.yaw = position.yaw();
        this.pitch = position.pitch();
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return name == null ? "warp" : name;
    }

    public String getDimension() {
        return dimension;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void update(
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            UUID updatedBy,
            long timestamp
    ) {
        applyPosition(dimension, x, y, z, yaw, pitch);

        if (createdBy == null) {
            createdBy = updatedBy;
        }

        if (createdAt <= 0) {
            createdAt = timestamp;
        }

        this.updatedAt = timestamp;
    }
}