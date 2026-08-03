package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Exact, rollback-capable 36-slot player inventory planning for shop transactions. */
final class NpcShopInventory {
    private static final int STORAGE_SLOTS = 36;
    private NpcShopInventory() {}

    static Plan planAdd(ServerPlayer player, ItemStack template, int quantity) {
        if (player == null || template == null || template.isEmpty() || quantity <= 0) return null;
        List<ItemStack> before = snapshot(player), after = copy(before);
        ItemStack remaining = template.copyWithCount(quantity);
        for (ItemStack existing : after) {
            if (remaining.isEmpty()) break;
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                int move = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (move > 0) { existing.grow(move); remaining.shrink(move); }
            }
        }
        while (!remaining.isEmpty()) {
            int empty = firstEmpty(after);
            if (empty < 0) return null;
            int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            after.set(empty, remaining.copyWithCount(move));
            remaining.shrink(move);
        }
        return new Plan(before, List.copyOf(after));
    }

    static Plan planRemove(ServerPlayer player, ItemStack template, int quantity) {
        if (player == null || template == null || template.isEmpty() || quantity <= 0) return null;
        List<ItemStack> before = snapshot(player), after = copy(before);
        int remaining = quantity;
        for (int index = 0; index < after.size() && remaining > 0; index++) {
            ItemStack existing = after.get(index);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, template)) continue;
            int move = Math.min(remaining, existing.getCount());
            existing.shrink(move);
            remaining -= move;
            if (existing.isEmpty()) after.set(index, ItemStack.EMPTY);
        }
        return remaining == 0 ? new Plan(before, List.copyOf(after)) : null;
    }

    static Plan planRemoveSlot(ServerPlayer player, int slot, ItemStack expected, int quantity) {
        if (player == null || slot < 0 || slot >= STORAGE_SLOTS || expected == null || expected.isEmpty() || quantity <= 0) return null;
        List<ItemStack> before = snapshot(player), after = copy(before);
        ItemStack existing = after.get(slot);
        if (existing.isEmpty() || existing.getCount() < quantity
                || !ItemStack.isSameItemSameComponents(existing, expected)) return null;
        existing.shrink(quantity);
        if (existing.isEmpty()) after.set(slot, ItemStack.EMPTY);
        return new Plan(before, List.copyOf(after));
    }

    static void apply(ServerPlayer player, List<ItemStack> contents) {
        for (int index = 0; index < STORAGE_SLOTS; index++) {
            player.getInventory().setItem(index, contents.get(index).copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static List<ItemStack> snapshot(ServerPlayer player) {
        ArrayList<ItemStack> result = new ArrayList<>(STORAGE_SLOTS);
        for (int index = 0; index < STORAGE_SLOTS; index++) result.add(player.getInventory().getItem(index).copy());
        return List.copyOf(result);
    }
    private static List<ItemStack> copy(List<ItemStack> input) {
        ArrayList<ItemStack> result = new ArrayList<>(input.size());
        for (ItemStack stack : input) result.add(stack.copy());
        return result;
    }
    private static int firstEmpty(List<ItemStack> contents) {
        for (int index = 0; index < contents.size(); index++) if (contents.get(index).isEmpty()) return index;
        return -1;
    }

    record Plan(List<ItemStack> before, List<ItemStack> after) {}
}
