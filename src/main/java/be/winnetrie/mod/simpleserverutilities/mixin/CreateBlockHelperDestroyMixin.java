package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.compat.create.CreateProtectionCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Filters Create's queued secondary block destruction one block at a time.
 * This is used by Mechanical Saw tree-felling so a tree cannot carry the saw's
 * effect through a claim or region boundary after the first log is cut.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.utility.BlockHelper", remap = false)
public abstract class CreateBlockHelperDestroyMixin {

    @Inject(method = "destroyBlockAs", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ssu$protectQueuedCreateDestruction(Level level, BlockPos target, Player player,
                                                            ItemStack usedTool, float effectChance,
                                                            Consumer<ItemStack> droppedItemCallback,
                                                            CallbackInfo ci) {
        if (!CreateProtectionCompat.canCreateAutomationDestroy(level, target)) {
            ci.cancel();
        }
    }
}
