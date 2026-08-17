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

        // Remember which feature modules were already running. refreshEnabledState()
        // starts newly enabled modules itself, so those must not be loaded a second
        // time below. Modules that stay active are explicitly reloaded from disk.
        boolean economyWasActive = active("economy");
        boolean claimsWasActive = active("claims");
        boolean permissionsWasActive = active("permissions");
        boolean mailWasActive = active("mail");
        boolean auctionWasActive = active("auction_house");
        boolean homesWasActive = active("homes");
        boolean warpsWasActive = active("warps");
        boolean spawnWasActive = active("spawn");
        boolean regionsWasActive = active("regions");
        boolean visualizationWasActive = active("visualization");
        boolean achievementsWasActive = active("achievements");
        boolean statisticsWasActive = active("statistics");
        boolean mapMarkersWasActive = active("map_markers");
        boolean npcsWasActive = active("npcs");
        boolean npcShopsWasActive = active("npc_shops");
        boolean questsWasActive = active("quests");
        boolean minigamesWasActive = active("minigames");
        boolean dungeonsWasActive = active("dungeons");
        boolean hologramsWasActive = active("holograms");
        boolean utilityMiningWasActive = active("utility_mining");

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

        // Re-evaluate configured/effective module state first. Hard dependants are
        // cascaded off safely; newly effective modules initialize/load themselves.
        SimpleServerUtilities.CORE.modules().refreshEnabledState(server);

        SimpleServerUtilities.TRANSACTIONS.clear();
        SimpleServerUtilities.CONTENT_PROGRESS.load(server);
        SimpleServerUtilities.CONTENT_REWARD_LEDGER.load(server);
        SimpleServerUtilities.TEMPORARY_PERMISSIONS.load(server);
        SimpleServerUtilities.UI_PREFERENCES.load(server);

        if (stayedActive(economyWasActive, "economy")) SimpleServerUtilities.ECONOMY.load(server);
        if (stayedActive(claimsWasActive, "claims")) {
            SimpleServerUtilities.PLAYER_CLAIMS.load(server);
            SimpleServerUtilities.CLAIM_TAX.load(server);
        }
        if (stayedActive(permissionsWasActive, "permissions")) {
            SimpleServerUtilities.PERMISSIONS.load(server);
            SimpleServerUtilities.PERMISSIONS.migrateLegacyClaimLimitOverrides();
        }
        if (stayedActive(mailWasActive, "mail")) SimpleServerUtilities.MAIL.load(server);
        if (stayedActive(auctionWasActive, "auction_house")) SimpleServerUtilities.AUCTION_HOUSE.load(server);
        if (stayedActive(homesWasActive, "homes")) SimpleServerUtilities.HOMES.load(server);
        if (stayedActive(warpsWasActive, "warps")) SimpleServerUtilities.WARPS.load(server);
        if (stayedActive(spawnWasActive, "spawn")) SimpleServerUtilities.SERVER_SPAWN.load(server);

        if (stayedActive(regionsWasActive, "regions")) {
            SimpleServerUtilities.REGIONS.load(server);
            SimpleServerUtilities.REGION_SNAPSHOTS.load(server);
            if (stayedActive(economyWasActive, "economy")) {
                SimpleServerUtilities.REGION_RENT_JOURNAL.loadAndRecover(server);
            } else {
                SimpleServerUtilities.REGION_RENT_JOURNAL.clear();
            }
        }
        if (stayedActive(visualizationWasActive, "visualization")) {
            SimpleServerUtilities.BORDER_SETTINGS.load(server);
        }

        if (stayedActive(statisticsWasActive, "statistics")) SimpleServerUtilities.STATISTICS.load(server);
        if (stayedActive(achievementsWasActive, "achievements")) SimpleServerUtilities.ACHIEVEMENTS.load(server);
        if (stayedActive(mapMarkersWasActive, "map_markers")) SimpleServerUtilities.MAP_MARKERS.load(server);
        if (stayedActive(npcsWasActive, "npcs")) {
            SimpleServerUtilities.NPC_DIALOGUES.clear();
            SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.load(server);
            SimpleServerUtilities.NPCS.load(server);
        }
        if (stayedActive(npcShopsWasActive, "npc_shops")) SimpleServerUtilities.NPC_SHOPS.load(server);
        if (stayedActive(questsWasActive, "quests")) SimpleServerUtilities.QUESTS.load(server);
        if (stayedActive(minigamesWasActive, "minigames")) SimpleServerUtilities.MINIGAMES.load(server);
        if (stayedActive(dungeonsWasActive, "dungeons")) SimpleServerUtilities.DUNGEONS.load(server);

        BlockInformationService.syncAll(server);
        if (active("utility_mining") && Config.ENABLE_TREECAPITATOR.get()) {
            if (utilityMiningWasActive) SimpleServerUtilities.TREE_PLACEMENTS.load(server);
        } else {
            SimpleServerUtilities.TREE_PLACEMENTS.save();
            SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(5));
            SimpleServerUtilities.TREE_PLACEMENTS.clear();
        }
        SimpleServerUtilities.UTILITY_MINING.clearClients(server);
        SimpleServerUtilities.UTILITY_MINING.clear();

        if (active("visualization")) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(server);
        } else {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.clearAllClients(server);
        }
        if (stayedActive(hologramsWasActive, "holograms")) {
            SimpleServerUtilities.HOLOGRAMS.load(server);
            SimpleServerUtilities.HOLOGRAMS.syncAll();
        } else if (!active("holograms")) {
            SimpleServerUtilities.HOLOGRAMS.clearClients(server);
        }

        return new ReloadResult(true,
                "Simple Server Utilities reloaded. Effective module dependencies were refreshed; managed dimension changes still require a server restart.",
                List.of("managed_dimensions"));
    }

    private static boolean stayedActive(boolean wasActive, String module) {
        return wasActive && active(module);
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
