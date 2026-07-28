package be.winnetrie.mod.simpleserverutilities.menu;

import java.util.Comparator;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.home.PlayerHome;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionRecord;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.visualization.PlayerBorderPreferences;
import be.winnetrie.mod.simpleserverutilities.warp.Warp;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SsuMenuService {

    private static final long MAX_MENU_ENTRIES = 4_096L;

    public void open(ServerPlayer player) {
        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        var uiPreferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        boolean administrator = isAdministrator(player);

        List<SsuMenuSnapshotPayload.ClaimSummary> claims = PermissionService.getBoolean(
                player,
                PermissionKeys.CLAIMS_USE,
                true
        )
                ? SimpleServerUtilities.PLAYER_CLAIMS.getClaims()
                        .stream()
                        .filter(claim -> claim.isOwner(player.getUUID()))
                        .sorted(Comparator.comparing(PlayerClaim::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .limit(MAX_MENU_ENTRIES)
                        .map(claim -> new SsuMenuSnapshotPayload.ClaimSummary(
                                claim.getDisplayName(),
                                claim.getDimension(),
                                claim.getChunks().size()
                        ))
                        .toList()
                : List.of();

        List<SsuMenuSnapshotPayload.RegionSummary> regions = SimpleServerUtilities.REGIONS.getAll()
                .stream()
                .filter(region -> administrator
                        || region.getRentData().isRentable()
                        || player.getUUID().equals(region.getRentData().getRenter()))
                .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_MENU_ENTRIES)
                .map(region -> regionSummary(player, region, preferences, administrator))
                .toList();

        List<SsuMenuSnapshotPayload.LocationSummary> homes = PermissionService.getBoolean(
                player,
                PermissionKeys.HOMES_USE,
                true
        )
                ? SimpleServerUtilities.HOMES.getHomes(player.getUUID())
                        .stream()
                        .limit(MAX_MENU_ENTRIES)
                        .map(SsuMenuService::homeSummary)
                        .toList()
                : List.of();

        List<SsuMenuSnapshotPayload.LocationSummary> warps = PermissionService.getBoolean(
                player,
                PermissionKeys.WARPS_USE,
                true
        )
                ? SimpleServerUtilities.WARPS.getWarps()
                        .stream()
                        .limit(MAX_MENU_ENTRIES)
                        .map(SsuMenuService::warpSummary)
                        .toList()
                : List.of();

        var performance = SimpleServerUtilities.PERFORMANCE.snapshot();
        var regionIndex = SimpleServerUtilities.REGIONS.spatialIndexStatistics();
        var coreSummary = new SsuMenuSnapshotPayload.CoreSummary(
                performance.permissionChecks(),
                (int) Math.round(performance.permissionCacheHitRate() * 1000.0D),
                SimpleServerUtilities.PERMISSIONS.cachedResolutionCount(),
                performance.regionLookups(),
                performance.averageRegionCandidates(),
                regionIndex.cells(),
                regionIndex.references()
        );

        var economySummary = buildEconomySummary(player, administrator);

        PacketDistributor.sendToPlayer(player, new SsuMenuSnapshotPayload(
                player.getName().getString(),
                SimpleServerUtilities.PERMISSIONS.getPrimaryRankName(player.getUUID()),
                PermissionService.getBoolean(player, PermissionKeys.SETTINGS_USE, true),
                new SsuMenuSnapshotPayload.UiSettingsSummary(
                        uiPreferences.isDashboardHints(),
                        uiPreferences.isMinimapEnabled(),
                        uiPreferences.getMinimapSize(),
                        uiPreferences.getMinimapShape().name(),
                        uiPreferences.getMinimapPosition().name(),
                        uiPreferences.isMinimapNorthUp(),
                        uiPreferences.isMinimapShowClaims(),
                        uiPreferences.isMinimapShowRegions()
                ),
                administrator,
                preferences.isClaimBordersVisible(),
                preferences.isRegionBordersVisible(),
                PermissionService.getBoolean(player, PermissionKeys.BORDER_CLAIMS_VIEW, true),
                PermissionService.getBoolean(player, PermissionKeys.BORDER_REGIONS_VIEW, true),
                SimpleServerUtilities.JOBS.size(),
                SimpleServerUtilities.STORAGE.statistics().pending(),
                coreSummary,
                economySummary,
                claims,
                regions,
                homes,
                warps
        ));
    }

    private static SsuMenuSnapshotPayload.EconomySummary buildEconomySummary(
            ServerPlayer player,
            boolean administrator
    ) {
        boolean canUse = PermissionService.getBoolean(player, PermissionKeys.ECONOMY_USE, true);
        boolean canAdmin = administrator
                && PermissionService.getBoolean(player, PermissionKeys.ECONOMY_ADMIN, false);
        boolean enabled = SimpleServerUtilities.ECONOMY.isEnabled() && (canUse || canAdmin);
        boolean canViewBalance = canUse
                && PermissionService.getBoolean(player, PermissionKeys.ECONOMY_BALANCE, true);
        boolean canPay = canUse
                && enabled
                && PermissionService.getBoolean(player, PermissionKeys.ECONOMY_PAY, true);

        if (!enabled) {
            return new SsuMenuSnapshotPayload.EconomySummary(
                    false, "Economy unavailable", 0L, false, canAdmin, 0, "",
                    0, 0, 100, 0, List.of()
            );
        }

        var account = SimpleServerUtilities.ECONOMY.ensureAccount(player);
        var settings = SimpleServerUtilities.ECONOMY.settings();
        var statistics = SimpleServerUtilities.ECONOMY.statistics();
        List<SsuMenuSnapshotPayload.TransactionSummary> recent = PermissionService.getBoolean(
                player,
                PermissionKeys.ECONOMY_HISTORY,
                true
        )
                ? SimpleServerUtilities.ECONOMY.history(player.getUUID(), 10)
                        .stream()
                        .map(record -> transactionSummary(player, record))
                        .toList()
                : List.of();

        return new SsuMenuSnapshotPayload.EconomySummary(
                true,
                canViewBalance ? MoneyFormat.format(account.getBalanceMinor(), settings) : "Balance hidden",
                canViewBalance ? account.getBalanceMinor() : 0L,
                canPay,
                canAdmin,
                canAdmin ? statistics.accounts() : 0,
                canAdmin ? MoneyFormat.format(statistics.totalSupplyMinor(), settings) : "",
                SimpleServerUtilities.REGIONS.rentEconomySettings().getOwnerSharePercent(),
                SimpleServerUtilities.REGIONS.rentEconomySettings().getPlayerCancelRefundPercent(),
                SimpleServerUtilities.REGIONS.rentEconomySettings().getAdminCancelRefundPercent(),
                SimpleServerUtilities.REGION_RENT_JOURNAL.pendingCount(),
                recent
        );
    }

    private static SsuMenuSnapshotPayload.TransactionSummary transactionSummary(
            ServerPlayer player,
            EconomyTransactionRecord record
    ) {
        String direction;
        String otherParty;
        if (player.getUUID().equals(record.getSourceId())
                && player.getUUID().equals(record.getDestinationId())) {
            direction = "set";
            otherParty = "";
        } else if (player.getUUID().equals(record.getSourceId())) {
            direction = "out";
            otherParty = record.getDestinationName();
        } else if (player.getUUID().equals(record.getDestinationId())) {
            direction = "in";
            otherParty = record.getSourceName();
        } else {
            direction = "admin";
            otherParty = record.getActorName();
        }

        return new SsuMenuSnapshotPayload.TransactionSummary(
                record.getType().name().toLowerCase(java.util.Locale.ROOT),
                direction,
                MoneyFormat.format(record.getAmountMinor(), SimpleServerUtilities.ECONOMY.settings()),
                otherParty,
                record.getStatus().name().toLowerCase(java.util.Locale.ROOT),
                record.getCreatedAtEpochMilli()
        );
    }

    private static SsuMenuSnapshotPayload.RegionSummary regionSummary(
            ServerPlayer player,
            Region region,
            PlayerBorderPreferences preferences,
            boolean administrator
    ) {
        var rent = region.getRentData();
        boolean rentedByPlayer = player.getUUID().equals(rent.getRenter());
        String period = rent.isPermanent() ? "permanent" : rent.getPeriodDays() + " day(s)";
        String renterName = administrator || rentedByPlayer ? rent.getDisplayRenterName() : "";
        return new SsuMenuSnapshotPayload.RegionSummary(
                region.getName(),
                region.getDimension().identifier().toString(),
                region.getBoundsText(),
                preferences.isRegionPinned(region.getName()),
                rent.isRented(),
                rent.isRentable(),
                rentedByPlayer,
                MoneyFormat.format(rent.getPriceMinor(SimpleServerUtilities.ECONOMY.settings()),
                        SimpleServerUtilities.ECONOMY.settings()),
                period,
                renterName,
                formatRemaining(rent)
        );
    }

    private static String formatRemaining(be.winnetrie.mod.simpleserverutilities.region.RegionRentData rent) {
        if (!rent.isRented()) {
            return "";
        }
        if (rent.isPermanent()) {
            return "permanent";
        }
        if (rent.isRentPaused()) {
            return "paused";
        }
        long remaining = Math.max(0L, rent.getRentEndTime() - System.currentTimeMillis());
        long days = remaining / 86_400_000L;
        long hours = (remaining % 86_400_000L) / 3_600_000L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        long minutes = (remaining % 3_600_000L) / 60_000L;
        return hours + "h " + minutes + "m";
    }

    private static boolean isAdministrator(ServerPlayer player) {
        return PermissionService.isAdmin(player)
                || PermissionService.getBoolean(player, PermissionKeys.ADMIN_MENU, false);
    }

    private static SsuMenuSnapshotPayload.LocationSummary homeSummary(PlayerHome home) {
        return new SsuMenuSnapshotPayload.LocationSummary(
                home.getDisplayName(), home.getDimension(), home.getX(), home.getY(), home.getZ()
        );
    }

    private static SsuMenuSnapshotPayload.LocationSummary warpSummary(Warp warp) {
        return new SsuMenuSnapshotPayload.LocationSummary(
                warp.getDisplayName(), warp.getDimension(), warp.getX(), warp.getY(), warp.getZ()
        );
    }
}
