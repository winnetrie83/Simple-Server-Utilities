package be.winnetrie.mod.simpleserverutilities.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Server-safe bridge for applying item components to vanilla block entities. */
@Mixin(BlockEntity.class)
public interface BlockEntityComponentInvoker {
    @Invoker("applyComponentsFromItemStack")
    void ssu$applyComponentsFromItemStack(ItemStack stack);
}
