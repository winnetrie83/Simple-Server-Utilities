package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.protection.ProtectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin extends Item {

    @Shadow
    @Final
    public Fluid content;

    public BucketItemMixin(Properties properties) {
        super(properties);
    }

    @Inject(
            method = "use",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ssu$preventBucketUse(
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (level.isClientSide()) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        BlockHitResult hitResult = getPlayerPOVHitResult(
                level,
                player,
                this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE
        );

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos clickedPos = hitResult.getBlockPos();
        BlockPos placePos = clickedPos.relative(hitResult.getDirection());

        BlockPos targetPos = this.content == Fluids.EMPTY
                ? clickedPos      // empty bucket = pickup
                : placePos;       // filled bucket = place

        if (ProtectionHelper.canPlayerModify(serverPlayer, level, targetPos)) {
            return;
        }

        cir.setReturnValue(InteractionResult.FAIL);
    }
}