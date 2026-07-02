package be.winnetrie.mod.simpleserverutilities.home;

import java.util.UUID;

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
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
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
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;

        if (createdAt <= 0) {
            createdAt = timestamp;
        }

        this.updatedAt = timestamp;
    }
}