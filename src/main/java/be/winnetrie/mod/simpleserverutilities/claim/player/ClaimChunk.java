package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.Objects;

public class ClaimChunk {

    private int x;
    private int z;

    public ClaimChunk() {
        // Required for Gson
    }

    public ClaimChunk(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ClaimChunk other)) {
            return false;
        }

        return x == other.x && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return x + ", " + z;
    }
}