package be.winnetrie.mod.simpleserverutilities.protection;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent.FluidPlaceBlockEvent;

public class FluidProtectionEvents {

    private static final Direction[] VANILLA_LIQUID_DIRECTIONS = new Direction[]{
            Direction.DOWN,
            Direction.SOUTH,
            Direction.NORTH,
            Direction.EAST,
            Direction.WEST
    };

    @SubscribeEvent
    public static void onFluidPlaceBlock(FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }

        if (!isGeneratedFluidBlock(event.getNewState())) {
            return;
        }

        BlockPos targetPos = event.getPos();

        for (Direction direction : VANILLA_LIQUID_DIRECTIONS) {
            BlockPos sourcePos = targetPos.relative(direction.getOpposite());

            if (level.getFluidState(sourcePos).isEmpty()) {
                continue;
            }

            if (!canFluidAffect(level, sourcePos, targetPos)) {
                blockForeignFluidFormation(event, level);
                return;
            }
        }
    }

    private static boolean canFluidAffect(Level level, BlockPos sourcePos, BlockPos targetPos) {
        return ProtectionHelper.canFluidAffect(
                level,
                sourcePos,
                targetPos,
                level.getFluidState(sourcePos)
        );
    }

    private static void blockForeignFluidFormation(FluidPlaceBlockEvent event, Level level) {
        BlockState originalState = event.getOriginalState();

        event.setNewState(originalState);

        if (isFlowingLava(originalState)) {
            scheduleFluidRetick(event, level, originalState);
        }
    }

    private static void scheduleFluidRetick(FluidPlaceBlockEvent event, Level level, BlockState state) {
        if (state.getFluidState().isEmpty()) {
            return;
        }

        level.scheduleTick(
                event.getPos(),
                state.getFluidState().getType(),
                1
        );
    }

    private static boolean isGeneratedFluidBlock(BlockState state) {
        return state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.BASALT);
    }

    private static boolean isFlowingLava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA)
                && !state.getFluidState().isSource();
    }


}