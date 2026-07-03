package be.winnetrie.mod.simpleserverutilities.permission.policy;

import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;

public class TeleportPolicy {

    private TeleportPolicy() {
    }

    public static TeleportOptions resolve(ServerPlayer player, TeleportType type, PermissionContext context) {
        String delayKey = switch (type) {
            case HOME -> PermissionKeys.HOMES_TELEPORT_DELAY;
            case WARP -> PermissionKeys.WARPS_TELEPORT_DELAY;
            case CLAIM, REGION -> "ssu." + type.name().toLowerCase() + ".teleport.delay";
        };

        String cooldownKey = switch (type) {
            case HOME -> PermissionKeys.HOMES_TELEPORT_COOLDOWN;
            case WARP -> PermissionKeys.WARPS_TELEPORT_COOLDOWN;
            case CLAIM, REGION -> "ssu." + type.name().toLowerCase() + ".teleport.cooldown";
        };

        int delay = PermissionService.getInt(player, delayKey, 0, context);
        int cooldown = PermissionService.getInt(player, cooldownKey, 0, context);

        if (PermissionService.getBoolean(player, PermissionKeys.TELEPORT_DELAY_BYPASS, false, context)) {
            delay = 0;
        }

        if (PermissionService.getBoolean(player, PermissionKeys.TELEPORT_COOLDOWN_BYPASS, false, context)) {
            cooldown = 0;
        }

        boolean cancelOnMove = PermissionService.getBoolean(
                player,
                PermissionKeys.TELEPORT_CANCEL_ON_MOVE,
                true,
                context
        );

        return new TeleportOptions(delay, cooldown, cancelOnMove);
    }
}
