package be.winnetrie.mod.simpleserverutilities.warp;

import java.util.UUID;

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
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdBy = createdBy;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
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
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;

        if (createdBy == null) {
            createdBy = updatedBy;
        }

        if (createdAt <= 0) {
            createdAt = timestamp;
        }

        this.updatedAt = timestamp;
    }
}