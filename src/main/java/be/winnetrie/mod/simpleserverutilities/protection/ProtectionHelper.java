package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class ProtectionHelper {

    private ProtectionHelper() {
    }

    public static boolean canPlayerBreak(ServerPlayer player, Level level, BlockPos pos) {
        if (PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            return true;
        }

        Region region = getRegionAt(level, pos);

        if (region != null) {
            if (region.getSettings().isAllowBlockBreak()) {
                return true;
            }

            return region.hasAccess(player.getUUID());
        }

        PlayerClaim claim = getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.canBuild(player.getUUID());
    }

    public static boolean canPlayerPlace(ServerPlayer player, Level level, BlockPos pos) {
        if (PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            return true;
        }

        Region region = getRegionAt(level, pos);

        if (region != null) {
            if (region.getSettings().isAllowBlockPlace()) {
                return true;
            }

            return region.hasAccess(player.getUUID());
        }

        PlayerClaim claim = getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.canBuild(player.getUUID());
    }

    public static boolean canPlayerModify(ServerPlayer player, Level level, BlockPos pos) {
        return canPlayerBreak(player, level, pos) && canPlayerPlace(player, level, pos);
    }

    public static boolean canPlayerInteract(ServerPlayer player, Level level, BlockPos pos) {
        if (PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            return true;
        }

        Region region = getRegionAt(level, pos);

        if (region != null) {
            if (region.getSettings().isAllowInteract()) {
                return true;
            }

            return region.hasAccess(player.getUUID());
        }

        PlayerClaim claim = getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.canBuild(player.getUUID());
    }

    public static PlayerClaim getClaimAt(Level level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        return SimpleServerUtilities.PLAYER_CLAIMS.getClaim(level, chunkPos);
    }

    public static PlayerClaim getClaimAt(LevelAccessor levelAccessor, BlockPos pos) {
        if (!(levelAccessor instanceof Level level)) {
            return null;
        }

        return getClaimAt(level, pos);
    }

    public static Region getRegionAt(Level level, BlockPos pos) {
        return SimpleServerUtilities.REGIONS.getAt(level.dimension(), pos);
    }

    public static Region getRegionAt(LevelAccessor levelAccessor, BlockPos pos) {
        if (!(levelAccessor instanceof Level level)) {
            return null;
        }

        return getRegionAt(level, pos);
    }
}