package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
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
        BlockPos sourcePos = pos.relative(direction.getOpposite());

        PlayerClaim targetClaim = ProtectionHelper.getClaimAt(level, targetPos);

        if (targetClaim == null) {
            return;
        }

        PlayerClaim sourceClaim = ProtectionHelper.getClaimAt(level, sourcePos);

        // Allow fluid flow inside the same claim
        if (sourceClaim != null && sourceClaim.equals(targetClaim)) {
            return;
        }

        if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
            if (!targetClaim.getSettings().isAllowWaterFlow()) {
                ci.cancel();
            }
            return;
        }

        if (fluidState.is(Fluids.LAVA) || fluidState.is(Fluids.FLOWING_LAVA)) {
            if (!targetClaim.getSettings().isAllowLavaFlow()) {
                ci.cancel();
            }
            return;
        }

        if (!targetClaim.getSettings().isAllowOtherFluidFlow()) {
            ci.cancel();
        }
    }
}