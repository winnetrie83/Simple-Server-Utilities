package be.winnetrie.mod.simpleserverutilities.menu;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.QuestAccessMode;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapService;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.blockinfo.BlockInformationService;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyAccount;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionRecord;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.home.PlayerHome;
import be.winnetrie.mod.simpleserverutilities.home.ClaimHomeSupport;
import be.winnetrie.mod.simpleserverutilities.hologram.AdminToolService;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramDefinition;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramEditorService;
import be.winnetrie.mod.simpleserverutilities.maintenance.SsuReloadService;
import be.winnetrie.mod.simpleserverutilities.mapmarker.MapMarkerService;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.PlayerUiSettingUpdatePayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionCatalog;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionRank;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.PlayerPermissionData;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionValueResolver;
import be.winnetrie.mod.simpleserverutilities.permission.policy.ClaimPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.HomePolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportOptions;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportType;
import be.winnetrie.mod.simpleserverutilities.permission.policy.WarpPolicy;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentOperationRecord;
import be.winnetrie.mod.simpleserverutilities.region.RegionMutationGuard;
import be.winnetrie.mod.simpleserverutilities.region.RegionOperationResult;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelection;
import be.winnetrie.mod.simpleserverutilities.region.RegionWorldEditManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentalService;
import be.winnetrie.mod.simpleserverutilities.spawn.ServerSpawn;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnPolicy;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnService;
import be.winnetrie.mod.simpleserverutilities.statistics.PlayerStatisticDefinition;
import be.winnetrie.mod.simpleserverutilities.statistics.StatisticEditorService;
import be.winnetrie.mod.simpleserverutilities.settings.MinimapPosition;
import be.winnetrie.mod.simpleserverutilities.settings.MinimapShape;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportSafety;
import be.winnetrie.mod.simpleserverutilities.utilitymining.MiningActivationMode;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import be.winnetrie.mod.simpleserverutilities.visualization.PlayerBorderPreferences;
import be.winnetrie.mod.simpleserverutilities.warp.Warp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative shell, paged dashboard queries and closed typed actions. */
public final class SsuMenuService {


