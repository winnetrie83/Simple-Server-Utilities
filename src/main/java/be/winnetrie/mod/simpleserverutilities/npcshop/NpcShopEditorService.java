package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcEditorService;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInstance;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative bridge for the visual shared NPC shop library and editor. */
public final class NpcShopEditorService {
    private NpcShopEditorService() {}

    public static void handleAdminRequest(NpcShopAdminRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npc_shops")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(), "", false));
    }

    public static void handleAdminAction(NpcShopAdminActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npc_shops")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(),
                        "NPC shop administrator permission is required.", true);
                return;
            }
            switch (payload.action()) {
                case "open" -> openEditor(player, payload.shopId(), payload.requestId());
                case "new" -> openNewEditor(player, payload.newShopId(), payload.requestId());
                case "delete" -> deleteShop(player, payload);
                default -> sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(),
                        "Unknown NPC shop administrator action.", true);
            }
        });
    }

    public static void handleEditorSubmit(NpcShopEditorSubmitPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npc_shops")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                sendResult(player, false, "NPC shop administrator permission is required.", "", false, payload.requestId());
                return;
            }
            try {
                switch (payload.operation()) {
                    case "capture_inventory" -> captureInventory(player, payload, false);
                    case "capture_inventory_one" -> captureInventory(player, payload, true);
                    case "save" -> saveAndKeepOpen(player, payload);
                    case "save_close" -> saveAndClose(player, payload);
                    case "save_previous" -> saveAndNavigate(player, payload, -1);
                    case "save_next" -> saveAndNavigate(player, payload, 1);
                    case "save_manager" -> saveAndOpenManager(player, payload);
                    default -> sendResult(player, false, "Unknown NPC shop editor operation.", "", false, payload.requestId());
                }
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? "NPC shop validation failed." : exception.getMessage();
                sendResult(player, false, message, "", false, payload.requestId());
            }
        });
    }

    public static void openManager(ServerPlayer player) {
        sendManager(player, "", 0, 0L, "", false);
    }

    public static void openEditor(ServerPlayer player, String shopId) {
        openEditor(player, shopId, 0L);
    }

    private static void openEditor(ServerPlayer player, String shopId, long requestId) {
        if (!canAdmin(player)) {
            player.sendSystemMessage(Component.literal("NPC shop administrator permission is required."));
            return;
        }
        NpcShopDefinition definition = SimpleServerUtilities.NPC_SHOPS.get(shopId);
        if (definition == null) {
            sendResult(player, false, "Unknown NPC shop: " + shopId, "", false, requestId);
            return;
        }
        sendEditor(player, definition.id, definition.copy(), firstEntryId(definition), "", requestId);
    }

    private static void openNewEditor(ServerPlayer player, String requestedId, long requestId) {
        if (!canAdmin(player)) {
            player.sendSystemMessage(Component.literal("NPC shop administrator permission is required."));
            return;
        }
        String id;
        try { id = NpcDefinition.sanitizeId(requestedId == null || requestedId.isBlank() ? "new_shop" : requestedId); }
        catch (RuntimeException exception) {
            sendResult(player, false, "Enter a valid new shop ID.", "", false, requestId);
            return;
        }
        if (SimpleServerUtilities.NPC_SHOPS.get(id) != null) {
            sendResult(player, false, "A shop with ID '" + id + "' already exists.", "", false, requestId);
            return;
        }
        NpcShopDefinition definition = new NpcShopDefinition();
        definition.id = id;
        definition.displayName = title(id);
        definition.entries = new ArrayList<>();
        definition.normalize();
        sendEditor(player, "", definition, "", "Create the shop, then assign its ID to one or more NPCs.", requestId);
    }

    private static void deleteShop(ServerPlayer player, NpcShopAdminActionPayload payload) {
        List<NpcShopEditorOpenPayload.Usage> usages = usages(payload.shopId());
        if (!usages.isEmpty()) {
            int placements = usages.stream().mapToInt(NpcShopEditorOpenPayload.Usage::placementCount).sum();
            String message = "Cannot delete '" + payload.shopId() + "': it is linked to " + usages.size()
                    + " NPC template(s) and " + placements + " placed NPC(s).";
            sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(), message, true);
            return;
        }
        if (SimpleServerUtilities.NPC_SHOPS.delete(payload.shopId())) {
            sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(),
                    "Deleted NPC shop '" + payload.shopId() + "'.", false);
        } else {
            sendManager(player, payload.query(), payload.pageIndex(), payload.requestId(),
                    "Unknown NPC shop: " + payload.shopId(), true);
        }
    }

    private static void captureInventory(ServerPlayer player, NpcShopEditorSubmitPayload payload, boolean oneItem) {
        int slot = payload.inventorySlot();
        if (slot < 0 || slot >= 36) {
            sendResult(player, false, "Select a valid inventory slot first.", "", false, payload.requestId());
            return;
        }
        ItemStack selected = player.getInventory().getItem(slot);
        if (selected.isEmpty()) {
            sendResult(player, false, "That inventory slot is empty or changed.", "", false, payload.requestId());
            return;
        }
        NpcShopDefinition draft = SimpleServerUtilities.NPC_SHOPS.fromDraftJson(payload.definitionJson());
        NpcShopEntry entry = draft.entry(payload.selectedEntryId());
        if (entry == null) {
            sendResult(player, false, "Select an offer before choosing an inventory item.", "", false, payload.requestId());
            return;
        }
        ItemStack exactCopy = selected.copy();
        if (oneItem) exactCopy.setCount(1);
        entry.setItem(player.level().registryAccess(), exactCopy);
        entry.itemCount = Math.max(1, exactCopy.getCount());
        entry.normalize();
        sendEditor(player, payload.originalShopId(), draft, entry.id, "", payload.requestId());
    }

    private static void saveAndKeepOpen(ServerPlayer player, NpcShopEditorSubmitPayload payload) {
        NpcShopDefinition saved = saveDraft(player, payload);
        if (saved == null) return;
        String selectedEntryId = payload.selectedEntryId();
        if (saved.entry(selectedEntryId) == null) selectedEntryId = firstEntryId(saved);
        sendEditor(player, saved.id, saved.copy(), selectedEntryId,
                "Shop '" + saved.displayName + "' saved.", payload.requestId());
    }

    private static void saveAndClose(ServerPlayer player, NpcShopEditorSubmitPayload payload) {
        NpcShopDefinition saved = saveDraft(player, payload);
        if (saved == null) return;
        sendResult(player, true, "NPC shop '" + saved.displayName + "' saved.", saved.id, true, payload.requestId());
    }

    private static void saveAndNavigate(ServerPlayer player, NpcShopEditorSubmitPayload payload, int direction) {
        NpcShopDefinition saved = saveDraft(player, payload);
        if (saved == null) return;
        List<NpcShopDefinition> shops = sortedShops();
        int index = indexOfShop(shops, saved.id);
        if (index < 0 || shops.isEmpty()) {
            sendEditor(player, saved.id, saved.copy(), firstEntryId(saved),
                    "Shop saved, but the shop list could not be refreshed.", payload.requestId());
            return;
        }
        int target = Math.max(0, Math.min(shops.size() - 1, index + direction));
        NpcShopDefinition next = shops.get(target);
        String notice = target == index
                ? "Shop saved. There is no " + (direction < 0 ? "previous" : "next") + " shop."
                : "Shop saved. Opened '" + next.displayName + "'.";
        sendEditor(player, next.id, next.copy(), firstEntryId(next), notice, payload.requestId());
    }

    private static void saveAndOpenManager(ServerPlayer player, NpcShopEditorSubmitPayload payload) {
        NpcShopDefinition saved = saveDraft(player, payload);
        if (saved == null) return;
        sendManager(player, "", pageForShop(saved.id), payload.requestId(),
                "NPC shop '" + saved.displayName + "' saved.", false);
    }

    private static NpcShopDefinition saveDraft(ServerPlayer player, NpcShopEditorSubmitPayload payload) {
        NpcShopDefinition draft = SimpleServerUtilities.NPC_SHOPS.fromJson(payload.definitionJson());
        if (!SimpleServerUtilities.NPC_SHOPS.saveDefinition(payload.originalShopId(), draft)) {
            sendResult(player, false,
                    payload.originalShopId().isBlank()
                            ? "That shop ID already exists or the shop limit was reached."
                            : "The original shop no longer exists.",
                    "", false, payload.requestId());
            return null;
        }
        return SimpleServerUtilities.NPC_SHOPS.get(draft.id);
    }

    private static void sendManager(ServerPlayer player, String rawQuery, int rawPage, long requestId,
            String notice, boolean error) {
        if (!canAdmin(player)) {
            PacketDistributor.sendToPlayer(player, new NpcShopAdminDataPayload("", 0, 1, 0, List.of(),
                    "NPC shop administrator permission is required.", true, requestId));
            return;
        }
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        List<NpcShopDefinition> filtered = SimpleServerUtilities.NPC_SHOPS.definitions().stream()
                .filter(shop -> query.isBlank() || shop.id.toLowerCase(Locale.ROOT).contains(query)
                        || shop.displayName.toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(shop -> shop.id))
                .toList();
        int total = filtered.size();
        int pages = Math.max(1, (total + NpcShopAdminDataPayload.MAX_ENTRIES - 1) / NpcShopAdminDataPayload.MAX_ENTRIES);
        int page = Math.max(0, Math.min(rawPage, pages - 1));
        int from = Math.min(total, page * NpcShopAdminDataPayload.MAX_ENTRIES);
        int to = Math.min(total, from + NpcShopAdminDataPayload.MAX_ENTRIES);
        List<NpcShopAdminDataPayload.Entry> entries = filtered.subList(from, to).stream()
                .map(shop -> {
                    List<NpcShopEditorOpenPayload.Usage> references = usages(shop.id);
                    int placements = references.stream().mapToInt(NpcShopEditorOpenPayload.Usage::placementCount).sum();
                    return new NpcShopAdminDataPayload.Entry(shop.id, shop.displayName, shop.enabled,
                            shop.entries.size(), references.size(), placements);
                })
                .toList();
        PacketDistributor.sendToPlayer(player, new NpcShopAdminDataPayload(query, page, pages, total,
                entries, notice, error, requestId));
    }

    private static void sendEditor(ServerPlayer player, String originalShopId, NpcShopDefinition definition,
            String selectedEntryId, String notice, long requestId) {
        var settings = SimpleServerUtilities.ECONOMY.settings();
        List<NpcShopDefinition> shops = sortedShops();
        String lookupId = originalShopId == null || originalShopId.isBlank() ? definition.id : originalShopId;
        int index = indexOfShop(shops, lookupId);
        PacketDistributor.sendToPlayer(player, new NpcShopEditorOpenPayload(originalShopId,
                SimpleServerUtilities.NPC_SHOPS.toJson(definition), selectedEntryId,
                settings.getCurrencySymbol(), settings.getDecimalPlaces(), index, shops.size(),
                usages(lookupId), notice, requestId));
    }

    private static List<NpcShopEditorOpenPayload.Usage> usages(String rawShopId) {
        String shopId = rawShopId == null ? "" : rawShopId.trim();
        if (shopId.isBlank()) return List.of();
        Map<String, Integer> placements = new HashMap<>();
        for (NpcInstance instance : SimpleServerUtilities.NPCS.instances()) {
            placements.merge(instance.definitionId, 1, Integer::sum);
        }
        ArrayList<NpcShopEditorOpenPayload.Usage> result = new ArrayList<>();
        for (NpcDefinition definition : SimpleServerUtilities.NPCS.definitions()) {
            if (shopId.equals(definition.shopId)) {
                result.add(new NpcShopEditorOpenPayload.Usage(definition.id, definition.displayName,
                        placements.getOrDefault(definition.id, 0), 1));
            }
        }
        result.sort(Comparator.comparing(NpcShopEditorOpenPayload.Usage::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NpcShopEditorOpenPayload.Usage::definitionId));
        return List.copyOf(result);
    }

    private static List<NpcShopDefinition> sortedShops() {
        return SimpleServerUtilities.NPC_SHOPS.definitions().stream()
                .sorted(Comparator.comparing(shop -> shop.id))
                .toList();
    }

    private static int indexOfShop(List<NpcShopDefinition> shops, String shopId) {
        for (int index = 0; index < shops.size(); index++) {
            if (shops.get(index).id.equals(shopId)) return index;
        }
        return -1;
    }

    private static int pageForShop(String shopId) {
        int index = indexOfShop(sortedShops(), shopId);
        return index < 0 ? 0 : index / NpcShopAdminDataPayload.MAX_ENTRIES;
    }

    private static void sendResult(ServerPlayer player, boolean success, String message, String shopId,
            boolean closeEditor, long requestId) {
        PacketDistributor.sendToPlayer(player, new NpcShopEditorResultPayload(
                success, message, shopId, closeEditor, requestId));
    }

    private static boolean canAdmin(ServerPlayer player) {
        return player != null && SimpleServerUtilities.CORE.modules().isActive("npc_shops")
                && NpcEditorService.canAdmin(player)
                && PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_ADMIN, false);
    }

    private static String firstEntryId(NpcShopDefinition definition) {
        return definition.entries == null || definition.entries.isEmpty() ? "" : definition.entries.getFirst().id;
    }

    private static String title(String id) {
        StringBuilder result = new StringBuilder();
        for (String word : id.replace('.', '_').split("_")) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? "Shop" : result.toString();
    }
}
