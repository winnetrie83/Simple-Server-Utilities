package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional Create hook that validates the entire moving contraption body, not
 * only mounted actors such as drills and deployers.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public abstract class CreateWholeContraptionBoundaryMixin {

    @Inject(
            method = "tickActors",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/simibubi/create/content/contraptions/Contraption;stalled:Z",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            require = 0,
            remap = false)
    private void ssu$protectWholeContraptionBoundary(CallbackInfo ci) {
        CreateProtectionCompat.enforceWholeContraptionBoundary(this);
    }
}
