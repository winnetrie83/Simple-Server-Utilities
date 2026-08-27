package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Create ploughs from breaking/tilling blocks across an SSU boundary. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour", remap = false)
public abstract class CreatePloughMovementMixin {

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectPloughBoundary(@Coerce Object context, BlockPos target, CallbackInfo ci) {
        if (!CreateProtectionCompat.canMovingActorAffect(context, target)
                || !CreateProtectionCompat.canMovingActorAffect(context, target.below())) {
            ci.cancel();
        }
    }
}
