package be.winnetrie.mod.simpleserverutilities.maintenance;

import java.time.Duration;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.blockinfo.BlockInformationService;
import net.minecraft.server.MinecraftServer;

/**
 * Shared, server-thread reload lifecycle used by both the legacy command and
 * the GUI. Managed dimensions remain restart-bound because datapack registry
 * changes cannot be applied safely to an already running server.
 */
public final class SsuReloadService {
    private SsuReloadService() {
    }

    public static ReloadResult reloadAll(MinecraftServer server) {
        if (server == null) return ReloadResult.failure("Server is not available.");
        if (SimpleServerUtilities.JOBS.size() > 0) {
            return ReloadResult.failure("Cannot reload SSU while " + SimpleServerUtilities.JOBS.size()
                    + " long-running job(s) are active. Finish or cancel them first.");
        }

        boolean achievementsWasActive = active("achievements");
        boolean statisticsWasActive = active("statistics");
        boolean mapMarkersWasActive = active("map_markers");
        boolean auctionWasActive = active("auction_house");
        boolean npcsWasActive = active("npcs");
        boolean npcShopsWasActive = active("npc_shops");
        boolean questsWasActive = active("quests");
        boolean minigamesWasActive = active("minigames");
        boolean dungeonsWasActive = active("dungeons");

        if (achievementsWasActive) SimpleServerUtilities.ACHIEVEMENTS.saveAll();
        if (statisticsWasActive) SimpleServerUtilities.STATISTICS.saveAll();
        if (mapMarkersWasActive) SimpleServerUtilities.MAP_MARKERS.save();
        if (auctionWasActive) SimpleServerUtilities.AUCTION_HOUSE.saveAllSync();
        if (npcsWasActive) {
            SimpleServerUtilities.NPCS.saveAll();
            SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.saveAll();
        }
        if (npcShopsWasActive) SimpleServerUtilities.NPC_SHOPS.saveAll();
        if (questsWasActive) SimpleServerUtilities.QUESTS.saveAll();
        if (minigamesWasActive) {
            SimpleServerUtilities.MINIGAMES.shutdownRuntime(true);
            SimpleServerUtilities.MINIGAMES.saveAll();
        }
        if (dungeonsWasActive) {
            SimpleServerUtilities.DUNGEONS.shutdownRuntime(true);
            SimpleServerUtilities.DUNGEONS.saveAll();
        }
        SimpleServerUtilities.CONTENT_PROGRESS.saveAll();
        SimpleServerUtilities.CONTENT_REWARD_LEDGER.saveAll();
        SimpleServerUtilities.TEMPORARY_PERMISSIONS.saveAll();
        SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));

        SimpleServerUtilities.CORE.modules().refreshEnabledState(server);

        SimpleServerUtilities.TRANSACTIONS.clear();
        SimpleServerUtilities.CONTENT_PROGRESS.load(server);
        SimpleServerUtilities.CONTENT_REWARD_LEDGER.load(server);
        SimpleServerUtilities.TEMPORARY_PERMISSIONS.load(server);
        SimpleServerUtilities.ECONOMY.load(server);
        if (Config.ENABLE_PLAYER_CLAIMS.get()) {
            SimpleServerUtilities.PLAYER_CLAIMS.load(server);
            SimpleServerUtilities.CLAIM_TAX.load(server);
        }
        if (Config.ENABLE_PERMISSION_SYSTEM.get()) {
            SimpleServerUtilities.PERMISSIONS.load(server);
            SimpleServerUtilities.PERMISSIONS.migrateLegacyClaimLimitOverrides();
        }
        if (Config.ENABLE_MAIL.get()) SimpleServerUtilities.MAIL.load(server);
        if (active("auction_house")) SimpleServerUtilities.AUCTION_HOUSE.load(server);
        if (Config.ENABLE_HOMES.get()) SimpleServerUtilities.HOMES.load(server);
        if (Config.ENABLE_WARPS.get()) SimpleServerUtilities.WARPS.load(server);
        SimpleServerUtilities.SERVER_SPAWN.load(server);
        SimpleServerUtilities.UI_PREFERENCES.load(server);

        if (Config.ENABLE_ADMIN_REGIONS.get()) {
            SimpleServerUtilities.REGIONS.load(server);
            SimpleServerUtilities.REGION_SNAPSHOTS.load(server);
            SimpleServerUtilities.REGION_RENT_JOURNAL.loadAndRecover(server);
        }
        SimpleServerUtilities.BORDER_SETTINGS.load(server);

        if (active("statistics")) SimpleServerUtilities.STATISTICS.load(server);
        if (active("achievements")) SimpleServerUtilities.ACHIEVEMENTS.load(server);
        if (active("map_markers")) SimpleServerUtilities.MAP_MARKERS.load(server);
        if (active("npcs")) {
            SimpleServerUtilities.NPC_DIALOGUES.clear();
            SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.load(server);
            SimpleServerUtilities.NPCS.load(server);
        }
        if (active("npc_shops")) SimpleServerUtilities.NPC_SHOPS.load(server);
        if (active("quests")) SimpleServerUtilities.QUESTS.load(server);
        if (active("minigames")) SimpleServerUtilities.MINIGAMES.load(server);
        if (active("dungeons")) SimpleServerUtilities.DUNGEONS.load(server);

        BlockInformationService.syncAll(server);
        if (Config.ENABLE_TREECAPITATOR.get()) {
            SimpleServerUtilities.TREE_PLACEMENTS.load(server);
        } else {
            SimpleServerUtilities.TREE_PLACEMENTS.save();
            SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));
            SimpleServerUtilities.TREE_PLACEMENTS.clear();
        }
        SimpleServerUtilities.UTILITY_MINING.clearClients(server);
        SimpleServerUtilities.UTILITY_MINING.clear();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(server);
        if (Config.ENABLE_HOLOGRAMS.get()) {
            SimpleServerUtilities.HOLOGRAMS.load(server);
            SimpleServerUtilities.HOLOGRAMS.syncAll();
        } else {
            SimpleServerUtilities.HOLOGRAMS.clearClients(server);
        }

        return new ReloadResult(true, "Simple Server Utilities reloaded. Managed dimension changes still require a server restart.",
                List.of("managed_dimensions"));
    }

    private static boolean active(String module) {
        return SimpleServerUtilities.CORE.modules().isActive(module);
    }

    public record ReloadResult(boolean successful, String message, List<String> restartBoundModules) {
        public ReloadResult {
            message = message == null ? "" : message;
            restartBoundModules = restartBoundModules == null ? List.of() : List.copyOf(restartBoundModules);
        }

        public static ReloadResult failure(String message) {
            return new ReloadResult(false, message, List.of());
        }
    }
}
