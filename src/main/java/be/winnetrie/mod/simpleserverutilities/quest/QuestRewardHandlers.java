package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentActionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentActionEngine;
import be.winnetrie.mod.simpleserverutilities.content.PreparedContentAction;
import be.winnetrie.mod.simpleserverutilities.core.transaction.SsuTransactionManager;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Quest-owned generic reward handlers that remain useful to later content modules. */
public final class QuestRewardHandlers {
    private QuestRewardHandlers() {}

    public static void register(ContentActionEngine engine) {
        if (!engine.isRegistered("give_money")) engine.register("give_money", (action, context, progression) -> money(action, context));
        if (!engine.isRegistered("give_item")) engine.register("give_item", (action, context, progression) -> item(action, context));
    }

    private static PreparedContentAction money(ContentAction action, ContentActionContext context) {
        ServerPlayer player = requirePlayer(context);
        long amount = positiveLong(action.parameter("amount_minor"), "amount_minor");
        long before = SimpleServerUtilities.ECONOMY.balance(player.getUUID());
        String key = context == null ? "" : context.idempotencyKey();
        return new PreparedContentAction("Give quest money", new SsuTransactionManager.TransactionStep() {
            @Override public void apply() throws Exception {
                EconomyResult result = SimpleServerUtilities.ECONOMY.credit(
                        null, "server", player.getUUID(), amount, "quests", "Quest reward", key + ":money");
                if (!result.successful()) throw new IllegalStateException(result.message());
            }
            @Override public void rollback() throws Exception {
                long current = SimpleServerUtilities.ECONOMY.balance(player.getUUID());
                if (current <= before) return;
                EconomyResult result = SimpleServerUtilities.ECONOMY.debit(
                        null, "server", player.getUUID(), current - before, "quests", "Quest reward rollback", "");
                if (!result.successful()) throw new IllegalStateException(result.message());
            }
        });
    }

    private static PreparedContentAction item(ContentAction action, ContentActionContext context) {
        ServerPlayer player = requirePlayer(context);
        String rawItem = required(action.parameter("item"), "item");
        int count = (int) Math.min(64_000L, positiveLong(action.parameter("count"), "count"));
        ItemStack template;
        try {
            template = BuiltInRegistries.ITEM.getOptional(Identifier.parse(rawItem))
                    .map(registeredItem -> new ItemStack(registeredItem)).orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid reward item: " + rawItem);
        }
        if (template.isEmpty()) throw new IllegalArgumentException("Unknown reward item: " + rawItem);
        List<ItemStack> incoming = split(template, count);
        InventoryPlan[] appliedPlan = new InventoryPlan[1];
        return new PreparedContentAction("Give quest item " + rawItem, new SsuTransactionManager.TransactionStep() {
            @Override public void apply() {
                // Plan against the inventory at commit time. Earlier item-reward steps in the same
                // transaction are then included instead of being overwritten by a stale snapshot.
                InventoryPlan plan = planInventory(player, incoming);
                if (plan == null) throw new IllegalArgumentException(
                        "The player does not have enough inventory space for this reward.");
                appliedPlan[0] = plan;
                applyInventory(player, plan.after());
            }
            @Override public void rollback() {
                InventoryPlan plan = appliedPlan[0];
                if (plan != null) applyInventory(player, plan.before());
            }
        });
    }

    private static List<ItemStack> split(ItemStack template, int count) {
        ArrayList<ItemStack> result = new ArrayList<>();
        int remaining = count;
        int maximum = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int move = Math.min(maximum, remaining);
            result.add(template.copyWithCount(move));
            remaining -= move;
        }
        return result;
    }

    private static InventoryPlan planInventory(ServerPlayer player, List<ItemStack> incoming) {
        final int slots = 36;
        ArrayList<ItemStack> before = new ArrayList<>(slots);
        ArrayList<ItemStack> after = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            ItemStack current = player.getInventory().getItem(i).copy();
            before.add(current.copy()); after.add(current);
        }
        for (ItemStack source : incoming) {
            ItemStack remaining = source.copy();
            for (ItemStack existing : after) {
                if (remaining.isEmpty()) break;
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                    int move = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                    if (move > 0) { existing.grow(move); remaining.shrink(move); }
                }
            }
            while (!remaining.isEmpty()) {
                int empty = -1;
                for (int i = 0; i < after.size(); i++) if (after.get(i).isEmpty()) { empty = i; break; }
                if (empty < 0) return null;
                int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                after.set(empty, remaining.copyWithCount(move)); remaining.shrink(move);
            }
        }
        return new InventoryPlan(List.copyOf(before), List.copyOf(after));
    }

    private static void applyInventory(ServerPlayer player, List<ItemStack> contents) {
        for (int i = 0; i < contents.size(); i++) player.getInventory().setItem(i, contents.get(i).copy());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static ServerPlayer requirePlayer(ContentActionContext context) {
        if (context == null || context.player() == null) throw new IllegalArgumentException("This reward requires a player.");
        return context.player();
    }
    private static String required(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing reward parameter: " + key);
        return value.trim();
    }
    private static long positiveLong(String value, String key) {
        try {
            long parsed = Long.parseLong(required(value, key));
            if (parsed <= 0L) throw new IllegalArgumentException("Reward parameter '" + key + "' must be positive.");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Reward parameter '" + key + "' must be a whole number.");
        }
    }
    private record InventoryPlan(List<ItemStack> before, List<ItemStack> after) {}
}
