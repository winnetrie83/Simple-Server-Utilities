package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcEditorService;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInstance;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopDefinition;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopEntry;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopEditorService;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Admin/debug commands for the NPC foundation; normal creation is performed with the GUI tool. */
public final class NpcCommands {
    private NpcCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("npc")
                .requires(NpcCommands::canAdmin)
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("tool").executes(context -> tool(context.getSource())))
                .then(Commands.literal("manage").executes(context -> manage(context.getSource())))
                .then(Commands.literal("refresh").executes(context -> refresh(context.getSource())))
                .then(shopCommands())
                .then(Commands.literal("edit")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .executes(context -> edit(context.getSource(),
                                        StringArgumentType.getString(context, "instance")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .executes(context -> delete(context.getSource(),
                                        StringArgumentType.getString(context, "instance")))))
                .then(Commands.literal("copy")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .executes(context -> copy(context.getSource(),
                                        StringArgumentType.getString(context, "instance")))))
                .then(Commands.literal("paste").executes(context -> paste(context.getSource())))
                .then(Commands.literal("respawn")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .executes(context -> respawn(context.getSource(), StringArgumentType.getString(context, "instance")))))
                .then(Commands.literal("bring")
                        .then(Commands.argument("instance", StringArgumentType.word())
                                .executes(context -> bring(context.getSource(), StringArgumentType.getString(context, "instance")))));
    }

    private static boolean canAdmin(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer player)
                || NpcEditorService.canAdmin(player);
    }

    private static int list(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("SSU NPC placements ("
                + SimpleServerUtilities.NPCS.instances().size() + "):"));
        for (NpcInstance instance : SimpleServerUtilities.NPCS.instances()) {
            NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
            source.sendSystemMessage(Component.literal(" - " + instance.id + " | "
                    + (definition == null ? instance.definitionId : definition.displayName)
                    + " [" + instance.definitionId + "] " + instance.dimension + " @ "
                    + one(instance.x) + ", " + one(instance.y) + ", " + one(instance.z)));
        }
        return 1;
    }

    private static int tool(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        SimpleServerUtilities.NPC_TOOLS.giveTool(player);
        player.sendSystemMessage(Component.literal(
                "NPC Tool: right-click empty space for the NPC Manager; right-click an NPC to edit; sneak-right-click an NPC to copy; sneak-right-click elsewhere to paste."));
        return 1;
    }

    private static int manage(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        SimpleServerUtilities.NPC_TOOLS.openManager(player);
        return 1;
    }

    private static int refresh(CommandSourceStack source) {
        SimpleServerUtilities.NPCS.refreshAll();
        source.sendSystemMessage(Component.literal("NPC placements reconciled with their saved templates."));
        return 1;
    }

    private static int edit(CommandSourceStack source, String rawId) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (!NpcEditorService.openEditor(player, rawId)) {
            source.sendFailure(Component.literal("Unknown NPC placement or the NPC module is disabled: " + rawId));
            return 0;
        }
        return 1;
    }

    private static int delete(CommandSourceStack source, String rawId) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawId);
        if (instance == null || !SimpleServerUtilities.NPCS.deleteInstance(instance.id)) {
            source.sendFailure(Component.literal("Unknown NPC placement: " + rawId));
            return 0;
        }
        be.winnetrie.mod.simpleserverutilities.quest.QuestNpcBridge.unlinkDeletedNpc(
                SimpleServerUtilities.QUESTS, SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS, instance.id);
        SimpleServerUtilities.NPCS.syncAll();
        source.sendSystemMessage(Component.literal("Deleted NPC placement " + rawId + ". Simple quest links were cleared; the reusable template was kept."));
        return 1;
    }

    private static int copy(CommandSourceStack source, String rawId) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawId);
        if (instance == null) {
            source.sendFailure(Component.literal("Unknown NPC placement: " + rawId));
            return 0;
        }
        SimpleServerUtilities.NPC_TOOLS.copy(player, instance);
        source.sendSystemMessage(Component.literal("NPC copied to your linked-placement clipboard."));
        return 1;
    }

    private static int paste(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        NpcInstance original = SimpleServerUtilities.NPC_TOOLS.clipboard(player, SimpleServerUtilities.NPCS);
        if (original == null) {
            source.sendFailure(Component.literal("Your NPC clipboard is empty."));
            return 0;
        }
        Vec3 target = player.position().add(player.getViewVector(1.0F).normalize().scale(2.0D));
        NpcInstance pasted = SimpleServerUtilities.NPCS.duplicateLinked(original,
                player.level().dimension().identifier().toString(), target.x(), target.y(), target.z(),
                player.getYRot(), 0.0F);
        if (pasted == null) {
            source.sendFailure(Component.literal("The NPC could not be pasted."));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Pasted linked NPC placement " + pasted.id + "."));
        return 1;
    }

    private static int respawn(CommandSourceStack source, String rawId) {
        if (!SimpleServerUtilities.NPCS.respawnNow(rawId)) {
            source.sendFailure(Component.literal("Unknown NPC placement or respawn location unavailable: " + rawId));
            return 0;
        }
        source.sendSystemMessage(Component.literal("NPC respawned at its configured respawn location."));
        return 1;
    }

    private static int bring(CommandSourceStack source, String rawId) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(rawId);
        if (instance == null) { source.sendFailure(Component.literal("Unknown NPC placement: " + rawId)); return 0; }
        instance.dimension = player.level().dimension().identifier().toString();
        instance.x = player.getX(); instance.y = player.getY(); instance.z = player.getZ();
        instance.yaw = player.getYRot(); instance.pitch = 0.0F;
        if (!SimpleServerUtilities.NPCS.saveInstance(instance, true)) { source.sendFailure(Component.literal("NPC could not be moved.")); return 0; }
        source.sendSystemMessage(Component.literal("NPC moved to your position."));
        return 1;
    }


    private static LiteralArgumentBuilder<CommandSourceStack> shopCommands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("shop");
        root.executes(context -> manageShops(context.getSource()));
        root.then(Commands.literal("manage").executes(context -> manageShops(context.getSource())));
        root.then(Commands.literal("list").executes(context -> listShops(context.getSource())));
        root.then(Commands.literal("buyback-minutes")
                .executes(context -> showBuybackMinutes(context.getSource()))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                        .executes(context -> setBuybackMinutes(context.getSource(),
                                IntegerArgumentType.getInteger(context, "minutes")))));
        root.then(Commands.literal("edit")
                .then(Commands.argument("shop", StringArgumentType.word())
                        .executes(context -> editShop(context.getSource(),
                                StringArgumentType.getString(context, "shop")))));
        root.then(Commands.literal("create")
                .then(Commands.argument("shop", StringArgumentType.word())
                        .executes(context -> createShop(context.getSource(),
                                StringArgumentType.getString(context, "shop")))));
        root.then(Commands.literal("delete")
                .then(Commands.argument("shop", StringArgumentType.word())
                        .executes(context -> deleteShop(context.getSource(),
                                StringArgumentType.getString(context, "shop")))));
        root.then(Commands.literal("name")
                .then(Commands.argument("shop", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> nameShop(context.getSource(),
                                        StringArgumentType.getString(context, "shop"),
                                        StringArgumentType.getString(context, "name"))))));
        root.then(Commands.literal("enable")
                .then(Commands.argument("shop", StringArgumentType.word())
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> enableShop(context.getSource(),
                                        StringArgumentType.getString(context, "shop"),
                                        BoolArgumentType.getBool(context, "enabled"))))));

        RequiredArgumentBuilder<CommandSourceStack, Integer> stockArgument = Commands.argument("stock", IntegerArgumentType.integer(-1, 1_000_000))
                .executes(context -> addHeldShopEntry(context.getSource(),
                        StringArgumentType.getString(context, "shop"),
                        StringArgumentType.getString(context, "entry"),
                        StringArgumentType.getString(context, "buy_price"),
                        StringArgumentType.getString(context, "sell_price"),
                        IntegerArgumentType.getInteger(context, "stock")));
        RequiredArgumentBuilder<CommandSourceStack, String> sellArgument = Commands.argument("sell_price", StringArgumentType.word()).then(stockArgument);
        RequiredArgumentBuilder<CommandSourceStack, String> buyArgument = Commands.argument("buy_price", StringArgumentType.word()).then(sellArgument);
        RequiredArgumentBuilder<CommandSourceStack, String> entryArgument = Commands.argument("entry", StringArgumentType.word()).then(buyArgument);
        root.then(Commands.literal("add-held")
                .then(Commands.argument("shop", StringArgumentType.word()).then(entryArgument)));

        root.then(Commands.literal("remove")
                .then(Commands.argument("shop", StringArgumentType.word())
                        .then(Commands.argument("entry", StringArgumentType.word())
                                .executes(context -> removeShopEntry(context.getSource(),
                                        StringArgumentType.getString(context, "shop"),
                                        StringArgumentType.getString(context, "entry"))))));

        RequiredArgumentBuilder<CommandSourceStack, Integer> minutesArgument = Commands.argument("minutes", IntegerArgumentType.integer(0, 525_600))
                .executes(context -> restockShopEntry(context.getSource(),
                        StringArgumentType.getString(context, "shop"),
                        StringArgumentType.getString(context, "entry"),
                        IntegerArgumentType.getInteger(context, "stock"),
                        IntegerArgumentType.getInteger(context, "max_stock"),
                        IntegerArgumentType.getInteger(context, "amount"),
                        IntegerArgumentType.getInteger(context, "minutes")));
        RequiredArgumentBuilder<CommandSourceStack, Integer> amountArgument = Commands.argument("amount", IntegerArgumentType.integer(0, 1_000_000)).then(minutesArgument);
        RequiredArgumentBuilder<CommandSourceStack, Integer> maxStockArgument = Commands.argument("max_stock", IntegerArgumentType.integer(0, 1_000_000)).then(amountArgument);
        RequiredArgumentBuilder<CommandSourceStack, Integer> currentStockArgument = Commands.argument("stock", IntegerArgumentType.integer(0, 1_000_000)).then(maxStockArgument);
        RequiredArgumentBuilder<CommandSourceStack, String> restockEntryArgument = Commands.argument("entry", StringArgumentType.word()).then(currentStockArgument);
        root.then(Commands.literal("restock")
                .then(Commands.argument("shop", StringArgumentType.word()).then(restockEntryArgument)));
        return root;
    }

    private static int showBuybackMinutes(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("NPC shop buy-back retention: "
                + Config.NPC_SHOP_BUYBACK_MINUTES.get() + " minute(s)."));
        return 1;
    }

    private static int setBuybackMinutes(CommandSourceStack source, int minutes) {
        Config.NPC_SHOP_BUYBACK_MINUTES.set(minutes);
        source.sendSystemMessage(Component.literal("NPC shop buy-back retention set to "
                + minutes + " minute(s). Existing transactions keep their original expiry time."));
        return 1;
    }

    private static int manageShops(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        NpcShopEditorService.openManager(player);
        return 1;
    }

    private static int editShop(CommandSourceStack source, String id) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        NpcShopEditorService.openEditor(player, id);
        return 1;
    }

    private static int listShops(CommandSourceStack source) {
        var shops = SimpleServerUtilities.NPC_SHOPS.definitions();
        source.sendSystemMessage(Component.literal("SSU NPC shops (" + shops.size() + "):"));
        for (NpcShopDefinition shop : shops) {
            source.sendSystemMessage(Component.literal(" - " + shop.id + " | " + shop.displayName
                    + " | " + shop.entries.size() + " offer(s) | " + (shop.enabled ? "enabled" : "disabled")));
            for (NpcShopEntry entry : shop.entries) {
                var item = entry.item(source.getServer().registryAccess());
                String itemName = item.isEmpty() ? "missing item" : item.getHoverName().getString();
                var price = item.isEmpty() ? new be.winnetrie.mod.simpleserverutilities.npcshop.NpcItemPrice()
                        : SimpleServerUtilities.NPC_SHOPS.itemPrice(
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                source.sendSystemMessage(Component.literal("    " + entry.id + ": " + itemName
                        + " | global buy/item " + (price.buyPriceMinor <= 0 ? "off" : SimpleServerUtilities.ECONOMY.format(price.buyPriceMinor))
                        + " | global sell/item " + (price.sellPriceMinor <= 0 ? "off" : SimpleServerUtilities.ECONOMY.format(price.sellPriceMinor))
                        + " | item stock " + (entry.stock < 0 ? "infinite" : entry.stock + "/" + entry.maxStock)));
            }
        }
        source.sendSystemMessage(Component.literal("Use the visual Shop Editor and Admin Center Item Price Catalog. add-held remains available for recovery and updates the global item price."));
        return 1;
    }

    private static int createShop(CommandSourceStack source, String id) {
        NpcShopDefinition created = SimpleServerUtilities.NPC_SHOPS.create(id);
        if (created == null) { source.sendFailure(Component.literal("That shop already exists or the shop limit was reached.")); return 0; }
        source.sendSystemMessage(Component.literal("Created NPC shop '" + created.id + "'. Configure an NPC function with service=shop and target=" + created.id + "."));
        return 1;
    }

    private static int deleteShop(CommandSourceStack source, String id) {
        if (!SimpleServerUtilities.NPC_SHOPS.delete(id)) { source.sendFailure(Component.literal("Unknown NPC shop: " + id)); return 0; }
        source.sendSystemMessage(Component.literal("Deleted NPC shop " + id + "."));
        return 1;
    }

    private static int nameShop(CommandSourceStack source, String id, String name) {
        if (!SimpleServerUtilities.NPC_SHOPS.setName(id, name)) { source.sendFailure(Component.literal("Unknown NPC shop: " + id)); return 0; }
        source.sendSystemMessage(Component.literal("Renamed NPC shop " + id + " to " + name + "."));
        return 1;
    }

    private static int enableShop(CommandSourceStack source, String id, boolean enabled) {
        if (!SimpleServerUtilities.NPC_SHOPS.setEnabled(id, enabled)) { source.sendFailure(Component.literal("Unknown NPC shop: " + id)); return 0; }
        source.sendSystemMessage(Component.literal("NPC shop " + id + " is now " + (enabled ? "enabled" : "disabled") + "."));
        return 1;
    }

    private static int addHeldShopEntry(CommandSourceStack source, String shop, String entry,
                                        String buyRaw, String sellRaw, int stock) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        if (player.getMainHandItem().isEmpty()) { source.sendFailure(Component.literal("Hold the exact item stack you want this offer to use.")); return 0; }
        try {
            long buy = parseOptionalMoney(buyRaw);
            long sell = parseOptionalMoney(sellRaw);
            if (buy <= 0L && sell <= 0L) throw new IllegalArgumentException("At least one price must be greater than zero.");
            if (!SimpleServerUtilities.NPC_SHOPS.putHeldEntry(player, shop, entry, buy, sell, stock)) {
                source.sendFailure(Component.literal("The shop or entry could not be updated.")); return 0;
            }
            source.sendSystemMessage(Component.literal("Saved exact held item as offer " + entry + " in shop " + shop
                    + " and updated that registered item's global base prices. Use 0 to disable a direction; stock -1 means infinite."));
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int removeShopEntry(CommandSourceStack source, String shop, String entry) {
        if (!SimpleServerUtilities.NPC_SHOPS.removeEntry(shop, entry)) { source.sendFailure(Component.literal("Unknown shop offer.")); return 0; }
        source.sendSystemMessage(Component.literal("Removed offer " + entry + " from shop " + shop + "."));
        return 1;
    }

    private static int restockShopEntry(CommandSourceStack source, String shop, String entry,
                                        int stock, int maxStock, int amount, int minutes) {
        if (stock > maxStock) { source.sendFailure(Component.literal("Stock cannot exceed max_stock.")); return 0; }
        if (!SimpleServerUtilities.NPC_SHOPS.configureRestock(shop, entry, stock, maxStock, amount, minutes)) {
            source.sendFailure(Component.literal("Unknown shop offer.")); return 0;
        }
        source.sendSystemMessage(Component.literal("Configured stock " + stock + "/" + maxStock
                + " and restock +" + amount + " every " + minutes + " minute(s)."));
        return 1;
    }

    private static long parseOptionalMoney(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw) || "off".equalsIgnoreCase(raw)) return 0L;
        long value = MoneyFormat.parseMinor(raw, SimpleServerUtilities.ECONOMY.settings());
        if (value < 0L) throw new IllegalArgumentException("Prices cannot be negative.");
        return value;
    }

    private static ServerPlayer player(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        source.sendFailure(Component.literal("This command requires a player."));
        return null;
    }

    private static String one(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
