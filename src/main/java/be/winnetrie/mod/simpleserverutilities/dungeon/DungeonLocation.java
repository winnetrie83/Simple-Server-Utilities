package be.winnetrie.mod.simpleserverutilities.dungeon;

import be.winnetrie.mod.simpleserverutilities.core.location.WorldPositionValues;
import net.minecraft.server.level.ServerPlayer;

/** Serializable world position used by dungeon arenas and crash recovery. */
public final class DungeonLocation {
    public String dimension = "minecraft:overworld";
    public double x;
    public double y = 64.0D;
    public double z;
    public float yaw;
    public float pitch;

    public DungeonLocation() {}

    public DungeonLocation(String dimension, double x, double y, double z, float yaw, float pitch) {
        this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        normalize();
    }

    public static DungeonLocation of(ServerPlayer player) {
        return new DungeonLocation(player.level().dimension().identifier().toString(),
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

    public DungeonLocation copy() { return new DungeonLocation(dimension, x, y, z, yaw, pitch); }
}
