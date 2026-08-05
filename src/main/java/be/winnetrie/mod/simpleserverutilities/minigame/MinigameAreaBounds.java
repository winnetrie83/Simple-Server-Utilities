package be.winnetrie.mod.simpleserverutilities.minigame;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Optional serializable cuboid used for mode-specific arena areas. */
public final class MinigameAreaBounds {
    public String dimension = "";
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;

    public MinigameAreaBounds() {
    }

    public MinigameAreaBounds(String dimension, BlockPos first, BlockPos second) {
        this.dimension = dimension == null ? "" : dimension;
        this.minX = Math.min(first.getX(), second.getX());
        this.minY = Math.min(first.getY(), second.getY());
        this.minZ = Math.min(first.getZ(), second.getZ());
        this.maxX = Math.max(first.getX(), second.getX());
        this.maxY = Math.max(first.getY(), second.getY());
        this.maxZ = Math.max(first.getZ(), second.getZ());
        normalize();
    }

    public boolean configured() {
        return dimension != null && !dimension.isBlank();
    }

    public void normalize() {
        dimension = dimension == null ? "" : dimension.trim().toLowerCase(java.util.Locale.ROOT);
        if (!configured()) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
            return;
        }
        int x1 = Math.min(minX, maxX), x2 = Math.max(minX, maxX);
        int y1 = Math.min(minY, maxY), y2 = Math.max(minY, maxY);
        int z1 = Math.min(minZ, maxZ), z2 = Math.max(minZ, maxZ);
        minX = x1; maxX = x2;
        minY = y1; maxY = y2;
        minZ = z1; maxZ = z2;
    }

    public boolean contains(ResourceKey<Level> level, BlockPos pos) {
        return configured() && level.identifier().toString().equals(dimension)
                && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean contains(MinigameLocation location) {
        return configured() && location != null && dimension.equals(location.dimension)
                && location.x >= minX && location.x < maxX + 1.0D
                && location.y >= minY && location.y < maxY + 1.0D
                && location.z >= minZ && location.z < maxZ + 1.0D;
    }

    public long volume() {
        if (!configured()) return 0L;
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String compact() {
        return configured() ? minX + ", " + minY + ", " + minZ + " -> "
                + maxX + ", " + maxY + ", " + maxZ : "Not configured";
    }
}
