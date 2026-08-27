package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Last-resort Create disassembly guard. Controller-level hooks run first for
 * controllers that clear their entity reference after disassembly; this base
 * hook also covers self-managed contraptions such as gantries.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class CreateContraptionDisassemblyMixin {

    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$preflightContraptionDisassembly(CallbackInfo ci) {
        if (!CreateProtectionCompat.canContraptionDisassemble(this)) {
            ci.cancel();
        }
    }
}
