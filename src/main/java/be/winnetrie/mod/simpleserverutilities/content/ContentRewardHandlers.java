package be.winnetrie.mod.simpleserverutilities.content;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.transaction.SsuTransactionManager;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.PlayerPermissionData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Module-independent reward actions shared by quests, minigames, dungeons and
 * future Content Core features.
 *
 * <p>Registration belongs to Content Core so disabling Quests can never remove
 * rewards from another content module.</p>
 */
public final class ContentRewardHandlers {
    private ContentRewardHandlers() {
    }

    public static void register(ContentActionEngine engine) {
        if (!engine.isRegistered("give_money")) {
            engine.register("give_money", (action, context, progression) -> money(action, context));
        }
        if (!engine.isRegistered("give_item")) {
            engine.register("give_item", (action, context, progression) -> item(action, context));
        }
        if (!engine.isRegistered("add_claim_chunks")) {
            engine.register("add_claim_chunks", (action, context, progression) -> claimChunks(action, context));
        }
    }

    private static PreparedContentAction claimChunks(ContentAction action, ContentActionContext context) {
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
            throw new IllegalArgumentException("The permission system is disabled.");
        }
        ServerPlayer player = requirePlayer(context);
        int amount = (int) Math.min(1_000_000L, positiveLong(action.parameter("amount"), "amount"));
        String permission = PermissionKeys.CLAIMS_MAX_CHUNKS;
        PlayerPermissionData playerData = SimpleServerUtilities.PERMISSIONS.getPlayerData(player.getUUID());
        String previous = playerData == null ? null : playerData.getPermissions().get(permission);
        int currentBase = PermissionService.getInt(
                player,
                permission,
                Config.MAX_PLAYER_CLAIM_CHUNKS.get(),
                PermissionContext.global(player)
        );
        long target = Math.min(Integer.MAX_VALUE, Math.max(0L, (long) currentBase + amount));
        String value = Long.toString(target);
        return new PreparedContentAction("Add " + amount + " personal claim chunks", new SsuTransactionManager.TransactionStep() {
            @Override public void apply() {
                SimpleServerUtilities.PERMISSIONS.setPlayerPermission(player.getUUID(), permission, value);
            }

            @Override public void rollback() {
                if (previous == null) SimpleServerUtilities.PERMISSIONS.removePlayerPermission(player.getUUID(), permission);
                else SimpleServerUtilities.PERMISSIONS.setPlayerPermission(player.getUUID(), permission, previous);
            }
        });
    }

    private static PreparedContentAction money(ContentAction action, ContentActionContext context) {
        ServerPlayer player = requirePlayer(context);
        if (!SimpleServerUtilities.CORE.modules().isActive("economy")
                || !SimpleServerUtilities.ECONOMY.isEnabled()) {
            throw new IllegalArgumentException("The Economy module must be active to grant a money reward.");
        }
        long amount = positiveLong(action.parameter("amount_minor"), "amount_minor");
        long before = SimpleServerUtilities.ECONOMY.balance(player.getUUID());
        String module = sourceModule(context);
        String source = sourceLabel(context);
        String key = rewardStepKey(context, "money");
        return new PreparedContentAction("Give content money", new SsuTransactionManager.TransactionStep() {
            private boolean credited;

            @Override
            public void apply() throws Exception {
                EconomyResult result = SimpleServerUtilities.ECONOMY.credit(
                        null, "server", player.getUUID(), amount, module, source + " reward", key);
                if (!result.successful()) {
                    // A durable economy duplicate means this exact content action was
                    // already committed before a retry/restart. Treat it as applied so
                    // the outer content transaction remains idempotent.
                    if ("duplicate".equals(result.code())) {
                        credited = false;
                        return;
                    }
                    throw new IllegalStateException(result.message());
                }
                credited = true;
            }

            @Override
            public void rollback() throws Exception {
                if (!credited) return;
                long current = SimpleServerUtilities.ECONOMY.balance(player.getUUID());
                long rollbackAmount = Math.min(amount, Math.max(0L, current - before));
                if (rollbackAmount <= 0L) return;
                EconomyResult result = SimpleServerUtilities.ECONOMY.debit(
                        null, "server", player.getUUID(), rollbackAmount, module,
                        source + " reward rollback", key.isBlank() ? "" : key + ":rollback");
                if (!result.successful() && !"duplicate".equals(result.code())) {
                    throw new IllegalStateException(result.message());
                }
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
        return new PreparedContentAction("Give content item " + rawItem, new SsuTransactionManager.TransactionStep() {
            @Override
            public void apply() {
                // Plan against the inventory at commit time. Earlier item-reward steps
                // in the same transaction are included rather than overwritten by a
                // stale inventory snapshot.
                InventoryPlan plan = planInventory(player, incoming);
                if (plan == null) {
                    throw new IllegalArgumentException(
                            "The player does not have enough inventory space for this reward.");
                }
                appliedPlan[0] = plan;
                applyInventory(player, plan.after());
            }

            @Override
            public void rollback() {
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
            before.add(current.copy());
            after.add(current);
        }
        for (ItemStack source : incoming) {
            ItemStack remaining = source.copy();
            for (ItemStack existing : after) {
                if (remaining.isEmpty()) break;
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                    int move = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                    if (move > 0) {
                        existing.grow(move);
                        remaining.shrink(move);
                    }
                }
            }
            while (!remaining.isEmpty()) {
                int empty = -1;
                for (int i = 0; i < after.size(); i++) {
                    if (after.get(i).isEmpty()) {
                        empty = i;
                        break;
                    }
                }
                if (empty < 0) return null;
                int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                after.set(empty, remaining.copyWithCount(move));
                remaining.shrink(move);
            }
        }
        return new InventoryPlan(List.copyOf(before), List.copyOf(after));
    }

    private static void applyInventory(ServerPlayer player, List<ItemStack> contents) {
        for (int i = 0; i < contents.size(); i++) {
            player.getInventory().setItem(i, contents.get(i).copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static ServerPlayer requirePlayer(ContentActionContext context) {
        if (context == null || context.player() == null) {
            throw new IllegalArgumentException("This reward requires a player.");
        }
        return context.player();
    }

    private static String sourceModule(ContentActionContext context) {
        return context == null || context.sourceModule().isBlank() ? "content" : context.sourceModule();
    }

    private static String sourceLabel(ContentActionContext context) {
        if (context == null || context.sourceId().isBlank()) return "Content";
        return context.sourceId();
    }

    private static String rewardStepKey(ContentActionContext context, String kind) {
        String base = context == null ? "" : context.idempotencyKey();
        return base.isBlank() ? "" : base + ":" + kind;
    }

    private static String required(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing reward parameter: " + key);
        }
        return value.trim();
    }

    private static long positiveLong(String value, String key) {
        try {
            long parsed = Long.parseLong(required(value, key));
            if (parsed <= 0L) {
                throw new IllegalArgumentException("Reward parameter '" + key + "' must be positive.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Reward parameter '" + key + "' must be a whole number.");
        }
    }

    private record InventoryPlan(List<ItemStack> before, List<ItemStack> after) {
    }
}
