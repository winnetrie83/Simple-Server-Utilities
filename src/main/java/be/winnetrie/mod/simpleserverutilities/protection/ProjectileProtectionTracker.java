package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the server-side origin of live projectiles. ProjectileImpactEvent only
 * gives SSU the eventual impact point; retaining the origin prevents a
 * dispenser/mob projectile from losing its protection context while flying.
 */
public final class ProjectileProtectionTracker {

    private static final Map<UUID, Origin> ORIGINS = new ConcurrentHashMap<>();

    private ProjectileProtectionTracker() {
    }

    public static void track(Projectile projectile) {
        if (projectile == null || projectile.level().isClientSide()) {
            return;
        }
        ORIGINS.putIfAbsent(projectile.getUUID(),
                new Origin(projectile.level().dimension(), projectile.blockPosition().immutable()));
    }

    public static void forget(Projectile projectile) {
        if (projectile != null) {
            ORIGINS.remove(projectile.getUUID());
        }
    }

    public static BlockPos origin(Projectile projectile) {
        if (projectile == null) {
            return null;
        }
        Origin origin = ORIGINS.get(projectile.getUUID());
        if (origin == null || !origin.dimension().equals(projectile.level().dimension())) {
            return projectile.blockPosition();
        }
        return origin.pos();
    }

    public static boolean canReach(Projectile projectile, BlockPos target) {
        if (projectile == null || target == null) {
            return true;
        }
        Level level = projectile.level();
        if (level.isClientSide()) {
            return true;
        }
        BlockPos source = origin(projectile);
        return source == null || ProtectionBoundary.canCross(level, source, target);
    }

    private record Origin(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
