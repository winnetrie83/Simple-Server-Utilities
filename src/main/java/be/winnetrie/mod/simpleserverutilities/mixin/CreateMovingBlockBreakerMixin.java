package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional Create hook for block-breaking MovementBehaviours (mounted Drill,
 * Saw and subclasses using Create's shared breaker implementation).
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour", remap = false)
public abstract class CreateMovingBlockBreakerMixin {

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectMovingBreakerBoundary(@Coerce Object context, BlockPos target, CallbackInfo ci) {
        if (!CreateProtectionCompat.canMovingBreakerVisit(context, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickBreaker", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$keepBoundaryBlockedBreakerStalled(@Coerce Object context, CallbackInfo ci) {
        if (CreateProtectionCompat.keepMovingBreakerStalled(context)) {
            ci.cancel();
        }
    }

    @Inject(method = "cancelStall", at = @At("HEAD"), require = 0, remap = false)
    private void ssu$clearBoundaryMarkerOnCancel(@Coerce Object context, CallbackInfo ci) {
        CreateProtectionCompat.clearMovingBreakerBoundaryState(context);
    }
}
