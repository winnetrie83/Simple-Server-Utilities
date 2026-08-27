package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.protection.ProtectionBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla droppers from transferring items across an SSU boundary. */
@Mixin(DropperBlock.class)
public abstract class DropperBlockMixin {

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true, require = 0)
    private void ssu$protectDropperBoundary(ServerLevel level, BlockState state, BlockPos source,
                                             CallbackInfo ci) {
        Direction facing = state.getValue(DispenserBlock.FACING);
        BlockPos target = source.relative(facing);
        if (!ProtectionBoundary.canCross(level, source, target)) {
            ci.cancel();
        }
    }
}