    public void open(ServerPlayer player) {
        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        var uiPreferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        boolean administrator = isAdministrator(player);

        List<PlayerClaim> playerClaims = ownedClaims(player);
        List<Region> visibleRegions = visibleRegions(player, administrator);
        int claimedChunks = playerClaims.stream().mapToInt(PlayerClaim::getChunkCount).sum();
        int activeRentals = (int) visibleRegions.stream().filter(r -> player.getUUID().equals(r.getRentData().getRenter())).count();

        var performance = SimpleServerUtilities.PERFORMANCE.snapshot();
        var regionIndex = SimpleServerUtilities.REGIONS.spatialIndexStatistics();
        var coreSummary = new SsuMenuSnapshotPayload.CoreSummary(
                performance.permissionChecks(),
                (int) Math.round(performance.permissionCacheHitRate() * 1000.0D),
                SimpleServerUtilities.PERMISSIONS.cachedResolutionCount(),
                performance.regionLookups(),
                performance.averageRegionCandidates(),
                regionIndex.cells(),
                regionIndex.references(),
                playerClaims.size(),
                claimedChunks,
                visibleRegions.size(),
                activeRentals,
                PermissionService.getBoolean(player, PermissionKeys.HOMES_USE, true)
                        ? SimpleServerUtilities.HOMES.countHomes(player.getUUID()) : 0,
                PermissionService.getBoolean(player, PermissionKeys.HOMES_USE, true)
                        ? HomePolicy.getMaxHomes(player) : 0,
                PermissionService.getBoolean(player, PermissionKeys.WARPS_USE, true)
                        ? SimpleServerUtilities.WARPS.countWarps() : 0
        );

        PacketDistributor.sendToPlayer(player, new SsuMenuSnapshotPayload(
                player.getName().getString(),
                SimpleServerUtilities.PERMISSIONS.getPrimaryRankName(player.getUUID()),
                PermissionService.getBoolean(player, PermissionKeys.SETTINGS_USE, true),
                new SsuMenuSnapshotPayload.UiSettingsSummary(
                        uiPreferences.isDashboardHints(), uiPreferences.isMinimapEnabled(),
                        uiPreferences.getMinimapSize(), uiPreferences.getMinimapShape().name(),
                        uiPreferences.getMinimapPosition().name(), uiPreferences.isMinimapNorthUp(),
                        uiPreferences.isMinimapShowClaims(), uiPreferences.isMinimapShowRegions(),
                        uiPreferences.isWorldMapShowClaims(), uiPreferences.isWorldMapShowRegions(),
                        uiPreferences.isWorldMapShowMarkers(), uiPreferences.isMinimapShowMarkers(),
                        uiPreferences.isMinimapShowCalendar(), uiPreferences.isWorldMarkersVisible(),
                        uiPreferences.isMarkerBeamsVisible(),
                        uiPreferences.getMarkerBeamDistance(),
                        uiPreferences.getMapLiveUpdateRadiusChunks(),
                        uiPreferences.isBlockInformationEnabled(),
                        PermissionService.getBooleanWithoutOperatorBypass(
                                player, PermissionKeys.BLOCK_INFORMATION_DEBUG, false),
                        uiPreferences.isBlockInformationDebugEnabled(),
                        uiPreferences.isMailAutoDeletePlayerAttachments(),
                        uiPreferences.isMailAutoDeleteSystemAttachments(),
                        uiPreferences.isMailAutoDeleteAuctionAttachments(),
                        uiPreferences.isTreecapitatorEnabled(),
                        uiPreferences.getTreecapitatorActivation().name(),
                        uiPreferences.getTreecapitatorOutlineColor(),
                        uiPreferences.getTreecapitatorOutlineBrightness(),
                        uiPreferences.isTreecapitatorInfoEnabled(),
                        uiPreferences.isVeinminerEnabled(),
                        uiPreferences.getVeinminerActivation().name(),
                        uiPreferences.getVeinminerOutlineColor(),
                        uiPreferences.getVeinminerOutlineBrightness(),
                        uiPreferences.isVeinminerInfoEnabled()
                ),
                administrator,
                Config.ENABLE_CROPS_HARVESTING.get(),
                SimpleServerUtilities.AUCTION_HOUSE.dashboardVisible(player),
                new SsuMenuSnapshotPayload.ModuleSettingsSummary(
                        Config.ENABLE_PLAYER_CLAIMS.get(),
                        Config.ENABLE_HOMES.get(),
                        Config.ENABLE_WARPS.get(),
                        Config.ENABLE_ADMIN_REGIONS.get(),
                        Config.ENABLE_TREECAPITATOR.get(),
                        Config.ENABLE_VEINMINER.get(),
                        Config.ENABLE_CROPS_HARVESTING.get(),
                        Config.ENABLE_HOLOGRAMS.get(),
                        Config.ENABLE_BLOCK_INFORMATION.get(),
                        Config.ENABLE_CUSTOM_STATISTICS.get(),
                        Config.ENABLE_MAIL.get(),
                        Config.ENABLE_AUCTION_HOUSE.get(),
                        Config.ENABLE_NPCS.get(),
                        Config.ENABLE_QUESTS.get(),
                        Config.ENABLE_MINIGAMES.get(),
                        Config.ENABLE_DUNGEONS.get(),
                        ContentAccessPolicy.configuredQuestAccessMode().serializedName(),
                        ContentAccessPolicy.effectiveQuestAccessMode().serializedName(),
                        Config.ENABLE_PERMISSION_SYSTEM.get(),
                        Config.ALLOW_REMOTE_HOLOGRAM_IMAGES.get(),
                        Config.HOLOGRAM_RENDER_DISTANCE.get(),
                        SimpleServerUtilities.BORDER_SETTINGS.settings().getClaimRenderDistanceBlocks(),
                        SimpleServerUtilities.BORDER_SETTINGS.settings().getRegionRenderDistanceBlocks()
                ),
                new SsuMenuSnapshotPayload.AdminAccessSummary(
                        canPermissionAdmin(player),
                        administrator && PermissionService.getBoolean(player, PermissionKeys.CORE_ADMIN, false),
                        administrator && PermissionService.getBoolean(player, PermissionKeys.REGIONS_RENT_ADMIN, false),
                        SpawnPolicy.canAdmin(player)
                ),
                preferences.isClaimBordersVisible(), preferences.isShowOtherClaims(), preferences.isRegionBordersVisible(),
                preferences.isMinigameGameBorderVisible(), preferences.isMinigameSpectatorBorderVisible(),
                Config.ENABLE_PLAYER_CLAIMS.get()
                        && PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_CLAIMS_VIEW, true),
                Config.ENABLE_ADMIN_REGIONS.get()
                        && PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_REGIONS_VIEW, true),
                SimpleServerUtilities.JOBS.size(), SimpleServerUtilities.STORAGE.statistics().pending(),
                coreSummary, buildEconomySummary(player, administrator),
                List.of(), List.of(), List.of(), List.of()
        ));
    }

    public void handlePageRequest(SsuMenuPageRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String page = payload.page();
        SsuMenuPageDataPayload response;
        try {
            response = switch (page) {
                case "claims" -> claimsPage(player, payload);
                case "homes" -> homesPage(player, payload);
                case "travel" -> travelPage(player, payload);
                case "travel_admin" -> adminTravelPage(player, payload);
                case "player_warps" -> playerWarpsPage(player, payload);
                case "admin_claims" -> adminClaimsPage(player, payload);
                case "regions" -> regionsPage(player, payload);
                case "region_admin" -> regionAdminPage(player, payload);
                case "wallet_transactions" -> walletTransactionsPage(player, payload);
                case "transactions" -> transactionsPage(player, payload);
                case "auction_tax" -> auctionTaxPage(player, payload);
                case "claim_tax" -> claimTaxPage(player, payload);
                case "warp_rental" -> warpRentalPage(player, payload);
                case "accounts" -> accountsPage(player, payload);
                case "jobs" -> jobsPage(player, payload);
                case "rent_operations" -> rentOperationsPage(player, payload);
                case "permissions" -> permissionsPage(player, payload);
                case "ranks" -> ranksPage(player, payload);
                case "utility_mining_admin" -> utilityMiningAdminPage(player, payload);
                case "maintenance" -> maintenancePage(player, payload);
                case "holograms" -> hologramsPage(player, payload);
                case "statistics" -> statisticsPage(player, payload);
                default -> SsuMenuPageDataPayload.empty(page, payload.pageIndex(), payload.pageSize(),
                        payload.requestId(), "Unknown dashboard page.", true);
            };
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Dashboard page '{}' failed for {}", page,
                    player.getName().getString(), e);
            response = SsuMenuPageDataPayload.empty(page, payload.pageIndex(), payload.pageSize(),
                    payload.requestId(), "The dashboard page could not be loaded safely.", true);
        }
        PacketDistributor.sendToPlayer(player, response);
    }

    public void handlePermissionEditorRequest(SsuPermissionEditorRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        SsuPermissionEditorDataPayload response;
        try {
            response = permissionEditorData(player, payload);
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Permission editor request failed for {}", player.getName().getString(), e);
            response = SsuPermissionEditorDataPayload.empty(payload.mode(), payload.requestId(),
                    "The permission editor could not be loaded safely.", true);
        }
        PacketDistributor.sendToPlayer(player, response);
    }

    public void handlePlayerProfileRequest(SsuPlayerProfileRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        SsuPlayerProfileDataPayload response;
        try {
            response = playerProfileData(player, payload);
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Player profile request failed for {}", player.getName().getString(), e);
            response = SsuPlayerProfileDataPayload.empty(payload.requestId(),
                    "The player profile could not be loaded safely.", true);
        }
        PacketDistributor.sendToPlayer(player, response);
    }

    public void handlePlayerUiSettingUpdate(PlayerUiSettingUpdatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        setting(player, payload.key(), payload.value());
    }

    public void handleAction(SsuMenuActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ActionResult result;
        try {
            result = performAction(player, payload);
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Dashboard action '{}' failed for {}", payload.action(), player.getName().getString(), e);
            result = ActionResult.fail("The dashboard action failed safely.", "");
        }
        PacketDistributor.sendToPlayer(player, new SsuMenuActionResultPayload(
                payload.requestId(), result.success(), result.message(), result.refreshPage()));
        if (result.refreshShell()) open(player);
    }

    private ActionResult performAction(ServerPlayer player, SsuMenuActionPayload payload) {
        return switch (payload.action()) {
            case "refresh_shell" -> ActionResult.shell("Dashboard refreshed.");
            case "auction_open" -> auctionOpen(player, payload.requestId());
            case "pay" -> pay(player, payload.target(), payload.value());
            case "setting" -> setting(player, payload.target(), payload.value());
            case "border" -> border(player, payload.target(), payload.value());
            case "claim_map" -> claimMap(player, payload.target());
            case "claim_show" -> claimShow(player, payload.target());
            case "claim_hide" -> claimHide(player);
            case "claim_visibility" -> claimVisibility(player, payload.target(), payload.value());
            case "home_set" -> setHome(player, payload.target(), payload.secondary());
            case "home_delete" -> deleteHome(player, payload.target(), payload.secondary());
            case "warp_set" -> setWarp(player, payload.target(), payload.secondary());
            case "warp_delete" -> deleteWarp(player, payload.target(), payload.secondary());
            case "player_warp_set" -> setPlayerWarp(player, payload.target(), payload.requestId());
            case "player_warp_move" -> movePlayerWarp(player, payload.target());
            case "player_warp_delete" -> deletePlayerWarp(player, payload.target());
            case "player_warp_visibility" -> setPlayerWarpVisibility(player, payload.target(), payload.value());
            case "warp_rental_settings" -> warpRentalSettings(player, payload.target(), payload.value());
            case "teleport_cancel" -> cancelTeleport(player, payload.target());
            case "admin_claim_teleport" -> adminClaimTeleport(player, payload.target());
            case "admin_claim_delete" -> adminClaimDelete(player, payload.target());
            case "region_visibility" -> regionVisibility(player, payload.target(), payload.value());
            case "regions_hide" -> regionsHide(player);
            case "region_rent" -> regionRent(player, payload.target(), "rent");
            case "region_extend" -> regionRent(player, payload.target(), "extend");
            case "region_unrent" -> regionRent(player, payload.target(), "unrent");
            case "region_admin_teleport" -> regionAdminTeleport(player, payload.target());
            case "region_admin_snapshot" -> regionAdminSnapshot(player, payload.target());
            case "region_admin_reset" -> regionAdminReset(player, payload.target());
            case "region_admin_clear" -> regionAdminClear(player, payload.target());
            case "region_admin_redefine" -> regionAdminRedefine(player, payload.target());
            case "region_admin_delete" -> regionAdminDelete(player, payload.target());
            case "region_admin_add_time" -> regionAdminAddTime(player, payload.target(), payload.value());
            case "region_admin_pause" -> regionAdminPause(player, payload.target(), payload.value());
            case "region_renting_toggle" -> regionRentingToggle(player, payload.value());
            case "region_selection_point1" -> regionSelectionPoint(player, 1);
            case "region_selection_point2" -> regionSelectionPoint(player, 2);
            case "region_selection_coordinates" -> regionSelectionCoordinates(player, payload.target(), payload.value());
            case "region_selection_clear" -> regionSelectionClear(player);
            case "region_selection_unbind" -> regionSelectionUnbind(player);
            case "region_selection_fill" -> regionSelectionFill(player, payload.value());
            case "teleport_home" -> teleportHome(player, payload.target(), payload.secondary(), payload.value());
            case "teleport_warp" -> teleportWarp(player, payload.target(), payload.secondary());
            case "teleport_spawn" -> teleportSpawn(player, payload.secondary());
            case "spawn_set" -> setServerSpawn(player, payload.secondary());
            case "spawn_clear" -> clearServerSpawn(player, payload.secondary());
            case "auction_tax_set" -> auctionTaxSet(player, payload.value());
            case "claim_tax_settings" -> claimTaxSettings(player, payload.target(), payload.secondary(), payload.value());
            case "claim_tax_toggle" -> claimTaxToggle(player, payload.value());
            case "claim_tax_dimension" -> claimTaxDimension(player, payload.target(), payload.value());
            case "claim_tax_dimension_remove" -> claimTaxDimensionRemove(player, payload.target());
            case "economy_give" -> economyAdminMutation(player, payload.target(), payload.value(), "give");
            case "economy_take" -> economyAdminMutation(player, payload.target(), payload.value(), "take");
            case "economy_set" -> economyAdminMutation(player, payload.target(), payload.value(), "set");
            case "economy_history_limit" -> economyHistoryLimit(player, payload.value());
            case "permission_assign_rank" -> assignRank(player, payload.target(), payload.value());
            case "permission_check" -> permissionCheck(player, payload.target(), payload.secondary());
            case "rank_create" -> rankCreate(player, payload.target());
            case "rank_delete" -> rankDelete(player, payload.target());
            case "rank_rename" -> rankRename(player, payload.target(), payload.secondary());
            case "rank_default" -> rankDefault(player, payload.target());
            case "rank_reset_player" -> rankResetPlayer(player, payload.target());
            case "permission_player_set" -> setPlayerPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_player_unset" -> unsetPlayerPermission(player, payload.target(), payload.secondary());
            case "permission_rank_set" -> setRankPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_rank_unset" -> unsetRankPermission(player, payload.target(), payload.secondary());
            case "permission_player_dimension_set" -> setPlayerDimensionPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_player_dimension_unset" -> unsetPlayerDimensionPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_rank_dimension_set" -> setRankDimensionPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_rank_dimension_unset" -> unsetRankDimensionPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_region_set" -> setRegionPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_region_unset" -> unsetRegionPermission(player, payload.target(), payload.secondary());
            case "permission_set" -> setPlayerPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_unset" -> unsetPlayerPermission(player, payload.target(), payload.secondary());
            case "job_cancel" -> cancelJob(player, payload.target());
            case "core_reset" -> resetCoreCounters(player);
            case "admin_tool_get" -> adminToolGet(player, payload.target());
            case "crops_harvesting_toggle" -> cropsHarvestingToggle(player, payload.value());
            case "module_toggle" -> moduleToggle(player, payload.target(), payload.value());
            case "utility_mining_setting" -> utilityMiningSetting(player, payload.target(), payload.value());
            case "utility_mining_list" -> utilityMiningList(player, payload.target(), payload.secondary(), payload.value());
            case "maintenance_reload" -> maintenanceReload(player);
            case "maintenance_border_refresh" -> maintenanceBorderRefresh(player);
            case "maintenance_border_color" -> maintenanceBorderColor(player, payload.target(), payload.value());
            case "maintenance_border_reset" -> maintenanceBorderReset(player, payload.target());
            case "maintenance_border_reset_all" -> maintenanceBorderResetAll(player);
            case "maintenance_hologram_refresh" -> maintenanceHologramRefresh(player);
            case "maintenance_npc_refresh" -> maintenanceNpcRefresh(player);
            case "maintenance_buyback_minutes" -> maintenanceBuybackMinutes(player, payload.value());
            case "quest_access_mode" -> questAccessMode(player, payload.value());
            case "render_distance" -> renderDistance(player, payload.target(), payload.value());
            case "hologram_edit" -> hologramEdit(player, payload.target());
            case "hologram_delete" -> hologramDelete(player, payload.target());
            case "hologram_teleport" -> hologramTeleport(player, payload.target());
            case "hologram_move_here" -> hologramMoveHere(player, payload.target());
            case "statistic_edit" -> statisticEdit(player, payload.target());
            case "statistic_toggle" -> statisticToggle(player, payload.target(), payload.value());
            case "statistic_reset" -> statisticReset(player, payload.target());
            case "statistic_delete" -> statisticDelete(player, payload.target());
            default -> ActionResult.fail("Unknown dashboard action.", "");
        };
    }

    private ActionResult auctionOpen(ServerPlayer player, long requestId) {
        SimpleServerUtilities.AUCTION_HOUSE.openFromDashboard(player, requestId);
        return ActionResult.ok("Opening Auction House.", "");
    }

    private ActionResult maintenanceReload(ServerPlayer player) {
        if (!PermissionService.getBoolean(player, PermissionKeys.SSU_RELOAD, false))
            return ActionResult.fail("SSU reload permission is required.", "maintenance");
        SsuReloadService.ReloadResult result = SsuReloadService.reloadAll(player.level().getServer());
        return result.successful() ? ActionResult.shellPage(result.message(), "maintenance")
                : ActionResult.fail(result.message(), "maintenance");
    }

    private ActionResult maintenanceBorderRefresh(ServerPlayer player) {
        SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        return ActionResult.ok("Your border visualization was rebuilt from current server settings.", "maintenance");
    }

    private ActionResult maintenanceBorderColor(ServerPlayer player, String rawCategory, String rawHex) {
        if (!PermissionService.getBoolean(player, PermissionKeys.VISUALIZATION_ADMIN, false))
            return ActionResult.fail("Border color administration denied.", "maintenance");
        BorderCategory category = BorderCategory.parse(rawCategory);
        if (category == null) return ActionResult.fail("Unknown border category.", "maintenance");
        Integer rgb = parseRgb(rawHex);
        if (rgb == null) return ActionResult.fail("Use a six-digit RGB color such as #42F56C.", "maintenance");
        SimpleServerUtilities.BORDER_SETTINGS.setColor(category, rgb);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(player.level().getServer());
        return ActionResult.shellPage("Border color updated.", "maintenance");
    }

    private ActionResult maintenanceBorderReset(ServerPlayer player, String rawCategory) {
        if (!PermissionService.getBoolean(player, PermissionKeys.VISUALIZATION_ADMIN, false))
            return ActionResult.fail("Border color administration denied.", "maintenance");
        BorderCategory category = BorderCategory.parse(rawCategory);
        if (category == null) return ActionResult.fail("Unknown border category.", "maintenance");
        SimpleServerUtilities.BORDER_SETTINGS.resetColor(category);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(player.level().getServer());
        return ActionResult.shellPage("Border color reset to its default.", "maintenance");
    }

    private ActionResult maintenanceBorderResetAll(ServerPlayer player) {
        if (!PermissionService.getBoolean(player, PermissionKeys.VISUALIZATION_ADMIN, false))
            return ActionResult.fail("Border color administration denied.", "maintenance");
        SimpleServerUtilities.BORDER_SETTINGS.resetColors();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(player.level().getServer());
        return ActionResult.shellPage("All border colors reset to their defaults.", "maintenance");
    }

    private ActionResult maintenanceHologramRefresh(ServerPlayer player) {
        if (!PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false))
            return ActionResult.fail("Hologram administration denied.", "maintenance");
        SimpleServerUtilities.HOLOGRAMS.syncAll();
        return ActionResult.ok("Holograms refreshed for all players.", "maintenance");
    }

    private ActionResult maintenanceNpcRefresh(ServerPlayer player) {
        if (!PermissionService.getBoolean(player, PermissionKeys.NPCS_ADMIN, false))
            return ActionResult.fail("NPC administration denied.", "maintenance");
        SimpleServerUtilities.NPCS.refreshAll();
        return ActionResult.ok("NPC placements reconciled with their saved templates.", "maintenance");
    }

    private ActionResult maintenanceBuybackMinutes(ServerPlayer player, String rawMinutes) {
        if (!PermissionService.getBoolean(player, PermissionKeys.NPCS_ADMIN, false))
            return ActionResult.fail("NPC shop administration denied.", "maintenance");
        int minutes;
        try { minutes = boundedInt(rawMinutes, 1, 1440, "Buy-back retention"); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "maintenance"); }
        Config.NPC_SHOP_BUYBACK_MINUTES.set(minutes);
        return ActionResult.shellPage("NPC shop buy-back retention updated. Existing transactions keep their original expiry.", "maintenance");
    }

    private static Integer parseRgb(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) normalized = normalized.substring(2);
        if (!normalized.matches("[0-9A-Fa-f]{6}")) return null;
        return Integer.parseInt(normalized, 16);
    }

    private ActionResult utilityMiningSetting(ServerPlayer player, String key, String rawValue) {
        if (!PermissionService.getBoolean(player, PermissionKeys.UTILITY_MINING_ADMIN, false))
            return ActionResult.fail("Utility Mining administration denied.", "utility_mining_admin");
        try {
            switch (key == null ? "" : key.trim().toLowerCase(Locale.ROOT)) {
                case "leaf_range" -> Config.TREECAPITATOR_LEAF_SEARCH_RANGE.set(boundedInt(rawValue, 0, 16, "Leaf range"));
                case "tree_default_max" -> Config.TREECAPITATOR_DEFAULT_MAX_BLOCKS.set(boundedInt(rawValue, 1, 2048, "Tree maximum"));
                case "vein_default_max" -> Config.VEINMINER_DEFAULT_MAX_BLOCKS.set(boundedInt(rawValue, 1, 2048, "Vein maximum"));
                case "break_leaves" -> Config.TREECAPITATOR_BREAK_NATURAL_LEAVES.set(strictBoolean(rawValue));
                default -> { return ActionResult.fail("Unknown Utility Mining setting.", "utility_mining_admin"); }
            }
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "utility_mining_admin");
        }
        SimpleServerUtilities.UTILITY_MINING.clearClients(player.level().getServer());
        SimpleServerUtilities.UTILITY_MINING.clear();
        return ActionResult.shellPage("Utility Mining setting updated.", "utility_mining_admin");
    }

    private ActionResult utilityMiningList(ServerPlayer player, String listKey, String operation, String rawBlock) {
        if (!PermissionService.getBoolean(player, PermissionKeys.UTILITY_MINING_ADMIN, false))
            return ActionResult.fail("Utility Mining administration denied.", "utility_mining_admin");
        ModConfigSpec.ConfigValue<String> setting = switch (listKey == null ? "" : listKey.trim().toLowerCase(Locale.ROOT)) {
            case "custom_logs" -> Config.TREECAPITATOR_CUSTOM_LOG_BLOCKS;
            case "disabled_logs" -> Config.TREECAPITATOR_DISABLED_LOG_BLOCKS;
            case "custom_ores" -> Config.VEINMINER_CUSTOM_ORE_BLOCKS;
            case "disabled_ores" -> Config.VEINMINER_DISABLED_ORE_BLOCKS;
            default -> null;
        };
        if (setting == null) return ActionResult.fail("Unknown Utility Mining block list.", "utility_mining_admin");
        String op = operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT);
        if ("clear".equals(op)) {
            setting.set("");
        } else {
            final String block;
            try { block = Identifier.parse(rawBlock).toString().toLowerCase(Locale.ROOT); }
            catch (Exception exception) { return ActionResult.fail("Enter a valid block id such as minecraft:oak_log.", "utility_mining_admin"); }
            LinkedHashSet<String> values = parseConfigList(setting.get());
            if ("add".equals(op)) values.add(block);
            else if ("remove".equals(op)) values.remove(block);
            else return ActionResult.fail("Unknown list operation.", "utility_mining_admin");
            setting.set(String.join(",", values));
        }
        SimpleServerUtilities.UTILITY_MINING.clearClients(player.level().getServer());
        SimpleServerUtilities.UTILITY_MINING.clear();
        return ActionResult.shellPage("Utility Mining block list updated.", "utility_mining_admin");
    }

    private static int boundedInt(String raw, int minimum, int maximum, String label) {
        final int value;
        try { value = Integer.parseInt(raw == null ? "" : raw.trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(label + " must be a whole number."); }
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum + ".");
        return value;
    }

    private static LinkedHashSet<String> parseConfigList(String raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return values;
        for (String value : raw.split(",")) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) values.add(normalized);
        }
        return values;
    }

    private static String blankList(String raw) { return raw == null || raw.isBlank() ? "(none)" : raw; }

    private ActionResult moduleToggle(ServerPlayer player, String rawModule, String rawValue) {
        if (!isAdministrator(player)) return ActionResult.fail("Administrator access is required.", "");
        final boolean enabled;
        try { enabled = strictBoolean(rawValue); }
        catch (IllegalArgumentException exception) { return ActionResult.fail("Invalid module state.", ""); }

        String module = rawModule == null ? "" : rawModule.trim().toLowerCase(Locale.ROOT);
        boolean utilityWasActive = SimpleServerUtilities.CORE.modules().isActive("utility_mining");
        boolean treecapitatorWasEnabled = Config.ENABLE_TREECAPITATOR.get();
        if ("permissions".equals(module) && !enabled && !PermissionService.isAdmin(player)) {
            return ActionResult.fail("Only a server operator can disable the permission system.", "");
        }
        switch (module) {
            case "claims" -> setAndSave(Config.ENABLE_PLAYER_CLAIMS, enabled);
            case "homes" -> setAndSave(Config.ENABLE_HOMES, enabled);
            case "warps" -> setAndSave(Config.ENABLE_WARPS, enabled);
            case "regions" -> setAndSave(Config.ENABLE_ADMIN_REGIONS, enabled);
            case "treecapitator" -> setAndSave(Config.ENABLE_TREECAPITATOR, enabled);
            case "veinminer" -> setAndSave(Config.ENABLE_VEINMINER, enabled);
            case "crop_harvesting" -> setAndSave(Config.ENABLE_CROPS_HARVESTING, enabled);
            case "holograms" -> setAndSave(Config.ENABLE_HOLOGRAMS, enabled);
            case "block_information" -> setAndSave(Config.ENABLE_BLOCK_INFORMATION, enabled);
            case "statistics" -> setAndSave(Config.ENABLE_CUSTOM_STATISTICS, enabled);
            case "mail" -> setAndSave(Config.ENABLE_MAIL, enabled);
            case "auction_house" -> setAndSave(Config.ENABLE_AUCTION_HOUSE, enabled);
            case "npcs" -> setAndSave(Config.ENABLE_NPCS, enabled);
            case "quests" -> setAndSave(Config.ENABLE_QUESTS, enabled);
            case "minigames" -> setAndSave(Config.ENABLE_MINIGAMES, enabled);
            case "dungeons" -> setAndSave(Config.ENABLE_DUNGEONS, enabled);
            case "permissions" -> setAndSave(Config.ENABLE_PERMISSION_SYSTEM, enabled);
            case "remote_hologram_images" -> setAndSave(Config.ALLOW_REMOTE_HOLOGRAM_IMAGES, enabled);
            default -> { return ActionResult.fail("Unknown module switch.", ""); }
        }

        MinecraftServer server = player.level().getServer();
        SimpleServerUtilities.CORE.modules().refreshEnabledState(server);
        if ("treecapitator".equals(module)) {
            boolean utilityStillActive = SimpleServerUtilities.CORE.modules().isActive("utility_mining");
            if (enabled && !treecapitatorWasEnabled && utilityWasActive && utilityStillActive) {
                SimpleServerUtilities.TREE_PLACEMENTS.load(server);
            } else if (!enabled && treecapitatorWasEnabled && utilityWasActive && utilityStillActive) {
                SimpleServerUtilities.TREE_PLACEMENTS.save();
                SimpleServerUtilities.STORAGE.flush(java.time.Duration.ofSeconds(5));
                SimpleServerUtilities.TREE_PLACEMENTS.clear();
            }
        }
        if ("treecapitator".equals(module) || "veinminer".equals(module)) {
            SimpleServerUtilities.UTILITY_MINING.clearClients(server);
            SimpleServerUtilities.UTILITY_MINING.clear();
        }
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(server);
        if ("block_information".equals(module)) BlockInformationService.syncAll(server);
        SimpleServerUtilities.HOLOGRAMS.syncAll();
        return ActionResult.shell(moduleLabel(module) + " is now " + (enabled ? "enabled" : "disabled") + ".");
    }

    private ActionResult renderDistance(ServerPlayer player, String rawTarget, String rawValue) {
        if (!isAdministrator(player)) return ActionResult.fail("Administrator access is required.", "");
        final int requested;
        try { requested = Integer.parseInt(rawValue == null ? "" : rawValue.trim()); }
        catch (NumberFormatException exception) { return ActionResult.fail("Invalid render distance.", ""); }

        String target = rawTarget == null ? "" : rawTarget.trim().toLowerCase(Locale.ROOT);
        int applied;
        switch (target) {
            case "holograms" -> {
                applied = Math.max(8, Math.min(512, requested));
                setAndSave(Config.HOLOGRAM_RENDER_DISTANCE, applied);
                SimpleServerUtilities.HOLOGRAMS.syncAll();
            }
            case "claim_borders" -> {
                applied = Math.max(16, Math.min(512, requested));
                SimpleServerUtilities.BORDER_SETTINGS.setClaimRenderDistanceBlocks(applied);
                SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(player.level().getServer());
            }
            case "region_borders" -> {
                applied = Math.max(16, Math.min(512, requested));
                SimpleServerUtilities.BORDER_SETTINGS.setRegionRenderDistanceBlocks(applied);
                SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(player.level().getServer());
            }
            default -> { return ActionResult.fail("Unknown render-distance setting.", ""); }
        }
        return ActionResult.shell("Render distance set to " + applied + " blocks.");
    }

    private ActionResult questAccessMode(ServerPlayer player, String rawValue) {
        if (!isAdministrator(player)) return ActionResult.fail("Administrator access is required.", "");
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (!"menu".equals(normalized) && !"npc".equals(normalized)) {
            return ActionResult.fail("Quest access mode must be menu or npc.", "");
        }
        QuestAccessMode requested = QuestAccessMode.parse(normalized);
        if (requested == QuestAccessMode.NPC && !Config.ENABLE_NPCS.get()) {
            return ActionResult.fail("NPC quest access cannot be selected while the NPC module is disabled.", "");
        }
        setAndSave(Config.QUEST_ACCESS_MODE, requested.serializedName());
        return ActionResult.shell("Quest access now uses "
                + (requested == QuestAccessMode.NPC ? "NPCs" : "the SSU menu") + " exclusively.");
    }

    private static void setAndSave(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Boolean> value,
            boolean enabled) {
        value.set(enabled);
        value.save();
    }

    private static void setAndSave(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Integer> value,
            int setting) {
        value.set(setting);
        value.save();
    }

    private static void setAndSave(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> value,
            String setting) {
        value.set(setting);
        value.save();
    }

    private static String moduleLabel(String key) {
        return switch (key) {
            case "claims" -> "Player Claims";
            case "homes" -> "Homes";
            case "warps" -> "Warps";
            case "regions" -> "Server Regions";
            case "treecapitator" -> "Treecapitator";
            case "veinminer" -> "Veinminer";
            case "crop_harvesting" -> "Crop Harvesting";
            case "holograms" -> "Floating Text & Holograms";
            case "block_information" -> "Block Information";
            case "statistics" -> "Player Statistics";
            case "mail" -> "Mail";
            case "auction_house" -> "Auction House";
            case "npcs" -> "NPCs";
            case "quests" -> "Quests";
            case "minigames" -> "Minigames";
            case "dungeons" -> "Customized Dungeons";
            case "permissions" -> "Permissions";
            case "remote_hologram_images" -> "Remote Hologram Images";
            default -> key;
        };
    }

    private ActionResult cropsHarvestingToggle(ServerPlayer player, String rawValue) {
        if (!isAdministrator(player)) {
            return ActionResult.fail("Administrator access is required.", "");
        }
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            return ActionResult.fail("Invalid Crops Harvesting state.", "");
        }
        boolean enabled = Boolean.parseBoolean(normalized);
        setAndSave(Config.ENABLE_CROPS_HARVESTING, enabled);
        return ActionResult.shell("Crops Harvesting is now " + (enabled ? "enabled" : "disabled") + ".");
    }

    private ActionResult hologramEdit(ServerPlayer player, String id) {
        if (!canHologramAdmin(player)) return ActionResult.fail("Hologram administration denied.", "holograms");
        return HologramEditorService.openEditor(player, id)
                ? ActionResult.ok("Hologram editor opened.", "")
                : ActionResult.fail("Hologram not found.", "holograms");
    }

    private ActionResult hologramDelete(ServerPlayer player, String id) {
        if (!canHologramAdmin(player)) return ActionResult.fail("Hologram administration denied.", "holograms");
        HologramDefinition value = SimpleServerUtilities.HOLOGRAMS.get(id);
        if (value == null) return ActionResult.fail("Hologram not found.", "holograms");
        return SimpleServerUtilities.HOLOGRAMS.delete(value.id)
                ? ActionResult.ok("Hologram '" + value.id + "' deleted.", "holograms")
                : ActionResult.fail("The hologram could not be deleted.", "holograms");
    }

    private ActionResult hologramTeleport(ServerPlayer player, String id) {
        if (!canHologramAdmin(player)) return ActionResult.fail("Hologram administration denied.", "holograms");
        HologramDefinition value = SimpleServerUtilities.HOLOGRAMS.get(id);
        if (value == null) return ActionResult.fail("Hologram not found.", "holograms");
        ServerLevel level = level(player.level().getServer(), value.dimension);
        if (level == null) return ActionResult.fail("The hologram dimension is not loaded.", "holograms");

        var safe = TeleportSafety.findSafeDestination(level, value.x, value.y - 1.0D, value.z, 8);
        if (safe.isEmpty()) {
            return ActionResult.fail("No safe standing position was found near that hologram.", "holograms");
        }
        var destination = safe.get();
        player.teleportTo(level, destination.x(), destination.y(), destination.z(),
                Set.of(), player.getYRot(), player.getXRot(), true);
        return ActionResult.ok("Teleported to hologram '" + value.id + "'.", "holograms");
    }

    private ActionResult adminToolGet(ServerPlayer player, String rawTool) {
        if (!isAdministrator(player)) return ActionResult.fail("Administrator access is required.", "");
        String tool = rawTool == null ? "" : rawTool.trim().toLowerCase(Locale.ROOT);
        return switch (tool) {
            case "region" -> {
                if (!RegionPolicy.canUseSelectionTool(player)) {
                    yield ActionResult.fail("You do not have permission to use the region selection tool.", "");
                }
                AdminToolService.giveRegionTool(player);
                yield ActionResult.ok("Region Tool added. Left-click point 1 and point 2, then right-click to open selection actions.", "");
            }
            case "hologram" -> {
                if (!Config.ENABLE_HOLOGRAMS.get()
                        || !PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false)) {
                    yield ActionResult.fail("The hologram module is disabled or you lack its admin permission.", "");
                }
                AdminToolService.giveHologramTool(player);
                yield ActionResult.ok("Hologram Tool added. Right-click to create; right-click existing text to edit.", "");
            }
            case "npc" -> {
                if (!Config.ENABLE_NPCS.get()
                        || !PermissionService.getBoolean(player, PermissionKeys.NPCS_ADMIN, false)) {
                    yield ActionResult.fail("The NPC module is disabled or you lack its admin permission.", "");
                }
                SimpleServerUtilities.NPC_TOOLS.giveTool(player);
                yield ActionResult.ok("NPC Tool added. Right-click to create/edit; sneak-right-click to copy and paste.", "");
            }
            case "shops" -> {
                if (!Config.ENABLE_NPCS.get()
                        || !PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_ADMIN, false)) {
                    yield ActionResult.fail("The NPC shop module is disabled or you lack its admin permission.", "");
                }
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopEditorService.openManager(player);
                yield ActionResult.ok("Opening Shop Manager.", "");
            }
            case "item_prices" -> {
                if (!Config.ENABLE_NPCS.get()
                        || !PermissionService.getBoolean(player, PermissionKeys.NPC_SHOPS_ADMIN, false)) {
                    yield ActionResult.fail("The NPC shop module is disabled or you lack its admin permission.", "");
                }
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcItemPriceCatalogService.open(player);
                yield ActionResult.ok("Opening Item Price Catalog.", "");
            }
            case "quest" -> {
                if (!Config.ENABLE_QUESTS.get()
                        || !SimpleServerUtilities.CORE.modules().isActive("quests")
                        || !PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false)) {
                    yield ActionResult.fail("The quest module is disabled or you lack its admin permission.", "");
                }
                be.winnetrie.mod.simpleserverutilities.quest.QuestEditorService.open(player, "");
                yield ActionResult.ok("Opening Quest Editor.", "");
            }
            case "minigame" -> {
                if (!Config.ENABLE_MINIGAMES.get()
                        || !SimpleServerUtilities.CORE.modules().isActive("minigames")
                        || !PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false)) {
                    yield ActionResult.fail("The minigame module is disabled or you lack its admin permission.", "");
                }
                SimpleServerUtilities.MINIGAME_SETUP_TOOLS.giveTool(player);
                be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupToolService.open(player,
                        "Minigame Setup Tool added. Right-click it to choose a game, arena and action.", false, 0L);
                yield ActionResult.ok("Minigame Setup Tool added.", "");
            }
            case "dungeon" -> {
                if (!Config.ENABLE_DUNGEONS.get()
                        || !SimpleServerUtilities.CORE.modules().isActive("dungeons")
                        || !PermissionService.getBoolean(player, PermissionKeys.DUNGEONS_ADMIN, false)) {
                    yield ActionResult.fail("The dungeon module is disabled or you lack its admin permission.", "");
                }
                be.winnetrie.mod.simpleserverutilities.dungeon.DungeonEditorService.open(player, "");
                yield ActionResult.ok("Opening Dungeon Editor.", "");
            }
            default -> ActionResult.fail("Unknown admin tool.", "");
        };
    }

    private SsuMenuPageDataPayload claimsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!PermissionService.getBoolean(player, PermissionKeys.CLAIMS_USE, true)) return denied(request);
        List<PlayerClaim> all = filter(ownedClaims(player), request.query(), PlayerClaim::getDisplayName);
        List<SsuMenuPageDataPayload.ClaimEntry> entries = page(all, request).stream().map(claim ->
                new SsuMenuPageDataPayload.ClaimEntry(
                        claim.getId().toString(), claim.getDisplayName(), claim.getDimension(), claim.getChunkCount(),
                        claim.getTrustedPlayers().size(), false,
                        SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID()).isClaimVisible(claim.getId()), "",
                        displayNames(player.level().getServer(), claim.getTrustedPlayers()), claimFlags(claim)
                )).toList();
        return data(request, all.size(), entries, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload homesPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!HomePolicy.canUseHomes(player)) return denied(request);
        String claimName = request.query().trim();
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        if (claim == null) {
            return SsuMenuPageDataPayload.empty("homes", request.pageIndex(), request.pageSize(),
                    request.requestId(), "Open Homes from the settings of one of your claims.", true);
        }
        List<SsuMenuPageDataPayload.LocationEntry> all = ClaimHomeSupport.homesInClaim(player.getUUID(), claim).stream()
                .map(home -> location("home", home))
                .sorted(Comparator.comparing(SsuMenuPageDataPayload.LocationEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        List<SsuMenuPageDataPayload.PermissionEntry> capabilities = List.of(
                new SsuMenuPageDataPayload.PermissionEntry("homes", "capability", "set_here",
                        Boolean.toString(HomePolicy.canSetHomeInClaim(player, player.blockPosition(), claim)),
                        "Requires the homes.set permission and standing inside this claim."),
                new SsuMenuPageDataPayload.PermissionEntry("homes", "capability", "teleport",
                        Boolean.toString(HomePolicy.canTeleportHome(player, context)),
                        "Effective home teleport permission at the player's current location."),
                new SsuMenuPageDataPayload.PermissionEntry("homes", "capability", "delete",
                        Boolean.toString(HomePolicy.canDeleteHome(player, context)),
                        "Effective home deletion permission at the player's current location.")
        );
        return data(request, all.size(), List.of(), page(all, request), List.of(), List.of(),
                List.of(), List.of(), List.of(), capabilities);
    }

    private SsuMenuPageDataPayload travelPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        TravelQuery travelQuery = parseTravelQuery(request.query(), Set.of("all", "home", "warp", "other"));
        List<SsuMenuPageDataPayload.LocationEntry> all = new ArrayList<>();

        PermissionContext context = PermissionContext.global(player);
        ServerSpawn serverSpawn = SimpleServerUtilities.SERVER_SPAWN.get();
        if (serverSpawn != null && SpawnPolicy.canUse(player, context)) {
            all.add(location(serverSpawn));
        }

        if (WarpPolicy.canTeleportWarp(player, context)) {
            SimpleServerUtilities.WARPS.getAccessibleWarps(player).forEach(warp -> all.add(location("warp", warp)));
        }

        if (HomePolicy.canTeleportHome(player, context)) {
            List<PlayerClaim> claims = ownedClaims(player);
            SimpleServerUtilities.HOMES.getHomes(player.getUUID()).stream()
                    .filter(home -> claims.stream().anyMatch(claim -> ClaimHomeSupport.contains(claim, home)))
                    .forEach(home -> all.add(location("home", home)));
        }

        List<SsuMenuPageDataPayload.LocationEntry> visible = all.stream()
                .filter(value -> travelFilterMatches(travelQuery.filter(), value.kind()))
                .sorted(Comparator.comparing(SsuMenuPageDataPayload.LocationEntry::kind)
                        .thenComparing(SsuMenuPageDataPayload.LocationEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SsuMenuPageDataPayload.LocationEntry> filtered = filter(visible, travelQuery.search(),
                value -> value.kind() + " " + value.name() + " " + value.dimension());
        return data(request, filtered.size(), List.of(), page(filtered, request), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload adminTravelPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!isAdministrator(player)) {
            return denied(request);
        }
        TravelQuery travelQuery = parseTravelQuery(request.query(), Set.of("all", "warp", "spawn"));
        List<SsuMenuPageDataPayload.LocationEntry> all = new ArrayList<>();
        ServerSpawn serverSpawn = SimpleServerUtilities.SERVER_SPAWN.get();
        if (serverSpawn != null) {
            all.add(location(serverSpawn));
        }
        SimpleServerUtilities.WARPS.getWarps().forEach(warp -> all.add(location("warp", warp)));
        List<SsuMenuPageDataPayload.LocationEntry> visible = all.stream()
                .filter(value -> travelFilterMatches(travelQuery.filter(), value.kind()))
                .sorted(Comparator.comparing(SsuMenuPageDataPayload.LocationEntry::kind)
                        .thenComparing(SsuMenuPageDataPayload.LocationEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<SsuMenuPageDataPayload.LocationEntry> filtered = filter(visible, travelQuery.search(),
                value -> value.kind() + " " + value.name() + " " + value.dimension());
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        List<SsuMenuPageDataPayload.PermissionEntry> capabilities = List.of(
                new SsuMenuPageDataPayload.PermissionEntry("travel_admin", "capability", "warp_set",
                        Boolean.toString(WarpPolicy.canSetWarp(player, context)),
                        "Create or move a server warp to the administrator's current position."),
                new SsuMenuPageDataPayload.PermissionEntry("travel_admin", "capability", "warp_delete",
                        Boolean.toString(WarpPolicy.canDeleteWarp(player, context)),
                        "Delete server warps."),
                new SsuMenuPageDataPayload.PermissionEntry("travel_admin", "capability", "spawn_admin",
                        Boolean.toString(SpawnPolicy.canAdmin(player)),
                        "Set or clear the server spawn."),
                new SsuMenuPageDataPayload.PermissionEntry("travel_admin", "capability", "warp_teleport",
                        Boolean.toString(WarpPolicy.canTeleportWarp(player, context)),
                        "Test a server warp through the normal teleport policy."),
                new SsuMenuPageDataPayload.PermissionEntry("travel_admin", "capability", "spawn_teleport",
                        Boolean.toString(SpawnPolicy.canUse(player, context)),
                        "Test the server spawn through the normal teleport policy.")
        );
        return data(request, filtered.size(), List.of(), page(filtered, request), List.of(), List.of(),
                List.of(), List.of(), List.of(), capabilities);
    }

    private SsuMenuPageDataPayload playerWarpsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        PermissionContext currentContext = PermissionContext.at(player, player.blockPosition());
        boolean canRent = WarpPolicy.canRentWarp(player, currentContext);
        int maximum = WarpPolicy.getMaxRentedWarps(player, currentContext);
        List<Warp> all = new ArrayList<>(SimpleServerUtilities.WARPS.getPlayerWarps(player.getUUID()));
        all = filter(all, request.query(), warp -> warp.getDisplayName() + " " + warp.getDimension());
        List<Warp> visiblePage = page(all, request);
        List<SsuMenuPageDataPayload.LocationEntry> locations = visiblePage.stream()
                .map(warp -> location(warp.isPublicWarp() ? "public" : "private", warp)).toList();
        List<SsuMenuPageDataPayload.PermissionEntry> metadata = new ArrayList<>();
        metadata.add(new SsuMenuPageDataPayload.PermissionEntry("player_warps", "capability", "can_rent",
                Boolean.toString(canRent), "Permission ssu.warps.rent controls rental access."));
        metadata.add(new SsuMenuPageDataPayload.PermissionEntry("player_warps", "capability", "can_use",
                Boolean.toString(WarpPolicy.canTeleportWarp(player, currentContext)),
                "Effective warp-use and teleport permission at the player's current location."));
        metadata.add(new SsuMenuPageDataPayload.PermissionEntry("player_warps", "capability", "economy_enabled",
                Boolean.toString(SimpleServerUtilities.ECONOMY.settings().isEnabled()),
                "Prepaid rentals require the Economy module."));
        metadata.add(new SsuMenuPageDataPayload.PermissionEntry("player_warps", "capability", "maximum",
                Integer.toString(maximum), "Maximum rented warps from ssu.warps.rent.max."));
        metadata.add(new SsuMenuPageDataPayload.PermissionEntry("player_warps", "status", "count",
                Integer.toString(SimpleServerUtilities.WARPS.countPlayerWarps(player.getUUID())), "Current rented warp count."));
        metadata.add(new SsuMenuPageDataPayload.PermissionEntry("player_warps", "setting", "price",
                SimpleServerUtilities.ECONOMY.format(SimpleServerUtilities.WARPS.rentalSettings().getPriceMinor()),
                "Charged in advance for each rental period."));
        metadata.add(new SsuMenuPageDataPayload.PermissionEntry("player_warps", "setting", "period",
                durationText(SimpleServerUtilities.WARPS.rentalSettings().getPeriodMillis()),
                "Length of one prepaid rental period."));
        for (Warp warp : visiblePage) {
            metadata.add(new SsuMenuPageDataPayload.PermissionEntry(warp.getDisplayName(), "warp", "paid_until",
                    Long.toString(warp.getPaidUntil()), warp.isPublicWarp() ? "public" : "private"));
        }
        return data(request, all.size(), List.of(), locations, List.of(), List.of(),
                List.of(), List.of(), List.of(), metadata);
    }

    private SsuMenuPageDataPayload adminClaimsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!ClaimPolicy.hasAdminBypass(player)) return denied(request);
        MinecraftServer server = player.level().getServer();
        List<PlayerClaim> all = new ArrayList<>(SimpleServerUtilities.PLAYER_CLAIMS.getClaims());
        all.sort(Comparator.comparing((PlayerClaim claim) -> displayName(server, claim.getOwner()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlayerClaim::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        String query = request.query().toLowerCase(Locale.ROOT);
        if (!query.isBlank()) {
            all = all.stream().filter(claim -> (displayName(server, claim.getOwner()) + " " + claim.getDisplayName()
                    + " " + claim.getDimension()).toLowerCase(Locale.ROOT).contains(query)).toList();
        }
        List<SsuMenuPageDataPayload.ClaimEntry> entries = page(all, request).stream().map(claim ->
                new SsuMenuPageDataPayload.ClaimEntry(
                        claim.getId().toString(), displayName(server, claim.getOwner()) + " / " + claim.getDisplayName(),
                        claim.getDimension(), claim.getChunkCount(), claim.getTrustedPlayers().size(), false, false, "",
                        displayNames(server, claim.getTrustedPlayers()), claimFlags(claim)
                )).toList();
        return data(request, all.size(), entries, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload regionsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        boolean administrator = isAdministrator(player);
        List<Region> all = filter(visibleRegions(player, administrator).stream()
                .filter(region -> !SimpleServerUtilities.MINIGAMES.isManagedArenaRegion(region.getName())).toList(),
                request.query(), Region::getName);
        List<Region> visiblePage = page(all, request);
        List<SsuMenuPageDataPayload.RegionEntry> entries = visiblePage.stream()
                .map(region -> regionEntry(player, region, administrator)).toList();
        return data(request, all.size(), List.of(), List.of(), entries, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload regionAdminPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!RegionPolicy.canEditRegion(player) && !RegionPolicy.canDeleteRegion(player)) return denied(request);
        List<Region> all = SimpleServerUtilities.REGIONS.getAll().stream()
                .filter(region -> !SimpleServerUtilities.MINIGAMES.isManagedArenaRegion(region.getName()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        all.sort(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER));
        all = filter(all, request.query(), region -> region.getName() + " " + region.getDimension().identifier());
        List<SsuMenuPageDataPayload.RegionEntry> entries = page(all, request).stream()
                .map(region -> regionEntry(player, region, true)).toList();
        List<SsuMenuPageDataPayload.PermissionEntry> settings = List.of(new SsuMenuPageDataPayload.PermissionEntry(
                "regions", "setting", "renting", Boolean.toString(SimpleServerUtilities.REGIONS.isRentingEnabled()),
                "Global region-renting switch"));
        return data(request, all.size(), List.of(), List.of(), entries, List.of(), List.of(), List.of(), List.of(), settings);
    }

    private SsuMenuPageDataPayload walletTransactionsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!PermissionService.getBoolean(player, PermissionKeys.ECONOMY_HISTORY, true)) return denied(request);
        List<EconomyTransactionRecord> all = SimpleServerUtilities.ECONOMY.history(player.getUUID(), 500);
        String q = request.query().trim().toLowerCase(Locale.ROOT);
        if (!q.isBlank()) all = all.stream().filter(record -> transactionSearch(record).contains(q)).toList();
        List<SsuMenuPageDataPayload.TransactionEntry> entries = page(all, request).stream().map(this::transactionEntry).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), entries, List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload transactionsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canEconomyAdmin(player)) return denied(request);
        TransactionQuery query = parseTransactionQuery(request.query());
        UUID selectedPlayer = null;
        String selectedReference = !query.manualPlayer().isBlank() ? query.manualPlayer() : query.selectedPlayer();
        if (!selectedReference.isBlank()) {
            selectedPlayer = resolvePlayerId(player.level().getServer(), selectedReference);
            if (selectedPlayer == null) {
                selectedPlayer = SimpleServerUtilities.ECONOMY
                        .findPlayerAccountByName(player.level().getServer(), selectedReference)
                        .map(EconomyAccount::getPlayerId).orElse(null);
            }
            if (selectedPlayer == null) {
                List<SsuMenuPageDataPayload.AccountEntry> accounts = transactionPlayerAccounts();
                return new SsuMenuPageDataPayload(request.page(), request.pageIndex(), request.pageSize(), 0,
                        request.requestId(), "Player not found. Choose a player or enter an exact name / UUID.", true,
                        List.of(), List.of(), List.of(), List.of(), accounts, List.of(), List.of(), List.of(), List.of());
            }
        }
        List<EconomyTransactionRecord> all = SimpleServerUtilities.ECONOMY.history(selectedPlayer, 500);
        String q = query.search().toLowerCase(Locale.ROOT);
        if (!q.isBlank()) all = all.stream().filter(record -> transactionSearch(record).contains(q)).toList();
        List<SsuMenuPageDataPayload.TransactionEntry> entries = page(all, request).stream().map(this::transactionEntry).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), entries,
                transactionPlayerAccounts(), List.of(), List.of(), List.of());
    }

    private List<SsuMenuPageDataPayload.AccountEntry> transactionPlayerAccounts() {
        return SimpleServerUtilities.ECONOMY.accounts().stream()
                .filter(account -> !account.isSystemAccount())
                .sorted(Comparator.comparing(EconomyAccount::getLastKnownName, String.CASE_INSENSITIVE_ORDER))
                .limit(SsuMenuPageRequestPayload.MAX_PAGE_SIZE)
                .map(account -> new SsuMenuPageDataPayload.AccountEntry(account.getPlayerId().toString(),
                        account.getLastKnownName(), MoneyFormat.format(account.getBalanceMinor(),
                        SimpleServerUtilities.ECONOMY.settings()), account.getBalanceMinor(),
                        account.getRevision(), account.getUpdatedAtEpochMilli()))
                .toList();
    }

    private SsuMenuPageDataPayload auctionTaxPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canEconomyAdmin(player)) return denied(request);
        String percent = formatPercentPermille(SimpleServerUtilities.AUCTION_HOUSE.saleTaxPermille());
        List<SsuMenuPageDataPayload.PermissionEntry> entries = List.of(
                new SsuMenuPageDataPayload.PermissionEntry("economics", "setting", "auction_house_tax",
                        percent, "Percentage withheld from completed Auction House sales.")
        );
        return data(request, entries.size(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), entries);
    }

    private SsuMenuPageDataPayload claimTaxPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canEconomyAdmin(player)) return denied(request);
        var settings = SimpleServerUtilities.CLAIM_TAX.settings();
        List<SsuMenuPageDataPayload.PermissionEntry> entries = new ArrayList<>();
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("claim_tax", "setting", "enabled",
                Boolean.toString(settings.isEnabled()), "Enable recurring automatic claim taxation."));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("claim_tax", "setting", "rate",
                majorPlain(settings.getRateMinorPerChunk()), "Base amount charged per claimed chunk."));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("claim_tax", "setting", "interval_hours",
                decimalPlain(settings.getIntervalMillis() / 3_600_000D), "Hours between automatic tax cycles."));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("claim_tax", "setting", "reminder_hours",
                decimalPlain(settings.getReminderLeadMillis() / 3_600_000D), "Hours before charging that reminder mail is sent."));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("claim_tax", "status", "next_charge",
                Long.toString(SimpleServerUtilities.CLAIM_TAX.nextDueAt()), "Earliest scheduled per-claim tax payment."));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("claim_tax", "status", "safety_halt",
                Boolean.toString(SimpleServerUtilities.CLAIM_TAX.isSafetyHalted()),
                "When true, all claim mutation and destructive tax processing is fail-closed."));
        List<SsuMenuPageDataPayload.PermissionEntry> dimensions = SimpleServerUtilities.CLAIM_TAX.dimensionMultipliers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SsuMenuPageDataPayload.PermissionEntry(
                        "claim_tax", "dimension", entry.getKey(), decimalPlain(entry.getValue()),
                        "Multiplier applied to claimed chunks in this dimension."))
                .toList();
        entries.addAll(page(dimensions, request));
        return data(request, dimensions.size(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), entries);
    }

    private SsuMenuPageDataPayload warpRentalPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canEconomyAdmin(player)) return denied(request);
        var settings = SimpleServerUtilities.WARPS.rentalSettings();
        List<SsuMenuPageDataPayload.PermissionEntry> entries = List.of(
                new SsuMenuPageDataPayload.PermissionEntry("warp_rental", "setting", "price",
                        majorPlain(settings.getPriceMinor()), "Amount charged in advance for each rental period."),
                new SsuMenuPageDataPayload.PermissionEntry("warp_rental", "setting", "days",
                        decimalPlain(settings.getPeriodMillis() / 86_400_000D), "Length of one prepaid rental period in days."),
                new SsuMenuPageDataPayload.PermissionEntry("warp_rental", "status", "active",
                        Long.toString(SimpleServerUtilities.WARPS.getWarps().stream().filter(Warp::isPlayerRental).count()),
                        "Current number of player-rented warps.")
        );
        return data(request, entries.size(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), entries);
    }

    private SsuMenuPageDataPayload accountsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canEconomyAdmin(player)) return denied(request);
        List<EconomyAccount> all = filter(SimpleServerUtilities.ECONOMY.accounts(), request.query(),
                account -> account.getLastKnownName() + " " + account.getPlayerId());
        List<SsuMenuPageDataPayload.AccountEntry> entries = page(all, request).stream().map(account ->
                new SsuMenuPageDataPayload.AccountEntry(account.getPlayerId().toString(), account.getLastKnownName(),
                        MoneyFormat.format(account.getBalanceMinor(), SimpleServerUtilities.ECONOMY.settings()),
                        account.getBalanceMinor(), account.getRevision(), account.getUpdatedAtEpochMilli())).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), List.of(), entries, List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload jobsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!isAdministrator(player) || !PermissionService.getBoolean(player, PermissionKeys.CORE_ADMIN, false)) return denied(request);
        List<be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.JobSnapshot> all =
                new ArrayList<>(SimpleServerUtilities.JOBS.snapshots());
        all = filter(all, request.query(), value -> value.description() + " " + value.id());
        List<SsuMenuPageDataPayload.JobEntry> entries = page(all, request).stream().map(job ->
                new SsuMenuPageDataPayload.JobEntry(job.id().toString(), job.description(), job.operations(), job.progress())).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), List.of(), List.of(), entries, List.of(), List.of());
    }

    private SsuMenuPageDataPayload rentOperationsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canEconomyAdmin(player)) return denied(request);
        List<RegionRentOperationRecord> all = filter(SimpleServerUtilities.REGION_RENT_JOURNAL.records(), request.query(),
                value -> value.getRegionName() + " " + value.getRenterName() + " " + value.getStatus());
        List<SsuMenuPageDataPayload.RentOperationEntry> entries = page(all, request).stream().map(record ->
                new SsuMenuPageDataPayload.RentOperationEntry(
                        record.getOperationId().toString(), record.getRegionName(), record.getAction().name().toLowerCase(Locale.ROOT),
                        record.getStatus().name().toLowerCase(Locale.ROOT), record.getRenterName(),
                        MoneyFormat.format(record.getGrossAmountMinor(), SimpleServerUtilities.ECONOMY.settings()),
                        MoneyFormat.format(record.getRefundAmountMinor(), SimpleServerUtilities.ECONOMY.settings()),
                        record.getError(), record.getUpdatedAtEpochMilli())).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), entries, List.of());
    }

    private SsuMenuPageDataPayload hologramsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canHologramAdmin(player)) return denied(request);
        List<HologramDefinition> all = filter(SimpleServerUtilities.HOLOGRAMS.all(), request.query(), value ->
                value.id + " " + value.type.name() + " " + value.dimension + " " + value.text);
        List<SsuMenuPageDataPayload.LocationEntry> entries = page(all, request).stream()
                .map(value -> new SsuMenuPageDataPayload.LocationEntry(
                        value.type.name().toLowerCase(Locale.ROOT), value.id, value.dimension,
                        value.x, value.y, value.z
                )).toList();
        return data(request, all.size(), List.of(), entries, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload maintenancePage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!isAdministrator(player)) return denied(request);
        List<SsuMenuPageDataPayload.PermissionEntry> colors = java.util.Arrays.stream(BorderCategory.values())
                .map(category -> new SsuMenuPageDataPayload.PermissionEntry("border", "color", category.serializedName(),
                        String.format("#%06X", SimpleServerUtilities.BORDER_SETTINGS.settings().getRgb(category) & 0xFFFFFF),
                        "default " + String.format("#%06X", category.defaultRgb() & 0xFFFFFF)))
                .filter(entry -> request.query().isBlank() || entry.key().toLowerCase(Locale.ROOT)
                        .contains(request.query().toLowerCase(Locale.ROOT)))
                .toList();
        List<SsuMenuPageDataPayload.PermissionEntry> entries = new ArrayList<>(page(colors, request));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("npc_shop", "setting", "buyback_minutes",
                Integer.toString(Config.NPC_SHOP_BUYBACK_MINUTES.get()), "1-1440 minutes"));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("system", "status", "jobs",
                Integer.toString(SimpleServerUtilities.JOBS.size()), "active long-running jobs"));
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("system", "status", "storage_pending",
                Long.toString(SimpleServerUtilities.STORAGE.statistics().pending()), "pending storage records"));
        var content = SimpleServerUtilities.CONTENT_PROGRESS.snapshot();
        var events = SimpleServerUtilities.CONTENT_EVENTS.snapshot();
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("system", "status", "content",
                content.players() + " players / " + content.playerFlags() + " flags / " + content.playerCounters() + " counters",
                events.publishedEvents() + " events / " + events.listenerFailures() + " listener failures"));
        var quests = SimpleServerUtilities.QUESTS.snapshot();
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("system", "status", "quests",
                quests.definitions() + " definitions / " + quests.active() + " active / " + quests.readyToTurnIn() + " ready",
                quests.journals() + " journals / " + quests.completions() + " completions"));
        var minigames = SimpleServerUtilities.MINIGAMES.snapshot();
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("system", "status", "minigames",
                minigames.queuedPlayers() + " queued / " + minigames.matches() + " matches / " + minigames.blockedArenas() + " blocked",
                minigames.definitions() + " definitions / " + minigames.pendingRecoveries() + " recoveries"));
        var dungeons = SimpleServerUtilities.DUNGEONS.snapshot();
        entries.add(new SsuMenuPageDataPayload.PermissionEntry("system", "status", "dungeons",
                dungeons.queuedPlayers() + " queued / " + dungeons.runs() + " runs / " + dungeons.blockedArenas() + " blocked",
                dungeons.definitions() + " definitions / " + dungeons.pendingRecoveries() + " recoveries"));
        return data(request, colors.size(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), entries);
    }

    private SsuMenuPageDataPayload utilityMiningAdminPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!PermissionService.getBoolean(player, PermissionKeys.UTILITY_MINING_ADMIN, false)) return denied(request);
        List<SsuMenuPageDataPayload.PermissionEntry> entries = List.of(
                new SsuMenuPageDataPayload.PermissionEntry("tree", "setting", "leaf_range",
                        Integer.toString(Config.TREECAPITATOR_LEAF_SEARCH_RANGE.get()), "0-16 blocks"),
                new SsuMenuPageDataPayload.PermissionEntry("tree", "setting", "break_leaves",
                        Boolean.toString(Config.TREECAPITATOR_BREAK_NATURAL_LEAVES.get()), "Instantly break natural leaves"),
                new SsuMenuPageDataPayload.PermissionEntry("tree", "setting", "default_max",
                        Integer.toString(Config.TREECAPITATOR_DEFAULT_MAX_BLOCKS.get()), "1-2048 blocks"),
                new SsuMenuPageDataPayload.PermissionEntry("vein", "setting", "default_max",
                        Integer.toString(Config.VEINMINER_DEFAULT_MAX_BLOCKS.get()), "1-2048 blocks"),
                new SsuMenuPageDataPayload.PermissionEntry("tree", "list", "custom_logs",
                        blankList(Config.TREECAPITATOR_CUSTOM_LOG_BLOCKS.get()), "Custom log block ids"),
                new SsuMenuPageDataPayload.PermissionEntry("tree", "list", "disabled_logs",
                        blankList(Config.TREECAPITATOR_DISABLED_LOG_BLOCKS.get()), "Disabled log block ids"),
                new SsuMenuPageDataPayload.PermissionEntry("vein", "list", "custom_ores",
                        blankList(Config.VEINMINER_CUSTOM_ORE_BLOCKS.get()), "Custom ore block ids"),
                new SsuMenuPageDataPayload.PermissionEntry("vein", "list", "disabled_ores",
                        blankList(Config.VEINMINER_DISABLED_ORE_BLOCKS.get()), "Disabled ore block ids")
        );
        return data(request, entries.size(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), entries);
    }

    private SsuMenuPageDataPayload ranksPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canPermissionAdmin(player)) return denied(request);
        String defaultRank = SimpleServerUtilities.PERMISSIONS.getDefaultRankName();
        String query = request.query().toLowerCase(Locale.ROOT);
        List<SsuMenuPageDataPayload.PermissionEntry> all = SimpleServerUtilities.PERMISSIONS.getRankNames().stream()
                .filter(rank -> query.isBlank() || rank.toLowerCase(Locale.ROOT).contains(query))
                .map(rank -> {
                    PermissionRank definition = SimpleServerUtilities.PERMISSIONS.getRank(rank);
                    String inherits = definition == null || definition.getInherits().isEmpty()
                            ? "none" : String.join(", ", definition.getInherits());
                    return new SsuMenuPageDataPayload.PermissionEntry(rank, "rank",
                            rank.equals(defaultRank) ? "default" : "rank",
                            definition == null ? "0" : Integer.toString(definition.getPriority()),
                            "inherits: " + inherits);
                }).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), page(all, request));
    }

    private SsuMenuPageDataPayload permissionsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!canPermissionAdmin(player)) return denied(request);
        String query = request.query().trim();
        List<SsuMenuPageDataPayload.PermissionEntry> all = new ArrayList<>();
        if (query.isBlank()) {
            for (String rankName : SimpleServerUtilities.PERMISSIONS.getRankNames()) {
                PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(rankName);
                all.add(new SsuMenuPageDataPayload.PermissionEntry(rankName, "rank", "priority",
                        Integer.toString(rank == null ? 0 : rank.getPriority()), "rank"));
                if (rank != null) rank.getPermissions().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> all.add(new SsuMenuPageDataPayload.PermissionEntry(rankName, "rank",
                                entry.getKey(), entry.getValue(), "rank override")));
            }
        } else {
            UUID playerId = SimpleServerUtilities.PERMISSIONS.findKnownPlayerId(query);
            if (playerId == null) {
                ServerPlayer online = player.level().getServer().getPlayerList().getPlayerByName(query);
                if (online != null) playerId = online.getUUID();
            }
            if (playerId != null) {
                PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(playerId);
                String owner = data == null || data.getLastKnownName().isBlank() ? query : data.getLastKnownName();
                if (data != null) {
                    data.getRanks().forEach(rank -> all.add(new SsuMenuPageDataPayload.PermissionEntry(owner,
                            "player", "rank", rank, "assigned rank")));
                    data.getPermissions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                            all.add(new SsuMenuPageDataPayload.PermissionEntry(owner, "player", entry.getKey(),
                                    entry.getValue(), "personal override")));
                }
            }
        }
        return data(request, all.size(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), page(all, request));
    }

    private SsuPermissionEditorDataPayload permissionEditorData(
            ServerPlayer viewer,
            SsuPermissionEditorRequestPayload request
    ) {
        if ("region".equals(request.mode())) return regionPermissionEditorData(viewer, request);
        if (!canPermissionAdmin(viewer)) {
            return SsuPermissionEditorDataPayload.empty(request.mode(), request.requestId(),
                    "You do not have permission to use the permission editor.", true);
        }

        boolean rankMode = "rank".equals(request.mode());
        MinecraftServer server = viewer.level().getServer();
        List<String> rankNames = SimpleServerUtilities.PERMISSIONS.getRankNames();
        List<String> rankOptions = rankNames.stream().limit(100).toList();
        List<SsuPermissionEditorDataPayload.TargetEntry> allTargets = new ArrayList<>();

        if (rankMode) {
            for (String rankName : rankNames) {
                PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(rankName);
                String summary = rank == null ? "" : "Priority " + rank.getPriority()
                        + (rank.getInherits().isEmpty() ? "" : " | inherits " + String.join(", ", rank.getInherits()));
                allTargets.add(new SsuPermissionEditorDataPayload.TargetEntry(rankName, rankName, summary));
            }
        } else {
            Map<UUID, be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer> known =
                    new LinkedHashMap<>();
            for (var entry : SimpleServerUtilities.PERMISSIONS.getKnownPlayers()) {
                if (SimpleServerUtilities.ECONOMY.isSystemAccount(entry.playerId())) continue;
                known.put(entry.playerId(), entry);
            }
            for (EconomyAccount account : SimpleServerUtilities.ECONOMY.playerAccounts()) {
                PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(account.getPlayerId());
                List<String> ranks = data == null ? List.of() : List.copyOf(data.getRanks());
                String name = account.getLastKnownName().isBlank()
                        ? account.getPlayerId().toString().substring(0, 8) : account.getLastKnownName();
                known.compute(account.getPlayerId(), (playerId, current) ->
                        current == null || current.name().isBlank()
                                ? new be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer(
                                playerId, name, ranks) : current);
            }
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(online.getUUID());
                List<String> ranks = data == null ? List.of() : List.copyOf(data.getRanks());
                known.put(online.getUUID(), new be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer(
                        online.getUUID(), online.getName().getString(), ranks));
            }
            known.values().stream()
                    .sorted(Comparator.comparing(
                            be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer::name,
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> allTargets.add(new SsuPermissionEditorDataPayload.TargetEntry(
                            entry.playerId().toString(), entry.name(),
                            entry.ranks().isEmpty() ? "Default rank" : "Rank: " + String.join(", ", entry.ranks())
                    )));
        }

        String targetQuery = request.targetQuery().toLowerCase(Locale.ROOT);
        List<SsuPermissionEditorDataPayload.TargetEntry> targets = allTargets.stream()
                .filter(target -> targetQuery.isBlank()
                        || (target.label() + " " + target.summary()).toLowerCase(Locale.ROOT).contains(targetQuery))
                .limit(200)
                .toList();

        List<SsuPermissionEditorDataPayload.TargetEntry> dimensions = new ArrayList<>();
        dimensions.add(new SsuPermissionEditorDataPayload.TargetEntry("", "All dimensions", "Global rank/player defaults"));
        SimpleServerUtilities.DIMENSIONS.dimensionInfos().stream()
                .limit(255)
                .forEach(info -> dimensions.add(new SsuPermissionEditorDataPayload.TargetEntry(
                        info.id(), info.displayName(), info.id() + " | "
                        + (info.managed() ? "SSU managed" : info.vanilla() ? "Vanilla" : "External"))));
        String requestedDimension = request.selectedDimension();
        String selectedDimension = requestedDimension.isBlank()
                || dimensions.stream().anyMatch(entry -> entry.id().equals(requestedDimension))
                ? requestedDimension
                : "";

        String selectedTarget = request.selectedTarget();
        String selectedLabel = "";
        String targetSummary = "";
        Map<String, String> directValues = Map.of();
        Map<String, String> inheritedValues = Map.of();
        Map<String, String> moduleDefaults = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(
                SimpleServerUtilities.PERMISSIONS.getDefaultRankName());

        if (!selectedTarget.isBlank()) {
            if (rankMode) {
                PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(selectedTarget);
                if (rank != null) {
                    selectedTarget = selectedTarget.toLowerCase(Locale.ROOT);
                    selectedLabel = selectedTarget;
                    targetSummary = "Priority " + rank.getPriority()
                            + (rank.getInherits().isEmpty() ? " | no inherited ranks"
                            : " | inherits " + String.join(", ", rank.getInherits()));
                    if (selectedDimension.isBlank()) {
                        directValues = Map.copyOf(rank.getPermissions());
                        inheritedValues = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(selectedTarget);
                    } else {
                        directValues = SimpleServerUtilities.PERMISSIONS.getRankDimensionPermissions(
                                selectedTarget, selectedDimension);
                        inheritedValues = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(
                                selectedTarget, selectedDimension);
                        targetSummary += " | dimension: " + dimensionLabel(dimensions, selectedDimension);
                    }
                } else selectedTarget = "";
            } else {
                UUID selectedId = resolvePlayerId(server, selectedTarget);
                if (selectedId != null && !SimpleServerUtilities.ECONOMY.isSystemAccount(selectedId)) {
                    PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(selectedId);
                    ServerPlayer online = server.getPlayerList().getPlayer(selectedId);
                    selectedTarget = selectedId.toString();
                    String economyName = SimpleServerUtilities.ECONOMY.findAccount(selectedId)
                            .map(EconomyAccount::getLastKnownName).orElse("");
                    selectedLabel = online != null ? online.getName().getString()
                            : data != null && !data.getLastKnownName().isBlank() ? data.getLastKnownName()
                            : !economyName.isBlank() ? economyName : selectedId.toString().substring(0, 8);
                    List<String> assignedRanks = data == null ? List.of() : List.copyOf(data.getRanks());
                    targetSummary = assignedRanks.isEmpty() ? "Uses default rank"
                            : "Assigned rank: " + String.join(", ", assignedRanks);
                    if (selectedDimension.isBlank()) {
                        directValues = data == null ? Map.of() : Map.copyOf(data.getPermissions());
                        inheritedValues = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(selectedId);
                    } else {
                        directValues = SimpleServerUtilities.PERMISSIONS.getPlayerDimensionPermissions(
                                selectedId, selectedDimension);
                        Map<String, String> fallback = new java.util.HashMap<>(
                                SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(selectedId, selectedDimension));
                        if (data != null) fallback.putAll(data.getPermissions());
                        inheritedValues = Map.copyOf(fallback);
                        targetSummary += " | dimension: " + dimensionLabel(dimensions, selectedDimension);
                    }
                } else selectedTarget = "";
            }
        }

        ArrayList<String> extraKeys = new ArrayList<>(directValues.keySet());
        extraKeys.addAll(inheritedValues.keySet());
        List<PermissionCatalog.Definition> definitions = PermissionCatalog.definitionsIncluding(extraKeys);
        String permissionQuery = request.permissionQuery().toLowerCase(Locale.ROOT);
        if (!permissionQuery.isBlank()) {
            definitions = definitions.stream()
                    .filter(definition -> (definition.key() + " " + definition.description())
                            .toLowerCase(Locale.ROOT).contains(permissionQuery))
                    .toList();
        }

        int totalPermissions = selectedTarget.isBlank() ? 0 : definitions.size();
        int pageCount = Math.max(1, (totalPermissions + request.pageSize() - 1) / request.pageSize());
        int resolvedPageIndex = Math.min(request.pageIndex(), pageCount - 1);
        int from = Math.min(totalPermissions, resolvedPageIndex * request.pageSize());
        int to = Math.min(totalPermissions, from + request.pageSize());
        List<SsuPermissionEditorDataPayload.PermissionEntry> permissionEntries = new ArrayList<>();
        if (!selectedTarget.isBlank()) {
            for (PermissionCatalog.Definition definition : definitions.subList(from, to)) {
                PermissionView resolved = resolvePermission(definition, directValues, inheritedValues, moduleDefaults, rankMode);
                String source = resolved.source();
                if (!selectedDimension.isBlank()) {
                    String scopedDirect = permissionValue(directValues, definition.key());
                    if (scopedDirect != null && !scopedDirect.isBlank()) source = rankMode
                            ? "rank dimension override" : "player dimension override";
                    else source = rankMode ? "global/inherited rank" : "player/rank fallback";
                }
                permissionEntries.add(new SsuPermissionEditorDataPayload.PermissionEntry(
                        definition.key(), resolved.directValue(), resolved.effectiveValue(), resolved.defaultValue(),
                        source, definition.type().name().toLowerCase(Locale.ROOT), definition.description(),
                        definition.minimum(), definition.maximum()
                ));
            }
        }

        return new SsuPermissionEditorDataPayload(
                rankMode ? "rank" : "player", selectedTarget, selectedDimension, selectedLabel, targetSummary,
                resolvedPageIndex, request.pageSize(), totalPermissions, request.requestId(), "", false,
                targets, dimensions, rankOptions, permissionEntries
        );
    }

    private static String dimensionLabel(
            List<SsuPermissionEditorDataPayload.TargetEntry> dimensions, String dimensionId
    ) {
        return dimensions.stream().filter(entry -> entry.id().equals(dimensionId))
                .map(SsuPermissionEditorDataPayload.TargetEntry::label).findFirst().orElse(dimensionId);
    }


    private SsuPermissionEditorDataPayload regionPermissionEditorData(
            ServerPlayer viewer,
            SsuPermissionEditorRequestPayload request
    ) {
        Region region = SimpleServerUtilities.REGIONS.get(request.selectedTarget());
        if (region == null) {
            return SsuPermissionEditorDataPayload.empty("region", request.requestId(),
                    "Region not found.", true);
        }
        if (!canEditRegionPermissions(viewer, region)) {
            return SsuPermissionEditorDataPayload.empty("region", request.requestId(),
                    "Region permission administration denied.", true);
        }

        Map<String, String> directValues = Map.copyOf(region.getPermissionOverrides());
        Map<String, String> moduleDefaults = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(
                SimpleServerUtilities.PERMISSIONS.getDefaultRankName());
        List<PermissionCatalog.Definition> definitions = PermissionCatalog.definitionsIncluding(directValues.keySet());
        String permissionQuery = request.permissionQuery().toLowerCase(Locale.ROOT);
        if (!permissionQuery.isBlank()) {
            definitions = definitions.stream()
                    .filter(definition -> (definition.key() + " " + definition.description())
                            .toLowerCase(Locale.ROOT).contains(permissionQuery))
                    .toList();
        }

        int totalPermissions = definitions.size();
        int pageCount = Math.max(1, (totalPermissions + request.pageSize() - 1) / request.pageSize());
        int resolvedPageIndex = Math.min(request.pageIndex(), pageCount - 1);
        int from = Math.min(totalPermissions, resolvedPageIndex * request.pageSize());
        int to = Math.min(totalPermissions, from + request.pageSize());
        List<SsuPermissionEditorDataPayload.PermissionEntry> entries = new ArrayList<>();
        for (PermissionCatalog.Definition definition : definitions.subList(from, to)) {
            String exactDirect = directValues.getOrDefault(definition.key(), "");
            String resolvedDirect = permissionValue(directValues, definition.key());
            String defaultValue = permissionValue(moduleDefaults, definition.key());
            if (defaultValue == null || defaultValue.isBlank()) {
                defaultValue = switch (definition.type()) {
                    case BOOLEAN -> Boolean.toString(PermissionService.getBuiltInDefault(definition.key()));
                    case INTEGER -> "0";
                    case TEXT -> "";
                };
            }
            String effective = resolvedDirect == null || resolvedDirect.isBlank() ? defaultValue : resolvedDirect;
            String source = resolvedDirect == null || resolvedDirect.isBlank()
                    ? "player/rank fallback"
                    : exactDirect.isBlank() ? "region wildcard" : "region override";
            entries.add(new SsuPermissionEditorDataPayload.PermissionEntry(
                    definition.key(), exactDirect, effective, defaultValue, source,
                    definition.type().name().toLowerCase(Locale.ROOT), definition.description(),
                    definition.minimum(), definition.maximum()
            ));
        }

        String summary = directValues.size() + " explicit override" + (directValues.size() == 1 ? "" : "s")
                + " | highest-priority containing region only";
        return new SsuPermissionEditorDataPayload(
                "region", region.getName(), "", region.getName(), summary,
                resolvedPageIndex, request.pageSize(), totalPermissions, request.requestId(), "", false,
                List.of(), List.of(), List.of(), entries
        );
    }


    private SsuPlayerProfileDataPayload playerProfileData(
            ServerPlayer viewer,
            SsuPlayerProfileRequestPayload request
    ) {
        if (!isAdministrator(viewer)) {
            return SsuPlayerProfileDataPayload.empty(request.requestId(),
                    "You do not have permission to inspect player profiles.", true);
        }

        MinecraftServer server = viewer.level().getServer();
        Map<UUID, be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer> known =
                new LinkedHashMap<>();
        for (var entry : SimpleServerUtilities.PERMISSIONS.getKnownPlayers()) {
            if (SimpleServerUtilities.ECONOMY.isSystemAccount(entry.playerId())) continue;
            known.put(entry.playerId(), entry);
        }
        for (EconomyAccount account : SimpleServerUtilities.ECONOMY.playerAccounts()) {
            PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(account.getPlayerId());
            List<String> ranks = data == null ? List.of() : List.copyOf(data.getRanks());
            String name = account.getLastKnownName().isBlank()
                    ? account.getPlayerId().toString().substring(0, 8) : account.getLastKnownName();
            known.compute(account.getPlayerId(), (playerId, current) ->
                    current == null || current.name().isBlank()
                            ? new be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer(
                            playerId, name, ranks) : current);
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(online.getUUID());
            List<String> ranks = data == null ? List.of() : List.copyOf(data.getRanks());
            known.put(online.getUUID(), new be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer(
                    online.getUUID(), online.getName().getString(), ranks));
        }

        String query = request.playerQuery().toLowerCase(Locale.ROOT);
        List<SsuPlayerProfileDataPayload.PlayerEntry> players = known.values().stream()
                .sorted(Comparator.comparing(
                        be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer::name,
                        String.CASE_INSENSITIVE_ORDER).thenComparing(value -> value.playerId().toString()))
                .filter(entry -> query.isBlank() || (entry.name() + " " + entry.playerId())
                        .toLowerCase(Locale.ROOT).contains(query))
                .limit(1_000)
                .map(entry -> {
                    boolean online = server.getPlayerList().getPlayer(entry.playerId()) != null;
                    String rank = SimpleServerUtilities.PERMISSIONS.getPrimaryRankName(entry.playerId());
                    return new SsuPlayerProfileDataPayload.PlayerEntry(
                            entry.playerId().toString(), entry.name(),
                            (online ? "Online" : "Offline") + " | Rank: " + blank(rank), online);
                })
                .toList();

        UUID selectedId = resolvePlayerId(server, request.selectedPlayer());
        if (selectedId == null && !query.isBlank()) {
            selectedId = known.values().stream()
                    .filter(entry -> entry.name().equalsIgnoreCase(request.playerQuery()))
                    .map(be.winnetrie.mod.simpleserverutilities.permission.PermissionManager.KnownPlayer::playerId)
                    .findFirst().orElse(null);
        }
        if (selectedId == null) {
            return new SsuPlayerProfileDataPayload("", "", 0, request.permissionPageSize(), 0,
                    request.requestId(), "", false, players,
                    SsuPlayerProfileDataPayload.Profile.empty(), List.of());
        }

        PlayerPermissionData playerData = SimpleServerUtilities.PERMISSIONS.getPlayerData(selectedId);
        ServerPlayer online = server.getPlayerList().getPlayer(selectedId);
        var selectedKnown = known.get(selectedId);
        String selectedName = online != null ? online.getName().getString()
                : playerData != null && !playerData.getLastKnownName().isBlank()
                ? playerData.getLastKnownName()
                : selectedKnown != null && !selectedKnown.name().isBlank()
                ? selectedKnown.name() : selectedId.toString().substring(0, 8);
        List<String> ranks = playerData == null ? List.of() : List.copyOf(playerData.getRanks());
        if (ranks.isEmpty()) ranks = List.of(SimpleServerUtilities.PERMISSIONS.getDefaultRankName());

        Map<String, String> directValues = playerData == null ? Map.of() : Map.copyOf(playerData.getPermissions());
        Map<String, String> inheritedValues = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(selectedId);
        Map<String, String> moduleDefaults = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(
                SimpleServerUtilities.PERMISSIONS.getDefaultRankName());
        ArrayList<String> extraKeys = new ArrayList<>(directValues.keySet());
        extraKeys.addAll(inheritedValues.keySet());
        List<PermissionCatalog.Definition> definitions = PermissionCatalog.definitionsIncluding(extraKeys);

        int totalPermissions = definitions.size();
        int pageCount = Math.max(1, (totalPermissions + request.permissionPageSize() - 1)
                / request.permissionPageSize());
        int resolvedPage = Math.min(request.permissionPageIndex(), pageCount - 1);
        int from = Math.min(totalPermissions, resolvedPage * request.permissionPageSize());
        int to = Math.min(totalPermissions, from + request.permissionPageSize());
        List<SsuPlayerProfileDataPayload.PermissionLine> permissionLines = new ArrayList<>();
        for (PermissionCatalog.Definition definition : definitions.subList(from, to)) {
            PermissionView resolved = resolvePermission(definition, directValues, inheritedValues, moduleDefaults, false);
            permissionLines.add(new SsuPlayerProfileDataPayload.PermissionLine(
                    definition.key(), blank(resolved.effectiveValue()), resolved.source()));
        }

        int claimGroups = SimpleServerUtilities.PLAYER_CLAIMS.countClaimGroups(selectedId);
        int claimChunks = SimpleServerUtilities.PLAYER_CLAIMS.countClaimChunks(selectedId);
        int homes = SimpleServerUtilities.HOMES.countHomes(selectedId);
        UUID profilePlayerId = selectedId;
        List<String> rentals = SimpleServerUtilities.REGIONS.getAll().stream()
                .filter(region -> profilePlayerId.equals(region.getRentData().getRenter()))
                .map(Region::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String rentalNames = rentals.isEmpty() ? "" : String.join(", ", rentals.stream().limit(12).toList())
                + (rentals.size() > 12 ? " +" + (rentals.size() - 12) + " more" : "");
        String balance = SimpleServerUtilities.ECONOMY.findAccount(selectedId)
                .map(account -> MoneyFormat.format(account.getBalanceMinor(), SimpleServerUtilities.ECONOMY.settings()))
                .orElse("No economy account");
        String dimension = online == null ? "Offline" : online.level().dimension().identifier().toString();
        String position = online == null ? "-" : blockPos(online.blockPosition());
        String healthAndFood = online == null ? "Offline"
                : String.format(Locale.ROOT, "%.1f / %.1f HP | food %d",
                online.getHealth(), online.getMaxHealth(), online.getFoodData().getFoodLevel());
        String adminStatus = online == null ? "Offline (live OP state unavailable)"
                : isAdministrator(online) ? "Administrator" : "Player";

        SsuPlayerProfileDataPayload.Profile profile = new SsuPlayerProfileDataPayload.Profile(
                true, online != null, selectedId.toString(), selectedName,
                SimpleServerUtilities.PERMISSIONS.getPrimaryRankName(selectedId), String.join(", ", ranks),
                adminStatus, balance, claimGroups, claimChunks, homes, rentals.size(), rentalNames,
                dimension, position, healthAndFood, directValues.size());

        return new SsuPlayerProfileDataPayload(selectedId.toString(), selectedName,
                resolvedPage, request.permissionPageSize(), totalPermissions, request.requestId(), "", false,
                players, profile, permissionLines);
    }

    private static String permissionValue(Map<String, String> values, String key) {
        String resolved = PermissionValueResolver.getValue(values, key);
        if (resolved == null && PermissionKeys.TELEPORT_REQUIRE_STILL.equals(key)) {
            return PermissionValueResolver.getValue(values, PermissionKeys.TELEPORT_CANCEL_ON_MOVE);
        }
        return resolved;
    }

    private static PermissionView resolvePermission(
            PermissionCatalog.Definition definition,
            Map<String, String> directValues,
            Map<String, String> inheritedValues,
            Map<String, String> moduleDefaults,
            boolean rankMode
    ) {
        String exactDirect = directValues.getOrDefault(definition.key(), "");
        String resolvedDirect = permissionValue(directValues, definition.key());
        String resolvedInherited = permissionValue(inheritedValues, definition.key());
        String defaultValue = permissionValue(moduleDefaults, definition.key());
        if (defaultValue == null || defaultValue.isBlank()) {
            defaultValue = switch (definition.type()) {
                case BOOLEAN -> Boolean.toString(PermissionService.getBuiltInDefault(definition.key()));
                case INTEGER -> "0";
                case TEXT -> "";
            };
        }

        String effective;
        String source;
        if (resolvedDirect != null && !resolvedDirect.isBlank()) {
            effective = resolvedDirect;
            if (!exactDirect.isBlank()) source = rankMode ? "rank override" : "personal override";
            else source = rankMode ? "rank wildcard" : "personal wildcard";
        } else if (resolvedInherited != null && !resolvedInherited.isBlank()) {
            effective = resolvedInherited;
            source = rankMode ? "inherited rank" : "assigned rank";
        } else {
            effective = defaultValue;
            source = "module default";
        }
        return new PermissionView(exactDirect, effective, defaultValue, source);
    }


    private SsuMenuPageDataPayload statisticsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        if (!StatisticEditorService.canAdmin(player)) return denied(request);
        List<PlayerStatisticDefinition> all = filter(SimpleServerUtilities.STATISTICS.definitions(), request.query(), definition ->
                definition.id + " " + definition.displayName + " " + definition.eventType.name() + " " + definition.target);
        List<SsuMenuPageDataPayload.StatisticEntry> entries = page(all, request).stream().map(definition -> {
            long total = SimpleServerUtilities.STATISTICS.total(definition.id);
            return new SsuMenuPageDataPayload.StatisticEntry(
                    definition.id, definition.displayName, definition.eventType.name(), definition.target,
                    definition.unit, definition.enabled, SimpleServerUtilities.STATISTICS.playerCount(definition.id),
                    total, SimpleServerUtilities.STATISTICS.format(definition, total), definition.updatedAtEpochMilli);
        }).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), entries);
    }

    private ActionResult hologramMoveHere(ServerPlayer player, String id) {
        if (!PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false))
            return ActionResult.fail("Hologram administration denied.", "holograms");
        HologramDefinition definition = SimpleServerUtilities.HOLOGRAMS.get(id);
        if (definition == null) return ActionResult.fail("Hologram not found.", "holograms");
        definition.dimension = player.level().dimension().identifier().toString();
        definition.x = player.getX(); definition.y = player.getY() + 1.8D; definition.z = player.getZ();
        return SimpleServerUtilities.HOLOGRAMS.put(definition)
                ? ActionResult.shellPage("Hologram moved to your current position.", "holograms")
                : ActionResult.fail("Hologram could not be saved.", "holograms");
    }

    private ActionResult statisticEdit(ServerPlayer player, String id) {
        return StatisticEditorService.open(player, id)
                ? ActionResult.ok(id == null || id.isBlank() ? "Statistic editor opened." : "Statistic editor opened.", "")
                : ActionResult.fail("Statistic administration is not allowed.", "statistics");
    }

    private ActionResult statisticToggle(ServerPlayer player, String id, String rawValue) {
        if (!StatisticEditorService.canAdmin(player)) return ActionResult.fail("Statistic administration is not allowed.", "statistics");
        final boolean enabled;
        try { enabled = strictBoolean(rawValue); }
        catch (IllegalArgumentException exception) { return ActionResult.fail("Invalid statistic state.", "statistics"); }
        if (!SimpleServerUtilities.STATISTICS.setEnabled(id, enabled)) return ActionResult.fail("Statistic not found.", "statistics");
        SimpleServerUtilities.HOLOGRAMS.syncAll();
        return ActionResult.ok("Statistic " + (enabled ? "resumed." : "paused."), "statistics");
    }

    private ActionResult statisticReset(ServerPlayer player, String id) {
        if (!StatisticEditorService.canAdmin(player)) return ActionResult.fail("Statistic administration is not allowed.", "statistics");
        if (!SimpleServerUtilities.STATISTICS.reset(id)) return ActionResult.fail("Statistic not found.", "statistics");
        SimpleServerUtilities.HOLOGRAMS.syncAll();
        return ActionResult.ok("Statistic values reset for every player.", "statistics");
    }

    private ActionResult statisticDelete(ServerPlayer player, String id) {
        if (!StatisticEditorService.canAdmin(player)) return ActionResult.fail("Statistic administration is not allowed.", "statistics");
        if (!SimpleServerUtilities.STATISTICS.delete(id)) return ActionResult.fail("Statistic not found.", "statistics");
        SimpleServerUtilities.HOLOGRAMS.syncAll();
        return ActionResult.ok("Statistic deleted.", "statistics");
    }

    private ActionResult pay(ServerPlayer player, String targetName, String rawAmount) {
        if (!SimpleServerUtilities.ECONOMY.isEnabled()
                || !PermissionService.getBoolean(player, PermissionKeys.ECONOMY_USE, true)
                || !PermissionService.getBoolean(player, PermissionKeys.ECONOMY_PAY, true)) {
            return ActionResult.fail("You cannot make payments.", "transactions");
        }
        Optional<EconomyAccount> target = SimpleServerUtilities.ECONOMY.findPlayerAccountByName(player.level().getServer(), targetName);
        if (target.isEmpty()) return ActionResult.fail("Economy account not found: " + targetName, "transactions");
        long amount;
        try { amount = MoneyFormat.parseMinor(rawAmount, SimpleServerUtilities.ECONOMY.settings()); }
        catch (IllegalArgumentException e) { return ActionResult.fail(e.getMessage(), "transactions"); }
        EconomyResult result = SimpleServerUtilities.ECONOMY.transfer(player,
                SimpleServerUtilities.ECONOMY.ensureAccount(player), target.get(), amount,
                "Dashboard payment", "dashboard-pay:" + player.getUUID() + ":" + UUID.randomUUID());
        return result.successful() ? ActionResult.shellPage(result.message(), "transactions")
                : ActionResult.fail(result.message(), "transactions");
    }

    private ActionResult setting(ServerPlayer player, String key, String value) {
        if (!PermissionService.getBoolean(player, PermissionKeys.SETTINGS_USE, true)) return ActionResult.fail("Settings are not available.", "");
        var prefs = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        try {
            switch (key.toLowerCase(Locale.ROOT)) {
                case "hints" -> prefs.setDashboardHints(strictBoolean(value));
                case "minimap_enabled" -> prefs.setMinimapEnabled(strictBoolean(value));
                case "minimap_size" -> prefs.setMinimapSize(Integer.parseInt(value));
                case "minimap_shape" -> prefs.setMinimapShape(MinimapShape.valueOf(value.toUpperCase(Locale.ROOT)));
                case "minimap_position" -> prefs.setMinimapPosition(MinimapPosition.valueOf(value.toUpperCase(Locale.ROOT)));
                case "minimap_northup" -> prefs.setMinimapNorthUp(strictBoolean(value));
                case "minimap_claims" -> prefs.setMinimapShowClaims(strictBoolean(value));
                case "minimap_regions" -> prefs.setMinimapShowRegions(strictBoolean(value));
                case "worldmap_claims" -> prefs.setWorldMapShowClaims(strictBoolean(value));
                case "worldmap_regions" -> prefs.setWorldMapShowRegions(strictBoolean(value));
                case "worldmap_markers" -> prefs.setWorldMapShowMarkers(strictBoolean(value));
                case "minimap_markers" -> prefs.setMinimapShowMarkers(strictBoolean(value));
                case "minimap_calendar" -> prefs.setMinimapShowCalendar(strictBoolean(value));
                case "world_markers" -> prefs.setWorldMarkersVisible(strictBoolean(value));
                case "marker_beams" -> prefs.setMarkerBeamsVisible(strictBoolean(value));
                case "marker_beam_distance" -> prefs.setMarkerBeamDistance(Integer.parseInt(value));
                case "map_live_update_radius" -> prefs.setMapLiveUpdateRadiusChunks(Integer.parseInt(value));
                case "block_information_enabled" -> {
                    boolean enabled = strictBoolean(value);
                    if (enabled && (!Config.ENABLE_BLOCK_INFORMATION.get()
                            || !SimpleServerUtilities.CORE.modules().isActive("block_information"))) {
                        return ActionResult.fail("Block Information is disabled by the server.", "");
                    }
                    if (enabled && !PermissionService.getBooleanWithoutOperatorBypass(
                            player, PermissionKeys.BLOCK_INFORMATION_USE, true)) {
                        return ActionResult.fail("You do not have permission to use Block Information.", "");
                    }
                    prefs.setBlockInformationEnabled(enabled);
                }
                case "block_information_debug" -> {
                    boolean enabled = strictBoolean(value);
                    if (enabled && (!Config.ENABLE_BLOCK_INFORMATION.get()
                            || !SimpleServerUtilities.CORE.modules().isActive("block_information")
                            || !prefs.isBlockInformationEnabled())) {
                        return ActionResult.fail("Enable Block Information before enabling debug details.", "");
                    }
                    if (enabled && !PermissionService.getBooleanWithoutOperatorBypass(
                            player, PermissionKeys.BLOCK_INFORMATION_DEBUG, false)) {
                        return ActionResult.fail("You do not have permission to use Block Information debug mode.", "");
                    }
                    prefs.setBlockInformationDebugEnabled(enabled);
                }
                case "mail_auto_delete_player" -> prefs.setMailAutoDeletePlayerAttachments(strictBoolean(value));
                case "mail_auto_delete_system" -> prefs.setMailAutoDeleteSystemAttachments(strictBoolean(value));
                case "mail_auto_delete_auction" -> prefs.setMailAutoDeleteAuctionAttachments(strictBoolean(value));
                case "treecapitator_enabled" -> {
                    boolean enabled = strictBoolean(value);
                    if (enabled && !Config.ENABLE_TREECAPITATOR.get()) {
                        return ActionResult.fail("Treecapitator is disabled by the server.", "");
                    }
                    if (enabled && !PermissionService.getBoolean(player, PermissionKeys.TREECAPITATOR_USE, true)) {
                        return ActionResult.fail("You do not have permission to use Treecapitator.", "");
                    }
                    prefs.setTreecapitatorEnabled(enabled);
                }
                case "treecapitator_activation" -> prefs.setTreecapitatorActivation(MiningActivationMode.valueOf(value.toUpperCase(Locale.ROOT)));
                case "treecapitator_color" -> prefs.setTreecapitatorOutlineColor(parseColor(value));
                case "treecapitator_brightness" -> prefs.setTreecapitatorOutlineBrightness(Integer.parseInt(value));
                case "treecapitator_info" -> prefs.setTreecapitatorInfoEnabled(strictBoolean(value));
                case "veinminer_enabled" -> {
                    boolean enabled = strictBoolean(value);
                    if (enabled && !Config.ENABLE_VEINMINER.get()) {
                        return ActionResult.fail("Veinminer is disabled by the server.", "");
                    }
                    if (enabled && !PermissionService.getBoolean(player, PermissionKeys.VEINMINER_USE, true)) {
                        return ActionResult.fail("You do not have permission to use Veinminer.", "");
                    }
                    prefs.setVeinminerEnabled(enabled);
                }
                case "veinminer_activation" -> prefs.setVeinminerActivation(MiningActivationMode.valueOf(value.toUpperCase(Locale.ROOT)));
                case "veinminer_color" -> prefs.setVeinminerOutlineColor(parseColor(value));
                case "veinminer_brightness" -> prefs.setVeinminerOutlineBrightness(Integer.parseInt(value));
                case "veinminer_info" -> prefs.setVeinminerInfoEnabled(strictBoolean(value));
                default -> { return ActionResult.fail("Unknown setting.", ""); }
            }
        } catch (Exception e) { return ActionResult.fail("Invalid setting value.", ""); }
        SimpleServerUtilities.UI_PREFERENCES.save();
        BlockInformationService.syncPlayer(player);
        MapMarkerService.sync(player);
        return ActionResult.shell("Setting saved.");
    }

    private static int parseColor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        if (value.length() != 6 && value.length() != 8) {
            throw new IllegalArgumentException("Color must be RRGGBB or AARRGGBB.");
        }
        long parsed = Long.parseUnsignedLong(value, 16);
        return (int) (value.length() == 6 ? 0xFF000000L | parsed : parsed);
    }

    private ActionResult border(ServerPlayer player, String target, String value) {
        final boolean visible;
        try { visible = strictBoolean(value); }
        catch (IllegalArgumentException e) { return ActionResult.fail(e.getMessage(), ""); }
        if ("claims".equalsIgnoreCase(target)) {
            if (!Config.ENABLE_PLAYER_CLAIMS.get()
                    || !PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_CLAIMS_VIEW, true)) {
                return ActionResult.fail("Claim borders are not allowed by the server.", "");
            }
            SimpleServerUtilities.BORDER_SETTINGS.setClaimsVisible(player.getUUID(), visible);
        } else if ("other_claims".equalsIgnoreCase(target)) {
            if (!Config.ENABLE_PLAYER_CLAIMS.get()
                    || !PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_CLAIMS_VIEW, true)) {
                return ActionResult.fail("Claim borders are not allowed by the server.", "");
            }
            SimpleServerUtilities.BORDER_SETTINGS.setShowOtherClaims(player.getUUID(), visible);
        } else if ("regions".equalsIgnoreCase(target)) {
            if (!Config.ENABLE_ADMIN_REGIONS.get()
                    || !PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_REGIONS_VIEW, true)) {
                return ActionResult.fail("Region borders are not allowed by the server.", "");
            }
            SimpleServerUtilities.BORDER_SETTINGS.setRegionsVisible(player.getUUID(), visible);
        } else if ("minigame_game".equalsIgnoreCase(target)) {
            if (!Config.ENABLE_MINIGAMES.get()) {
                return ActionResult.fail("Minigames are disabled by the server.", "");
            }
            SimpleServerUtilities.BORDER_SETTINGS.setMinigameGameBorderVisible(player.getUUID(), visible);
        } else if ("minigame_spectator".equalsIgnoreCase(target)) {
            if (!Config.ENABLE_MINIGAMES.get()) {
                return ActionResult.fail("Minigames are disabled by the server.", "");
            }
            SimpleServerUtilities.BORDER_SETTINGS.setMinigameSpectatorBorderVisible(player.getUUID(), visible);
        } else return ActionResult.fail("Unknown border layer.", "");
        SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        SimpleServerUtilities.MINIGAMES.syncRuntimeBorders(player);
        return ActionResult.shell("Border visibility updated.");
    }

    private ActionResult claimMap(ServerPlayer player, String claimName) {
        if (!ClaimPolicy.canUseMap(player)) return ActionResult.fail("You cannot use the claim map.", "claims");
        ClaimMapService.open(player, claimName);
        return ActionResult.ok("Claim map opened.", "");
    }

    private ActionResult claimShow(ServerPlayer player, String claimName) {
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        if (claim == null || !PermissionService.getBoolean(player, PermissionKeys.CLAIMS_VISUALIZE, true))
            return ActionResult.fail("Claim not found or visualization denied.", "claims");
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showClaim(player, claim);
        return ActionResult.ok("Showing claim '" + claim.getDisplayName() + "'.", "claims");
    }

    private ActionResult claimHide(ServerPlayer player) {
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideClaim(player);
        return ActionResult.ok("All individually selected claim borders are hidden.", "claims");
    }

    private ActionResult claimVisibility(ServerPlayer player, String claimName, String rawValue) {
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        if (claim == null || !PermissionService.getBoolean(player, PermissionKeys.CLAIMS_VISUALIZE, true)) {
            return ActionResult.fail("Claim not found or visualization denied.", "claims");
        }
        final boolean visible;
        try { visible = strictBoolean(rawValue); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "claims"); }
        SimpleServerUtilities.BORDER_VISUALIZATIONS.setClaimVisible(player, claim, visible);
        boolean enabled = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID()).isClaimBordersVisible();
        String message = visible
                ? enabled ? "Claim border is now shown."
                : "Claim border selected. Enable claim borders in Settings to render it."
                : "Claim border is now hidden.";
        return ActionResult.shellPage(message, "claims");
    }

    private ActionResult regionVisibility(ServerPlayer player, String name, String value) {
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return ActionResult.fail("Region not found.", "regions");
        if (!Config.ENABLE_ADMIN_REGIONS.get() || !isAdministrator(player)) {
            return ActionResult.fail("Server-region administration is required.", "regions");
        }
        final boolean show;
        try { show = strictBoolean(value); }
        catch (IllegalArgumentException e) { return ActionResult.fail(e.getMessage(), "regions"); }
        if (show) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.showRegion(player, region);
        } else {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.hideRegion(player, region.getName());
        }
        return ActionResult.ok(show
                ? "Region border enabled for players."
                : "Region border disabled for players.", "regions");
    }

    private ActionResult regionsHide(ServerPlayer player) {
        if (!Config.ENABLE_ADMIN_REGIONS.get() || !isAdministrator(player)) {
            return ActionResult.fail("Server-region administration is required.", "regions");
        }
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideRegion(player);
        return ActionResult.ok("All server-region borders disabled for players.", "regions");
    }

    private ActionResult regionRent(ServerPlayer player, String name, String operation) {
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return ActionResult.fail("Region not found.", "regions");
        if (!PermissionService.getBoolean(player, PermissionKeys.REGIONS_RENT, true)) return ActionResult.fail("Region renting denied.", "regions");
        if ("unrent".equals(operation)
                && !player.getUUID().equals(region.getRentData().getRenter())
                && !PermissionService.getBoolean(player, PermissionKeys.REGIONS_RENT_ADMIN, false)) {
            return ActionResult.fail("You are not allowed to cancel this rental.", "regions");
        }
        RegionRentalService.RentalResult result = switch (operation) {
            case "rent" -> RegionRentalService.rent(player, region);
            case "extend" -> RegionRentalService.extend(player, region);
            case "unrent" -> RegionRentalService.unrent(player, player.level().getServer(), region, true);
            default -> RegionRentalService.RentalResult.fail("Unknown rental operation.");
        };
        return result.success() ? ActionResult.shellPage(result.message(), "regions") : ActionResult.fail(result.message(), "regions");
    }

    private static final long GUI_REGION_BLOCK_OPERATION_LIMIT = 1_000_000L;

    private ActionResult regionAdminTeleport(ServerPlayer player, String name) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region administration denied.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return ActionResult.fail("Region not found.", "region_admin");
        if (region.getSpawnPos() == null) return ActionResult.fail("Set a region spawn in Region Settings first.", "region_admin");
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!RegionPolicy.canTeleportRegion(player, context)) return ActionResult.fail(TeleportPolicy.denialMessage(TeleportType.REGION, context), "region_admin");
        ServerLevel level = player.level().getServer().getLevel(region.getDimension());
        if (level == null) return ActionResult.fail("Region dimension is not loaded.", "region_admin");
        BlockPos spawn = region.getSpawnPos();
        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.REGION, context);
        int result = SimpleServerUtilities.TELEPORTS.requestTeleport(player, "regions", "region '" + region.getName() + "'",
                options, level, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                region.getSpawnYaw(), region.getSpawnPitch());
        return result > 0 ? ActionResult.ok("Region teleport requested.", "region_admin")
                : ActionResult.fail("Region teleport failed.", "region_admin");
    }

    private ActionResult regionAdminSnapshot(ServerPlayer player, String name) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        ActionResult validation = validateRegionWorldOperation(player, region, RegionMutationGuard.saveSnapshot(region), false, "region_admin");
        if (validation != null) return validation;
        ServerLevel level = player.level().getServer().getLevel(region.getDimension());
        try {
            var job = SimpleServerUtilities.REGION_SNAPSHOTS.createCaptureJob(level, region);
            UUID actorId = player.getUUID(); MinecraftServer server = player.level().getServer();
            UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> notifyJobResult(server, actorId,
                    "Snapshot for '" + region.getName() + "'", result.status().name(), result.error()));
            return ActionResult.ok("Snapshot capture scheduled as job " + jobId + ".", "region_admin");
        } catch (IOException | IllegalStateException exception) {
            return ActionResult.fail("Snapshot could not be scheduled: " + exception.getMessage(), "region_admin");
        }
    }

    private ActionResult regionAdminReset(ServerPlayer player, String name) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        ActionResult validation = validateRegionWorldOperation(player, region, RegionMutationGuard.resetFromSnapshot(region), true, "region_admin");
        if (validation != null) return validation;
        ServerLevel level = player.level().getServer().getLevel(region.getDimension());
        try {
            var job = SimpleServerUtilities.REGION_SNAPSHOTS.createResetJob(level, region);
            UUID actorId = player.getUUID(); MinecraftServer server = player.level().getServer();
            UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> notifyJobResult(server, actorId,
                    "Reset for '" + region.getName() + "'", result.status().name(), result.error()));
            return ActionResult.ok("Snapshot reset scheduled as job " + jobId + ".", "region_admin");
        } catch (IOException | IllegalStateException exception) {
            return ActionResult.fail("Reset could not be scheduled: " + exception.getMessage(), "region_admin");
        }
    }

    private ActionResult regionAdminClear(ServerPlayer player, String name) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        ActionResult validation = validateRegionWorldOperation(player, region, RegionMutationGuard.clearRegion(region), false, "region_admin");
        if (validation != null) return validation;
        ServerLevel level = player.level().getServer().getLevel(region.getDimension());
        try {
            RegionWorldEditManager.RegionClearJob job = RegionWorldEditManager.createClearJob(level, region, GUI_REGION_BLOCK_OPERATION_LIMIT);
            UUID actorId = player.getUUID(); MinecraftServer server = player.level().getServer();
            UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> notifyJobResult(server, actorId,
                    "Clear for '" + region.getName() + "'", result.status().name(), result.error()));
            return ActionResult.ok("Region clear scheduled as job " + jobId + ".", "region_admin");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ActionResult.fail("Clear could not be scheduled: " + exception.getMessage(), "region_admin");
        }
    }

    private ActionResult regionAdminRedefine(ServerPlayer player, String name) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) return ActionResult.fail("Set both selection points first.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return ActionResult.fail("Region not found.", "region_admin");
        RegionMutationGuard.Check safety = RegionMutationGuard.redefine(region);
        if (!safety.allowed()) return ActionResult.fail(safety.message(), "region_admin");
        RegionOperationResult result = SimpleServerUtilities.REGIONS.redefine(name, selection.getDimension(), selection.getPoint1(), selection.getPoint2());
        if (!result.isSuccess()) return ActionResult.fail("Region could not be redefined: " + result.getDetails(), "region_admin");
        String notice = "Region redefined.";
        try {
            int archived = SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(name, "region-redefined");
            if (archived > 0) notice += " The previous snapshot was archived.";
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Region '{}' redefined but old snapshot could not be archived.", name, exception);
            notice += " Warning: the old snapshot could not be archived; save a new snapshot before resetting.";
        }
        RegionCommands.getSelectionManager().clear(player);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshShownRegion(player);
        return ActionResult.shellPage(notice, "region_admin");
    }

    private ActionResult regionAdminDelete(ServerPlayer player, String name) {
        if (!RegionPolicy.canDeleteRegion(player)) return ActionResult.fail("Region delete permission is required.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return ActionResult.fail("Region not found.", "region_admin");
        RegionMutationGuard.Check safety = RegionMutationGuard.delete(region);
        if (!safety.allowed()) return ActionResult.fail(safety.message(), "region_admin");
        try {
            SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(region.getName(), "region-deleted");
        } catch (IOException exception) {
            return ActionResult.fail("Region was not deleted because its snapshot could not be archived safely.", "region_admin");
        }
        boolean removed = SimpleServerUtilities.REGIONS.delete(name);
        if (removed) SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(player.level().getServer());
        return removed ? ActionResult.shellPage("Region deleted.", "region_admin")
                : ActionResult.fail("Region could not be deleted.", "region_admin");
    }

    private ActionResult regionAdminAddTime(ServerPlayer player, String name, String rawDays) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return ActionResult.fail("Region not found.", "region_admin");
        int days;
        try { days = Integer.parseInt(rawDays == null ? "" : rawDays.trim()); }
        catch (NumberFormatException exception) { return ActionResult.fail("Days must be a whole number.", "region_admin"); }
        if (days < 1 || days > 36500) return ActionResult.fail("Days must be between 1 and 36500.", "region_admin");
        RegionRentalService.RentalResult result = RegionRentalService.adminAddTime(region, days);
        return result.success() ? ActionResult.shellPage(result.message(), "region_admin")
                : ActionResult.fail(result.message(), "region_admin");
    }

    private ActionResult regionAdminPause(ServerPlayer player, String name, String rawValue) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        Region region = SimpleServerUtilities.REGIONS.get(name);
        if (region == null) return ActionResult.fail("Region not found.", "region_admin");
        boolean paused; try { paused = strictBoolean(rawValue); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "region_admin"); }
        RegionRentalService.RentalResult result = RegionRentalService.setPaused(region, paused);
        return result.success() ? ActionResult.shellPage(result.message(), "region_admin")
                : ActionResult.fail(result.message(), "region_admin");
    }

    private ActionResult regionRentingToggle(ServerPlayer player, String rawValue) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        boolean enabled; try { enabled = strictBoolean(rawValue); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "region_admin"); }
        SimpleServerUtilities.REGIONS.setRentingEnabled(enabled);
        return ActionResult.shellPage("Region renting is now " + (enabled ? "enabled" : "paused") + ".", "region_admin");
    }

    private ActionResult regionSelectionPoint(ServerPlayer player, int point) {
        if (!RegionPolicy.canUseSelectionTool(player)) return ActionResult.fail("Region selection permission is required.", "region_admin");
        if (point == 1) RegionCommands.getSelectionManager().setPoint1(player, player.blockPosition());
        else RegionCommands.getSelectionManager().setPoint2(player, player.blockPosition());
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, RegionCommands.getSelectionManager().getSelection(player));
        return ActionResult.ok("Selection point " + point + " set to your current block position.", "region_admin");
    }

    private ActionResult regionSelectionCoordinates(ServerPlayer player, String rawPoint, String rawCoordinates) {
        if (!RegionPolicy.canUseSelectionTool(player)) return ActionResult.fail("Region selection permission is required.", "region_admin");
        int point;
        try { point = Integer.parseInt(rawPoint == null ? "" : rawPoint.trim()); }
        catch (NumberFormatException exception) { return ActionResult.fail("Choose selection point 1 or 2.", "region_admin"); }
        if (point != 1 && point != 2) return ActionResult.fail("Choose selection point 1 or 2.", "region_admin");
        String[] parts = rawCoordinates == null ? new String[0] : rawCoordinates.trim().split("[,\\s]+");
        if (parts.length != 3) return ActionResult.fail("Coordinates must be entered as x y z, for example 120 64 -35.", "region_admin");
        final BlockPos pos;
        try { pos = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])); }
        catch (NumberFormatException exception) { return ActionResult.fail("Coordinates must be whole numbers.", "region_admin"); }
        if (point == 1) RegionCommands.getSelectionManager().setPoint1(player, pos);
        else RegionCommands.getSelectionManager().setPoint2(player, pos);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, RegionCommands.getSelectionManager().getSelection(player));
        return ActionResult.ok("Selection point " + point + " set to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".", "region_admin");
    }

    private ActionResult regionSelectionClear(ServerPlayer player) {
        if (!RegionPolicy.canUseSelectionTool(player)) return ActionResult.fail("Region selection permission is required.", "region_admin");
        RegionCommands.getSelectionManager().clear(player);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        return ActionResult.ok("Region selection cleared.", "region_admin");
    }

    private ActionResult regionSelectionUnbind(ServerPlayer player) {
        if (!RegionPolicy.canUseSelectionTool(player)) return ActionResult.fail("Region selection permission is required.", "region_admin");
        SimpleServerUtilities.REGION_SELECTION_TOOLS.unbind(player);
        return ActionResult.ok("Region selector tool unbound.", "region_admin");
    }

    private ActionResult regionSelectionFill(ServerPlayer player, String blocks) {
        if (!RegionPolicy.canEditRegion(player)) return ActionResult.fail("Region editing permission is required.", "region_admin");
        if (blocks == null || blocks.isBlank()) return ActionResult.fail("Enter one or more block ids.", "region_admin");
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) return ActionResult.fail("Set both selection points first.", "region_admin");
        ServerLevel level = player.level().getServer().getLevel(selection.getDimension());
        if (level == null) return ActionResult.fail("Selection dimension is not loaded.", "region_admin");
        try {
            RegionWorldEditManager.RegionFillJob job = RegionWorldEditManager.createFillJob(level, selection, blocks, GUI_REGION_BLOCK_OPERATION_LIMIT);
            UUID actorId = player.getUUID(); MinecraftServer server = player.level().getServer();
            UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> notifyJobResult(server, actorId,
                    "Selection fill", result.status().name(), result.error()));
            return ActionResult.ok("Selection fill scheduled as job " + jobId + ".", "region_admin");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ActionResult.fail("Fill could not be scheduled: " + exception.getMessage(), "region_admin");
        }
    }

    private ActionResult validateRegionWorldOperation(ServerPlayer player, Region region, RegionMutationGuard.Check safety, boolean requireSnapshot, String page) {
        if (region == null) return ActionResult.fail("Region not found.", page);
        if (!safety.allowed()) return ActionResult.fail(safety.message(), page);
        if (requireSnapshot && !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName()))
            return ActionResult.fail("No saved snapshot exists for this region.", page);
        if (region.getVolume() > GUI_REGION_BLOCK_OPERATION_LIMIT)
            return ActionResult.fail("Region is too large for this safe operation: " + region.getVolume()
                    + " blocks; limit " + GUI_REGION_BLOCK_OPERATION_LIMIT + ".", page);
        if (player.level().getServer().getLevel(region.getDimension()) == null)
            return ActionResult.fail("Region dimension is not loaded.", page);
        return null;
    }

    private static void notifyJobResult(MinecraftServer server, UUID actorId, String label, String status, String error) {
        ServerPlayer online = server.getPlayerList().getPlayer(actorId);
        if (online == null) return;
        String suffix = error == null || error.isBlank() ? "." : ": " + error;
        online.sendSystemMessage(Component.literal(label + " " + status.toLowerCase(Locale.ROOT) + suffix));
    }

    private ActionResult setHome(ServerPlayer player, String rawName, String claimName) {
        if (SimpleServerUtilities.CLAIM_TAX.isMutationLocked(player.getUUID())) {
            return ActionResult.fail("Homes cannot be created or moved while a claim-tax settlement is in progress.", "homes");
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) return ActionResult.fail("Enter a home name.", "homes");
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        if (claim == null) return ActionResult.fail("The selected claim no longer exists.", "claims");
        if (!HomePolicy.canSetHomeInClaim(player, player.blockPosition(), claim)) {
            return ActionResult.fail("Stand inside this claim and ensure you have permission to set homes.", "homes");
        }
        boolean existed = SimpleServerUtilities.HOMES.getHome(player.getUUID(), name) != null;
        boolean success;
        try { success = SimpleServerUtilities.HOMES.setHome(player, name); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "homes"); }
        if (!success) return ActionResult.fail("Your home limit of " + HomePolicy.getMaxHomes(player) + " has been reached.", "homes");
        return ActionResult.shellPage(existed ? "Home moved to your current position in this claim."
                : "Home created in this claim.", "homes");
    }

    private ActionResult deleteHome(ServerPlayer player, String name, String claimName) {
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!HomePolicy.canDeleteHome(player, context)) return ActionResult.fail("You may not delete homes here.", "homes");
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        PlayerHome home = SimpleServerUtilities.HOMES.getHome(player.getUUID(), name);
        if (claim == null || home == null || !ClaimHomeSupport.contains(claim, home)) {
            return ActionResult.fail("Home not found in this claim.", "homes");
        }
        boolean removed;
        try { removed = SimpleServerUtilities.HOMES.deleteHome(player.getUUID(), name); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "homes"); }
        return removed ? ActionResult.shellPage("Home deleted.", "homes") : ActionResult.fail("Home not found.", "homes");
    }

    private ActionResult setWarp(ServerPlayer player, String rawName, String refreshPage) {
        String page = travelRefreshPage(refreshPage);
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!WarpPolicy.canSetWarp(player, context)) return ActionResult.fail("Warp administration denied at this location.", page);
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) return ActionResult.fail("Enter a warp name.", page);
        Warp existingWarp = SimpleServerUtilities.WARPS.getWarp(name);
        if (existingWarp != null && existingWarp.isPlayerRental()) {
            return ActionResult.fail("That name belongs to a player-rented warp. Delete it or choose another name.", page);
        }
        boolean existed = existingWarp != null;
        boolean success;
        try { success = SimpleServerUtilities.WARPS.setWarp(player, name); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), page); }
        if (!success) return ActionResult.fail("The server warp limit has been reached.", page);
        return ActionResult.shellPage(existed ? "Warp moved to your current position." : "Warp created at your current position.", page);
    }

    private ActionResult deleteWarp(ServerPlayer player, String name, String refreshPage) {
        String page = travelRefreshPage(refreshPage);
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!WarpPolicy.canDeleteWarp(player, context)) return ActionResult.fail("Warp administration denied.", page);
        boolean removed;
        try { removed = SimpleServerUtilities.WARPS.deleteWarp(name); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), page); }
        return removed ? ActionResult.shellPage("Warp deleted.", page) : ActionResult.fail("Warp not found.", page);
    }

    private ActionResult setPlayerWarp(ServerPlayer player, String rawName, long requestId) {
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!WarpPolicy.canRentWarp(player, context)) {
            return ActionResult.fail("You do not have permission to rent player warps at this location.", "player_warps");
        }
        try {
            var result = SimpleServerUtilities.WARPS.setPlayerRentalWarp(player, rawName, requestId);
            return result.successful() ? ActionResult.shellPage(result.message(), "player_warps")
                    : ActionResult.fail(result.message(), "player_warps");
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "player_warps");
        }
    }

    private ActionResult movePlayerWarp(ServerPlayer player, String name) {
        try {
            var result = SimpleServerUtilities.WARPS.movePlayerRentalWarp(player, name);
            return result.successful() ? ActionResult.shellPage(result.message(), "player_warps")
                    : ActionResult.fail(result.message(), "player_warps");
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "player_warps");
        }
    }

    private ActionResult deletePlayerWarp(ServerPlayer player, String name) {
        boolean removed;
        try { removed = SimpleServerUtilities.WARPS.deletePlayerWarp(player.getUUID(), name); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "player_warps"); }
        return removed ? ActionResult.shellPage("Your rented warp was deleted and its name is available again.", "player_warps")
                : ActionResult.fail("That rented warp was not found.", "player_warps");
    }

    private ActionResult setPlayerWarpVisibility(ServerPlayer player, String name, String rawVisible) {
        boolean visible = Boolean.parseBoolean(rawVisible);
        boolean updated;
        try { updated = SimpleServerUtilities.WARPS.setPlayerWarpVisibility(player.getUUID(), name, visible); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "player_warps"); }
        return updated ? ActionResult.ok("Warp is now " + (visible ? "public" : "private") + ".", "player_warps")
                : ActionResult.fail("That rented warp was not found.", "player_warps");
    }

    private ActionResult warpRentalSettings(ServerPlayer player, String rawPrice, String rawDays) {
        if (!canEconomyAdmin(player)) {
            return ActionResult.fail("Economy administration permission is required.", "warp_rental");
        }
        try {
            long price = MoneyFormat.parseMinor(rawPrice, SimpleServerUtilities.ECONOMY.settings());
            if (price < 0L) throw new IllegalArgumentException("Rental price cannot be negative.");
            BigDecimal days = new BigDecimal(rawDays == null ? "" : rawDays.trim().replace(',', '.'));
            long period = days.multiply(BigDecimal.valueOf(86_400_000L)).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (period < Duration.ofHours(1).toMillis() || period > Duration.ofDays(3650).toMillis()) {
                throw new IllegalArgumentException("Rental period must be between 1 hour and 3650 days.");
            }
            SimpleServerUtilities.WARPS.configureRental(price, period);
            return ActionResult.ok("Player-warp rental price and period updated.", "warp_rental");
        } catch (Exception exception) {
            String message = exception.getMessage();
            return ActionResult.fail(message == null || message.isBlank() ? "Enter a valid rental price and number of days." : message, "warp_rental");
        }
    }

    private ActionResult cancelTeleport(ServerPlayer player, String refreshPage) {
        boolean cancelled = SimpleServerUtilities.TELEPORTS.cancel(player);
        String page = switch (refreshPage) {
            case "homes" -> "homes";
            case "travel_admin" -> "travel_admin";
            default -> "travel";
        };
        return cancelled ? ActionResult.ok("Pending teleport cancelled.", page)
                : ActionResult.fail("You do not have a pending teleport.", page);
    }

    private ActionResult adminClaimTeleport(ServerPlayer player, String claimId) {
        if (!ClaimPolicy.hasAdminBypass(player)) return ActionResult.fail("Player-claim administration permission is required.", "admin_claims");
        PlayerClaim claim = findClaim(claimId);
        if (claim == null || claim.getChunks().isEmpty()) return ActionResult.fail("Claim not found or empty.", "admin_claims");
        ServerLevel level = level(player.level().getServer(), claim.getDimension());
        if (level == null) return ActionResult.fail("Claim dimension is not loaded.", "admin_claims");
        var chunk = claim.getChunks().stream().sorted(Comparator.comparingInt(be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk::getX)
                .thenComparingInt(be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk::getZ)).findFirst().orElse(null);
        if (chunk == null) return ActionResult.fail("Claim has no chunks.", "admin_claims");
        int x = chunk.getX() * 16 + 8;
        int z = chunk.getZ() * 16 + 8;
        BlockPos target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        int result = SimpleServerUtilities.TELEPORTS.requestTeleport(player, "claims-admin",
                "claim '" + claim.getDisplayName() + "'", new TeleportOptions(0, 0, false), level,
                target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, player.getYRot(), player.getXRot());
        return result > 0 ? ActionResult.ok("Admin claim teleport requested.", "admin_claims")
                : ActionResult.fail("Claim teleport failed.", "admin_claims");
    }

    private ActionResult adminClaimDelete(ServerPlayer player, String claimId) {
        if (!ClaimPolicy.hasAdminBypass(player)) return ActionResult.fail("Player-claim administration permission is required.", "admin_claims");
        PlayerClaim claim = findClaim(claimId);
        if (claim == null) return ActionResult.fail("Claim not found.", "admin_claims");
        if (SimpleServerUtilities.CLAIM_TAX.isMutationLocked(claim.getOwner())) {
            return ActionResult.fail("This player's claims are locked by a claim-tax settlement or safety halt.", "admin_claims");
        }
        boolean removed = SimpleServerUtilities.PLAYER_CLAIMS.deleteClaimGroup(claim.getOwner(), claim.getName(), true);
        if (removed) SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(player.level().getServer());
        return removed ? ActionResult.shellPage("Claim and its linked homes were deleted administratively.", "admin_claims")
                : ActionResult.fail("Claim could not be deleted.", "admin_claims");
    }

    private PlayerClaim findClaim(String rawId) {
        try {
            UUID id = UUID.fromString(rawId);
            return SimpleServerUtilities.PLAYER_CLAIMS.getClaims().stream()
                    .filter(claim -> id.equals(claim.getId())).findFirst().orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ActionResult teleportHome(ServerPlayer player, String name, String claimName, String refreshPage) {
        String page = "travel".equals(refreshPage) ? "travel" : "homes";
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!HomePolicy.canTeleportHome(player, context)) return ActionResult.fail(TeleportPolicy.denialMessage(TeleportType.HOME, context), page);
        PlayerHome home = SimpleServerUtilities.HOMES.getHome(player.getUUID(), name);
        if (home == null) {
            return ActionResult.fail("Home was not found.", page);
        }
        PlayerClaim claim = claimName == null || claimName.isBlank()
                ? ownedClaims(player).stream().filter(value -> ClaimHomeSupport.contains(value, home)).findFirst().orElse(null)
                : SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(player.getUUID(), claimName);
        if (claim == null || !ClaimHomeSupport.contains(claim, home)) {
            return ActionResult.fail("Home is no longer linked to one of your claims.", page);
        }
        ServerLevel level = level(player.level().getServer(), home.getDimension());
        if (level == null) return ActionResult.fail("Home dimension is not loaded.", page);
        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.HOME, context);
        int result = SimpleServerUtilities.TELEPORTS.requestTeleport(
                player, "homes", "home '" + home.getDisplayName() + "'",
                options, level, home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch(),
                candidate -> HomePolicy.canTeleportHome(candidate,
                        PermissionContext.at(candidate, candidate.blockPosition())),
                candidate -> TeleportPolicy.denialMessage(TeleportType.HOME,
                        PermissionContext.at(candidate, candidate.blockPosition())));
        return result > 0 ? ActionResult.ok("Home teleport requested.", page) : ActionResult.fail("Home teleport failed.", page);
    }

    private ActionResult teleportWarp(ServerPlayer player, String name, String refreshPage) {
        String page = travelRefreshPage(refreshPage);
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!WarpPolicy.canTeleportWarp(player, context)) return ActionResult.fail(TeleportPolicy.denialMessage(TeleportType.WARP, context), page);
        Warp warp = SimpleServerUtilities.WARPS.getWarp(name);
        if (warp == null || !SimpleServerUtilities.WARPS.canAccess(player, warp)) return ActionResult.fail("Warp not found or not accessible.", page);
        ServerLevel level = level(player.level().getServer(), warp.getDimension());
        if (level == null) return ActionResult.fail("Warp dimension is not loaded.", page);
        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.WARP, context);
        int result = SimpleServerUtilities.TELEPORTS.requestTeleport(
                player, "warps", "warp '" + warp.getDisplayName() + "'",
                options, level, warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch(),
                candidate -> {
                    Warp current = SimpleServerUtilities.WARPS.getWarp(name);
                    return current != null
                            && SimpleServerUtilities.WARPS.canAccess(candidate, current)
                            && WarpPolicy.canTeleportWarp(candidate, PermissionContext.at(candidate, candidate.blockPosition()));
                },
                candidate -> TeleportPolicy.denialMessage(TeleportType.WARP,
                        PermissionContext.at(candidate, candidate.blockPosition())));
        return result > 0 ? ActionResult.ok("Warp teleport requested.", page) : ActionResult.fail("Warp teleport failed.", page);
    }

    private ActionResult teleportSpawn(ServerPlayer player, String refreshPage) {
        String page = travelRefreshPage(refreshPage);
        int result = SpawnService.requestTeleport(player);
        return result > 0 ? ActionResult.ok("Server-spawn teleport requested.", page)
                : ActionResult.fail("Server-spawn teleport failed.", page);
    }

    private ActionResult setServerSpawn(ServerPlayer player, String refreshPage) {
        String page = travelRefreshPage(refreshPage);
        if (!SpawnPolicy.canAdmin(player)) return ActionResult.fail("Server-spawn administration denied.", page);
        SimpleServerUtilities.SERVER_SPAWN.set(player);
        return ActionResult.shellPage("Server spawn set to your current position.", page);
    }

    private ActionResult clearServerSpawn(ServerPlayer player, String refreshPage) {
        String page = travelRefreshPage(refreshPage);
        if (!SpawnPolicy.canAdmin(player)) return ActionResult.fail("Server-spawn administration denied.", page);
        boolean removed = SimpleServerUtilities.SERVER_SPAWN.clear();
        return removed ? ActionResult.shellPage("Server spawn cleared.", page)
                : ActionResult.ok("The server spawn was already unset.", page);
    }

    private static String travelRefreshPage(String requested) {
        if ("travel_admin".equals(requested)) return "travel_admin";
        if ("player_warps".equals(requested)) return "player_warps";
        return "travel";
    }

    private ActionResult auctionTaxSet(ServerPlayer player, String rawPercentage) {
        if (!canEconomyAdmin(player)) return ActionResult.fail("Economy administration denied.", "auction_tax");
        try {
            String raw = rawPercentage == null ? "" : rawPercentage.trim().replace(',', '.').replace("%", "");
            java.math.BigDecimal percentage = new java.math.BigDecimal(raw);
            int permille = percentage.multiply(java.math.BigDecimal.TEN).intValueExact();
            if (permille < 0 || permille > 1_000) {
                throw new IllegalArgumentException("Tax must be between 0% and 100%.");
            }
            if (!SimpleServerUtilities.AUCTION_HOUSE.updateSaleTaxPermille(permille)) {
                return ActionResult.fail("The Auction House tax could not be saved.", "auction_tax");
            }
            return ActionResult.ok("Auction House tax updated to " + formatPercentPermille(permille) + "%.", "auction_tax");
        } catch (Exception exception) {
            String message = exception.getMessage();
            return ActionResult.fail(message == null || message.isBlank()
                    ? "Enter a valid tax percentage." : message, "auction_tax");
        }
    }

    private ActionResult claimTaxSettings(ServerPlayer player, String rawRate, String rawIntervalHours, String rawReminderHours) {
        if (!canEconomyAdmin(player)) return ActionResult.fail("Economy administration denied.", "claim_tax");
        try {
            long rate = MoneyFormat.parseMinor(rawRate, SimpleServerUtilities.ECONOMY.settings());
            if (rate < 0L) throw new IllegalArgumentException("Claim tax cannot be negative.");
            if (rate > SimpleServerUtilities.ECONOMY.settings().getMaximumBalanceMinor()) {
                throw new IllegalArgumentException("The per-chunk rate cannot exceed the Economy maximum balance.");
            }
            long interval = hoursMillis(rawIntervalHours, "Billing interval", false);
            long reminder = hoursMillis(rawReminderHours, "Reminder time", false);
            if (reminder >= interval) throw new IllegalArgumentException("Reminder time must be shorter than the billing interval.");
            if (SimpleServerUtilities.CLAIM_TAX.settings().isEnabled() && rate <= 0L) {
                throw new IllegalArgumentException("Disable Player Claim tax before setting its rate to zero.");
            }
            SimpleServerUtilities.CLAIM_TAX.configure(SimpleServerUtilities.CLAIM_TAX.settings().isEnabled(), rate, interval, reminder);
            return ActionResult.ok("Player Claim tax settings updated.", "claim_tax");
        } catch (Exception exception) {
            String message = exception.getMessage();
            return ActionResult.fail(message == null || message.isBlank() ? "Enter valid tax settings." : message, "claim_tax");
        }
    }

    private ActionResult claimTaxToggle(ServerPlayer player, String rawEnabled) {
        if (!canEconomyAdmin(player)) return ActionResult.fail("Economy administration denied.", "claim_tax");
        var settings = SimpleServerUtilities.CLAIM_TAX.settings();
        boolean enabled = Boolean.parseBoolean(rawEnabled);
        if (enabled && !Config.ENABLE_MAIL.get()) {
            return ActionResult.fail("Enable the Mail module before enabling destructive claim taxation so reminders can be delivered.", "claim_tax");
        }
        if (enabled && !SimpleServerUtilities.ECONOMY.settings().isEnabled()) {
            return ActionResult.fail("Enable the Economy before enabling Player Claim tax.", "claim_tax");
        }
        if (enabled && settings.getRateMinorPerChunk() <= 0L) {
            return ActionResult.fail("Set a positive tax rate before enabling Player Claim tax.", "claim_tax");
        }
        if (enabled && settings.getReminderLeadMillis() <= 0L) {
            return ActionResult.fail("Set a reminder time before enabling Player Claim tax.", "claim_tax");
        }
        SimpleServerUtilities.CLAIM_TAX.configure(enabled, settings.getRateMinorPerChunk(),
                settings.getIntervalMillis(), settings.getReminderLeadMillis());
        return ActionResult.ok("Player Claim tax is now " + (enabled ? "enabled" : "disabled") + ".", "claim_tax");
    }

    private ActionResult claimTaxDimension(ServerPlayer player, String dimension, String rawMultiplier) {
        if (!canEconomyAdmin(player)) return ActionResult.fail("Economy administration denied.", "claim_tax");
        try {
            double multiplier = Double.parseDouble(rawMultiplier == null ? "" : rawMultiplier.trim().replace(',', '.'));
            if (!Double.isFinite(multiplier) || multiplier < 0D || multiplier > 1000D) {
                throw new IllegalArgumentException("Dimension multiplier must be between 0 and 1000.");
            }
            SimpleServerUtilities.CLAIM_TAX.setDimensionMultiplier(dimension, multiplier);
            return ActionResult.ok("Dimension tax multiplier updated.", "claim_tax");
        } catch (Exception exception) {
            String message = exception.getMessage();
            return ActionResult.fail(message == null || message.isBlank() ? "Enter a valid dimension id and multiplier." : message, "claim_tax");
        }
    }

    private ActionResult claimTaxDimensionRemove(ServerPlayer player, String dimension) {
        if (!canEconomyAdmin(player)) return ActionResult.fail("Economy administration denied.", "claim_tax");
        try {
            SimpleServerUtilities.CLAIM_TAX.removeDimensionMultiplier(dimension);
            return ActionResult.ok("Custom dimension multiplier removed.", "claim_tax");
        } catch (Exception exception) {
            return ActionResult.fail(exception.getMessage(), "claim_tax");
        }
    }

    private static long hoursMillis(String raw, String label, boolean allowZero) {
        String field = label == null || label.isBlank() ? "Hours" : label;
        BigDecimal hours = new BigDecimal(raw == null ? "" : raw.trim().replace(',', '.'));
        if (hours.signum() < 0 || (!allowZero && hours.signum() == 0)) {
            throw new IllegalArgumentException(allowZero ? field + " cannot be negative." : field + " must be greater than zero.");
        }
        long millis = hours.multiply(BigDecimal.valueOf(3_600_000L)).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        if (!allowZero && millis < Duration.ofHours(1).toMillis()) {
            throw new IllegalArgumentException(field + " must be at least 1 hour.");
        }
        if (millis > Duration.ofDays(3650).toMillis()) {
            throw new IllegalArgumentException(field + " cannot exceed 3650 days.");
        }
        return millis;
    }

    private ActionResult economyHistoryLimit(ServerPlayer actor, String rawLimit) {
        if (!canEconomyAdmin(actor)) {
            return ActionResult.fail("You do not have permission to change economy retention.", "transactions");
        }
        int limit;
        try {
            limit = Integer.parseInt(rawLimit == null ? "" : rawLimit.trim());
        } catch (NumberFormatException exception) {
            return ActionResult.fail("Transaction history limit must be a whole number.", "transactions");
        }
        if (limit < 1 || limit > 1_000) {
            return ActionResult.fail("Transaction history limit must be between 1 and 1000.", "transactions");
        }
        int applied = SimpleServerUtilities.ECONOMY.setRecentHistoryLimit(limit);
        return ActionResult.shellPage("Economy now retains the latest " + applied
                + " transaction(s) per participating account.", "transactions");
    }

    private ActionResult economyAdminMutation(ServerPlayer actor, String rawAccountId, String rawAmount, String operation) {
        if (!canEconomyAdmin(actor)) return ActionResult.fail("Economy administration denied.", "accounts");
        EconomyAccount account;
        try {
            account = SimpleServerUtilities.ECONOMY.findAccount(UUID.fromString(rawAccountId)).orElse(null);
        } catch (IllegalArgumentException e) {
            account = null;
        }
        if (account == null) return ActionResult.fail("Economy account not found.", "accounts");
        long amount;
        try { amount = MoneyFormat.parseMinor(rawAmount, SimpleServerUtilities.ECONOMY.settings()); }
        catch (IllegalArgumentException e) { return ActionResult.fail(e.getMessage(), "accounts"); }
        EconomyResult result = switch (operation) {
            case "give" -> SimpleServerUtilities.ECONOMY.give(actor.getUUID(), actor.getName().getString(), account, amount,
                    "Dashboard admin give");
            case "take" -> SimpleServerUtilities.ECONOMY.take(actor.getUUID(), actor.getName().getString(), account, amount,
                    "Dashboard admin take");
            case "set" -> SimpleServerUtilities.ECONOMY.setBalance(actor.getUUID(), actor.getName().getString(), account, amount,
                    "Dashboard admin set");
            default -> EconomyResult.failure("invalid_operation", "Unknown economy operation.");
        };
        return result.successful() ? ActionResult.shellPage(result.message(), "accounts")
                : ActionResult.fail(result.message(), "accounts");
    }

    private ActionResult rankCreate(ServerPlayer actor, String rawName) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "ranks");
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) return ActionResult.fail("Enter a rank name.", "ranks");
        if (SimpleServerUtilities.PERMISSIONS.getRank(name) != null) return ActionResult.fail("That rank already exists.", "ranks");
        try {
            SimpleServerUtilities.PERMISSIONS.getOrCreateRank(name);
            SimpleServerUtilities.PERMISSIONS.save();
            return ActionResult.shellPage("Rank created.", "ranks");
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "ranks");
        }
    }

    private ActionResult rankDelete(ServerPlayer actor, String rank) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "ranks");
        boolean removed;
        try { removed = SimpleServerUtilities.PERMISSIONS.deleteRank(rank); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "ranks"); }
        return removed ? ActionResult.shellPage("Rank deleted; affected players now use the default rank.", "ranks")
                : ActionResult.fail("The default/admin rank cannot be deleted, or the rank no longer exists.", "ranks");
    }

    private ActionResult rankRename(ServerPlayer actor, String oldName, String newName) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "ranks");
        if (newName == null || newName.isBlank()) return ActionResult.fail("Enter a new rank name.", "ranks");
        boolean renamed;
        try { renamed = SimpleServerUtilities.PERMISSIONS.renameRank(oldName, newName); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "ranks"); }
        return renamed ? ActionResult.shellPage("Rank renamed and references migrated.", "ranks")
                : ActionResult.fail("The source rank was not found or the new name is already in use.", "ranks");
    }

    private ActionResult rankDefault(ServerPlayer actor, String rank) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "ranks");
        if (SimpleServerUtilities.PERMISSIONS.getRank(rank) == null) return ActionResult.fail("Rank not found.", "ranks");
        SimpleServerUtilities.PERMISSIONS.setDefaultRankName(rank);
        return ActionResult.shellPage("Default rank updated.", "ranks");
    }

    private ActionResult rankResetPlayer(ServerPlayer actor, String playerReference) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "ranks");
        UUID target = resolvePlayerId(actor.level().getServer(), playerReference);
        if (target == null) return ActionResult.fail("Player not found. Use an exact known name or UUID.", "ranks");
        SimpleServerUtilities.PERMISSIONS.assignPlayerRank(target, SimpleServerUtilities.PERMISSIONS.getDefaultRankName());
        return ActionResult.shellPage("Player rank reset to the server default. Personal overrides were preserved.", "ranks");
    }

    private ActionResult permissionCheck(ServerPlayer actor, String playerReference, String key) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        if (key == null || key.isBlank()) return ActionResult.fail("Choose a permission to check.", "permissions");
        MinecraftServer server = actor.level().getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(playerReference == null ? "" : playerReference.trim());
        if (target == null) {
            try { target = server.getPlayerList().getPlayer(UUID.fromString(playerReference == null ? "" : playerReference.trim())); }
            catch (IllegalArgumentException ignored) { }
        }
        if (target == null) return ActionResult.fail("Live permission checks require the selected player to be online.", "permissions");
        PermissionContext context = PermissionContext.at(target, target.blockPosition());
        String personal = SimpleServerUtilities.PERMISSIONS.resolvePersonalValue(target, key);
        String effective = SimpleServerUtilities.PERMISSIONS.resolveValue(target, key, context);
        String message = target.getName().getString() + " • " + key + " • personal "
                + (personal == null ? "<none>" : personal) + " • effective "
                + (effective == null ? "<unset>" : effective) + " • "
                + target.level().dimension().identifier();
        return ActionResult.ok(message, "");
    }

    private ActionResult assignRank(ServerPlayer actor, String playerName, String rankName) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        UUID target = resolvePlayerId(actor.level().getServer(), playerName);
        if (target == null || SimpleServerUtilities.PERMISSIONS.getRank(rankName) == null)
            return ActionResult.fail("Player or rank not found.", "permissions");
        SimpleServerUtilities.PERMISSIONS.assignPlayerRank(target, rankName);
        SimpleServerUtilities.PERMISSIONS.save();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return ActionResult.ok("Assigned rank '" + rankName + "'.", "permissions");
    }

    private ActionResult setPlayerPermission(ServerPlayer actor, String playerReference, String key, String value) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        UUID target = resolvePlayerId(actor.level().getServer(), playerReference);
        if (target == null || key.isBlank()) return ActionResult.fail("Player and permission are required.", "permissions");
        final String normalized;
        try {
            normalized = PermissionCatalog.normalizeValue(key, value);
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "permissions");
        }
        SimpleServerUtilities.PERMISSIONS.setPlayerPermission(target, key, normalized);
        SimpleServerUtilities.PERMISSIONS.save();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return ActionResult.ok("Personal permission updated.", "permissions");
    }

    private ActionResult unsetPlayerPermission(ServerPlayer actor, String playerReference, String key) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        UUID target = resolvePlayerId(actor.level().getServer(), playerReference);
        if (target == null || key.isBlank()) return ActionResult.fail("Player and permission are required.", "permissions");
        boolean removed = SimpleServerUtilities.PERMISSIONS.removePlayerPermission(target, key);
        SimpleServerUtilities.PERMISSIONS.save();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return removed ? ActionResult.ok("Personal permission reset to inherited.", "permissions")
                : ActionResult.ok("Permission was already inherited.", "permissions");
    }

    private ActionResult setRankPermission(ServerPlayer actor, String rankName, String key, String value) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        if (SimpleServerUtilities.PERMISSIONS.getRank(rankName) == null || key.isBlank()) {
            return ActionResult.fail("Rank and permission are required.", "permissions");
        }
        final String normalized;
        try {
            normalized = PermissionCatalog.normalizeValue(key, value);
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "permissions");
        }
        SimpleServerUtilities.PERMISSIONS.setRankPermission(rankName, key, normalized);
        SimpleServerUtilities.PERMISSIONS.save();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return ActionResult.ok("Rank permission updated.", "permissions");
    }

    private ActionResult unsetRankPermission(ServerPlayer actor, String rankName, String key) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        if (SimpleServerUtilities.PERMISSIONS.getRank(rankName) == null || key.isBlank()) {
            return ActionResult.fail("Rank and permission are required.", "permissions");
        }
        boolean removed = SimpleServerUtilities.PERMISSIONS.removeRankPermission(rankName, key);
        SimpleServerUtilities.PERMISSIONS.save();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return removed ? ActionResult.ok("Rank permission reset to inherited.", "permissions")
                : ActionResult.ok("Permission was already inherited.", "permissions");
    }


    private ActionResult setPlayerDimensionPermission(
            ServerPlayer actor, String playerReference, String key, String encodedValue
    ) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        UUID target = resolvePlayerId(actor.level().getServer(), playerReference);
        DimensionPermissionValue scoped = parseDimensionPermissionValue(encodedValue, true);
        if (target == null || SimpleServerUtilities.ECONOMY.isSystemAccount(target) || key.isBlank() || scoped == null) {
            return ActionResult.fail("Player, dimension and permission are required.", "permissions");
        }
        final String normalized;
        try { normalized = PermissionCatalog.normalizeValue(key, scoped.value()); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "permissions"); }
        SimpleServerUtilities.PERMISSIONS.setPlayerDimensionPermission(target, scoped.dimensionId(), key, normalized);
        refreshPermissionDependants(actor.level().getServer());
        return ActionResult.ok("Player dimension permission updated.", "permissions");
    }

    private ActionResult unsetPlayerDimensionPermission(
            ServerPlayer actor, String playerReference, String key, String dimensionId
    ) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        UUID target = resolvePlayerId(actor.level().getServer(), playerReference);
        String dimension = validDimensionId(dimensionId);
        if (target == null || key.isBlank() || dimension == null) {
            return ActionResult.fail("Player, dimension and permission are required.", "permissions");
        }
        boolean removed = SimpleServerUtilities.PERMISSIONS.removePlayerDimensionPermission(target, dimension, key);
        refreshPermissionDependants(actor.level().getServer());
        return ActionResult.ok(removed ? "Player dimension permission reset to fallback."
                : "Permission already used its fallback value.", "permissions");
    }

    private ActionResult setRankDimensionPermission(
            ServerPlayer actor, String rankName, String key, String encodedValue
    ) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        DimensionPermissionValue scoped = parseDimensionPermissionValue(encodedValue, true);
        if (SimpleServerUtilities.PERMISSIONS.getRank(rankName) == null || key.isBlank() || scoped == null) {
            return ActionResult.fail("Rank, dimension and permission are required.", "permissions");
        }
        final String normalized;
        try { normalized = PermissionCatalog.normalizeValue(key, scoped.value()); }
        catch (IllegalArgumentException exception) { return ActionResult.fail(exception.getMessage(), "permissions"); }
        SimpleServerUtilities.PERMISSIONS.setRankDimensionPermission(rankName, scoped.dimensionId(), key, normalized);
        refreshPermissionDependants(actor.level().getServer());
        return ActionResult.ok("Rank dimension permission updated.", "permissions");
    }

    private ActionResult unsetRankDimensionPermission(
            ServerPlayer actor, String rankName, String key, String dimensionId
    ) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        String dimension = validDimensionId(dimensionId);
        if (SimpleServerUtilities.PERMISSIONS.getRank(rankName) == null || key.isBlank() || dimension == null) {
            return ActionResult.fail("Rank, dimension and permission are required.", "permissions");
        }
        boolean removed = SimpleServerUtilities.PERMISSIONS.removeRankDimensionPermission(rankName, dimension, key);
        refreshPermissionDependants(actor.level().getServer());
        return ActionResult.ok(removed ? "Rank dimension permission reset to global/inherited."
                : "Permission already used its fallback value.", "permissions");
    }

    private static DimensionPermissionValue parseDimensionPermissionValue(String encoded, boolean requireValue) {
        if (encoded == null) return null;
        int split = encoded.indexOf('\n');
        String dimension = split < 0 ? encoded : encoded.substring(0, split);
        String value = split < 0 ? "" : encoded.substring(split + 1);
        dimension = validDimensionId(dimension);
        if (dimension == null || requireValue && value.isBlank()) return null;
        return new DimensionPermissionValue(dimension, value);
    }

    private static String validDimensionId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Identifier.parse(raw.trim()).toString(); }
        catch (Exception exception) { return null; }
    }

    private static void refreshPermissionDependants(MinecraftServer server) {
        SimpleServerUtilities.PERMISSIONS.invalidateResolutionCache();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(server);
        BlockInformationService.syncAll(server);
    }

    private ActionResult setRegionPermission(ServerPlayer actor, String regionName, String key, String value) {
        Region region = SimpleServerUtilities.REGIONS.get(regionName);
        if (region == null) return ActionResult.fail("Region not found.", "region_permissions");
        if (!canEditRegionPermissions(actor, region)) {
            return ActionResult.fail("Region permission administration denied.", "region_permissions");
        }
        if (key == null || key.isBlank()) return ActionResult.fail("A permission key is required.", "region_permissions");
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        final String normalizedValue;
        try {
            normalizedValue = PermissionCatalog.normalizeValue(normalizedKey, value);
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "region_permissions");
        }
        region.setPermissionOverride(normalizedKey, normalizedValue);
        SimpleServerUtilities.REGIONS.save();
        SimpleServerUtilities.PERMISSIONS.invalidateResolutionCache();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return ActionResult.ok("Region permission updated.", "region_permissions");
    }

    private ActionResult unsetRegionPermission(ServerPlayer actor, String regionName, String key) {
        Region region = SimpleServerUtilities.REGIONS.get(regionName);
        if (region == null) return ActionResult.fail("Region not found.", "region_permissions");
        if (!canEditRegionPermissions(actor, region)) {
            return ActionResult.fail("Region permission administration denied.", "region_permissions");
        }
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (normalizedKey.isBlank()) return ActionResult.fail("A permission key is required.", "region_permissions");
        boolean existed = region.getPermissionOverrides().containsKey(normalizedKey);
        region.removePermissionOverride(normalizedKey);
        SimpleServerUtilities.REGIONS.save();
        SimpleServerUtilities.PERMISSIONS.invalidateResolutionCache();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return ActionResult.ok(existed ? "Region permission reset to player/rank fallback."
                : "Permission already used the player/rank fallback.", "region_permissions");
    }


    private ActionResult cancelJob(ServerPlayer player, String rawId) {
        if (!isAdministrator(player) || !PermissionService.getBoolean(player, PermissionKeys.CORE_ADMIN, false))
            return ActionResult.fail("Core administration denied.", "jobs");
        try { return SimpleServerUtilities.JOBS.cancel(UUID.fromString(rawId))
                ? ActionResult.ok("Job cancelled.", "jobs") : ActionResult.fail("Job not found.", "jobs"); }
        catch (IllegalArgumentException e) { return ActionResult.fail("Invalid job id.", "jobs"); }
    }

    private ActionResult resetCoreCounters(ServerPlayer player) {
        if (!isAdministrator(player) || !PermissionService.getBoolean(player, PermissionKeys.CORE_ADMIN, false))
            return ActionResult.fail("Core administration denied.", "jobs");
        SimpleServerUtilities.PERFORMANCE.reset();
        return ActionResult.shell("Performance counters reset.");
    }

    private SsuMenuSnapshotPayload.EconomySummary buildEconomySummary(ServerPlayer player, boolean administrator) {
        boolean canUse = PermissionService.getBoolean(player, PermissionKeys.ECONOMY_USE, true);
        boolean canAdmin = administrator && PermissionService.getBoolean(player, PermissionKeys.ECONOMY_ADMIN, false);
        boolean enabled = SimpleServerUtilities.ECONOMY.isEnabled() && (canUse || canAdmin);
        boolean canViewBalance = canUse && PermissionService.getBoolean(player, PermissionKeys.ECONOMY_BALANCE, true);
        boolean canPay = canUse && enabled && PermissionService.getBoolean(player, PermissionKeys.ECONOMY_PAY, true);
        if (!enabled) {
            return new SsuMenuSnapshotPayload.EconomySummary(
                    false, "Economy unavailable", 0L, false, canAdmin,
                    0, "", 0, 100, 0, 50, List.of()
            );
        }
        var account = SimpleServerUtilities.ECONOMY.ensureAccount(player);
        var settings = SimpleServerUtilities.ECONOMY.settings();
        var statistics = SimpleServerUtilities.ECONOMY.statistics();
        return new SsuMenuSnapshotPayload.EconomySummary(
                true,
                canViewBalance ? MoneyFormat.format(account.getBalanceMinor(), settings) : "Balance hidden",
                canViewBalance ? account.getBalanceMinor() : 0L,
                canPay,
                canAdmin,
                canAdmin ? statistics.accounts() : 0,
                canAdmin ? MoneyFormat.format(statistics.totalSupplyMinor(), settings) : "",
                SimpleServerUtilities.REGIONS.rentEconomySettings().getPlayerCancelRefundPercent(),
                SimpleServerUtilities.REGIONS.rentEconomySettings().getAdminCancelRefundPercent(),
                SimpleServerUtilities.REGION_RENT_JOURNAL.pendingCount(),
                settings.getRecentHistoryLimit(),
                List.of()
        );
    }

    private List<PlayerClaim> ownedClaims(ServerPlayer player) {
        if (!PermissionService.getBoolean(player, PermissionKeys.CLAIMS_USE, true)) return List.of();
        return SimpleServerUtilities.PLAYER_CLAIMS.getClaims().stream().filter(claim -> claim.isOwner(player.getUUID()))
                .sorted(Comparator.comparing(PlayerClaim::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private List<Region> visibleRegions(ServerPlayer player, boolean administrator) {
        return SimpleServerUtilities.REGIONS.getAll().stream().filter(region -> administrator
                        || region.isManager(player.getUUID())
                        || region.getRentData().isRentable()
                        || player.getUUID().equals(region.getRentData().getRenter()))
                .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private SsuMenuPageDataPayload.RegionEntry regionEntry(ServerPlayer player, Region region,
            boolean administrator) {
        var rent = region.getRentData(); boolean own = player.getUUID().equals(rent.getRenter());
        return new SsuMenuPageDataPayload.RegionEntry(region.getName(), region.getDimension().identifier().toString(),
                region.getBoundsText(), region.isBorderVisible(), rent.isRented(), rent.isRentable(), own,
                MoneyFormat.format(rent.getPriceMinor(SimpleServerUtilities.ECONOMY.settings()), SimpleServerUtilities.ECONOMY.settings()),
                rent.isPermanent() ? "permanent" : rent.getPeriodDays() + " day(s)", administrator || own ? rent.getDisplayRenterName() : "",
                formatRemaining(rent), region.getManagers().size(), region.getMembers().size(),
                displayNames(player.level().getServer(), region.getManagers()), displayNames(player.level().getServer(), region.getMembers()),
                regionFlags(region), rentPolicy(region), region.getPriority(), region.getVolume(), region.getSpawnPos() != null,
                blockPos(region.getSpawnPos()), SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName()),
                SimpleServerUtilities.JOBS.isResourceLocked(SsuJobLocks.region(region.getDimension(), region.getName())));
    }

    private SsuMenuPageDataPayload.TransactionEntry transactionEntry(EconomyTransactionRecord record) {
        return new SsuMenuPageDataPayload.TransactionEntry(record.getTransactionId() == null ? "" : record.getTransactionId().toString(),
                record.getType().name().toLowerCase(Locale.ROOT), record.getStatus().name().toLowerCase(Locale.ROOT),
                MoneyFormat.format(record.getAmountMinor(), SimpleServerUtilities.ECONOMY.settings()), record.getSourceName(),
                record.getDestinationName(), record.getActorName(), record.getModule(), record.getReason(), record.getFailureMessage(),
                record.getCreatedAtEpochMilli(), record.getCompletedAtEpochMilli());
    }

    private static String transactionSearch(EconomyTransactionRecord record) {
        return (record.getType() + " " + record.getStatus() + " " + record.getSourceName() + " "
                + record.getDestinationName() + " " + record.getActorName() + " " + record.getReason()).toLowerCase(Locale.ROOT);
    }

    private static String majorPlain(long minor) {
        return BigDecimal.valueOf(minor, SimpleServerUtilities.ECONOMY.settings().getDecimalPlaces())
                .stripTrailingZeros().toPlainString();
    }

    private static String decimalPlain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String durationText(long millis) {
        long minutes = Math.max(1L, Duration.ofMillis(Math.max(0L, millis)).toMinutes());
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long rest = minutes % 60L;
        if (days > 0L) return days + " day(s) " + hours + " hour(s)";
        if (hours > 0L) return hours + " hour(s) " + rest + " minute(s)";
        return rest + " minute(s)";
    }

    private static TravelQuery parseTravelQuery(String rawQuery, Set<String> allowedFilters) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        int separator = query.indexOf('|');
        String requestedFilter = separator < 0 ? "all" : query.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String search = separator < 0 ? query : query.substring(separator + 1).trim();
        String filter = allowedFilters.contains(requestedFilter) ? requestedFilter : "all";
        return new TravelQuery(filter, search);
    }

    private static boolean travelFilterMatches(String filter, String kind) {
        if ("all".equals(filter)) {
            return true;
        }
        if ("other".equals(filter)) {
            return !"home".equals(kind) && !"warp".equals(kind);
        }
        return filter.equals(kind);
    }

    private static TransactionQuery parseTransactionQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery;
        String[] parts = query.split("\\|", 3);
        String selected = parts.length > 0 ? parts[0].trim() : "";
        String manual = parts.length > 1 ? parts[1].trim() : "";
        String search = parts.length > 2 ? parts[2].trim() : "";
        return new TransactionQuery(selected, manual, search);
    }

    private static String formatPercentPermille(int permille) {
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(Math.max(0, Math.min(1_000, permille)), 1)
                .stripTrailingZeros();
        return value.toPlainString();
    }

    private record TransactionQuery(String selectedPlayer, String manualPlayer, String search) {}

    private record TravelQuery(String filter, String search) {}

    private static SsuMenuPageDataPayload.LocationEntry location(ServerSpawn spawn) {
        return new SsuMenuPageDataPayload.LocationEntry("spawn", "Server Spawn", spawn.getDimension(),
                spawn.getX(), spawn.getY(), spawn.getZ());
    }

    private static SsuMenuPageDataPayload.LocationEntry location(String kind, PlayerHome home) {
        return new SsuMenuPageDataPayload.LocationEntry(kind, home.getDisplayName(), home.getDimension(), home.getX(), home.getY(), home.getZ());
    }
    private static SsuMenuPageDataPayload.LocationEntry location(String kind, Warp warp) {
        return new SsuMenuPageDataPayload.LocationEntry(kind, warp.getDisplayName(), warp.getDimension(), warp.getX(), warp.getY(), warp.getZ());
    }

    private static String claimFlags(PlayerClaim claim) {
        var s = claim.getSettings();
        List<String> flags = new ArrayList<>();
        if (s.isAllowPvp()) flags.add("pvp"); if (s.isAllowExplosions()) flags.add("explosions");
        if (s.isAllowPistons()) flags.add("pistons"); if (s.isAllowRedstone()) flags.add("redstone");
        if (s.isAllowHoppers()) flags.add("hoppers"); if (s.isAllowFireSpread()) flags.add("fire");
        return flags.isEmpty() ? "protected" : String.join(", ", flags);
    }

    private static String regionFlags(Region region) {
        var s = region.getSettings();
        List<String> flags = new ArrayList<>();
        if (s.isAllowBlockBreak()) flags.add("break"); if (s.isAllowBlockPlace()) flags.add("place");
        if (s.isAllowInteract()) flags.add("interact"); if (s.isAllowPvp()) flags.add("pvp");
        if (s.isAllowExplosions()) flags.add("explosions"); if (s.isAllowPistons()) flags.add("pistons");
        if (s.isAllowWaterFlow()) flags.add("water"); if (s.isAllowLavaFlow()) flags.add("lava");
        if (s.isAllowRedstone()) flags.add("redstone"); if (s.isAllowHoppers()) flags.add("hoppers");
        if (s.isAllowFireSpread()) flags.add("fire");
        if (!region.getPermissionOverrides().isEmpty()) flags.add(region.getPermissionOverrides().size() + " permission override(s)");
        return flags.isEmpty() ? "fully protected" : String.join(", ", flags);
    }

    private static String rentPolicy(Region region) {
        var rent = region.getRentData();
        return "reset expiry=" + rent.isResetOnExpire() + ", reset cancel=" + rent.isResetOnUnrent()
                + ", sequence=" + rent.getRentalSequence();
    }

    private static String blockPos(net.minecraft.core.BlockPos pos) {
        return pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String displayName(MinecraftServer server, UUID id) {
        if (id == null) return "unknown";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getName().getString();
        PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(id);
        if (data != null && !data.getLastKnownName().isBlank()) return data.getLastKnownName();
        return id.toString().substring(0, 8);
    }

    private static String displayNames(MinecraftServer server, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (UUID id : ids.stream().sorted().limit(12).toList()) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                names.add(online.getName().getString());
                continue;
            }
            PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(id);
            if (data != null && !data.getLastKnownName().isBlank()) names.add(data.getLastKnownName());
            else names.add(id.toString().substring(0, 8));
        }
        if (ids.size() > names.size()) names.add("+" + (ids.size() - names.size()) + " more");
        return String.join(", ", names);
    }

    private static String formatRemaining(be.winnetrie.mod.simpleserverutilities.region.RegionRentData rent) {
        if (!rent.isRented()) return ""; if (rent.isPermanent()) return "permanent"; if (rent.isRentPaused()) return "paused";
        long remaining=Math.max(0L,rent.getRentEndTime()-System.currentTimeMillis()); long days=remaining/86_400_000L;
        long hours=(remaining%86_400_000L)/3_600_000L; if(days>0L)return days+"d "+hours+"h";
        return hours+"h "+((remaining%3_600_000L)/60_000L)+"m";
    }

    private static boolean strictBoolean(String raw) {
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        throw new IllegalArgumentException("Boolean values must be true or false.");
    }
    private static int percent(String raw) { try { int v=Integer.parseInt(raw.trim()); if(v<0||v>100)throw new NumberFormatException(); return v; }
        catch(Exception e){throw new IllegalArgumentException("Percentages must be between 0 and 100.");}}
    private static UUID resolvePlayerId(MinecraftServer server,String reference){
        if(reference==null||reference.isBlank())return null;
        try{return UUID.fromString(reference.trim());}catch(IllegalArgumentException ignored){}
        ServerPlayer online=server.getPlayerList().getPlayerByName(reference);
        if(online!=null)return online.getUUID();return SimpleServerUtilities.PERMISSIONS.findKnownPlayerId(reference);}
    private static ServerLevel level(MinecraftServer server,String raw){try{return server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(raw)));}
        catch(Exception e){return null;}}
    private static boolean isAdministrator(ServerPlayer player){return PermissionService.isAdmin(player)
            || PermissionService.getBoolean(player,PermissionKeys.ADMIN_MENU,false);}
    private static boolean canEconomyAdmin(ServerPlayer p){return isAdministrator(p)&&PermissionService.getBoolean(p,PermissionKeys.ECONOMY_ADMIN,false);}
    private static boolean canPermissionAdmin(ServerPlayer p){return isAdministrator(p)&&PermissionService.getBoolean(p,PermissionKeys.PERMISSIONS_ADMIN,false);}
    private static boolean canHologramAdmin(ServerPlayer p){return isAdministrator(p)&&Config.ENABLE_HOLOGRAMS.get()
            && PermissionService.getBoolean(p,PermissionKeys.HOLOGRAMS_ADMIN,false);}
    private static boolean canEditRegionPermissions(ServerPlayer player, Region region) {
        return region != null && (RegionPolicy.canEditRegion(player) || RegionPolicy.isRegionAdmin(player));
    }

    private static <T> List<T> filter(Collection<T> input,String query,java.util.function.Function<T,String> text){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);return input.stream()
                .filter(v->q.isBlank()||text.apply(v).toLowerCase(Locale.ROOT).contains(q)).toList();}
    private static <T> List<T> page(List<T> all, SsuMenuPageRequestPayload request) {
        long requestedFrom = (long) request.pageIndex() * (long) request.pageSize();
        int from = (int) Math.min((long) all.size(), requestedFrom);
        int to = (int) Math.min((long) all.size(), (long) from + request.pageSize());
        return List.copyOf(all.subList(from, to));
    }
    private static SsuMenuPageDataPayload denied(SsuMenuPageRequestPayload r){return SsuMenuPageDataPayload.empty(r.page(),r.pageIndex(),r.pageSize(),r.requestId(),"You do not have permission to view this page.",true);}
    private static SsuMenuPageDataPayload data(SsuMenuPageRequestPayload r,int total,
            List<SsuMenuPageDataPayload.ClaimEntry> claims,List<SsuMenuPageDataPayload.LocationEntry> locations,
            List<SsuMenuPageDataPayload.RegionEntry> regions,List<SsuMenuPageDataPayload.TransactionEntry> transactions,
            List<SsuMenuPageDataPayload.AccountEntry> accounts,List<SsuMenuPageDataPayload.JobEntry> jobs,
            List<SsuMenuPageDataPayload.RentOperationEntry> rentOps,List<SsuMenuPageDataPayload.PermissionEntry> permissions){
        return data(r, total, claims, locations, regions, transactions, accounts, jobs, rentOps, permissions, List.of());
    }
    private static SsuMenuPageDataPayload data(SsuMenuPageRequestPayload r,int total,
            List<SsuMenuPageDataPayload.ClaimEntry> claims,List<SsuMenuPageDataPayload.LocationEntry> locations,
            List<SsuMenuPageDataPayload.RegionEntry> regions,List<SsuMenuPageDataPayload.TransactionEntry> transactions,
            List<SsuMenuPageDataPayload.AccountEntry> accounts,List<SsuMenuPageDataPayload.JobEntry> jobs,
            List<SsuMenuPageDataPayload.RentOperationEntry> rentOps,List<SsuMenuPageDataPayload.PermissionEntry> permissions,
            List<SsuMenuPageDataPayload.StatisticEntry> statistics){
        return new SsuMenuPageDataPayload(r.page(),r.pageIndex(),r.pageSize(),total,r.requestId(),"",false,
                claims,locations,regions,transactions,accounts,jobs,rentOps,permissions,statistics);}

    private record DimensionPermissionValue(String dimensionId, String value) {}

    private record PermissionView(String directValue, String effectiveValue, String defaultValue, String source) {}

    private record ActionResult(boolean success,String message,String refreshPage,boolean refreshShell){
        static ActionResult ok(String m,String p){return new ActionResult(true,m,p,false);} static ActionResult fail(String m,String p){return new ActionResult(false,m,p,false);}
        static ActionResult shell(String m){return new ActionResult(true,m,"",true);} static ActionResult shellPage(String m,String p){return new ActionResult(true,m,p,true);}}
}
