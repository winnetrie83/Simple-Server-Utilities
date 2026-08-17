package be.winnetrie.mod.simpleserverutilities.core.job;

import java.util.Locale;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Stable resource keys used to prevent conflicting long-running jobs. */
public final class SsuJobLocks {

    private SsuJobLocks() {
    }

    public static String region(ResourceKey<Level> dimension, String regionName) {
        return "region:"
                + dimension.location()
                + ":"
                + normalize(regionName);
    }

    public static String cuboid(
            ResourceKey<Level> dimension,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        return "cuboid:"
                + dimension.location()
                + ":"
                + minX + "," + minY + "," + minZ
                + ":"
                + maxX + "," + maxY + "," + maxZ;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
