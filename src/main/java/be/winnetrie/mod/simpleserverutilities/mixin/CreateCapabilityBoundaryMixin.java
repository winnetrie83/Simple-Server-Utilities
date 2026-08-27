package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Create's generic adjacent-capability discovery from reaching across
 * SSU claim/region boundaries. This covers inventory manipulation used by
 * funnels and other Create logistics built on CapManipulationBehaviourBase.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase", remap = false)
public abstract class CreateCapabilityBoundaryMixin {

    @Inject(method = "findNewCapability", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectCapabilityBoundary(CallbackInfo ci) {
        if (!CreateProtectionCompat.canCapabilityBehaviourConnect(this)) {
            ci.cancel();
        }
    }
}
