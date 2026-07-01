package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(
            method = "checkBurnOut",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ssu$preventFireBurnOut(
            Level level,
            BlockPos pos,
            int chance,
            RandomSource random,
            int age,
            Direction direction,
            CallbackInfo ci
    ) {
        if (level.isClientSide()) {
            return;
        }

        if (!ProtectionHelper.canFireAffect(level, pos)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private boolean ssu$preventFireSetBlock(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            int flags
    ) {
        if (!ProtectionHelper.canFireAffect(level, pos)) {
            return false;
        }

        return level.setBlock(pos, state, flags);
    }
}