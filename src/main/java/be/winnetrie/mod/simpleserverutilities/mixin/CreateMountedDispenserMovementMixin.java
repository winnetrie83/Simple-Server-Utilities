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
 * Vanilla dispensers/droppers mounted on Create contraptions do not execute
 * through vanilla DispenserBlock/DropperBlock. Protect Create's movement
 * behaviours directly so mounted bonemeal, buckets, TNT, projectiles and item
 * drops cannot act across an SSU protection boundary.
 */
@Pseudo
@Mixin(targets = {
        "com.simibubi.create.content.contraptions.behaviour.dispenser.DispenserMovementBehaviour",
        "com.simibubi.create.content.contraptions.behaviour.dispenser.DropperMovementBehaviour"
}, remap = false)
public abstract class CreateMountedDispenserMovementMixin {

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectMountedDispense(@Coerce Object context, BlockPos target, CallbackInfo ci) {
        if (!CreateProtectionCompat.canMovingActorAffect(context, target)) {
            ci.cancel();
        }
    }
}
