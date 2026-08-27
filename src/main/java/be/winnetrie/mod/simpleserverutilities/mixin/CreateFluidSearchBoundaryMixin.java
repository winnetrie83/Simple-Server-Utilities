package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes foreign protected fluid positions invisible to Create's hose-pulley
 * flood search, preventing the search queue itself from crossing an SSU boundary.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour", remap = false)
public abstract class CreateFluidSearchBoundaryMixin {

    @Redirect(
            method = "search",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
                    remap = true
            ),
            require = 0,
            remap = false
    )
    private FluidState ssu$hideFluidAcrossBoundary(Level level, BlockPos target) {
        if (!CreateProtectionCompat.canFluidManipulationAffect(this, level, target)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return level.getFluidState(target);
    }
}
