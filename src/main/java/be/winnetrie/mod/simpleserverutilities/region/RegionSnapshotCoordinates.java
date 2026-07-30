package be.winnetrie.mod.simpleserverutilities.region;

/** Pure coordinate packing used by the version 2 snapshot format. */
public final class RegionSnapshotCoordinates {

    private RegionSnapshotCoordinates() {
    }

    public static int pack(
            int minX,
            int minY,
            int minZ,
            int sizeY,
            int sizeZ,
            int x,
            int y,
            int z
    ) {
        if (sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Snapshot dimensions must be positive.");
        }
        int relativeX = x - minX;
        int relativeY = y - minY;
        int relativeZ = z - minZ;
        if (relativeX < 0 || relativeY < 0 || relativeY >= sizeY || relativeZ < 0 || relativeZ >= sizeZ) {
            throw new IllegalArgumentException("Block is outside the snapshot coordinate range.");
        }
        long packed = ((long) relativeX * sizeY + relativeY) * sizeZ + relativeZ;
        if (packed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Snapshot coordinate range is too large.");
        }
        return (int) packed;
    }

    public static Decoded unpack(
            int minX,
            int minY,
            int minZ,
            int sizeY,
            int sizeZ,
            int packed
    ) {
        if (sizeY <= 0 || sizeZ <= 0 || packed < 0) {
            throw new IllegalArgumentException("Invalid packed snapshot coordinate.");
        }
        int relativeZ = packed % sizeZ;
        int yz = packed / sizeZ;
        int relativeY = yz % sizeY;
        int relativeX = yz / sizeY;
        return new Decoded(minX + relativeX, minY + relativeY, minZ + relativeZ);
    }

    public record Decoded(int x, int y, int z) {
    }
}
