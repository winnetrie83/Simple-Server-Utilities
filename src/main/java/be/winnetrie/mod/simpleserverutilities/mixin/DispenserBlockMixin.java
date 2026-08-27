package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.protection.ProtectionBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Treats a vanilla dispenser as an automation source at its own block
 * position. No dispense behaviour may affect the cell in front when that
 * transition crosses an SSU claim/region boundary.
 *
 * <p>Guarding the common dispense entry point protects all vanilla dispenser
 * behaviours at once (bonemeal, buckets, flint and steel, TNT, shears,
 * projectiles, boats, etc.) instead of maintaining an item-specific list.</p>
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true, require = 0)
    private void ssu$protectDispenseBoundary(ServerLevel level, BlockState state, BlockPos source,
                                              CallbackInfo ci) {
        Direction facing = state.getValue(DispenserBlock.FACING);
        BlockPos target = source.relative(facing);
        if (!ProtectionBoundary.canCross(level, source, target)) {
            ci.cancel();
        }
    }
}
