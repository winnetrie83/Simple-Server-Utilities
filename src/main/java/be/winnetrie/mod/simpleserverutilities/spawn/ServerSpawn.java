package be.winnetrie.mod.simpleserverutilities.spawn;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.core.location.WorldPositionValues;

/** Persisted, dimension-aware server spawn destination. */
public final class ServerSpawn {

    private String dimension = "minecraft:overworld";
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private UUID updatedBy;
    private String updatedByName = "";
    private long updatedAt;

    public ServerSpawn() {
    }

    public ServerSpawn(
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            UUID updatedBy,
            String updatedByName,
            long updatedAt
    ) {
        update(dimension, x, y, z, yaw, pitch, updatedBy, updatedByName, updatedAt);
    }

    public void update(
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            UUID updatedBy,
            String updatedByName,
            long updatedAt
    ) {
        WorldPositionValues position = WorldPositionValues.normalize(dimension, x, y, z, yaw, pitch);
        this.dimension = position.dimension();
        this.x = position.x();
        this.y = position.y();
        this.z = position.z();
        this.yaw = position.yaw();
        this.pitch = position.pitch();
        this.updatedBy = updatedBy;
        this.updatedByName = updatedByName == null ? "" : updatedByName;
        this.updatedAt = Math.max(0L, updatedAt);
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

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public String getUpdatedByName() {
        return updatedByName == null ? "" : updatedByName;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
