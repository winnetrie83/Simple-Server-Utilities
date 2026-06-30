package be.winnetrie.mod.simpleserverutilities.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class RegionSelection {

    private ResourceKey<Level> dimension;
    private BlockPos point1;
    private BlockPos point2;

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getPoint1() {
        return point1;
    }

    public BlockPos getPoint2() {
        return point2;
    }

    public void setPoint1(ResourceKey<Level> dimension, BlockPos point1) {
        this.dimension = dimension;
        this.point1 = point1;
    }

    public void setPoint2(ResourceKey<Level> dimension, BlockPos point2) {
        this.dimension = dimension;
        this.point2 = point2;
    }

    public boolean isComplete() {
        return point1 != null && point2 != null && dimension != null;
    }
}