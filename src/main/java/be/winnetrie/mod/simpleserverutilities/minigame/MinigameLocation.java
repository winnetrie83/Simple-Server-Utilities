package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.core.location.WorldPositionValues;
import net.minecraft.server.level.ServerPlayer;

/** Serializable world position used by minigame arenas and crash recovery. */
public final class MinigameLocation {
    public String dimension = "minecraft:overworld";
    public double x;
    public double y = 64.0D;
    public double z;
    public float yaw;
    public float pitch;

    public MinigameLocation() {
    }

    public MinigameLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        normalize();
    }

    public static MinigameLocation of(ServerPlayer player) {
        return new MinigameLocation(player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    public void normalize() {
        WorldPositionValues value = WorldPositionValues.normalize(dimension, x, y, z, yaw, pitch);
        dimension = value.dimension();
        x = value.x();
        y = value.y();
        z = value.z();
        yaw = value.yaw();
        pitch = value.pitch();
    }

    public MinigameLocation copy() {
        return new MinigameLocation(dimension, x, y, z, yaw, pitch);
    }
}
