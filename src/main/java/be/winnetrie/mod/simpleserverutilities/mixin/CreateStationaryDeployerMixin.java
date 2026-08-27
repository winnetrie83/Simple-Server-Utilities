package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents stationary Create Deployers from acting across an SSU boundary. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity", remap = false)
public abstract class CreateStationaryDeployerMixin {

    @Inject(method = "activate", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectStationaryDeployerBoundary(CallbackInfo ci) {
        if (!CreateProtectionCompat.canStationaryDeployerActivate(this)) {
            ci.cancel();
        }
    }
}
