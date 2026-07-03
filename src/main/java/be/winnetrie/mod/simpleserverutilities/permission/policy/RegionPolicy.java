package be.winnetrie.mod.simpleserverutilities.permission.policy;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;

public class RegionPolicy {

    private RegionPolicy() {
    }

    public static boolean canUseRegions(ServerPlayer player) {
        return Config.ENABLE_ADMIN_REGIONS.get()
                && PermissionService.getBoolean(player, PermissionKeys.REGIONS_USE, false);
    }

    public static boolean canCreateRegion(ServerPlayer player) {
        return Config.ENABLE_ADMIN_REGIONS.get()
                && PermissionService.getBoolean(player, PermissionKeys.REGIONS_CREATE, false);
    }

    public static boolean canDeleteRegion(ServerPlayer player) {
        return Config.ENABLE_ADMIN_REGIONS.get()
                && PermissionService.getBoolean(player, PermissionKeys.REGIONS_DELETE, false);
    }

    public static boolean canEditRegion(ServerPlayer player) {
        return Config.ENABLE_ADMIN_REGIONS.get()
                && PermissionService.getBoolean(player, PermissionKeys.REGIONS_EDIT, false);
    }

    public static boolean canTeleportRegion(ServerPlayer player) {
        return Config.ENABLE_ADMIN_REGIONS.get()
                && PermissionService.getBoolean(player, PermissionKeys.REGIONS_TELEPORT, false);
    }

    public static boolean isRegionAdmin(ServerPlayer player) {
        return Config.ENABLE_ADMIN_REGIONS.get()
                && PermissionService.getBoolean(player, PermissionKeys.REGIONS_ADMIN, false);
    }
}
