package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preflights known Create controllers before they change their own world state
 * or clear their moved-contraption reference.
 */
@Pseudo
@Mixin(targets = {
        "com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity",
        "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity",
        "com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity"
}, remap = false)
public abstract class CreateControllerDisassemblyMixin {

    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$preflightControllerDisassembly(CallbackInfo ci) {
        if (!CreateProtectionCompat.canControllerDisassemble(this)) {
            ci.cancel();
        }
    }
}
