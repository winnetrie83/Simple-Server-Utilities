package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
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

        if (!ProtectionHelper.canFluidAffect(level, sourcePos, targetPos, fluidState)) {
            ci.cancel();
        }
    }
}