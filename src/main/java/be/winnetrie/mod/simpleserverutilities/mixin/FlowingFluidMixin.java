package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(
            method = "spreadTo",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ssu$preventFluidSpread(
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            Direction direction,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        if (level.isClientSide()) {
            return;
        }

        BlockPos targetPos = pos;
        BlockPos sourcePos = targetPos.relative(direction.getOpposite());

        Region sourceRegion = ProtectionHelper.getRegionAt(level, sourcePos);
        Region targetRegion = ProtectionHelper.getRegionAt(level, targetPos);

        boolean allowed = ProtectionHelper.canFluidAffect(level, sourcePos, targetPos, fluidState);

        SimpleServerUtilities.LOGGER.info(
                "[SSU Fluid Debug] dir={} source={} sourceRegion={} target={} targetRegion={} fluid={} allowed={}",
                direction,
                sourcePos,
                sourceRegion == null ? "none" : sourceRegion.getName(),
                targetPos,
                targetRegion == null ? "none" : targetRegion.getName(),
                fluidState.getType(),
                allowed
        );

        if (!allowed) {
            ci.cancel();
        }
    }
}