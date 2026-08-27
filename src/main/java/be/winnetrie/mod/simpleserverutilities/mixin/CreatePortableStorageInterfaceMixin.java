package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks Create item/fluid Portable Storage Interface links across SSU boundaries. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity", remap = false)
public abstract class CreatePortableStorageInterfaceMixin {

    @Inject(method = "startTransferringTo", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectPortableInterfaceBoundary(@Coerce Object contraption, float distance, CallbackInfo ci) {
        if (!CreateProtectionCompat.canPortableInterfaceConnect(this, contraption)) {
            ci.cancel();
        }
    }
}
