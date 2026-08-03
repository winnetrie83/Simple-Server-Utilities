package be.winnetrie.mod.simpleserverutilities.permission.policy;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionValueResolver;
import net.minecraft.server.level.ServerPlayer;

/** Shared policy for every player-initiated SSU teleport. */
public final class TeleportPolicy {

    private TeleportPolicy() {
    }

    public static TeleportOptions resolve(ServerPlayer player, TeleportType type, PermissionContext context) {
        String delayKey = switch (type) {
            case HOME -> PermissionKeys.HOMES_TELEPORT_DELAY;
            case WARP -> PermissionKeys.WARPS_TELEPORT_DELAY;
            case SPAWN -> PermissionKeys.SPAWN_TELEPORT_DELAY;
            case CLAIM -> PermissionKeys.CLAIMS_TELEPORT_DELAY;
            case REGION -> PermissionKeys.REGIONS_TELEPORT_DELAY;
        };

        String cooldownKey = switch (type) {
            case HOME -> PermissionKeys.HOMES_TELEPORT_COOLDOWN;
            case WARP -> PermissionKeys.WARPS_TELEPORT_COOLDOWN;
            case SPAWN -> PermissionKeys.SPAWN_TELEPORT_COOLDOWN;
            case CLAIM -> PermissionKeys.CLAIMS_TELEPORT_COOLDOWN;
            case REGION -> PermissionKeys.REGIONS_TELEPORT_COOLDOWN;
        };

        int delay = PermissionService.getInt(player, delayKey, 0, context);
        int cooldown = PermissionService.getInt(player, cooldownKey, 0, context);

        if (PermissionService.getBoolean(player, PermissionKeys.TELEPORT_DELAY_BYPASS, false, context)) {
            delay = 0;
        }

        if (PermissionService.getBoolean(player, PermissionKeys.TELEPORT_COOLDOWN_BYPASS, false, context)) {
            cooldown = 0;
        }

        // New positive key wins when configured. If it is absent, retain the old
        // cancel_on_move value so existing worlds and custom ranks behave identically.
        boolean legacyRequireStill = PermissionService.getBoolean(
                player,
                PermissionKeys.TELEPORT_CANCEL_ON_MOVE,
                true,
                context
        );
        boolean requireStill = PermissionService.getBoolean(
                player,
                PermissionKeys.TELEPORT_REQUIRE_STILL,
                legacyRequireStill,
                context
        );

        return new TeleportOptions(delay, cooldown, requireStill);
    }

    /**
     * Resolves general escape permission plus the teleport-type permission.
     * An explicit deny in the effective region is authoritative: personal/rank
     * allows cannot override it. Only ssu.teleport.region_bypass can ignore that
     * region layer, after which dimension/rank/personal policy is still checked.
     */
    public static boolean canTeleport(ServerPlayer player, TeleportType type, PermissionContext context) {
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
            return true;
        }
        PermissionContext resolved = context == null ? PermissionContext.global(player) : context;
        if (!resolveRegionAuthoritative(
                player, PermissionKeys.TELEPORT_ESCAPE, true, resolved, false)) {
            return false;
        }
        return resolveRegionAuthoritative(
                player, type.usePermission(), type.builtInDefault(), resolved,
                type == TeleportType.SPAWN);
    }

    public static String denialMessage(TeleportType type, PermissionContext context) {
        if (context != null && context.getRegion() != null) {
            var overrides = context.getRegion().getPermissionOverrides();
            if (isExplicitDeny(PermissionValueResolver.getValue(overrides, PermissionKeys.TELEPORT_ESCAPE))
                    || isExplicitDeny(PermissionValueResolver.getValue(overrides, type.usePermission()))) {
                return "Teleport cancelled: " + type.displayName()
                        + " are not allowed in region '" + context.getRegion().getName() + "'.";
            }
        }
        // Rank/player dimension overrides are included by PermissionService. Unlike the
        // removed global dimension scope, their exact source is player-specific, so the
        // generic location message below avoids claiming a server-wide dimension deny.
        return "Teleport cancelled: " + type.displayName() + " are not allowed from your current location.";
    }

    private static boolean resolveRegionAuthoritative(
            ServerPlayer player,
            String permission,
            boolean fallback,
            PermissionContext context,
            boolean allowLegacySpawnBypass
    ) {
        if (context.getRegion() != null) {
            String raw = PermissionValueResolver.getValue(
                    context.getRegion().getPermissionOverrides(), permission);
            if (isExplicitDeny(raw)) {
                PermissionContext withoutRegion = withoutRegion(player, context);
                boolean bypass = PermissionService.getBoolean(
                        player, PermissionKeys.TELEPORT_REGION_BYPASS, false, withoutRegion);
                if (!bypass && allowLegacySpawnBypass) {
                    bypass = PermissionService.getBoolean(
                            player, PermissionKeys.SPAWN_REGION_BYPASS, false, withoutRegion);
                }
                if (!bypass) {
                    return false;
                }
                return PermissionService.getBoolean(player, permission, fallback, withoutRegion);
            }
        }
        return PermissionService.getBoolean(player, permission, fallback, context);
    }

    private static PermissionContext withoutRegion(ServerPlayer player, PermissionContext context) {
        PermissionContext.Builder builder = PermissionContext.builder(player)
                .dimension(context.getDimension())
                .position(context.getPosition())
                .action(context.getAction());
        if (context.getPlayerClaim() != null) {
            builder.playerClaim(context.getPlayerClaim());
        }
        return builder.build();
    }

    private static boolean isExplicitDeny(String raw) {
        if (raw == null) {
            return false;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "false", "no", "0", "deny", "off" -> true;
            default -> false;
        };
    }
}
