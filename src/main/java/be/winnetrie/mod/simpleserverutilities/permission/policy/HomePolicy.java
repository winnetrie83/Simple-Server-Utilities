package be.winnetrie.mod.simpleserverutilities.permission.policy;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public class HomePolicy {

    private HomePolicy() {
    }

    public static boolean canUseHomes(ServerPlayer player) {
        return canUseHomes(player, PermissionContext.global(player));
    }

    public static boolean canUseHomes(ServerPlayer player, PermissionContext context) {
        return Config.ENABLE_HOMES.get()
                && PermissionService.getBoolean(player, PermissionKeys.HOMES_USE, true, context);
    }

    public static boolean canSetHome(ServerPlayer player) {
        return canSetHome(player, PermissionContext.global(player));
    }

    public static boolean canSetHome(ServerPlayer player, PermissionContext context) {
        return canUseHomes(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.HOMES_SET, true, context);
    }

    public static boolean canSetHomeAt(ServerPlayer player, BlockPos pos) {
        return canSetHome(player, PermissionContext.at(player, pos));
    }

    public static boolean canTeleportHome(ServerPlayer player, PermissionContext context) {
        return canUseHomes(player, context)
                && TeleportPolicy.canTeleport(player, TeleportType.HOME, context);
    }

    public static boolean canDeleteHome(ServerPlayer player) {
        return canDeleteHome(player, PermissionContext.global(player));
    }

    public static boolean canDeleteHome(ServerPlayer player, PermissionContext context) {
        return canUseHomes(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.HOMES_DELETE, true, context);
    }

    public static int getMaxHomes(ServerPlayer player) {
        return PermissionService.getInt(
                player,
                PermissionKeys.HOMES_MAX,
                Config.MAX_PLAYER_HOMES.get(),
                PermissionContext.global(player)
        );
    }
}
