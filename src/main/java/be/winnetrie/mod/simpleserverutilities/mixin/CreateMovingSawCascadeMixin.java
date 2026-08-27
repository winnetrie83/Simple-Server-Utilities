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

/** Tracks the source of Create's synchronous moving-saw tree destruction. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.saw.SawMovementBehaviour", remap = false)
public abstract class CreateMovingSawCascadeMixin {

    @Inject(method = "onBlockBroken", at = @At("HEAD"), require = 0, remap = false)
    private void ssu$beginMovingSawCascade(@Coerce Object context, BlockPos brokenPos, BlockState brokenState,
                                            CallbackInfo ci) {
        CreateProtectionCompat.beginMovingSawCascade(context, brokenPos);
    }

    @Inject(method = "onBlockBroken", at = @At("RETURN"), require = 0, remap = false)
    private void ssu$endMovingSawCascade(@Coerce Object context, BlockPos brokenPos, BlockState brokenState,
                                          CallbackInfo ci) {
        CreateProtectionCompat.endSawCascade();
    }
}
