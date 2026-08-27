package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracks the source of Create's synchronous stationary-saw tree destruction. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.saw.SawBlockEntity", remap = false)
public abstract class CreateStationarySawCascadeMixin {

    @Inject(method = "onBlockBroken", at = @At("HEAD"), require = 0, remap = false)
    private void ssu$beginStationarySawCascade(BlockState brokenState, CallbackInfo ci) {
        CreateProtectionCompat.beginStationarySawCascade(this);
    }

    @Inject(method = "onBlockBroken", at = @At("RETURN"), require = 0, remap = false)
    private void ssu$endStationarySawCascade(BlockState brokenState, CallbackInfo ci) {
        CreateProtectionCompat.endSawCascade();
    }
}
