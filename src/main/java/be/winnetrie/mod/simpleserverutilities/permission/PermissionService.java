package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class PermissionService {

    /**
     * Legacy names kept so older code keeps compiling while modules move to PermissionKeys.
     */
    public static final String CLAIM_CREATE = PermissionKeys.CLAIMS_CREATE;
    public static final String CLAIM_DELETE = PermissionKeys.CLAIMS_DELETE;
    public static final String CLAIM_BYPASS = PermissionKeys.CLAIMS_ADMIN_BYPASS;

    public static final String REGION_CREATE = PermissionKeys.REGIONS_CREATE;
    public static final String REGION_DELETE = PermissionKeys.REGIONS_DELETE;
    public static final String REGION_EDIT = PermissionKeys.REGIONS_EDIT;

    public static final String WARP_ADMIN = PermissionKeys.WARPS_ADMIN;
    public static final String WARP_USE = PermissionKeys.WARPS_USE;

    private PermissionService() {
    }

    public static boolean has(ServerPlayer player, String permission) {
        return has(player, permission, PermissionContext.global(player));
    }

    public static boolean has(ServerPlayer player, String permission, PermissionContext context) {
        return getBoolean(player, permission, getBuiltInDefault(permission), context);
    }

    public static boolean getBoolean(ServerPlayer player, String permission, boolean fallback) {
        return getBoolean(player, permission, fallback, PermissionContext.global(player));
    }

    /**
     * Resolves a server-controlled boolean without the normal operator bypass.
     * Use this for capability gates where an explicit server deny must also
     * apply while testing as an operator.
     */
    public static boolean getBooleanWithoutOperatorBypass(
            ServerPlayer player,
            String permission,
            boolean fallback
    ) {
        return getBooleanWithoutOperatorBypass(player, permission, fallback, PermissionContext.global(player));
    }

    public static boolean getBooleanWithoutOperatorBypass(
            ServerPlayer player,
            String permission,
            boolean fallback,
            PermissionContext context
    ) {
        if (isJailed(player)) {
            return false;
        }
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
            return fallback;
        }

        String resolvedValue = SimpleServerUtilities.PERMISSIONS.resolveValue(player, permission, context);
        if (resolvedValue != null) {
            Boolean value = parseBoolean(resolvedValue);
            if (value != null) {
                return value;
            }
        }
        return fallback;
    }

    /**
     * Resolves the player's actual configured permission while jailed, without granting operator bypass.
     * This is intentionally narrow and is used only by explicitly permitted Jail gameplay such as
     * a task that happens to be inside a Mine. Jail restrictions still gate every other action.
     */
    public static boolean getBooleanForJailGameplay(ServerPlayer player, String permission, boolean fallback) {
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) return fallback;
        String resolvedValue = SimpleServerUtilities.PERMISSIONS.resolveValue(player, permission, PermissionContext.global(player));
        if (resolvedValue != null) {
            Boolean value = parseBoolean(resolvedValue);
            if (value != null) return value;
        }
        return fallback;
    }

    public static boolean getBoolean(ServerPlayer player, String permission, boolean fallback, PermissionContext context) {
        if (isJailed(player)) {
            return false;
        }
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
            return fallback;
        }

        if (isAdmin(player)) {
            return true;
        }

        String resolvedValue = SimpleServerUtilities.PERMISSIONS.resolveValue(player, permission, context);

        if (resolvedValue != null) {
            Boolean value = parseBoolean(resolvedValue);

            if (value != null) {
                return value;
            }
        }

        return fallback;
    }

    public static int getInt(ServerPlayer player, String permission, int fallback) {
        return getInt(player, permission, fallback, PermissionContext.global(player));
    }

    public static int getInt(ServerPlayer player, String permission, int fallback, PermissionContext context) {
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
            return fallback;
        }

        String resolvedValue = SimpleServerUtilities.PERMISSIONS.resolveValue(player, permission, context);

        if (resolvedValue != null) {
            try {
                return Integer.parseInt(resolvedValue.trim());
            } catch (NumberFormatException ignored) {
                // Invalid configured value: use fallback.
            }
        }

        return fallback;
    }

    public static String getString(ServerPlayer player, String permission, String fallback) {
        return getString(player, permission, fallback, PermissionContext.global(player));
    }

    public static String getString(ServerPlayer player, String permission, String fallback, PermissionContext context) {
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
            return fallback;
        }

        String resolvedValue = SimpleServerUtilities.PERMISSIONS.resolveValue(player, permission, context);

        if (resolvedValue != null) {
            return resolvedValue;
        }

        return fallback;
    }

    public static boolean isAdmin(ServerPlayer player) {
        if (player == null || isJailed(player)) {
            return false;
        }

        MinecraftServer server = player.level().getServer();

        if (server == null) {
            return false;
        }

        return server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }

    private static boolean isJailed(ServerPlayer player) {
        return player != null && SimpleServerUtilities.MODERATION != null
                && SimpleServerUtilities.MODERATION.jailed(player.getUUID());
    }

    public static boolean getBuiltInDefault(String permission) {
        return switch (permission) {
            case PermissionKeys.CLAIMS_USE,
                    PermissionKeys.CLAIMS_CREATE,
                    PermissionKeys.CLAIMS_DELETE,
                    PermissionKeys.CLAIMS_TRUST,
                    PermissionKeys.CLAIMS_FLAGS,
                    PermissionKeys.CLAIMS_MAP,
                    PermissionKeys.CLAIMS_TELEPORT,
                    PermissionKeys.HOMES_USE,
                    PermissionKeys.HOMES_SET,
                    PermissionKeys.HOMES_DELETE,
                    PermissionKeys.HOMES_TELEPORT,
                    PermissionKeys.WARPS_USE,
                    PermissionKeys.WARPS_TELEPORT,
                    PermissionKeys.SPAWN_USE,
                    PermissionKeys.TELEPORT_ESCAPE,
                    PermissionKeys.TELEPORT_REQUIRE_STILL,
                    PermissionKeys.TELEPORT_CANCEL_ON_MOVE,
                    PermissionKeys.REGIONS_RENT,
                    PermissionKeys.ECONOMY_USE,
                    PermissionKeys.ECONOMY_BALANCE,
                    PermissionKeys.ECONOMY_PAY,
                    PermissionKeys.ECONOMY_HISTORY,
                    PermissionKeys.SETTINGS_USE,
                    PermissionKeys.MINIMAP_USE,
                    PermissionKeys.MAIL_ACCESS,
                    PermissionKeys.MAIL_SEND,
                    PermissionKeys.MAIL_SEND_ITEMS,
                    PermissionKeys.MAIL_SEND_MONEY,
                    PermissionKeys.AUCTION_HOUSE_ACCESS,
                    PermissionKeys.AUCTION_HOUSE_DASHBOARD,
                    PermissionKeys.NPCS_USE,
                    PermissionKeys.NPCS_INTERACT,
                    PermissionKeys.QUESTS_USE,
                    PermissionKeys.QUESTS_TRACK,
                    PermissionKeys.QUESTS_ABANDON,
                    PermissionKeys.MINIGAMES_USE,
                    PermissionKeys.MINIGAMES_QUEUE,
                    PermissionKeys.DUNGEONS_USE,
                    PermissionKeys.DUNGEONS_QUEUE,
                    PermissionKeys.KITS_USE,
                    PermissionKeys.CROPS_HARVESTING_USE,
                    PermissionKeys.TREECAPITATOR_USE,
                    PermissionKeys.TREECAPITATOR_BLOCKS,
                    PermissionKeys.VEINMINER_USE,
                    PermissionKeys.VEINMINER_ORE_COAL -> true;
            default -> false;
        };
    }

    private static Boolean parseBoolean(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim().toLowerCase(Locale.ROOT);

        if (value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("allow")) {
            return true;
        }

        if (value.equals("false") || value.equals("no") || value.equals("0") || value.equals("deny")) {
            return false;
        }

        return null;
    }
}
