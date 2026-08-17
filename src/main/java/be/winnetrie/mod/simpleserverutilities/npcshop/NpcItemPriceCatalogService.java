package be.winnetrie.mod.simpleserverutilities.npcshop;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogRequestPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcEditorService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative paged editor for all vanilla and modded registered item prices. */
public final class NpcItemPriceCatalogService {
    private NpcItemPriceCatalogService() {}

    public static void handleRequest(NpcItemPriceCatalogRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npc_shops")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> send(player, payload.query(), payload.pageIndex(), payload.requestId(), "", false));
    }

    public static void handleAction(NpcItemPriceCatalogActionPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npc_shops")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                send(player, payload.query(), payload.pageIndex(), payload.requestId(),
                        "NPC shop administrator permission is required.", true);
                return;
            }
            if (!SimpleServerUtilities.NPC_SHOPS.setItemPrice(payload.itemId(),
                    payload.buyPriceMinor(), payload.sellPriceMinor())) {
                send(player, payload.query(), payload.pageIndex(), payload.requestId(),
                        "That item is no longer registered on this server.", true);
                return;
            }
            send(player, payload.query(), payload.pageIndex(), payload.requestId(),
                    "Saved base prices for " + payload.itemId() + ".", false);
        });
    }

    public static void open(ServerPlayer player) {
        send(player, "", 0, 0L, "All active vanilla and modded items are loaded from the live registry.", false);
    }

    private static void send(ServerPlayer player, String query, int requestedPage, long requestId,
            String notice, boolean error) {
        if (!canAdmin(player)) {
            PacketDistributor.sendToPlayer(player, new NpcItemPriceCatalogDataPayload("", 0, 1, 0,
                    "", 2, List.of(), "NPC shop administrator permission is required.", true, requestId));
            return;
        }
        List<NpcShopManager.CatalogItem> all = SimpleServerUtilities.NPC_SHOPS.catalogItems(query);
        int total = all.size();
        int pages = Math.max(1, (total + NpcItemPriceCatalogDataPayload.PAGE_SIZE - 1)
                / NpcItemPriceCatalogDataPayload.PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = Math.min(total, page * NpcItemPriceCatalogDataPayload.PAGE_SIZE);
        int to = Math.min(total, from + NpcItemPriceCatalogDataPayload.PAGE_SIZE);
        List<NpcItemPriceCatalogDataPayload.Entry> entries = all.subList(from, to).stream()
                .map(item -> new NpcItemPriceCatalogDataPayload.Entry(item.itemId(), item.displayName(),
                        item.buyPriceMinor(), item.sellPriceMinor(), item.maxStackSize()))
                .toList();
        var settings = SimpleServerUtilities.ECONOMY.settings();
        PacketDistributor.sendToPlayer(player, new NpcItemPriceCatalogDataPayload(
                query == null ? "" : query.trim(), page, pages, total,
                settings.getCurrencySymbol(), settings.getDecimalPlaces(), entries, notice, error, requestId));
    }

    private static boolean canAdmin(ServerPlayer player) {
        return player != null && SimpleServerUtilities.CORE.modules().isActive("npc_shops")
                && NpcEditorService.canAdmin(player)
                && PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_ADMIN, false);
    }
}
