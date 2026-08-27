package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.policy.ClaimPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
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
        if (SsuModuleAccess.active("minigames") && SimpleServerUtilities.MINIGAMES.canBypassRegionPvp(attacker, targetPos)) return true;
        Region region = getRegionAt(level, targetPos);

        if (region != null) {
            // Selection-created arenas are owned by the Minigame lifecycle. Even an
            // administrator bypass may not turn an idle/foreign arena into an ad-hoc
            // PvP zone; only the exact live match rule above can allow damage.
            if (SsuModuleAccess.active("minigames") && SimpleServerUtilities.MINIGAMES.isManagedArenaRegion(region.getName())) return false;
            return RegionPolicy.hasAdminBypass(attacker) || region.getSettings().isAllowPvp();
        }

        PlayerClaim claim = getClaimAt(level, targetPos);

        if (claim == null) {
            return true;
        }

        return ClaimPolicy.hasAdminBypass(attacker) || claim.getSettings().isAllowPvp();
    }

    public static boolean canPlayerModify(ServerPlayer player, Level level, BlockPos pos) {
        return canPlayerBreak(player, level, pos) && canPlayerPlace(player, level, pos);
    }

    public static PlayerClaim getClaimAt(Level level, BlockPos pos) {
        if (!SsuModuleAccess.active("claims")) return null;
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
        return SsuModuleAccess.active("regions")
                ? SimpleServerUtilities.REGIONS.getAt(level.dimension(), pos)
                : null;
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

        ProtectionBoundary.Relation relation = ProtectionBoundary.relation(level, sourcePos, targetPos);
        if (relation == ProtectionBoundary.Relation.CROSSES_PROTECTION_BOUNDARY) {
            return false;
        }

        if (relation == ProtectionBoundary.Relation.UNPROTECTED) {
            return true;
        }

        Region region = getRegionAt(level, sourcePos);
        if (region == null) region = getRegionAt(level, targetPos);
        if (region != null) {
            return isFluidAllowedInRegion(region, fluidState);
        }

        PlayerClaim claim = getClaimAt(level, sourcePos);
        if (claim == null) claim = getClaimAt(level, targetPos);
        return claim != null && isFluidAllowedInClaim(claim, fluidState);
    }

    private static boolean isFluidAllowedInRegion(Region region, FluidState fluidState) {
        if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
            return region.getSettings().isAllowWaterFlow();
        }

        if (fluidState.is(Fluids.LAVA) || fluidState.is(Fluids.FLOWING_LAVA)) {
            return region.getSettings().isAllowLavaFlow();
        }

        // Regions currently have no separate "other fluids" toggle. Preserve
        // the old behaviour for modded fluids that stay entirely inside one region.
        return true;
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
        ProtectionBoundary.Relation relation = ProtectionBoundary.relation(level, from, to);
        if (relation == ProtectionBoundary.Relation.CROSSES_PROTECTION_BOUNDARY) {
            return false;
        }

        if (relation == ProtectionBoundary.Relation.UNPROTECTED) {
            return true;
        }

        Region region = getRegionAt(level, from);
        if (region == null) region = getRegionAt(level, to);
        if (region != null) {
            return region.getSettings().isAllowPistons();
        }

        PlayerClaim claim = getClaimAt(level, from);
        if (claim == null) claim = getClaimAt(level, to);
        return claim != null && claim.getSettings().isAllowPistons();
    }

    public static boolean canRedstoneAffect(Level level, BlockPos sourcePos, BlockPos targetPos) {
        ProtectionBoundary.Relation relation = ProtectionBoundary.relation(level, sourcePos, targetPos);
        if (relation == ProtectionBoundary.Relation.CROSSES_PROTECTION_BOUNDARY) {
            return false;
        }

        if (relation == ProtectionBoundary.Relation.UNPROTECTED) {
            return true;
        }

        Region region = getRegionAt(level, sourcePos);
        if (region == null) region = getRegionAt(level, targetPos);
        if (region != null) {
            return region.getSettings().isAllowRedstone();
        }

        PlayerClaim claim = getClaimAt(level, sourcePos);
        if (claim == null) claim = getClaimAt(level, targetPos);
        return claim != null && claim.getSettings().isAllowRedstone();
    }

    private static boolean sameRegion(Region a, Region b) {
        return a.getName().equalsIgnoreCase(b.getName());
    }

    private static boolean sameClaim(PlayerClaim a, PlayerClaim b) {
        return a.getId().equals(b.getId());
    }

    public static boolean canPlayerPerform(ServerPlayer player, Level level, BlockPos pos, ActionType action) {
        // A Minigame Setup Tool build session is the only administrator-authorized
        // way to physically change a managed arena while it is idle. The first edit
        // invalidates the old reset snapshot and disables the arena until recaptured.
        if (SsuModuleAccess.active("minigames")
                && (action == ActionType.BREAK || action == ActionType.PLACE)
                && SimpleServerUtilities.MINIGAME_SETUP_TOOLS.existing(player) != null
                && be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupToolService.canEditBlock(player, pos)) {
            // Keep the bypass narrow: only actual block breaking and placement are allowed.
            return true;
        }
        // Managed minigame arenas remain protected while idle. During a running Spleef
        // match only the server-validated participant/tool/block combination bypasses
        // the normal region break flag.
        var minigameBreak = SsuModuleAccess.active("minigames") && action == ActionType.BREAK
                ? SimpleServerUtilities.MINIGAMES.blockBreakDecision(player, pos, level.getBlockState(pos))
                : be.winnetrie.mod.simpleserverutilities.minigame.MinigameManager.BlockBreakDecision.PASS;
        if (minigameBreak
                == be.winnetrie.mod.simpleserverutilities.minigame.MinigameManager.BlockBreakDecision.ALLOW) {
            return true;
        }
        Region region = getRegionAt(level, pos);

        if (region != null) {
            // Managed arena ownership outranks the normal region admin bypass. Editing
            // the arena while idle or from another match would invalidate its reset
            // snapshot and isolation guarantees. Delete the minigame first to release it.
            if (SsuModuleAccess.active("minigames") && SimpleServerUtilities.MINIGAMES.isManagedArenaRegion(region.getName())) return false;
            if (RegionPolicy.hasAdminBypass(player)) {
                return true;
            }

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

        if (ClaimPolicy.hasAdminBypass(player) || claim.isOwner(player.getUUID())) return true;
        String permission = switch (action) {
            case BREAK -> PermissionKeys.CLAIM_CONTEXT_BREAK_BLOCKS;
            case PLACE -> PermissionKeys.CLAIM_CONTEXT_PLACE_BLOCKS;
            case INTERACT -> PermissionKeys.CLAIM_CONTEXT_INTERACT_OTHER;
        };
        return claimPermission(player, claim, pos, permission, false);
    }

    public static boolean canOpenClaimContainer(ServerPlayer player, Level level, BlockPos pos) {
        return canClaimSpecific(player, level, pos, PermissionKeys.CLAIM_CONTEXT_OPEN_CONTAINERS);
    }

    public static boolean canUseClaimDoor(ServerPlayer player, Level level, BlockPos pos) {
        return canClaimSpecific(player, level, pos, PermissionKeys.CLAIM_CONTEXT_USE_DOORS);
    }

    public static boolean canUseClaimSwitch(ServerPlayer player, Level level, BlockPos pos) {
        return canClaimSpecific(player, level, pos, PermissionKeys.CLAIM_CONTEXT_USE_SWITCHES);
    }

    public static boolean canModifyClaimNonLiving(ServerPlayer player, Level level, BlockPos pos) {
        return canClaimSpecific(player, level, pos, PermissionKeys.CLAIM_CONTEXT_MODIFY_NONLIVING);
    }

    public static boolean canDamageClaimLiving(ServerPlayer player, Level level, BlockPos pos) {
        return canClaimSpecific(player, level, pos, PermissionKeys.CLAIM_CONTEXT_DAMAGE_LIVING);
    }

    public static boolean canInteractClaimEntity(ServerPlayer player, Level level, BlockPos pos) {
        return canClaimSpecific(player, level, pos, PermissionKeys.CLAIM_CONTEXT_INTERACT_ENTITIES);
    }

    public static boolean canTransferClaimItems(ServerPlayer player, Level level, BlockPos pos) {
        return canClaimSpecific(player, level, pos, PermissionKeys.CLAIM_CONTEXT_ITEM_TRANSFER);
    }

    public static boolean canUseClaimHomes(ServerPlayer player, PlayerClaim claim) {
        if (claim == null) return false;
        return ClaimPolicy.hasAdminBypass(player) || claim.isOwner(player.getUUID())
                || claimPermission(player, claim, player.blockPosition(), PermissionKeys.CLAIM_CONTEXT_USE_HOMES, false);
    }

    private static boolean canClaimSpecific(ServerPlayer player, Level level, BlockPos pos, String permission) {
        if (getRegionAt(level, pos) != null) return canPlayerInteract(player, level, pos);
        PlayerClaim claim = getClaimAt(level, pos);
        if (claim == null) return true;
        if (ClaimPolicy.hasAdminBypass(player) || claim.isOwner(player.getUUID())) return true;
        return claimPermission(player, claim, pos, permission, false);
    }

    private static boolean claimPermission(ServerPlayer player, PlayerClaim claim, BlockPos pos,
            String permission, boolean fallback) {
        String role = claim.isCoOwner(player.getUUID()) ? "co_owner"
                : claim.isTrusted(player.getUUID()) ? "member" : "visitor";
        String localOverride = claim.getRolePermissionOverride(role, permission);
        if (localOverride != null) return Boolean.parseBoolean(localOverride);
        PermissionContext context = PermissionContext.builder(player)
                .position(pos)
                .playerClaim(claim)
                .build();
        return PermissionService.getBoolean(player, permission, fallback, context);
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
        ProtectionBoundary.Relation relation = ProtectionBoundary.relation(level, from, to);
        if (relation == ProtectionBoundary.Relation.CROSSES_PROTECTION_BOUNDARY) {
            return false;
        }

        if (relation == ProtectionBoundary.Relation.UNPROTECTED) {
            return true;
        }

        Region region = getRegionAt(level, from);
        if (region == null) region = getRegionAt(level, to);
        if (region != null) {
            return region.getSettings().isAllowHoppers();
        }

        PlayerClaim claim = getClaimAt(level, from);
        if (claim == null) claim = getClaimAt(level, to);
        return claim != null && claim.getSettings().isAllowHoppers();
    }

    public static boolean canFireAffect(Level level, BlockPos sourcePos, BlockPos targetPos) {
        if (!ProtectionBoundary.canCross(level, sourcePos, targetPos)) {
            return false;
        }
        return canFireAffect(level, targetPos);
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