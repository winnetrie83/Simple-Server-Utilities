package be.winnetrie.mod.simpleserverutilities.spawn;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportType;
import net.minecraft.server.level.ServerPlayer;

/** Permission policy for server-spawn administration and context-aware use. */
public final class SpawnPolicy {

    private SpawnPolicy() {
    }

    public static boolean canUse(ServerPlayer player, PermissionContext context) {
        return SsuModuleAccess.active("spawn") && TeleportPolicy.canTeleport(player, TeleportType.SPAWN, context);
    }

    public static boolean canAdmin(ServerPlayer player) {
        return SsuModuleAccess.active("spawn") && PermissionService.getBoolean(player, PermissionKeys.SPAWN_ADMIN, false);
    }
}
