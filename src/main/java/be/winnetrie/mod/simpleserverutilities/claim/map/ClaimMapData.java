package be.winnetrie.mod.simpleserverutilities.claim.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClaimMapData {

    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radius;
    private final String selectedClaimGroup;
    private final List<String> ownedClaimGroups;
    private final int usedChunks;
    private final int maxChunks;
    private final int usedClaimGroups;
    private final int maxClaimGroups;
    private final int selectedClaimChunks;
    private final int maxChunksPerClaim;
    private final boolean canCreateClaims;
    private final String notice;
    private final boolean error;

    private final List<ClaimMapChunk> chunks = new ArrayList<>();

    public ClaimMapData(
            int centerChunkX,
            int centerChunkZ,
            int radius,
            String selectedClaimGroup,
            List<String> ownedClaimGroups,
            int usedChunks,
            int maxChunks,
            int usedClaimGroups,
            int maxClaimGroups,
            int selectedClaimChunks,
            int maxChunksPerClaim,
            boolean canCreateClaims,
            String notice,
            boolean error
    ) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radius = radius;
        this.selectedClaimGroup = selectedClaimGroup == null ? "" : selectedClaimGroup;
        this.ownedClaimGroups = ownedClaimGroups == null ? List.of() : List.copyOf(ownedClaimGroups);
        this.usedChunks = Math.max(0, usedChunks);
        this.maxChunks = Math.max(0, maxChunks);
        this.usedClaimGroups = Math.max(0, usedClaimGroups);
        this.maxClaimGroups = Math.max(0, maxClaimGroups);
        this.selectedClaimChunks = Math.max(0, selectedClaimChunks);
        this.maxChunksPerClaim = Math.max(0, maxChunksPerClaim);
        this.canCreateClaims = canCreateClaims;
        this.notice = notice == null ? "" : notice;
        this.error = error;
    }

    public int getCenterChunkX() {
        return centerChunkX;
    }

    public int getCenterChunkZ() {
        return centerChunkZ;
    }

    public int getRadius() {
        return radius;
    }

    public String getSelectedClaimGroup() {
        return selectedClaimGroup;
    }

    public List<String> getOwnedClaimGroups() {
        return ownedClaimGroups;
    }

    public int getUsedChunks() {
        return usedChunks;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public int getUsedClaimGroups() {
        return usedClaimGroups;
    }

    public int getMaxClaimGroups() {
        return maxClaimGroups;
    }

    public int getSelectedClaimChunks() {
        return selectedClaimChunks;
    }

    public int getMaxChunksPerClaim() {
        return maxChunksPerClaim;
    }

    public boolean canCreateClaims() {
        return canCreateClaims;
    }

    public String getNotice() {
        return notice;
    }

    public boolean isError() {
        return error;
    }

    public void addChunk(ClaimMapChunk chunk) {
        chunks.add(chunk);
    }

    public List<ClaimMapChunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }
}
