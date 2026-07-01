package be.winnetrie.mod.simpleserverutilities.claim.map;

import java.util.UUID;

public class ClaimMapChunk {

    private final int chunkX;
    private final int chunkZ;
    private final ClaimChunkStatus status;

    private final String claimName;
    private final UUID owner;

    private final boolean currentChunk;
    private final boolean canClaim;
    private final boolean canUnclaim;

    public ClaimMapChunk(
            int chunkX,
            int chunkZ,
            ClaimChunkStatus status,
            String claimName,
            UUID owner,
            boolean currentChunk,
            boolean canClaim,
            boolean canUnclaim
    ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.status = status;
        this.claimName = claimName;
        this.owner = owner;
        this.currentChunk = currentChunk;
        this.canClaim = canClaim;
        this.canUnclaim = canUnclaim;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public ClaimChunkStatus getStatus() {
        return status;
    }

    public String getClaimName() {
        return claimName;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean isCurrentChunk() {
        return currentChunk;
    }

    public boolean canClaim() {
        return canClaim;
    }

    public boolean canUnclaim() {
        return canUnclaim;
    }
}