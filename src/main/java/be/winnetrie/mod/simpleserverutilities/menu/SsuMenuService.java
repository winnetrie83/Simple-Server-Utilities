package be.winnetrie.mod.simpleserverutilities.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapService;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.blockinfo.BlockInformationService;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyAccount;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionRecord;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.home.PlayerHome;
import be.winnetrie.mod.simpleserverutilities.hologram.AdminToolService;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramDefinition;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramEditorService;
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
import be.winnetrie.mod.simpleserverutilities.visualization.PlayerBorderPreferences;
import be.winnetrie.mod.simpleserverutilities.warp.Warp;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
                        uiPreferences.isWorldMarkersVisible(), uiPreferences.isMarkerBeamsVisible(),
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
                        uiPreferences.isVeinminerEnabled(),
                        uiPreferences.getVeinminerActivation().name(),
                        uiPreferences.getVeinminerOutlineColor(),
                        uiPreferences.getVeinminerOutlineBrightness()
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
                preferences.isClaimBordersVisible(), preferences.isRegionBordersVisible(),
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
                case "travel" -> travelPage(player, payload);
                case "regions" -> regionsPage(player, payload);
                case "transactions" -> transactionsPage(player, payload);
                case "accounts" -> accountsPage(player, payload);
                case "jobs" -> jobsPage(player, payload);
                case "rent_operations" -> rentOperationsPage(player, payload);
                case "permissions" -> permissionsPage(player, payload);
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
            case "region_visibility" -> regionVisibility(player, payload.target(), payload.value());
            case "regions_hide" -> regionsHide(player);
            case "region_rent" -> regionRent(player, payload.target(), "rent");
            case "region_extend" -> regionRent(player, payload.target(), "extend");
            case "region_unrent" -> regionRent(player, payload.target(), "unrent");
            case "teleport_home" -> teleportHome(player, payload.target());
            case "teleport_warp" -> teleportWarp(player, payload.target());
            case "teleport_spawn" -> teleportSpawn(player);
            case "spawn_set" -> setServerSpawn(player);
            case "spawn_clear" -> clearServerSpawn(player);
            case "rent_policy" -> rentPolicy(player, payload.target(), payload.value());
            case "economy_give" -> economyAdminMutation(player, payload.target(), payload.value(), "give");
            case "economy_take" -> economyAdminMutation(player, payload.target(), payload.value(), "take");
            case "economy_set" -> economyAdminMutation(player, payload.target(), payload.value(), "set");
            case "permission_assign_rank" -> assignRank(player, payload.target(), payload.value());
            case "permission_player_set" -> setPlayerPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_player_unset" -> unsetPlayerPermission(player, payload.target(), payload.secondary());
            case "permission_rank_set" -> setRankPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_rank_unset" -> unsetRankPermission(player, payload.target(), payload.secondary());
            case "permission_region_set" -> setRegionPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_region_unset" -> unsetRegionPermission(player, payload.target(), payload.secondary());
            case "permission_dimension_set" -> setDimensionPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_dimension_unset" -> unsetDimensionPermission(player, payload.target(), payload.secondary());
            case "permission_set" -> setPlayerPermission(player, payload.target(), payload.secondary(), payload.value());
            case "permission_unset" -> unsetPlayerPermission(player, payload.target(), payload.secondary());
            case "job_cancel" -> cancelJob(player, payload.target());
            case "core_reset" -> resetCoreCounters(player);
            case "admin_tool_get" -> adminToolGet(player, payload.target());
            case "crops_harvesting_toggle" -> cropsHarvestingToggle(player, payload.value());
            case "module_toggle" -> moduleToggle(player, payload.target(), payload.value());
            case "render_distance" -> renderDistance(player, payload.target(), payload.value());
            case "hologram_edit" -> hologramEdit(player, payload.target());
            case "hologram_delete" -> hologramDelete(player, payload.target());
            case "hologram_teleport" -> hologramTeleport(player, payload.target());
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
                yield ActionResult.ok("Region Tool added to your inventory.", "");
            }
            case "hologram" -> {
                if (!Config.ENABLE_HOLOGRAMS.get()
                        || !PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false)) {
                    yield ActionResult.fail("The hologram module is disabled or you lack its admin permission.", "");
                }
                AdminToolService.giveHologramTool(player);
                yield ActionResult.ok("Hologram Tool added. Right-click to create; right-click existing text to edit.", "");
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
                        claim.getTrustedPlayers().size(), false, "",
                        displayNames(player.level().getServer(), claim.getTrustedPlayers()), claimFlags(claim)
                )).toList();
        return data(request, all.size(), entries, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload travelPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        List<SsuMenuPageDataPayload.LocationEntry> all = new ArrayList<>();
        ServerSpawn serverSpawn = SimpleServerUtilities.SERVER_SPAWN.get();
        if (serverSpawn != null) {
            all.add(location(serverSpawn));
        }
        if (PermissionService.getBoolean(player, PermissionKeys.HOMES_USE, true)) {
            SimpleServerUtilities.HOMES.getHomes(player.getUUID()).forEach(home -> all.add(location("home", home)));
        }
        if (PermissionService.getBoolean(player, PermissionKeys.WARPS_USE, true)) {
            SimpleServerUtilities.WARPS.getWarps().forEach(warp -> all.add(location("warp", warp)));
        }
        all.sort(Comparator.comparing(SsuMenuPageDataPayload.LocationEntry::kind)
                .thenComparing(SsuMenuPageDataPayload.LocationEntry::name, String.CASE_INSENSITIVE_ORDER));
        List<SsuMenuPageDataPayload.LocationEntry> filtered = filter(all, request.query(),
                value -> value.kind() + " " + value.name() + " " + value.dimension());
        return data(request, filtered.size(), List.of(), page(filtered, request), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload regionsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        boolean administrator = isAdministrator(player);
        List<Region> all = filter(visibleRegions(player, administrator), request.query(), Region::getName);
        List<SsuMenuPageDataPayload.RegionEntry> entries = page(all, request).stream()
                .map(region -> regionEntry(player, region, administrator)).toList();
        return data(request, all.size(), List.of(), List.of(), entries, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private SsuMenuPageDataPayload transactionsPage(ServerPlayer player, SsuMenuPageRequestPayload request) {
        boolean admin = isAdministrator(player) && PermissionService.getBoolean(player, PermissionKeys.ECONOMY_ADMIN, false);
        if (!admin && !PermissionService.getBoolean(player, PermissionKeys.ECONOMY_HISTORY, true)) return denied(request);
        List<EconomyTransactionRecord> all = SimpleServerUtilities.ECONOMY.history(admin ? null : player.getUUID(), 500);
        String q = request.query().toLowerCase(Locale.ROOT);
        if (!q.isBlank()) all = all.stream().filter(record -> transactionSearch(record).contains(q)).toList();
        List<SsuMenuPageDataPayload.TransactionEntry> entries = page(all, request).stream().map(this::transactionEntry).toList();
        return data(request, all.size(), List.of(), List.of(), List.of(), entries, List.of(), List.of(), List.of(), List.of());
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
        if ("region".equals(request.mode())) {
            return regionPermissionEditorData(viewer, request);
        }
        if (!canPermissionAdmin(viewer)) {
            return SsuPermissionEditorDataPayload.empty(request.mode(), request.requestId(),
                    "You do not have permission to use the permission editor.", true);
        }
        if ("dimension".equals(request.mode())) {
            return dimensionPermissionEditorData(viewer, request);
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
                known.put(entry.playerId(), entry);
            }
            for (EconomyAccount account : SimpleServerUtilities.ECONOMY.accounts()) {
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
                    directValues = Map.copyOf(rank.getPermissions());
                    inheritedValues = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(selectedTarget);
                } else {
                    selectedTarget = "";
                }
            } else {
                UUID selectedId = resolvePlayerId(server, selectedTarget);
                if (selectedId != null) {
                    PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(selectedId);
                    ServerPlayer online = server.getPlayerList().getPlayer(selectedId);
                    selectedTarget = selectedId.toString();
                    String economyName = SimpleServerUtilities.ECONOMY.findAccount(selectedId)
                            .map(EconomyAccount::getLastKnownName).orElse("");
                    selectedLabel = online != null ? online.getName().getString()
                            : data != null && !data.getLastKnownName().isBlank()
                            ? data.getLastKnownName()
                            : !economyName.isBlank() ? economyName : selectedId.toString().substring(0, 8);
                    List<String> assignedRanks = data == null ? List.of() : List.copyOf(data.getRanks());
                    targetSummary = assignedRanks.isEmpty() ? "Uses default rank"
                            : "Assigned rank: " + String.join(", ", assignedRanks);
                    directValues = data == null ? Map.of() : Map.copyOf(data.getPermissions());
                    inheritedValues = SimpleServerUtilities.PERMISSIONS.getEffectiveRankPermissions(selectedId);
                } else {
                    selectedTarget = "";
                }
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
        int requestedFrom = resolvedPageIndex * request.pageSize();
        int from = Math.min(totalPermissions, requestedFrom);
        int to = Math.min(totalPermissions, from + request.pageSize());
        List<SsuPermissionEditorDataPayload.PermissionEntry> permissionEntries = new ArrayList<>();
        if (!selectedTarget.isBlank()) {
            for (PermissionCatalog.Definition definition : definitions.subList(from, to)) {
                PermissionView resolved = resolvePermission(definition, directValues, inheritedValues, moduleDefaults, rankMode);
                permissionEntries.add(new SsuPermissionEditorDataPayload.PermissionEntry(
                        definition.key(), resolved.directValue(), resolved.effectiveValue(), resolved.defaultValue(),
                        resolved.source(), definition.type().name().toLowerCase(Locale.ROOT), definition.description(),
                        definition.minimum(), definition.maximum()
                ));
            }
        }

        return new SsuPermissionEditorDataPayload(
                rankMode ? "rank" : "player", selectedTarget, selectedLabel, targetSummary,
                resolvedPageIndex, request.pageSize(), totalPermissions, request.requestId(), "", false,
                targets, rankOptions, permissionEntries
        );
    }


    private SsuPermissionEditorDataPayload dimensionPermissionEditorData(
            ServerPlayer viewer,
            SsuPermissionEditorRequestPayload request
    ) {
        MinecraftServer server = viewer.level().getServer();
        Map<String, SsuPermissionEditorDataPayload.TargetEntry> known = new LinkedHashMap<>();
        for (String dimensionId : SimpleServerUtilities.PERMISSIONS.getData().getDimensions().keySet()) {
            known.put(dimensionId, new SsuPermissionEditorDataPayload.TargetEntry(
                    dimensionId, dimensionId, "Stored dimension permission scope"));
        }
        for (ServerLevel level : server.getAllLevels()) {
            String dimensionId = level.dimension().identifier().toString();
            known.put(dimensionId, new SsuPermissionEditorDataPayload.TargetEntry(
                    dimensionId, dimensionId, "Loaded dimension"));
        }

        String targetQuery = request.targetQuery().toLowerCase(Locale.ROOT);
        List<SsuPermissionEditorDataPayload.TargetEntry> targets = known.values().stream()
                .sorted(Comparator.comparing(SsuPermissionEditorDataPayload.TargetEntry::label,
                        String.CASE_INSENSITIVE_ORDER))
                .filter(target -> targetQuery.isBlank()
                        || (target.label() + " " + target.summary()).toLowerCase(Locale.ROOT).contains(targetQuery))
                .limit(200)
                .toList();

        String selectedTarget = request.selectedTarget();
        Map<String, String> directValues = Map.of();
        if (!selectedTarget.isBlank()) {
            var scope = SimpleServerUtilities.PERMISSIONS.getData().getDimensions().get(selectedTarget);
            directValues = scope == null ? Map.of() : Map.copyOf(scope.getPermissions());
            if (!known.containsKey(selectedTarget)) {
                selectedTarget = "";
            }
        }

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

        int totalPermissions = selectedTarget.isBlank() ? 0 : definitions.size();
        int pageCount = Math.max(1, (totalPermissions + request.pageSize() - 1) / request.pageSize());
        int resolvedPageIndex = Math.min(request.pageIndex(), pageCount - 1);
        int from = Math.min(totalPermissions, resolvedPageIndex * request.pageSize());
        int to = Math.min(totalPermissions, from + request.pageSize());
        List<SsuPermissionEditorDataPayload.PermissionEntry> entries = new ArrayList<>();
        if (!selectedTarget.isBlank()) {
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
                String effective = resolvedDirect == null || resolvedDirect.isBlank()
                        ? defaultValue : resolvedDirect;
                entries.add(new SsuPermissionEditorDataPayload.PermissionEntry(
                        definition.key(), exactDirect, effective, defaultValue,
                        exactDirect.isBlank() ? "rank/default" : "dimension",
                        definition.type().name().toLowerCase(Locale.ROOT), definition.description(),
                        definition.minimum(), definition.maximum()
                ));
            }
        }

        return new SsuPermissionEditorDataPayload(
                "dimension", selectedTarget, selectedTarget,
                selectedTarget.isBlank() ? "" : "Permissions applied while a player is in this dimension",
                resolvedPageIndex, request.pageSize(), totalPermissions, request.requestId(), "", false,
                targets, List.of(), entries
        );
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
                "region", region.getName(), region.getName(), summary,
                resolvedPageIndex, request.pageSize(), totalPermissions, request.requestId(), "", false,
                List.of(), List.of(), entries
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
            known.put(entry.playerId(), entry);
        }
        for (EconomyAccount account : SimpleServerUtilities.ECONOMY.accounts()) {
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
        Optional<EconomyAccount> target = SimpleServerUtilities.ECONOMY.findAccountByName(player.level().getServer(), targetName);
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
        } else if ("regions".equalsIgnoreCase(target)) {
            if (!Config.ENABLE_ADMIN_REGIONS.get()
                    || !PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_REGIONS_VIEW, true)) {
                return ActionResult.fail("Region borders are not allowed by the server.", "");
            }
            SimpleServerUtilities.BORDER_SETTINGS.setRegionsVisible(player.getUUID(), visible);
        } else return ActionResult.fail("Unknown border layer.", "");
        SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
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
        return ActionResult.ok("Claim border hidden.", "claims");
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

    private ActionResult teleportHome(ServerPlayer player, String name) {
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!HomePolicy.canTeleportHome(player, context)) return ActionResult.fail(TeleportPolicy.denialMessage(TeleportType.HOME, context), "travel");
        PlayerHome home = SimpleServerUtilities.HOMES.getHome(player.getUUID(), name);
        if (home == null) return ActionResult.fail("Home not found.", "travel");
        ServerLevel level = level(player.level().getServer(), home.getDimension());
        if (level == null) return ActionResult.fail("Home dimension is not loaded.", "travel");
        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.HOME, context);
        int result = SimpleServerUtilities.TELEPORTS.requestTeleport(
                player, "homes", "home '" + home.getDisplayName() + "'",
                options, level, home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch(),
                candidate -> HomePolicy.canTeleportHome(candidate,
                        PermissionContext.at(candidate, candidate.blockPosition())),
                candidate -> TeleportPolicy.denialMessage(TeleportType.HOME,
                        PermissionContext.at(candidate, candidate.blockPosition())));
        return result > 0 ? ActionResult.ok("Home teleport requested.", "travel") : ActionResult.fail("Home teleport failed.", "travel");
    }

    private ActionResult teleportWarp(ServerPlayer player, String name) {
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!WarpPolicy.canTeleportWarp(player, context)) return ActionResult.fail(TeleportPolicy.denialMessage(TeleportType.WARP, context), "travel");
        Warp warp = SimpleServerUtilities.WARPS.getWarp(name);
        if (warp == null) return ActionResult.fail("Warp not found.", "travel");
        ServerLevel level = level(player.level().getServer(), warp.getDimension());
        if (level == null) return ActionResult.fail("Warp dimension is not loaded.", "travel");
        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.WARP, context);
        int result = SimpleServerUtilities.TELEPORTS.requestTeleport(
                player, "warps", "warp '" + warp.getDisplayName() + "'",
                options, level, warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch(),
                candidate -> WarpPolicy.canTeleportWarp(candidate,
                        PermissionContext.at(candidate, candidate.blockPosition())),
                candidate -> TeleportPolicy.denialMessage(TeleportType.WARP,
                        PermissionContext.at(candidate, candidate.blockPosition())));
        return result > 0 ? ActionResult.ok("Warp teleport requested.", "travel") : ActionResult.fail("Warp teleport failed.", "travel");
    }


    private ActionResult teleportSpawn(ServerPlayer player) {
        int result = SpawnService.requestTeleport(player);
        return result > 0 ? ActionResult.ok("Server-spawn teleport requested.", "travel")
                : ActionResult.fail("Server-spawn teleport failed.", "travel");
    }

    private ActionResult setServerSpawn(ServerPlayer player) {
        if (!SpawnPolicy.canAdmin(player)) return ActionResult.fail("Server-spawn administration denied.", "travel");
        SimpleServerUtilities.SERVER_SPAWN.set(player);
        return ActionResult.shellPage("Server spawn set to your current position.", "travel");
    }

    private ActionResult clearServerSpawn(ServerPlayer player) {
        if (!SpawnPolicy.canAdmin(player)) return ActionResult.fail("Server-spawn administration denied.", "travel");
        boolean removed = SimpleServerUtilities.SERVER_SPAWN.clear();
        return removed ? ActionResult.shellPage("Server spawn cleared.", "travel")
                : ActionResult.ok("The server spawn was already unset.", "travel");
    }

    private ActionResult rentPolicy(ServerPlayer player, String playerRefund, String adminRefund) {
        if (!canEconomyAdmin(player) || !PermissionService.getBoolean(player, PermissionKeys.REGIONS_RENT_ADMIN, false))
            return ActionResult.fail("Rent policy administration denied.", "transactions");
        try {
            int playerPercent = percent(playerRefund);
            int adminPercent = percent(adminRefund);
            SimpleServerUtilities.REGIONS.rentEconomySettings().setPlayerCancelRefundPermille(playerPercent * 10);
            SimpleServerUtilities.REGIONS.rentEconomySettings().setAdminCancelRefundPermille(adminPercent * 10);
            SimpleServerUtilities.REGIONS.save();
            return ActionResult.shellPage("Rent refund policy updated.", "transactions");
        } catch (IllegalArgumentException e) { return ActionResult.fail(e.getMessage(), "transactions"); }
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


    private ActionResult setDimensionPermission(ServerPlayer actor, String dimensionId, String key, String value) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        if (dimensionId == null || dimensionId.isBlank() || key == null || key.isBlank()) {
            return ActionResult.fail("Dimension and permission are required.", "permissions");
        }
        try {
            Identifier.parse(dimensionId);
        } catch (Exception exception) {
            return ActionResult.fail("Invalid dimension identifier.", "permissions");
        }
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        final String normalizedValue;
        try {
            normalizedValue = PermissionCatalog.normalizeValue(normalizedKey, value);
        } catch (IllegalArgumentException exception) {
            return ActionResult.fail(exception.getMessage(), "permissions");
        }
        SimpleServerUtilities.PERMISSIONS.setDimensionPermission(dimensionId, normalizedKey, normalizedValue);
        SimpleServerUtilities.PERMISSIONS.invalidateResolutionCache();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return ActionResult.ok("Dimension permission updated.", "permissions");
    }

    private ActionResult unsetDimensionPermission(ServerPlayer actor, String dimensionId, String key) {
        if (!canPermissionAdmin(actor)) return ActionResult.fail("Permission administration denied.", "permissions");
        if (dimensionId == null || dimensionId.isBlank() || key == null || key.isBlank()) {
            return ActionResult.fail("Dimension and permission are required.", "permissions");
        }
        boolean removed = SimpleServerUtilities.PERMISSIONS.removeDimensionPermission(
                dimensionId, key.trim().toLowerCase(Locale.ROOT));
        SimpleServerUtilities.PERMISSIONS.invalidateResolutionCache();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(actor.level().getServer());
        BlockInformationService.syncAll(actor.level().getServer());
        return ActionResult.ok(removed ? "Dimension permission reset to rank/default."
                : "Permission already used the rank/default value.", "permissions");
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
                    0, "", 0, 100, 0, List.of()
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

    private record PermissionView(String directValue, String effectiveValue, String defaultValue, String source) {}

    private record ActionResult(boolean success,String message,String refreshPage,boolean refreshShell){
        static ActionResult ok(String m,String p){return new ActionResult(true,m,p,false);} static ActionResult fail(String m,String p){return new ActionResult(false,m,p,false);}
        static ActionResult shell(String m){return new ActionResult(true,m,"",true);} static ActionResult shellPage(String m,String p){return new ActionResult(true,m,p,true);}}
}
