package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Treats positions across an SSU boundary as blocked space for Create fluid filling. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour", remap = false)
public abstract class CreateFluidFillingBoundaryMixin {

    @Inject(method = "getAtPos", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$blockFluidFillAcrossBoundary(Level level, BlockPos target, Fluid fluid,
                                                   CallbackInfoReturnable<Object> cir) {
        if (CreateProtectionCompat.canFluidManipulationAffect(this, level, target)) {
            return;
        }
        Object blocking = CreateProtectionCompat.createFluidBlockingSpaceType();
        if (blocking != null) {
            cir.setReturnValue(blocking);
        }
    }
}
