package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional Create hook. Safe to load when Create is absent. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity", remap = false)
public abstract class CreateStationaryBlockBreakerMixin {

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectStationaryBreakerBoundary(BlockState stateToBreak, float blockHardness,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!CreateProtectionCompat.canStationaryBreakerAffect(this)) {
            cir.setReturnValue(false);
        }
    }
}
