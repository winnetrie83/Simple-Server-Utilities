package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional Create hook that prevents a newly assembled contraption from
 * capturing blocks across an SSU claim/region boundary.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.Contraption", remap = false)
public abstract class CreateContraptionAssemblyMixin {

    @Inject(method = "movementAllowed", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ssu$protectContraptionAssemblyBoundary(BlockState state, Level level, BlockPos candidate,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (!CreateProtectionCompat.canContraptionCapture(this, level, candidate)) {
            cir.setReturnValue(false);
        }
    }
}
