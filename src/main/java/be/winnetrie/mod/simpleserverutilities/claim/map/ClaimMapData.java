package be.winnetrie.mod.simpleserverutilities.claim.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClaimMapData {

    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radius;
    private final String selectedClaimGroup;

    private final List<ClaimMapChunk> chunks = new ArrayList<>();

    public ClaimMapData(int centerChunkX, int centerChunkZ, int radius, String selectedClaimGroup) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radius = radius;
        this.selectedClaimGroup = selectedClaimGroup;
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

    public void addChunk(ClaimMapChunk chunk) {
        chunks.add(chunk);
    }

    public List<ClaimMapChunk> getChunks() {
        return Collections.unmodifiableList(chunks);
    }
}