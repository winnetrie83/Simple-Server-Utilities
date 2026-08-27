package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Covers Roller-specific world changes that bypass the shared moving-breaker
 * visit hook: its secondary tunnel destruction and its paving/fill placement.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour", remap = false)
public abstract class CreateRollerMovementMixin {

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectRollerSecondaryBreak(@Coerce Object context, BlockPos target, CallbackInfo ci) {
        if (!CreateProtectionCompat.canMovingActorAffect(context, target)) {
            ci.cancel();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "tryFill", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectRollerPaving(@Coerce Object context, BlockPos target, BlockState state,
                                         CallbackInfoReturnable cir) {
        if (CreateProtectionCompat.canMovingActorAffect(context, target)) {
            return;
        }
        Object fail = CreateProtectionCompat.createRollerPaveFailResult();
        if (fail != null) {
            cir.setReturnValue(fail);
        }
    }
}
