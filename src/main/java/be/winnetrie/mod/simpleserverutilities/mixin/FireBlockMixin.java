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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes vanilla fire obey the same source -> target firewall as fluids,
 * pistons and automation. The per-area allowFireSpread flag is still applied
 * after the boundary check succeeds.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    private static final ThreadLocal<BlockPos> SSU_FIRE_TICK_SOURCE = new ThreadLocal<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void ssu$rememberFireSource(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    ) {
        if (!level.isClientSide()) {
            SSU_FIRE_TICK_SOURCE.set(pos.immutable());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ssu$forgetFireSource(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    ) {
        SSU_FIRE_TICK_SOURCE.remove();
    }

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

        // checkBurnOut is invoked from the current fire tick. Prefer the exact
        // ticking fire position captured at tick HEAD instead of reconstructing
        // it from directional arguments, whose semantics can vary between
        // mappings/versions. If no tick context exists, retain the legacy
        // target-only area policy as a safe fallback.
        BlockPos source = SSU_FIRE_TICK_SOURCE.get();
        boolean allowed = source == null
                ? ProtectionHelper.canFireAffect(level, pos)
                : ProtectionHelper.canFireAffect(level, source, pos);
        if (!allowed) {
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
            BlockPos targetPos,
            BlockState state,
            int flags
    ) {
        BlockPos source = SSU_FIRE_TICK_SOURCE.get();
        boolean allowed = source == null
                ? ProtectionHelper.canFireAffect(level, targetPos)
                : ProtectionHelper.canFireAffect(level, source, targetPos);
        if (!allowed) {
            return false;
        }

        return level.setBlock(targetPos, state, flags);
    }
}
