package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Deployers mounted on Create contraptions from crossing SSU protection boundaries. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour", remap = false)
public abstract class CreateMovingDeployerMixin {

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectMovingDeployerBoundary(@Coerce Object context, BlockPos target, CallbackInfo ci) {
        if (!CreateProtectionCompat.canMovingBreakerVisit(context, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$keepBoundaryBlockedDeployerStalled(@Coerce Object context, CallbackInfo ci) {
        if (CreateProtectionCompat.keepMovingBreakerStalled(context)) {
            ci.cancel();
        }
    }

    @Inject(method = "cancelStall", at = @At("HEAD"), require = 0, remap = false)
    private void ssu$clearDeployerBoundaryMarker(@Coerce Object context, CallbackInfo ci) {
        CreateProtectionCompat.clearMovingBreakerBoundaryState(context);
    }
}
