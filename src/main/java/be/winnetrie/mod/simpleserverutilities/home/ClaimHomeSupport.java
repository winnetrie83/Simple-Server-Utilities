package be.winnetrie.mod.simpleserverutilities.home;

import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Shared, server-authoritative rules for linking personal homes to owned claims. */
public final class ClaimHomeSupport {
    private ClaimHomeSupport() {}

    public static PlayerClaim ownedClaimAt(ServerPlayer player) {
        return ownedClaimAt(player, player.blockPosition());
    }

    public static PlayerClaim ownedClaimAt(ServerPlayer player, BlockPos position) {
        if (player == null || position == null || !SsuModuleAccess.active("claims")) return null;
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(
                player.level(), new ChunkPos(position.getX() >> 4, position.getZ() >> 4));
        return claim != null && claim.isOwner(player.getUUID()) ? claim : null;
    }

    public static boolean contains(PlayerClaim claim, Level level, BlockPos position) {
        if (claim == null || level == null || position == null) return false;
        String dimension = level.dimension().location().toString();
        return claim.getDimension().equals(dimension)
                && claim.hasChunk(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }

    public static boolean contains(PlayerClaim claim, PlayerHome home) {
        if (claim == null || home == null || !claim.getDimension().equals(home.getDimension())) return false;
        int blockX = (int) Math.floor(home.getX());
        int blockZ = (int) Math.floor(home.getZ());
        return claim.hasChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    public static List<PlayerHome> homesInClaim(UUID owner, PlayerClaim claim) {
        if (owner == null || claim == null || !SsuModuleAccess.active("homes")) return List.of();
        return SimpleServerUtilities.HOMES.getHomes(owner).stream()
                .filter(home -> contains(claim, home))
                .toList();
    }
}
