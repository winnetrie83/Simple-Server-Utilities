package be.winnetrie.mod.simpleserverutilities.permission.policy;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;

public class ClaimPolicy {

    private ClaimPolicy() {
    }

    public static boolean canUseClaims(ServerPlayer player) {
        return canUseClaims(player, PermissionContext.global(player));
    }

    public static boolean canUseClaims(ServerPlayer player, PermissionContext context) {
        return Config.ENABLE_PLAYER_CLAIMS.get()
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_USE, true, context);
    }

    public static boolean canCreateClaim(ServerPlayer player) {
        return canCreateClaim(player, PermissionContext.global(player));
    }

    public static boolean canCreateClaim(ServerPlayer player, PermissionContext context) {
        return canUseClaims(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_CREATE, true, context);
    }

    public static boolean canDeleteClaim(ServerPlayer player) {
        return canUseClaims(player)
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_DELETE, true);
    }

    public static boolean canTrust(ServerPlayer player) {
        return canUseClaims(player)
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_TRUST, true);
    }

    public static boolean canEditFlags(ServerPlayer player) {
        return canUseClaims(player)
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_FLAGS, true);
    }

    public static boolean canUseMap(ServerPlayer player) {
        return canUseClaims(player)
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_MAP, true);
    }

    public static boolean canVisualizeClaims(ServerPlayer player) {
        return canUseClaims(player)
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_VISUALIZE, true);
    }

    public static boolean canTeleportClaim(ServerPlayer player, PermissionContext context) {
        return canUseClaims(player, context)
                && PermissionService.getBoolean(player, PermissionKeys.CLAIMS_TELEPORT, true, context);
    }

    public static boolean hasAdminBypass(ServerPlayer player) {
        return PermissionService.isAdmin(player)
                || PermissionService.getBoolean(player, PermissionKeys.CLAIMS_ADMIN_BYPASS, false);
    }

    public static int getMaxClaimChunks(ServerPlayer player) {
        return getMaxClaimChunks(player, PermissionContext.global(player));
    }

    public static int getMaxClaimChunks(ServerPlayer player, PermissionContext context) {
        return PermissionService.getInt(
                player,
                PermissionKeys.CLAIMS_MAX_CHUNKS,
                Config.MAX_PLAYER_CLAIM_CHUNKS.get(),
                context
        );
    }

    public static int getMaxClaimGroups(ServerPlayer player) {
        return getMaxClaimGroups(player, PermissionContext.global(player));
    }

    public static int getMaxClaimGroups(ServerPlayer player, PermissionContext context) {
        return PermissionService.getInt(
                player,
                PermissionKeys.CLAIMS_MAX_GROUPS,
                Config.MAX_PLAYER_CLAIM_GROUPS.get(),
                context
        );
    }

    public static int getMaxChunksPerClaim(ServerPlayer player) {
        return getMaxChunksPerClaim(player, PermissionContext.global(player));
    }

    public static int getMaxChunksPerClaim(ServerPlayer player, PermissionContext context) {
        return PermissionService.getInt(
                player,
                PermissionKeys.CLAIMS_MAX_CHUNKS_PER_GROUP,
                Config.MAX_PLAYER_CLAIM_CHUNKS_PER_GROUP.get(),
                context
        );
    }
}
