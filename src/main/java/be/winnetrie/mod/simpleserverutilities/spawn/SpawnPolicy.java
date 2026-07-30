package be.winnetrie.mod.simpleserverutilities.spawn;

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
        return TeleportPolicy.canTeleport(player, TeleportType.SPAWN, context);
    }

    public static boolean canAdmin(ServerPlayer player) {
        return PermissionService.getBoolean(player, PermissionKeys.SPAWN_ADMIN, false);
    }
}
