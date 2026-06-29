package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {

    @Redirect(
            method = "shouldSpreadLiquid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
            )
    )
    private FluidState ssu$hideForeignWaterFromLava(
            Level level,
            BlockPos checkPos,
            Level methodLevel,
            BlockPos lavaPos,
            BlockState lavaState
    ) {
        FluidState fluidState = level.getFluidState(checkPos);

        // De lava zelf moet vanilla blijven werken.
        if (checkPos.equals(lavaPos)) {
            return fluidState;
        }

        // Alleen water verbergen.
        if (!fluidState.is(FluidTags.WATER)) {
            return fluidState;
        }

        PlayerClaim lavaClaim = ProtectionHelper.getClaimAt(level, lavaPos);

        if (lavaClaim == null) {
            return fluidState;
        }

        PlayerClaim waterClaim = ProtectionHelper.getClaimAt(level, checkPos);

        if (waterClaim == null || !waterClaim.equals(lavaClaim)) {
            return Fluids.EMPTY.defaultFluidState();
        }

        return fluidState;
    }
}