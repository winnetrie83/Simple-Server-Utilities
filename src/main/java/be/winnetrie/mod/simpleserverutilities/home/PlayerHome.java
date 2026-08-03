package be.winnetrie.mod.simpleserverutilities.home;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.core.location.WorldPositionValues;

public class PlayerHome {

    private UUID owner;
    private String name;
    private String dimension;

    private double x;
    private double y;
    private double z;

    private float yaw;
    private float pitch;

    private long createdAt;
    private long updatedAt;

    public PlayerHome() {
        // Required for Gson
    }

    public PlayerHome(
            UUID owner,
            String name,
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            long timestamp
    ) {
        this.owner = owner;
        this.name = name;
        applyPosition(dimension, x, y, z, yaw, pitch);
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

    public UUID getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return name == null ? "home" : name;
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
            long timestamp
    ) {
        applyPosition(dimension, x, y, z, yaw, pitch);

        if (createdAt <= 0) {
            createdAt = timestamp;
        }

        this.updatedAt = timestamp;
    }
}