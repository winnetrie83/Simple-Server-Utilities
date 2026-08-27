package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Create harvesters from modifying crops/vegetation across an SSU boundary. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour", remap = false)
public abstract class CreateHarvesterMovementMixin {

    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectHarvesterBoundary(@Coerce Object context, BlockPos target, CallbackInfo ci) {
        if (!CreateProtectionCompat.canMovingActorAffect(context, target)) {
            ci.cancel();
        }
    }
}
