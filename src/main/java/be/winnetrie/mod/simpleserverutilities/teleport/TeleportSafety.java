package be.winnetrie.mod.simpleserverutilities.teleport;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolves a nearby two-block-high safe standing position. This deliberately
 * favours correctness over teleporting into a wall, void, lava surface or
 * outside the world border.
 */
public final class TeleportSafety {

    public static final int DEFAULT_VERTICAL_SEARCH_RANGE = 8;

    private TeleportSafety() {
    }

    public static Optional<TeleportDestination> findSafeDestination(
            ServerLevel level,
            double x,
            double y,
            double z
    ) {
        return findSafeDestination(level, x, y, z, DEFAULT_VERTICAL_SEARCH_RANGE);
    }

    public static Optional<TeleportDestination> findSafeDestination(
            ServerLevel level,
            double x,
            double y,
            double z,
            int verticalSearchRange
    ) {
        int blockX = Mth.floor(x);
        int blockY = Mth.floor(y);
        int blockZ = Mth.floor(z);
        BlockPos requested = new BlockPos(blockX, blockY, blockZ);

        if (!level.getWorldBorder().isWithinBounds(requested)) {
            return Optional.empty();
        }

        // A teleport may target an unloaded home or warp. Loading one target
        // chunk on demand is preferable to evaluating unloaded block states.
        level.getChunkAt(requested);

        int range = Math.max(0, verticalSearchRange);
        for (int offset = 0; offset <= range; offset++) {
            if (offset == 0) {
                Optional<TeleportDestination> exact = check(level, requested);
                if (exact.isPresent()) {
                    return exact;
                }
                continue;
            }

            Optional<TeleportDestination> above = check(level, requested.above(offset));
            if (above.isPresent()) {
                return above;
            }

            Optional<TeleportDestination> below = check(level, requested.below(offset));
            if (below.isPresent()) {
                return below;
            }
        }

        return Optional.empty();
    }

    private static Optional<TeleportDestination> check(
            ServerLevel level,
            BlockPos feetPos
    ) {
        BlockPos headPos = feetPos.above();
        BlockPos floorPos = feetPos.below();

        if (!level.isInsideBuildHeight(floorPos)
                || !level.isInsideBuildHeight(headPos)
                || !level.getWorldBorder().isWithinBounds(feetPos)) {
            return Optional.empty();
        }

        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(headPos);
        BlockState floorState = level.getBlockState(floorPos);

        if (!feetState.getCollisionShape(level, feetPos).isEmpty()
                || !headState.getCollisionShape(level, headPos).isEmpty()
                || !feetState.getFluidState().isEmpty()
                || !headState.getFluidState().isEmpty()
                || !floorState.isFaceSturdy(level, floorPos, Direction.UP)) {
            return Optional.empty();
        }

        // Centering avoids placing the player partly inside a neighbouring wall
        // when an old home or warp was saved very close to a block edge.
        return Optional.of(new TeleportDestination(
                feetPos.getX() + 0.5,
                feetPos.getY(),
                feetPos.getZ() + 0.5
        ));
    }
}
