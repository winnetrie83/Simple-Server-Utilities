package be.winnetrie.mod.simpleserverutilities.network;

import net.minecraft.world.item.ItemStack;

/** One fixed visual loot slot carried by the NPC editor payloads. */
public record NpcEditorLootSlot(ItemStack item, int chanceHundredthPercent) {
    public NpcEditorLootSlot {
        item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
        if (!item.isEmpty()) item.setCount(Math.max(1, Math.min(item.getMaxStackSize(), item.getCount())));
        chanceHundredthPercent = Math.max(1, Math.min(10_000, chanceHundredthPercent));
    }

    public boolean configured() {
        return !item.isEmpty();
    }
}
