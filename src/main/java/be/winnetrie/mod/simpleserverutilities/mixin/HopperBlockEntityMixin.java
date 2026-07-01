package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(
            method = "ejectItems",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ssu$preventHopperEject(
            Level level,
            BlockPos hopperPos,
            HopperBlockEntity hopper,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (level.isClientSide()) {
            return;
        }

        BlockState state = level.getBlockState(hopperPos);

        if (!state.hasProperty(HopperBlock.FACING)) {
            return;
        }

        BlockPos targetPos = hopperPos.relative(state.getValue(HopperBlock.FACING));

        if (!ProtectionHelper.canHopperTransfer(level, hopperPos, targetPos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "suckInItems",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ssu$preventHopperSuck(
            Level level,
            Hopper hopper,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (level.isClientSide()) {
            return;
        }

        if (!(hopper instanceof HopperBlockEntity hopperBlockEntity)) {
            return;
        }

        BlockPos hopperPos = hopperBlockEntity.getBlockPos();
        BlockPos sourcePos = hopperPos.above();

        if (!ProtectionHelper.canHopperTransfer(level, sourcePos, hopperPos)) {
            cir.setReturnValue(false);
        }
    }
}