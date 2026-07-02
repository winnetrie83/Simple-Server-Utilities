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
import net.minecraft.world.level.material.FluidState;

import net.minecraft.world.level.material.Fluids;

public class ProtectionHelper {

    public enum ActionType {
        BREAK,
        PLACE,
        INTERACT
    }

    private ProtectionHelper() {
    }

    public static boolean canPlayerBreak(ServerPlayer player, Level level, BlockPos pos) {
        return canPlayerPerform(player, level, pos, ActionType.BREAK);
    }

    public static boolean canPlayerPlace(ServerPlayer player, Level level, BlockPos pos) {
        return canPlayerPerform(player, level, pos, ActionType.PLACE);
    }

    public static boolean canPlayerInteract(ServerPlayer player, Level level, BlockPos pos) {
        return canPlayerPerform(player, level, pos, ActionType.INTERACT);
    }


    public static boolean canPlayerPvp(ServerPlayer attacker, Level level, BlockPos targetPos) {
        if (PermissionService.has(attacker, PermissionService.CLAIM_BYPASS)) {
            return true;
        }

        Region region = getRegionAt(level, targetPos);

        if (region != null) {
            return region.getSettings().isAllowPvp();
        }

        PlayerClaim claim = getClaimAt(level, targetPos);

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowPvp();
    }

    public static boolean canPlayerModify(ServerPlayer player, Level level, BlockPos pos) {
        return canPlayerBreak(player, level, pos) && canPlayerPlace(player, level, pos);
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

    public static boolean canFluidAffect(LevelAccessor levelAccessor, BlockPos sourcePos, BlockPos targetPos, FluidState fluidState) {
        if (!(levelAccessor instanceof Level level)) {
            return true;
        }

        Region sourceRegion = getRegionAt(level, sourcePos);
        Region targetRegion = getRegionAt(level, targetPos);

        if (targetRegion != null) {
            if (sourceRegion != null && sameRegion(sourceRegion, targetRegion)) {
                return true;
            }

            return isFluidAllowedInRegion(targetRegion, fluidState);
        }

        PlayerClaim sourceClaim = getClaimAt(level, sourcePos);
        PlayerClaim targetClaim = getClaimAt(level, targetPos);

        if (targetClaim == null) {
            return true;
        }

        if (sourceClaim != null && sameClaim(sourceClaim, targetClaim)) {
            return true;
        }

        return isFluidAllowedInClaim(targetClaim, fluidState);
    }

    private static boolean isFluidAllowedInRegion(Region region, FluidState fluidState) {
        if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
            return region.getSettings().isAllowWaterFlow();
        }

        if (fluidState.is(Fluids.LAVA) || fluidState.is(Fluids.FLOWING_LAVA)) {
            return region.getSettings().isAllowLavaFlow();
        }

        return false;
    }

    private static boolean isFluidAllowedInClaim(PlayerClaim claim, FluidState fluidState) {
        if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
            return claim.getSettings().isAllowWaterFlow();
        }

        if (fluidState.is(Fluids.LAVA) || fluidState.is(Fluids.FLOWING_LAVA)) {
            return claim.getSettings().isAllowLavaFlow();
        }

        return claim.getSettings().isAllowOtherFluidFlow();
    }

    public static boolean canExplosionAffect(Level level, BlockPos pos) {
        Region region = getRegionAt(level, pos);

        if (region != null) {
            return region.getSettings().isAllowExplosions();
        }

        PlayerClaim claim = getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowExplosions();
    }

    public static boolean canPistonMove(Level level, BlockPos from, BlockPos to) {
        Region fromRegion = getRegionAt(level, from);
        Region toRegion = getRegionAt(level, to);

        if (fromRegion != null || toRegion != null) {
            if (fromRegion != null && toRegion != null && sameRegion(fromRegion, toRegion)) {
                return fromRegion.getSettings().isAllowPistons();
            }

            if (fromRegion != null && !fromRegion.getSettings().isAllowPistons()) {
                return false;
            }

            if (toRegion != null && !toRegion.getSettings().isAllowPistons()) {
                return false;
            }

            return true;
        }

        PlayerClaim fromClaim = getClaimAt(level, from);
        PlayerClaim toClaim = getClaimAt(level, to);

        if (fromClaim == null && toClaim == null) {
            return true;
        }

        if (fromClaim != null && toClaim != null && sameClaim(fromClaim, toClaim)) {
            return true;
        }

        if (fromClaim != null && !fromClaim.getSettings().isAllowPistons()) {
            return false;
        }

        if (toClaim != null && !toClaim.getSettings().isAllowPistons()) {
            return false;
        }

        return true;
    }

    private static boolean sameRegion(Region a, Region b) {
        return a.getName().equalsIgnoreCase(b.getName());
    }

    private static boolean sameClaim(PlayerClaim a, PlayerClaim b) {
        return a.getId().equals(b.getId());
    }

    public static boolean canPlayerPerform(ServerPlayer player, Level level, BlockPos pos, ActionType action) {
        if (PermissionService.has(player, PermissionService.CLAIM_BYPASS)) {
            return true;
        }

        Region region = getRegionAt(level, pos);

        if (region != null) {
            return switch (action) {
                case BREAK -> region.getSettings().isAllowBlockBreak() || region.hasAccess(player.getUUID());
                case PLACE -> region.getSettings().isAllowBlockPlace() || region.hasAccess(player.getUUID());
                case INTERACT -> region.getSettings().isAllowInteract() || region.hasAccess(player.getUUID());
            };
        }

        PlayerClaim claim = getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.canBuild(player.getUUID());
    }

    public static boolean canOwnerlessProjectileHit(Level level, BlockPos pos) {
        Region region = getRegionAt(level, pos);

        if (region != null) {
            return false;
        }

        PlayerClaim claim = getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowOwnerlessProjectiles();
    }

    public static boolean canHopperTransfer(Level level, BlockPos from, BlockPos to) {
        Region fromRegion = getRegionAt(level, from);
        Region toRegion = getRegionAt(level, to);

        if (fromRegion != null || toRegion != null) {
            if (fromRegion == null || toRegion == null) {
                return false;
            }

            if (!sameRegion(fromRegion, toRegion)) {
                return false;
            }

            return fromRegion.getSettings().isAllowHoppers();
        }

        PlayerClaim fromClaim = getClaimAt(level, from);
        PlayerClaim toClaim = getClaimAt(level, to);

        if (fromClaim != null || toClaim != null) {
            if (fromClaim == null || toClaim == null) {
                return false;
            }

            if (!sameClaim(fromClaim, toClaim)) {
                return false;
            }

            return fromClaim.getSettings().isAllowHoppers();
        }

        return true;
    }

    public static boolean canFireAffect(Level level, BlockPos pos) {
        Region region = getRegionAt(level, pos);

        if (region != null) {
            return region.getSettings().isAllowFireSpread();
        }

        PlayerClaim claim = getClaimAt(level, pos);

        if (claim == null) {
            return true;
        }

        return claim.getSettings().isAllowFireSpread();
    }

    public static boolean canFireAffect(LevelAccessor levelAccessor, BlockPos pos) {
        if (!(levelAccessor instanceof Level level)) {
            return true;
        }

        return canFireAffect(level, pos);
    }
}