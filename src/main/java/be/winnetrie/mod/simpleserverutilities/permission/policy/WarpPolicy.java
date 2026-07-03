package be.winnetrie.mod.simpleserverutilities.permission.policy;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;

public class WarpPolicy {

    private WarpPolicy() {
    }

    public static boolean canUseWarps(ServerPlayer player) {
        return canUseWarps(player, PermissionContext.global(player));
    }

    public static boolean canUseWarps(ServerPlayer player, PermissionContext context) {
        return Config.ENABLE_WARPS.get()
                && PermissionService.getBoolean(player, PermissionKeys.WARPS_USE, true, context);
    }

    public static boolean canTeleportWarp(ServerPlayer player, PermissionContext context) {
        return canUseWarps(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.WARPS_TELEPORT, true, context);
    }

    public static boolean canAdminWarps(ServerPlayer player) {
        return canAdminWarps(player, PermissionContext.global(player));
    }

    public static boolean canAdminWarps(ServerPlayer player, PermissionContext context) {
        return Config.ENABLE_WARPS.get()
                && PermissionService.getBoolean(player, PermissionKeys.WARPS_ADMIN, false, context);
    }

    public static boolean canSetWarp(ServerPlayer player) {
        return canSetWarp(player, PermissionContext.global(player));
    }

    public static boolean canSetWarp(ServerPlayer player, PermissionContext context) {
        return canAdminWarps(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.WARPS_SET, true, context);
    }

    public static boolean canDeleteWarp(ServerPlayer player) {
        return canDeleteWarp(player, PermissionContext.global(player));
    }

    public static boolean canDeleteWarp(ServerPlayer player, PermissionContext context) {
        return canAdminWarps(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.WARPS_DELETE, true, context);
    }

    public static boolean canViewWarpInfo(ServerPlayer player) {
        return canViewWarpInfo(player, PermissionContext.global(player));
    }

    public static boolean canViewWarpInfo(ServerPlayer player, PermissionContext context) {
        return canAdminWarps(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.WARPS_INFO, true, context);
    }

    public static int getMaxWarps(ServerPlayer player) {
        return PermissionService.getInt(
                player,
                PermissionKeys.WARPS_MAX,
                Config.MAX_WARPS.get()
        );
    }
}
