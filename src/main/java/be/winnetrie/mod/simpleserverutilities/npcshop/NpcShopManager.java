package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopRequestPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInstance;
import be.winnetrie.mod.simpleserverutilities.npc.NpcServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Persistent NPC shops, click-only player sessions and rollback-safe transactions. */
public final class NpcShopManager {
    public static final int MAX_SHOPS = 512;
    public static final int MAX_SERIALIZED_CHARACTERS = 131_072;
    private static final int MAX_BUYBACK_ENTRIES = 9;
    private static final long SESSION_MILLIS = 5L * 60L * 1_000L;
    private static final double MAX_DISTANCE_SQUARED = 100.0D;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, NpcShopDefinition> shops = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final Map<UUID, Long> lastActionRequests = new LinkedHashMap<>();
    private final Map<UUID, ArrayList<BuybackRecord>> buybacks = new LinkedHashMap<>();
    private final DirtyJsonRecordStore store = new DirtyJsonRecordStore();
    private NpcItemPriceCatalog itemPrices = new NpcItemPriceCatalog();
    private MinecraftServer server;
    private Path folder;
    private Path itemPricesFile;
    private long buybackSequence;
    private long sessionSequence;

    public synchronized void load(MinecraftServer server) {
        clear();
        this.server = server;
        Path root = StoragePaths.root(server);
        folder = StoragePaths.npcShops(root);
        itemPricesFile = StoragePaths.npcItemPrices(root);
        try {
            Files.createDirectories(folder);
            store.discover(folder);
            store.discoverFile(itemPricesFile);
            if (Files.exists(itemPricesFile)) {
                try {
                    NpcItemPriceCatalog loaded = JsonStorage.read(GSON, itemPricesFile, NpcItemPriceCatalog.class);
                    itemPrices = loaded == null ? new NpcItemPriceCatalog() : loaded.normalize();
                } catch (Exception exception) {
                    Path archived = JsonStorage.archiveBrokenFile(itemPricesFile);
                    itemPrices = new NpcItemPriceCatalog();
                    SimpleServerUtilities.LOGGER.error("Failed to load NPC item prices; archived as {}.", archived, exception);
                }
            } else {
                itemPrices = new NpcItemPriceCatalog();
            }
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    NpcShopDefinition shop = JsonStorage.read(GSON, file, NpcShopDefinition.class);
                    if (shop == null) continue;
                    shop.normalize();
                    validateSerialized(shop);
                    if (shops.putIfAbsent(shop.id, shop) != null) {
                        throw new IllegalArgumentException("Duplicate NPC shop ID across files: " + shop.id);
                    }
                } catch (Exception exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load NPC shop; archived as {}.", archived, exception);
                }
            }
            migrateLegacyPrices();
            applyRestocks(System.currentTimeMillis());
            saveAll();
            SimpleServerUtilities.LOGGER.info("Loaded {} SSU NPC shops.", shops.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU NPC shops.", exception);
        }
    }

    public synchronized Collection<NpcShopDefinition> definitions() {
        return shops.values().stream().map(NpcShopDefinition::copy)
                .sorted(Comparator.comparing(value -> value.id)).toList();
    }

    public synchronized NpcShopDefinition get(String rawId) {
        return shops.get(normalizeId(rawId));
    }

    public synchronized List<CatalogItem> catalogItems(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        ArrayList<CatalogItem> result = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            try {
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) continue;
                ItemStack stack = item.getDefaultInstance();
                if (stack.isEmpty()) continue;
                String itemId = id.toString();
                String name = stack.getHoverName().getString();
                if (!query.isBlank() && !itemId.toLowerCase(Locale.ROOT).contains(query)
                        && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
                NpcItemPrice price = itemPrices.get(itemId);
                result.add(new CatalogItem(itemId, name, price.buyPriceMinor, price.sellPriceMinor,
                        Math.max(1, stack.getMaxStackSize())));
            } catch (RuntimeException exception) {
                SimpleServerUtilities.LOGGER.warn("Skipping item {} in the NPC price catalog because its default stack failed.", id, exception);
            }
        }
        result.sort(Comparator.comparing(CatalogItem::itemId));
        return List.copyOf(result);
    }

    public synchronized NpcItemPrice itemPrice(String itemId) {
        return itemPrices.get(itemId);
    }

    public synchronized boolean setItemPrice(String rawItemId, long buyPriceMinor, long sellPriceMinor) {
        ResourceLocation id;
        try { id = ResourceLocation.parse(rawItemId == null ? "" : rawItemId.trim()); }
        catch (RuntimeException exception) { return false; }
        if (BuiltInRegistries.ITEM.getOptional(id).isEmpty()) return false;
        itemPrices.set(id.toString(), buyPriceMinor, sellPriceMinor);
        saveAll();
        return true;
    }

    public synchronized String toJson(NpcShopDefinition definition) {
        NpcShopDefinition safe = definition == null ? new NpcShopDefinition() : definition.copy();
        String json = GSON.toJson(safe);
        if (json.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("NPC shop exceeds " + MAX_SERIALIZED_CHARACTERS + " serialized characters.");
        }
        return json;
    }

    public synchronized NpcShopDefinition fromJson(String json) {
        NpcShopDefinition raw = parseEditorJson(json);
        validateEditorDraft(raw);
        raw.normalize();
        validateSerialized(raw);
        return raw;
    }

    public synchronized NpcShopDefinition fromDraftJson(String json) {
        NpcShopDefinition raw = parseEditorJson(json);
        validateEditorShape(raw);
        raw.normalize();
        validateSerialized(raw);
        return raw;
    }

    private static NpcShopDefinition parseEditorJson(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("NPC shop data is empty.");
        if (json.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("NPC shop exceeds " + MAX_SERIALIZED_CHARACTERS + " serialized characters.");
        }
        NpcShopDefinition raw;
        try { raw = GSON.fromJson(json, NpcShopDefinition.class); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("NPC shop data is not valid JSON."); }
        if (raw == null) throw new IllegalArgumentException("NPC shop data is empty.");
        return raw;
    }

    public synchronized boolean saveDefinition(String rawOriginalId, NpcShopDefinition definition) {
        if (definition == null) return false;
        String originalId = normalizeId(rawOriginalId);
        NpcShopDefinition safe = definition.copy();
        validateEditorDraft(safe);
        safe.normalize();
        validateSerialized(safe);
        if (safe.id.isBlank()) throw new IllegalArgumentException("Shop ID cannot be empty.");
        if (originalId.isBlank()) {
            if (shops.containsKey(safe.id) || shops.size() >= MAX_SHOPS) return false;
        } else {
            if (!shops.containsKey(originalId)) return false;
            if (!safe.id.equals(originalId)) {
                throw new IllegalArgumentException("An existing shop ID cannot be changed. Duplicate the shop instead.");
            }
        }
        shops.put(safe.id, safe);
        saveAll();
        return true;
    }

    private void validateEditorDraft(NpcShopDefinition shop) {
        validateEditorShape(shop);
        for (int index = 0; index < shop.entries.size(); index++) {
            NpcShopEntry entry = shop.entries.get(index);
            String entryId = normalizeId(entry.id);
            ItemStack configuredItem = server == null ? ItemStack.EMPTY : entry.item(server.registryAccess());
            if (configuredItem.isEmpty()) {
                throw new IllegalArgumentException("Offer '" + entryId + "' has no valid exact item stack.");
            }
            if (entry.itemCount > configuredItem.getMaxStackSize()) {
                throw new IllegalArgumentException("Offer '" + entryId + "' exceeds the item's maximum stack size of "
                        + configuredItem.getMaxStackSize() + ".");
            }
            if (entry.stock >= 0 && entry.maxStock >= 0 && entry.stock > entry.maxStock) {
                throw new IllegalArgumentException("Offer '" + entryId + "' stock cannot exceed maximum stock.");
            }
        }
    }

    private static void validateEditorShape(NpcShopDefinition shop) {
        if (shop.id == null || shop.id.isBlank()) throw new IllegalArgumentException("Shop ID cannot be empty.");
        String normalizedShopId = normalizeId(shop.id);
        if (normalizedShopId.isBlank()) throw new IllegalArgumentException("Shop ID is invalid.");
        if (shop.entries == null) shop.entries = new ArrayList<>();
        validateFilters(shop.sellWhitelist, "whitelist");
        validateFilters(shop.sellBlacklist, "blacklist");
        if (shop.entries.size() > NpcShopDefinition.MAX_ENTRIES) {
            throw new IllegalArgumentException("A shop supports at most " + NpcShopDefinition.MAX_ENTRIES + " offers.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < shop.entries.size(); index++) {
            NpcShopEntry entry = shop.entries.get(index);
            if (entry == null) throw new IllegalArgumentException("Offer " + (index + 1) + " is empty.");
            String entryId = normalizeId(entry.id);
            if (entryId.isBlank()) throw new IllegalArgumentException("Offer " + (index + 1) + " needs an ID.");
            if (!ids.add(entryId)) throw new IllegalArgumentException("Duplicate offer ID: " + entryId);
        }
    }

    private static void validateFilters(List<String> filters, String label) {
        if (filters == null) return;
        if (filters.size() > 256) throw new IllegalArgumentException("The sale " + label + " supports at most 256 entries.");
        for (String raw : filters) {
            String value = raw == null ? "" : raw.trim();
            if (value.isBlank()) continue;
            if (value.length() > 160) throw new IllegalArgumentException("A sale " + label + " entry is too long.");
            String identifier = value.startsWith("#") ? value.substring(1) : value;
            try { ResourceLocation.parse(identifier); }
            catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid sale " + label + " item ID or tag: " + value);
            }
        }
    }

    public synchronized NpcShopDefinition create(String rawId) {
        String id = normalizeId(rawId);
        if (id.isBlank() || shops.containsKey(id) || shops.size() >= MAX_SHOPS) return null;
        NpcShopDefinition shop = new NpcShopDefinition();
        shop.id = id;
        shop.displayName = title(id);
        shop.normalize();
        shops.put(id, shop);
        saveAll();
        return shop.copy();
    }

    public synchronized boolean delete(String rawId) {
        String id = normalizeId(rawId);
        if (shops.remove(id) == null) return false;
        sessions.values().removeIf(session -> session.shopId.equals(id));
        buybacks.values().forEach(list -> list.removeIf(record -> record.shopId.equals(id)));
        buybacks.values().removeIf(history -> history.isEmpty());
        saveAll();
        return true;
    }

    public synchronized boolean setName(String rawId, String name) {
        NpcShopDefinition shop = shops.get(normalizeId(rawId));
        if (shop == null) return false;
        shop.displayName = name;
        shop.normalize();
        saveAll();
        return true;
    }

    public synchronized boolean setEnabled(String rawId, boolean enabled) {
        NpcShopDefinition shop = shops.get(normalizeId(rawId));
        if (shop == null) return false;
        shop.enabled = enabled;
        saveAll();
        return true;
    }

    public synchronized boolean putHeldEntry(ServerPlayer player, String rawShopId, String rawEntryId,
                                             long buyPriceMinor, long sellPriceMinor, int stock) {
        if (server == null || player == null) return false;
        NpcShopDefinition shop = shops.get(normalizeId(rawShopId));
        ItemStack held = player.getMainHandItem();
        if (shop == null || held.isEmpty()) return false;
        String entryId = normalizeId(rawEntryId);
        NpcShopEntry entry = shop.entry(entryId);
        if (entry == null) {
            if (shop.entries.size() >= NpcShopDefinition.MAX_ENTRIES) return false;
            entry = new NpcShopEntry();
            entry.id = entryId;
            shop.entries.add(entry);
        }
        entry.setItem(server.registryAccess(), held.copy());
        entry.itemCount = Math.max(1, held.getCount());
        // Kept as a recovery/automation command: schema 3 prices are global per registered item,
        // so using add-held updates the Item Price Catalog rather than a private offer price.
        itemPrices.set(itemId(held), buyPriceMinor, sellPriceMinor);
        entry.buyPriceMinor = 0L;
        entry.sellPriceMinor = 0L;
        if (stock < 0) {
            entry.stock = -1; entry.maxStock = -1; entry.restockAmount = 0;
            entry.restockIntervalMinutes = 0; entry.nextRestockEpochMilli = 0L;
        } else {
            entry.stock = stock; entry.maxStock = stock;
        }
        shop.normalize();
        saveAll();
        return true;
    }

    public synchronized boolean removeEntry(String rawShopId, String rawEntryId) {
        NpcShopDefinition shop = shops.get(normalizeId(rawShopId));
        if (shop == null) return false;
        boolean removed = shop.entries.removeIf(entry -> entry.id.equals(normalizeId(rawEntryId)));
        if (removed) saveAll();
        return removed;
    }

    public synchronized boolean configureRestock(String rawShopId, String rawEntryId,
                                                 int stock, int maxStock, int amount, int minutes) {
        NpcShopDefinition shop = shops.get(normalizeId(rawShopId));
        NpcShopEntry entry = shop == null ? null : shop.entry(rawEntryId);
        if (entry == null) return false;
        if (maxStock < 0) {
            entry.stock = -1; entry.maxStock = -1; entry.restockAmount = 0;
            entry.restockIntervalMinutes = 0; entry.nextRestockEpochMilli = 0L;
        } else {
            entry.stock = stock; entry.maxStock = maxStock; entry.restockAmount = amount;
            entry.restockIntervalMinutes = minutes;
            entry.nextRestockEpochMilli = amount > 0 && minutes > 0
                    ? System.currentTimeMillis() + (long) minutes * 60_000L : 0L;
        }
        entry.normalize();
        saveAll();
        return true;
    }

    public synchronized NpcServiceRegistry.ServiceResult validateService(
            ServerPlayer player, NpcInstance instance, NpcDefinition definition, String target) {
        if (!SsuModuleAccess.active("npc_shops")) {
            return NpcServiceRegistry.ServiceResult.fail("NPC Shops are unavailable because a required module is disabled.");
        }
        if (!PermissionService.getBoolean(player, PermissionKeys.NPCS_SERVICE_SHOPS, true)
                || !PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_USE, true)) {
            return NpcServiceRegistry.ServiceResult.fail("You cannot use NPC shops.");
        }
        if (!SimpleServerUtilities.ECONOMY.isEnabled()) {
            return NpcServiceRegistry.ServiceResult.fail("The Economy Core is disabled.");
        }
        NpcShopDefinition shop = shops.get(normalizeId(target));
        if (shop == null) return NpcServiceRegistry.ServiceResult.fail("NPC shop not found: " + target);
        if (!shop.enabled) return NpcServiceRegistry.ServiceResult.fail("This NPC shop is disabled.");
        if (!canUseNpc(player, instance, definition)) {
            return NpcServiceRegistry.ServiceResult.fail("You are too far away from this NPC.");
        }
        return NpcServiceRegistry.ServiceResult.ok(false, "");
    }

    public synchronized NpcServiceRegistry.ServiceResult executeService(
            ServerPlayer player, NpcInstance instance, NpcDefinition definition, String target) {
        NpcServiceRegistry.ServiceResult validation = validateService(player, instance, definition, target);
        if (!validation.successful()) return validation;
        String shopId = normalizeId(target);
        sessions.put(player.getUUID(), new Session(instance.id, shopId, System.currentTimeMillis() + SESSION_MILLIS, ++sessionSequence));
        lastActionRequests.remove(player.getUUID());
        sendPage(player, instance, definition, shops.get(shopId), 0, 0L, "", false);
        return NpcServiceRegistry.ServiceResult.ok(true, "");
    }

    public static void handleRequest(NpcShopRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npc_shops")) return;
        if (context.player() instanceof ServerPlayer player) SimpleServerUtilities.NPC_SHOPS.request(player, payload);
    }

    public static void handleAction(NpcShopActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npc_shops")) return;
        if (context.player() instanceof ServerPlayer player) SimpleServerUtilities.NPC_SHOPS.action(player, payload);
    }

    private synchronized void request(ServerPlayer player, NpcShopRequestPayload payload) {
        SessionAccess access = session(player, payload.instanceId(), payload.shopId());
        if (access == null) {
            PacketDistributor.sendToPlayer(player, NpcShopDataPayload.denied(
                    payload.instanceId(), payload.shopId(), payload.requestId(), "Your NPC shop session is no longer valid."));
            return;
        }
        sendPage(player, access.instance, access.definition, access.shop,
                payload.pageIndex(), payload.requestId(), "", false);
    }

    private synchronized void action(ServerPlayer player, NpcShopActionPayload payload) {
        if ("close".equals(payload.action())) {
            sessions.remove(player.getUUID());
            lastActionRequests.remove(player.getUUID());
            return;
        }
        SessionAccess access = session(player, payload.instanceId(), payload.shopId());
        if (access == null) {
            PacketDistributor.sendToPlayer(player, NpcShopDataPayload.denied(
                    payload.instanceId(), payload.shopId(), payload.requestId(), "Your NPC shop session is no longer valid."));
            return;
        }
        long previous = lastActionRequests.getOrDefault(player.getUUID(), -1L);
        if (payload.requestId() <= previous) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(), "", false);
            return;
        }
        lastActionRequests.put(player.getUUID(), payload.requestId());
        switch (payload.action()) {
            case "buy_stack" -> buy(player, access, payload, false);
            case "buy_one" -> buy(player, access, payload, true);
            case "sell_stack" -> sellSlot(player, access, payload, false);
            case "sell_one" -> sellSlot(player, access, payload, true);
            case "buyback_stack" -> buyBack(player, access, payload, false);
            case "buyback_one" -> buyBack(player, access, payload, true);
            default -> sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(),
                    payload.requestId(), "Unknown NPC shop action.", true);
        }
    }

    private void buy(ServerPlayer player, SessionAccess access, NpcShopActionPayload payload, boolean singleItem) {
        NpcShopEntry entry = access.shop.entry(payload.entryId());
        if (!PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_BUY, true)) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "You do not have permission to buy from NPC shops.", true); return;
        }
        GameCalendar.Moment gameMoment = GameCalendar.fromClockTime(player.level().getDayTime());
        long unitBuyPrice = entry == null ? 0L : purchasePrice(player, access.definition, access.shop, entry);
        if (entry == null || unitBuyPrice <= 0L || !entry.available(gameMoment)) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    entry != null && !entry.available(gameMoment)
                            ? "That shop item is not available on " + gameMoment.displayText() + "."
                            : "That shop item has no base purchase price.", true); return;
        }
        long now = System.currentTimeMillis();
        expireBuybacks(now);
        applyRestocks(access.shop, now);
        int quantity = singleItem ? 1 : Math.max(1, entry.itemCount);
        if (!entry.infiniteStock() && entry.stock < quantity) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    singleItem ? "This shop has no stock left." : "This shop does not have the full offered stack in stock.", true);
            return;
        }
        ItemStack template = entry.item(server.registryAccess()).copyWithCount(1);
        long totalPrice;
        try { totalPrice = Math.multiplyExact(unitBuyPrice, (long) quantity); }
        catch (ArithmeticException exception) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "That purchase is too large.", true); return;
        }
        NpcShopInventory.Plan plan = NpcShopInventory.planAdd(player, template, quantity);
        if (plan == null) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "You do not have enough inventory space.", true); return;
        }
        String key = transactionKey("buy", player, payload.requestId(), access.sessionNonce);
        EconomyResult payment = SimpleServerUtilities.ECONOMY.debitTyped(player.getUUID(), player.getName().getString(),
                player.getUUID(), totalPrice, EconomyTransactionType.NPC_SHOP_BUY,
                "npc_shops", "Bought " + quantity + " item(s) from " + access.shop.id, key);
        if (!payment.successful()) {
            String failure = purchaseFailureMessage(payment, player, unitBuyPrice, totalPrice, quantity, singleItem, false);
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    failure, true); return;
        }
        int previousStock = entry.stock;
        try {
            NpcShopInventory.apply(player, plan.after());
            if (!entry.infiniteStock()) entry.stock -= quantity;
            saveAll();
        } catch (RuntimeException exception) {
            entry.stock = previousStock;
            NpcShopInventory.apply(player, plan.before());
            EconomyResult rollback = SimpleServerUtilities.ECONOMY.creditTyped(null, "server", player.getUUID(), totalPrice,
                    EconomyTransactionType.NPC_SHOP_BUY_ROLLBACK, "npc_shops",
                    "NPC shop purchase rollback", key + ":rollback");
            if (!rollback.successful()) {
                SimpleServerUtilities.LOGGER.error("Could not refund failed NPC shop purchase {} for {}.",
                        key, player.getName().getString(), exception);
            }
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    rollback.successful() ? "The purchase failed safely and your payment was returned."
                            : "The purchase failed and its automatic refund could not be completed. Contact an administrator.", true);
            return;
        }
        sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                "Purchased " + quantity + " × " + template.getHoverName().getString() + ".", false);
    }

    private void sellSlot(ServerPlayer player, SessionAccess access, NpcShopActionPayload payload, boolean singleItem) {
        if (!PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_SELL, true)) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "You do not have permission to sell to NPC shops.", true); return;
        }
        int slot = payload.quantity();
        if (slot < 0 || slot >= 36) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "That inventory slot is not valid.", true); return;
        }
        ItemStack clicked = player.getInventory().getItem(slot);
        if (clicked == null || clicked.isEmpty()) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "That inventory slot is empty.", true); return;
        }
        if (!allowsSale(access.shop, clicked)) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "This shop does not buy that type of item.", true); return;
        }
        long unitSellPrice = sellPrice(clicked);
        if (unitSellPrice <= 0L) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "That item has no base sale price in the Item Price Catalog.", true); return;
        }
        int quantity = singleItem ? 1 : clicked.getCount();
        long now = System.currentTimeMillis();
        boolean changed = expireBuybacks(now);
        changed |= applyRestocks(access.shop, now);
        if (changed) saveAll();
        NpcShopEntry stockEntry = bestStockEntry(access.shop, clicked, quantity);
        long totalPrice;
        try { totalPrice = Math.multiplyExact(unitSellPrice, (long) quantity); }
        catch (ArithmeticException exception) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "That sale is too large.", true); return;
        }
        ItemStack template = clicked.copyWithCount(1);
        NpcShopInventory.Plan plan = NpcShopInventory.planRemoveSlot(player, slot, template, quantity);
        if (plan == null) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "The clicked inventory stack changed before the sale could complete.", true); return;
        }
        NpcShopInventory.apply(player, plan.after());
        String key = transactionKey("sell", player, payload.requestId(), access.sessionNonce);
        EconomyResult payout = SimpleServerUtilities.ECONOMY.creditTyped(player.getUUID(), player.getName().getString(),
                player.getUUID(), totalPrice, EconomyTransactionType.NPC_SHOP_SELL,
                "npc_shops", "Sold " + quantity + " item(s) to " + access.shop.id, key);
        if (!payout.successful()) {
            NpcShopInventory.apply(player, plan.before());
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    payout.message(), true); return;
        }
        try {
            String stockEntryId = stockEntry == null ? "" : stockEntry.id;
            boolean stockChanged = reserveBuyback(player.getUUID(), access.shop.id, stockEntryId, template, quantity, unitSellPrice, now);
            if (stockChanged) saveAll();
        } catch (RuntimeException exception) {
            EconomyResult rollback = SimpleServerUtilities.ECONOMY.debitTyped(null, "server", player.getUUID(), totalPrice,
                    EconomyTransactionType.NPC_SHOP_SELL_ROLLBACK, "npc_shops",
                    "NPC shop sale rollback", key + ":rollback");
            if (rollback.successful()) NpcShopInventory.apply(player, plan.before());
            else SimpleServerUtilities.LOGGER.error("Could not roll back NPC shop sale {} for {}.",
                    key, player.getName().getString(), exception);
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "The sale could not be finalized safely.", true);
            return;
        }
        sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                "Sold " + quantity + " × " + template.getHoverName().getString() + ".", false);
    }

    private void buyBack(ServerPlayer player, SessionAccess access, NpcShopActionPayload payload, boolean singleItem) {
        if (!PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_BUY, true)) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "You do not have permission to use buy-back.", true); return;
        }
        long now = System.currentTimeMillis();
        boolean changed = expireBuybacks(now);
        if (changed) saveAll();
        BuybackRecord record = buyback(player.getUUID(), payload.entryId(), access.shop.id);
        if (record == null || record.remaining <= 0 || record.expiresAt <= now) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "That buy-back transaction has expired or no longer exists.", true); return;
        }
        int quantity = singleItem ? 1 : record.remaining;
        long totalPrice;
        try { totalPrice = Math.multiplyExact(record.unitPriceMinor, (long) quantity); }
        catch (ArithmeticException exception) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "That buy-back is too large.", true); return;
        }
        NpcShopInventory.Plan plan = NpcShopInventory.planAdd(player, record.item.copyWithCount(1), quantity);
        if (plan == null) {
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "You do not have enough inventory space.", true); return;
        }
        String key = transactionKey("buyback", player, payload.requestId(), access.sessionNonce);
        EconomyResult payment = SimpleServerUtilities.ECONOMY.debitTyped(player.getUUID(), player.getName().getString(),
                player.getUUID(), totalPrice, EconomyTransactionType.NPC_SHOP_BUYBACK,
                "npc_shops", "Bought back " + quantity + " item(s) from " + access.shop.id, key);
        if (!payment.successful()) {
            String failure = purchaseFailureMessage(payment, player, record.unitPriceMinor, totalPrice, quantity, singleItem, true);
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    failure, true); return;
        }
        try {
            NpcShopInventory.apply(player, plan.after());
        } catch (RuntimeException exception) {
            NpcShopInventory.apply(player, plan.before());
            EconomyResult rollback = SimpleServerUtilities.ECONOMY.creditTyped(null, "server", player.getUUID(), totalPrice,
                    EconomyTransactionType.NPC_SHOP_BUYBACK_ROLLBACK, "npc_shops",
                    "NPC shop buy-back rollback", key + ":rollback");
            if (!rollback.successful()) {
                SimpleServerUtilities.LOGGER.error("Could not refund failed NPC shop buy-back {} for {}.",
                        key, player.getName().getString(), exception);
            }
            sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                    "The buy-back could not be finalized safely.", true);
            return;
        }
        record.remaining -= quantity;
        if (record.remaining <= 0) removeBuyback(player.getUUID(), record.id);
        sendPage(player, access.instance, access.definition, access.shop, payload.pageIndex(), payload.requestId(),
                "Bought back " + quantity + " × " + record.item.getHoverName().getString() + ".", false);
    }

    private void sendPage(ServerPlayer player, NpcInstance instance, NpcDefinition definition,
                          NpcShopDefinition shop, int requestedPage, long requestId, String notice, boolean error) {
        long now = System.currentTimeMillis();
        boolean changed = expireBuybacks(now);
        changed |= applyRestocks(shop, now);
        if (changed) saveAll();
        GameCalendar.Moment gameMoment = GameCalendar.fromClockTime(player.level().getDayTime());
        List<NpcShopEntry> available = shop.entries.stream()
                .filter(entry -> entry.configured(server.registryAccess()))
                .filter(entry -> entry.available(gameMoment))
                .filter(entry -> purchasePrice(player, definition, shop, entry) > 0L)
                .toList();
        int total = available.size();
        int pages = Math.max(1, (total + NpcShopDataPayload.MAX_ENTRIES - 1) / NpcShopDataPayload.MAX_ENTRIES);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        int from = Math.min(total, page * NpcShopDataPayload.MAX_ENTRIES);
        int to = Math.min(total, from + NpcShopDataPayload.MAX_ENTRIES);
        boolean buyPermission = PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_BUY, true);
        boolean sellPermission = PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_SELL, true);
        ArrayList<NpcShopDataPayload.Entry> entries = new ArrayList<>();
        for (NpcShopEntry entry : available.subList(from, to)) {
            ItemStack item = entry.item(server.registryAccess());
            long unitBuyPrice = purchasePrice(player, definition, shop, entry);
            long unitSellPrice = sellPrice(item);
            boolean canBuy = buyPermission && unitBuyPrice > 0L && (entry.infiniteStock() || entry.stock > 0);
            boolean canSell = sellPermission && unitSellPrice > 0L && allowsSale(shop, item);
            long stackBuyPrice = safeMultiplyPrice(unitBuyPrice, Math.max(1, item.getCount()));
            entries.add(new NpcShopDataPayload.Entry(entry.id, item, item.getHoverName().getString(),
                    unitBuyPrice <= 0L ? "Not sold" : SimpleServerUtilities.ECONOMY.format(unitBuyPrice),
                    stackBuyPrice <= 0L ? "Not sold" : SimpleServerUtilities.ECONOMY.format(stackBuyPrice),
                    unitSellPrice <= 0L ? "Not buying" : SimpleServerUtilities.ECONOMY.format(unitSellPrice),
                    unitBuyPrice, unitSellPrice, entry.stock, entry.maxStock, canBuy, canSell));
        }
        ArrayList<NpcShopDataPayload.BuybackEntry> buybackEntries = new ArrayList<>();
        ArrayList<BuybackRecord> history = buybacks.get(player.getUUID());
        if (history != null) {
            for (BuybackRecord record : history) {
                if (!record.shopId.equals(shop.id) || record.remaining <= 0 || record.expiresAt <= now) continue;
                ItemStack display = record.item.copyWithCount(Math.min(record.item.getMaxStackSize(), record.remaining));
                buybackEntries.add(new NpcShopDataPayload.BuybackEntry(record.id, display,
                        record.item.getHoverName().getString(), SimpleServerUtilities.ECONOMY.format(record.unitPriceMinor),
                        record.unitPriceMinor, record.expiresAt));
                if (buybackEntries.size() >= MAX_BUYBACK_ENTRIES) break;
            }
        }
        ArrayList<NpcShopDataPayload.InventorySaleQuote> inventorySaleQuotes = new ArrayList<>();
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = player.getInventory().getItem(inventorySlot);
            if (stack == null || stack.isEmpty()) continue;
            long unitSellPrice = sellPrice(stack);
            boolean allowedByShop = allowsSale(shop, stack);
            boolean canSell = sellPermission && allowedByShop && unitSellPrice > 0L;
            String reason = !sellPermission ? "You do not have permission to sell to NPC shops."
                    : !allowedByShop ? "This shop does not buy this item."
                    : unitSellPrice <= 0L ? "No base sale price is configured for this item."
                    : "";
            inventorySaleQuotes.add(new NpcShopDataPayload.InventorySaleQuote(inventorySlot,
                    unitSellPrice > 0L ? SimpleServerUtilities.ECONOMY.format(unitSellPrice) : "Not bought",
                    unitSellPrice, canSell, reason));
        }
        Session currentSession = sessions.get(player.getUUID());
        long sessionNonce = currentSession != null && currentSession.instanceId.equals(instance.id)
                && currentSession.shopId.equals(shop.id) ? currentSession.nonce : ++sessionSequence;
        sessions.put(player.getUUID(), new Session(instance.id, shop.id, now + SESSION_MILLIS, sessionNonce));
        long playerBalance = SimpleServerUtilities.ECONOMY.balance(player);
        PacketDistributor.sendToPlayer(player, new NpcShopDataPayload(true, instance.id, shop.id,
                definition.displayName, shop.displayName, SimpleServerUtilities.ECONOMY.format(playerBalance), playerBalance,
                gameMoment.displayText(), page, NpcShopDataPayload.MAX_ENTRIES, total, requestId, notice, error,
                entries, buybackEntries, inventorySaleQuotes));
    }

    private SessionAccess session(ServerPlayer player, String instanceId, String shopId) {
        Session session = sessions.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (session == null || session.expiresAt < now || !session.instanceId.equals(instanceId)
                || !session.shopId.equals(normalizeId(shopId))) {
            sessions.remove(player.getUUID());
            lastActionRequests.remove(player.getUUID());
            return null;
        }
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(session.instanceId);
        NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
        NpcShopDefinition shop = shops.get(session.shopId);
        if (shop == null || !shop.enabled || !canUseNpc(player, instance, definition)
                || !PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_USE, true)) {
            sessions.remove(player.getUUID());
            lastActionRequests.remove(player.getUUID());
            return null;
        }
        sessions.put(player.getUUID(), new Session(session.instanceId, session.shopId, now + SESSION_MILLIS, session.nonce));
        return new SessionAccess(instance, definition, shop, session.nonce);
    }

    private static boolean canUseNpc(ServerPlayer player, NpcInstance instance, NpcDefinition definition) {
        if (player == null || instance == null || definition == null || !SsuModuleAccess.active("npc_shops")
                || !SsuModuleAccess.active("npcs")
                || !ContentAccessPolicy.canInteractWithNpc(player) || !definition.enabled || !instance.enabled || instance.dead) return false;
        if (!instance.dimension.equals(player.level().dimension().location().toString())) return false;
        double dx = player.getX() - instance.x, dy = player.getY() - instance.y, dz = player.getZ() - instance.z;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQUARED) return false;
        int reputation = definition.factionId.isBlank() ? 0
                : SimpleServerUtilities.CONTENT_PROGRESS.reputation(player.getUUID(), definition.factionId);
        return definition.factionId.isBlank() || reputation >= definition.minimumReputation;
    }

    private String purchaseFailureMessage(EconomyResult payment, ServerPlayer player,
                                          long unitPrice, long totalPrice, int quantity,
                                          boolean singleItem, boolean buyback) {
        if (payment == null || !"insufficient_funds".equals(payment.code())) {
            return payment == null ? "The payment could not be processed." : payment.message();
        }
        long balance = SimpleServerUtilities.ECONOMY.balance(player);
        if (singleItem || quantity <= 1) {
            return "Need " + SimpleServerUtilities.ECONOMY.format(totalPrice) + "; balance "
                    + SimpleServerUtilities.ECONOMY.format(balance) + ".";
        }
        return (buyback ? "Buy-back stack: " : "Stack: ") + SimpleServerUtilities.ECONOMY.format(totalPrice)
                + "; balance " + SimpleServerUtilities.ECONOMY.format(balance)
                + ". Right-click 1: " + SimpleServerUtilities.ECONOMY.format(unitPrice) + ".";
    }

    private static long safeMultiplyPrice(long unitPrice, int quantity) {
        if (unitPrice <= 0L || quantity <= 0) return 0L;
        try { return Math.multiplyExact(unitPrice, (long) quantity); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }

    private long buyPrice(NpcShopEntry entry) {
        if (entry == null || server == null) return 0L;
        ItemStack item = entry.item(server.registryAccess());
        return itemPrices.get(itemId(item)).buyPriceMinor;
    }

    private long purchasePrice(ServerPlayer player, NpcDefinition definition,
                               NpcShopDefinition shop, NpcShopEntry entry) {
        long basePrice = buyPrice(entry);
        return NpcShopPricingService.purchaseQuote(basePrice,
                purchaseDiscounts(player, definition, shop, entry)).effectivePriceMinor();
    }

    /** Extension point for future faction-reputation and temporary event discounts. */
    private List<NpcShopPricingService.Discount> purchaseDiscounts(ServerPlayer player, NpcDefinition definition,
                                                                    NpcShopDefinition shop, NpcShopEntry entry) {
        return List.of();
    }

    private long sellPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        return itemPrices.get(itemId(stack)).sellPriceMinor;
    }

    private String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private boolean allowsSale(NpcShopDefinition shop, ItemStack stack) {
        if (shop == null || stack == null || stack.isEmpty()) return false;
        for (String filter : shop.sellBlacklist) if (matchesFilter(stack, filter)) return false;
        if (shop.sellWhitelist.isEmpty()) return true;
        for (String filter : shop.sellWhitelist) if (matchesFilter(stack, filter)) return true;
        return false;
    }

    private boolean matchesFilter(ItemStack stack, String rawFilter) {
        String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
        if (filter.isBlank()) return false;
        try {
            if (filter.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(filter.substring(1)));
                return stack.is(tag);
            }
            return itemId(stack).equals(ResourceLocation.parse(filter).toString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private NpcShopEntry bestStockEntry(NpcShopDefinition shop, ItemStack stack, int quantity) {
        return shop.entries.stream()
                .filter(entry -> ItemStack.isSameItemSameComponents(entry.item(server.registryAccess()), stack))
                .filter(entry -> entry.infiniteStock()
                        || (long) entry.stock + reservedQuantity(shop.id, entry.id) + quantity <= entry.maxStock)
                .findFirst().orElse(null);
    }

    private void migrateLegacyPrices() {
        boolean catalogChanged = false;
        boolean shopsChanged = false;
        for (NpcShopDefinition shop : shops.values()) {
            for (NpcShopEntry entry : shop.entries) {
                if (entry.buyPriceMinor <= 0L && entry.sellPriceMinor <= 0L) continue;
                ItemStack stack = server == null ? ItemStack.EMPTY : entry.item(server.registryAccess());
                String id = itemId(stack);
                if (!id.isBlank()) {
                    NpcItemPrice current = itemPrices.get(id);
                    long buy = current.buyPriceMinor > 0L ? current.buyPriceMinor : entry.buyPriceMinor;
                    long sell = current.sellPriceMinor > 0L ? current.sellPriceMinor : entry.sellPriceMinor;
                    if (buy != current.buyPriceMinor || sell != current.sellPriceMinor) {
                        itemPrices.set(id, buy, sell);
                        catalogChanged = true;
                    }
                }
                // Schema 3 owns prices exclusively in item_prices.json. Clearing the legacy fields is
                // essential: an administrator must be able to disable a catalog direction by setting it to zero.
                entry.buyPriceMinor = 0L;
                entry.sellPriceMinor = 0L;
                shopsChanged = true;
            }
        }
        if (catalogChanged || shopsChanged) {
            SimpleServerUtilities.LOGGER.info("Migrated legacy NPC shop prices into the global item catalog.");
        }
    }

    private boolean reserveBuyback(UUID playerId, String shopId, String entryId, ItemStack item,
                                   int quantity, long unitPriceMinor, long now) {
        ArrayList<BuybackRecord> history = buybacks.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        long retentionMillis;
        try { retentionMillis = Math.multiplyExact((long) Config.NPC_SHOP_BUYBACK_MINUTES.get(), 60_000L); }
        catch (ArithmeticException exception) { retentionMillis = Long.MAX_VALUE; }
        String id = "bb_" + Long.toUnsignedString(++buybackSequence, 36);
        history.add(0, new BuybackRecord(id, shopId, entryId, item.copyWithCount(1), quantity,
                unitPriceMinor, safeAdd(now, retentionMillis)));
        boolean changed = false;
        while (history.size() > MAX_BUYBACK_ENTRIES) changed |= finalizeRecord(history.remove(history.size() - 1));
        return changed;
    }

    private BuybackRecord buyback(UUID playerId, String recordId, String shopId) {
        ArrayList<BuybackRecord> history = buybacks.get(playerId);
        if (history == null) return null;
        for (BuybackRecord record : history) {
            if (record.id.equals(recordId) && record.shopId.equals(shopId)) return record;
        }
        return null;
    }

    private void removeBuyback(UUID playerId, String recordId) {
        ArrayList<BuybackRecord> history = buybacks.get(playerId);
        if (history == null) return;
        history.removeIf(record -> record.id.equals(recordId));
        if (history.isEmpty()) buybacks.remove(playerId);
    }

    private int reservedQuantity(String shopId, String entryId) {
        long total = 0L;
        for (ArrayList<BuybackRecord> history : buybacks.values()) {
            for (BuybackRecord record : history) {
                if (record.shopId.equals(shopId) && record.entryId.equals(entryId)) total += record.remaining;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private boolean expireBuybacks(long now) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, ArrayList<BuybackRecord>>> players = buybacks.entrySet().iterator();
        while (players.hasNext()) {
            ArrayList<BuybackRecord> history = players.next().getValue();
            Iterator<BuybackRecord> records = history.iterator();
            while (records.hasNext()) {
                BuybackRecord record = records.next();
                if (record.remaining <= 0 || record.expiresAt <= now) {
                    changed |= finalizeRecord(record);
                    records.remove();
                }
            }
            if (history.isEmpty()) players.remove();
        }
        return changed;
    }

    private boolean finalizeRecord(BuybackRecord record) {
        if (record == null || record.remaining <= 0) return false;
        NpcShopDefinition shop = shops.get(record.shopId);
        NpcShopEntry entry = shop == null ? null : shop.entry(record.entryId);
        if (entry == null || entry.infiniteStock()) return false;
        int before = entry.stock;
        entry.stock = (int) Math.min((long) entry.maxStock, (long) entry.stock + record.remaining);
        return entry.stock != before;
    }

    public synchronized void finalizeBuybacks() {
        boolean changed = false;
        for (ArrayList<BuybackRecord> history : buybacks.values()) {
            for (BuybackRecord record : history) changed |= finalizeRecord(record);
        }
        buybacks.clear();
        if (changed) saveAll();
    }

    private boolean applyRestocks(long now) {
        boolean changed = false;
        for (NpcShopDefinition shop : shops.values()) changed |= applyRestocks(shop, now);
        return changed;
    }

    private boolean applyRestocks(NpcShopDefinition shop, long now) {
        boolean changed = false;
        for (NpcShopEntry entry : shop.entries) {
            changed |= entry.applyRestock(now);
            if (!entry.infiniteStock()) {
                int maximumPublicStock = Math.max(0, entry.maxStock - reservedQuantity(shop.id, entry.id));
                if (entry.stock > maximumPublicStock) {
                    entry.stock = maximumPublicStock;
                    changed = true;
                }
            }
        }
        return changed;
    }

    public synchronized void saveAll() {
        if (folder == null) return;
        Set<Path> files = new LinkedHashSet<>();
        for (NpcShopDefinition shop : shops.values()) {
            shop.normalize();
            validateSerialized(shop);
            Path file = StoragePaths.jsonFile(folder, shop.id);
            files.add(file.toAbsolutePath().normalize());
            store.queueJson(GSON, file, shop);
        }
        if (itemPricesFile != null) files.add(itemPricesFile.toAbsolutePath().normalize());
        store.queueDeleteMissing(files);
        if (itemPricesFile != null) store.queueJson(GSON, itemPricesFile, itemPrices.normalize());
    }

    public synchronized void clear() {
        shops.clear();
        sessions.clear();
        lastActionRequests.clear();
        buybacks.clear();
        store.reset();
        itemPrices = new NpcItemPriceCatalog();
        server = null;
        folder = null;
        itemPricesFile = null;
        buybackSequence = 0L;
        sessionSequence = 0L;
    }

    private static void validateSerialized(NpcShopDefinition shop) {
        if (GSON.toJson(shop).length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("NPC shop exceeds " + MAX_SERIALIZED_CHARACTERS + " serialized characters.");
        }
    }

    private static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try { return NpcDefinition.sanitizeId(raw); }
        catch (RuntimeException exception) { return ""; }
    }

    private static String title(String id) {
        String[] words = id.replace('.', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? "Shop" : result.toString();
    }

    private static String transactionKey(String action, ServerPlayer player, long requestId, long sessionNonce) {
        return "npc_shop:" + action + ":" + player.getUUID() + ":" + sessionNonce + ":" + requestId;
    }

    private static long safeAdd(long a, long b) {
        try { return Math.addExact(a, b); }
        catch (ArithmeticException exception) { return Long.MAX_VALUE; }
    }

    public record CatalogItem(String itemId, String displayName, long buyPriceMinor, long sellPriceMinor, int maxStackSize) {}

    private record Session(String instanceId, String shopId, long expiresAt, long nonce) {}
    private record SessionAccess(NpcInstance instance, NpcDefinition definition, NpcShopDefinition shop, long sessionNonce) {}

    private static final class BuybackRecord {
        private final String id;
        private final String shopId;
        private final String entryId;
        private final ItemStack item;
        private int remaining;
        private final long unitPriceMinor;
        private final long expiresAt;

        private BuybackRecord(String id, String shopId, String entryId, ItemStack item, int remaining,
                              long unitPriceMinor, long expiresAt) {
            this.id = id;
            this.shopId = shopId;
            this.entryId = entryId;
            this.item = item;
            this.remaining = remaining;
            this.unitPriceMinor = unitPriceMinor;
            this.expiresAt = expiresAt;
        }
    }
}
