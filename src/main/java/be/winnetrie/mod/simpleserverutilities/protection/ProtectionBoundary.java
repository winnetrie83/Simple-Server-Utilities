package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Central claim/region boundary policy for non-player world automation.
 *
 * <p>The protected world is treated as a firewall: an automated effect may
 * move freely in wilderness, and may operate inside one protected area, but it
 * may not cross between wilderness and a protected area or between two
 * different protected areas. Action-specific settings are evaluated by the
 * caller after this boundary check succeeds.</p>
 *
 * <p>When a compatibility layer can provide a <em>reliable</em> actor UUID,
 * {@link #canCross(Level, BlockPos, BlockPos, UUID)} uses the existing
 * claim/region access rules and revalidates that access even while the machine
 * remains inside one protected area. Unknown automation deliberately falls
 * back to the closed firewall rule. SSU never guesses machine ownership.</p>
 *
 * <p>This class intentionally knows nothing about pistons, hoppers, Create or
 * any other machine implementation. Compatibility layers only need to provide
 * a source and affected/destination position.</p>
 */
public final class ProtectionBoundary {

    public enum Relation {
        UNPROTECTED,
        SAME_PROTECTED_AREA,
        CROSSES_PROTECTION_BOUNDARY
    }

    /**
     * Small immutable identity used when many targets are compared to one
     * source (for example a large moving contraption). Resolving the source
     * once avoids repeating its region + claim lookup for every block.
     */
    public record AreaSnapshot(AreaType type, UUID claimId, String regionName) {
        public static AreaSnapshot wilderness() {
            return new AreaSnapshot(AreaType.WILDERNESS, null, null);
        }

        public static AreaSnapshot claim(UUID claimId) {
            return new AreaSnapshot(AreaType.PLAYER_CLAIM, claimId, null);
        }

        public static AreaSnapshot region(String regionName) {
            return new AreaSnapshot(AreaType.REGION, null, regionName);
        }
    }

    public enum AreaType {
        WILDERNESS,
        PLAYER_CLAIM,
        REGION
    }

    private ProtectionBoundary() {
    }

    public static Relation relation(Level level, BlockPos sourcePos, BlockPos targetPos) {
        // Keep the hot generic path allocation-free. This method is used by
        // vanilla hoppers, fluids, pistons, redstone and many mod hooks. The
        // AreaSnapshot helper below is reserved for loops that compare many
        // targets to one source.
        Region sourceRegion = ProtectionHelper.getRegionAt(level, sourcePos);
        Region targetRegion = ProtectionHelper.getRegionAt(level, targetPos);

        // Regions have priority over player claims everywhere else in SSU, so
        // any transition involving a region is resolved before claims.
        if (sourceRegion != null || targetRegion != null) {
            if (sourceRegion != null && targetRegion != null && sameRegion(sourceRegion, targetRegion)) {
                return Relation.SAME_PROTECTED_AREA;
            }
            return Relation.CROSSES_PROTECTION_BOUNDARY;
        }

        PlayerClaim sourceClaim = ProtectionHelper.getClaimAt(level, sourcePos);
        PlayerClaim targetClaim = ProtectionHelper.getClaimAt(level, targetPos);

        if (sourceClaim == null && targetClaim == null) {
            return Relation.UNPROTECTED;
        }

        if (sourceClaim != null && targetClaim != null && sourceClaim.getId().equals(targetClaim.getId())) {
            return Relation.SAME_PROTECTED_AREA;
        }

        return Relation.CROSSES_PROTECTION_BOUNDARY;
    }

    public static boolean canCross(Level level, BlockPos sourcePos, BlockPos targetPos) {
        return relation(level, sourcePos, targetPos) != Relation.CROSSES_PROTECTION_BOUNDARY;
    }

    /**
     * Owner-aware variant for integrations that have an authoritative player
     * UUID (currently Create Deployers). A known actor must still have current
     * access to every protected endpoint, even when source and target are in the
     * same claim/region; this makes permission revocation take effect immediately.
     * Unknown automation keeps the normal positional firewall semantics.
     */
    public static boolean canCross(Level level, BlockPos sourcePos, BlockPos targetPos, UUID actor) {
        Relation relation = relation(level, sourcePos, targetPos);

        // Unknown automation keeps the original firewall semantics: it may work
        // within one area but may never cross a protection boundary.
        if (actor == null) {
            return relation != Relation.CROSSES_PROTECTION_BOUNDARY;
        }

        // A reliable actor UUID is stronger information than position alone.
        // Do not let an old/now-revoked machine keep operating merely because
        // both source and target happen to sit inside the same foreign claim or
        // region. Wilderness needs no permission; any protected endpoint does.
        if (relation == Relation.UNPROTECTED) {
            return true;
        }
        return canActorModifyAt(level, actor, sourcePos) && canActorModifyAt(level, actor, targetPos);
    }

    /** Resolve the effective region/claim at one position, respecting region priority. */
    public static AreaSnapshot resolveArea(Level level, BlockPos pos) {
        Region region = ProtectionHelper.getRegionAt(level, pos);
        if (region != null) {
            return AreaSnapshot.region(region.getName().toLowerCase(java.util.Locale.ROOT));
        }

        PlayerClaim claim = ProtectionHelper.getClaimAt(level, pos);
        if (claim != null) {
            return AreaSnapshot.claim(claim.getId());
        }

        return AreaSnapshot.wilderness();
    }

    /**
     * Fast target comparison for loops that already resolved the source area.
     * This halves the normal protection lookups performed by the exact Create
     * footprint scan near a boundary.
     */
    public static boolean sameArea(Level level, AreaSnapshot sourceArea, BlockPos targetPos) {
        if (sourceArea == null) {
            return false;
        }

        Region targetRegion = ProtectionHelper.getRegionAt(level, targetPos);
        if (sourceArea.type() == AreaType.REGION) {
            return targetRegion != null
                    && sourceArea.regionName().equalsIgnoreCase(targetRegion.getName());
        }
        if (targetRegion != null) {
            return false;
        }

        PlayerClaim targetClaim = ProtectionHelper.getClaimAt(level, targetPos);
        if (sourceArea.type() == AreaType.PLAYER_CLAIM) {
            return targetClaim != null && sourceArea.claimId().equals(targetClaim.getId());
        }
        return targetClaim == null;
    }

    /**
     * Cheap conservative envelope test used before scanning every block in a
     * large moving contraption. Returning {@code true} guarantees that every
     * position in the axis-aligned envelope resolves to the source protection
     * area. Returning {@code false} only means "possibly crosses" and causes
     * the caller to run the exact per-block test.
     *
     * <p>Cost is proportional to intersected chunks/region candidates, not to
     * the number of blocks in the contraption.</p>
     */
    public static boolean envelopeStaysInSameArea(
            Level level,
            BlockPos origin,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        if (level == null || origin == null) {
            return false;
        }

        int lowX = Math.min(minX, maxX);
        int highX = Math.max(minX, maxX);
        int lowY = Math.min(minY, maxY);
        int highY = Math.max(minY, maxY);
        int lowZ = Math.min(minZ, maxZ);
        int highZ = Math.max(minZ, maxZ);

        Region originRegion = ProtectionHelper.getRegionAt(level, origin);
        if (SsuModuleAccess.active("regions")) {
            for (Region region : SimpleServerUtilities.REGIONS.getIntersecting2D(
                    level.dimension(), lowX, lowZ, highX, highZ)) {
                if (highY < region.getMinY() || lowY > region.getMaxY()) {
                    continue;
                }

                // Any foreign/nested region makes the envelope ambiguous. The
                // exact scan will then account for region priority per block.
                if (originRegion == null || !sameRegion(originRegion, region)) {
                    return false;
                }
            }
        }

        if (originRegion != null) {
            // Regions are rectangular, so if the full AABB is inside the same
            // region and no foreign region intersects it, every contained block
            // resolves to that region regardless of underlying player claims.
            return lowX >= originRegion.getMinX() && highX <= originRegion.getMaxX()
                    && lowY >= originRegion.getMinY() && highY <= originRegion.getMaxY()
                    && lowZ >= originRegion.getMinZ() && highZ <= originRegion.getMaxZ();
        }

        PlayerClaim originClaim = ProtectionHelper.getClaimAt(level, origin);
        if (!SsuModuleAccess.active("claims")) {
            return true;
        }

        int minChunkX = Math.floorDiv(lowX, 16);
        int maxChunkX = Math.floorDiv(highX, 16);
        int minChunkZ = Math.floorDiv(lowZ, 16);
        int maxChunkZ = Math.floorDiv(highZ, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                PlayerClaim candidate = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(
                        level, new ChunkPos(chunkX, chunkZ));
                if (!sameNullableClaim(originClaim, candidate)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Uses existing SSU access rules without requiring a machine-owner database.
     * Wilderness is always accessible. Region members/managers can be checked by
     * UUID even while offline. Player-claim owners can likewise be checked
     * offline; other role/permission decisions are evaluated only while that
     * player is online so SSU does not invent permission semantics.
     */
    private static boolean canActorModifyAt(Level level, UUID actor, BlockPos pos) {
        Region region = ProtectionHelper.getRegionAt(level, pos);
        if (region != null) {
            if (region.hasAccess(actor)) {
                return true;
            }
            ServerPlayer player = onlinePlayer(level, actor);
            return player != null && ProtectionHelper.canPlayerModify(player, level, pos);
        }

        PlayerClaim claim = ProtectionHelper.getClaimAt(level, pos);
        if (claim == null) {
            return true;
        }
        if (claim.isOwner(actor)) {
            return true;
        }

        ServerPlayer player = onlinePlayer(level, actor);
        return player != null && ProtectionHelper.canPlayerModify(player, level, pos);
    }

    private static ServerPlayer onlinePlayer(Level level, UUID actor) {
        if (level == null || level.getServer() == null || actor == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(actor);
    }

    private static boolean sameRegion(Region a, Region b) {
        return a.getName().equalsIgnoreCase(b.getName());
    }

    private static boolean sameNullableClaim(PlayerClaim a, PlayerClaim b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getId().equals(b.getId());
    }
}
