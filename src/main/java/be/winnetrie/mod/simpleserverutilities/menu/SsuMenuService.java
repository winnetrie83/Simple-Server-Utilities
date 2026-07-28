package be.winnetrie.mod.simpleserverutilities.menu;

import java.util.Comparator;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.home.PlayerHome;
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

        List<SsuMenuSnapshotPayload.RegionSummary> regions = administrator
                ? SimpleServerUtilities.REGIONS.getAll()
                        .stream()
                        .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
                        .limit(MAX_MENU_ENTRIES)
                        .map(region -> new SsuMenuSnapshotPayload.RegionSummary(
                                region.getName(),
                                region.getDimension().identifier().toString(),
                                region.getBoundsText(),
                                preferences.isRegionPinned(region.getName()),
                                region.getRentData().isRented()
                        ))
                        .toList()
                : List.of();

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

        PacketDistributor.sendToPlayer(player, new SsuMenuSnapshotPayload(
                administrator,
                preferences.isClaimBordersVisible(),
                preferences.isRegionBordersVisible(),
                PermissionService.getBoolean(player, PermissionKeys.BORDER_CLAIMS_VIEW, true),
                PermissionService.getBoolean(player, PermissionKeys.BORDER_REGIONS_VIEW, true),
                SimpleServerUtilities.JOBS.size(),
                SimpleServerUtilities.STORAGE.statistics().pending(),
                claims,
                regions,
                homes,
                warps
        ));
    }

    private static boolean isAdministrator(ServerPlayer player) {
        return PermissionService.isAdmin(player)
                || PermissionService.getBoolean(player, PermissionKeys.REGIONS_ADMIN, false)
                || PermissionService.getBoolean(player, PermissionKeys.PERMISSIONS_ADMIN, false)
                || PermissionService.getBoolean(player, PermissionKeys.CORE_ADMIN, false)
                || PermissionService.getBoolean(player, PermissionKeys.VISUALIZATION_ADMIN, false);
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
