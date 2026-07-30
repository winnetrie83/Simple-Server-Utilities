package be.winnetrie.mod.simpleserverutilities.client.map;

/** Pure height-relief calculations shared by the aerial renderer and tests. */
final class TerrainReliefMath {

    private TerrainReliefMath() {
    }

    /**
     * Directional hill shade normalized so perfectly flat terrain returns 1.0.
     * The virtual sun comes from the north-west. The local pass is deliberately
     * restrained; broad landform passes carry most of the terrain shape so the
     * result does not turn into a noisy outline around every block.
     */
    static double localReliefLight(
            int northWest,
            int north,
            int northEast,
            int west,
            int current,
            int east,
            int southWest,
            int south,
            int southEast
    ) {
        double gradientX = ((northEast + 2.0D * east + southEast)
                - (northWest + 2.0D * west + southWest)) / 8.0D;
        double gradientZ = ((southWest + 2.0D * south + southEast)
                - (northWest + 2.0D * north + northEast)) / 8.0D;

        double normalX = -gradientX * 0.50D;
        double normalY = 1.0D;
        double normalZ = -gradientZ * 0.50D;
        double normalLength = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);

        double sunX = -0.58D;
        double sunY = 0.76D;
        double sunZ = -0.30D;
        double diffuse = (normalX * sunX + normalY * sunY + normalZ * sunZ) / normalLength;

        int highestNeighbour = Math.max(
                Math.max(Math.max(northWest, north), Math.max(northEast, west)),
                Math.max(Math.max(east, southWest), Math.max(south, southEast))
        );
        double occlusion = Math.min(8, Math.max(0, highestNeighbour - current)) * 0.008D;
        return clamp(1.0D + (diffuse - sunY) * 0.70D - occlusion, 0.78D, 1.16D);
    }

    /**
     * Adds a small two-distance directional bevel. Minecraft mountains are
     * terraced rather than continuous heightfields; this keeps those terraces
     * legible without drawing a hard outline around every individual block.
     * The virtual light comes from the north-west.
     */
    static double directionalTerraceLight(
            int current,
            int west,
            int north,
            int northWest,
            int westTwo,
            int northTwo,
            int northWestTwo,
            int westTwoNorthOne,
            int westOneNorthTwo
    ) {
        double primaryReference = (west + north + northWest) / 3.0D;
        double secondaryReference = (westTwo + northTwo + northWestTwo
                + westTwoNorthOne + westOneNorthTwo) / 5.0D;
        double primaryDelta = clamp(current - primaryReference, -3.0D, 3.0D);
        double secondaryDelta = clamp(current - secondaryReference, -8.0D, 8.0D);

        double primary = primaryDelta >= 0.0D
                ? 1.0D + primaryDelta * 0.034D
                : 1.0D + primaryDelta * 0.050D;
        double secondary = secondaryDelta >= 0.0D
                ? 1.0D + secondaryDelta * 0.010D
                : 1.0D + secondaryDelta * 0.014D;
        return clamp(primary * secondary, 0.74D, 1.15D);
    }

    /** Adds coherent hill and valley shading over a six-block diameter. */
    static double broadReliefLight(int west, int north, int current, int east, int south) {
        double gradientX = (east - west) / 6.0D;
        double gradientZ = (south - north) / 6.0D;
        double directional = (-gradientX * 0.62D) + (-gradientZ * 0.38D);
        int highest = Math.max(Math.max(west, east), Math.max(north, south));
        double valley = Math.min(16, Math.max(0, highest - current)) * 0.0045D;
        return clamp(1.0D + directional * 0.105D - valley, 0.82D, 1.15D);
    }

    /** Adds a low-frequency shade so complete hills and valleys remain legible. */
    static double macroReliefLight(int west, int north, int current, int east, int south) {
        double gradientX = (east - west) / 16.0D;
        double gradientZ = (south - north) / 16.0D;
        double directional = (-gradientX * 0.64D) + (-gradientZ * 0.36D);
        int highest = Math.max(Math.max(west, east), Math.max(north, south));
        double basin = Math.min(28, Math.max(0, highest - current)) * 0.0024D;
        return clamp(1.0D + directional * 0.145D - basin, 0.84D, 1.14D);
    }

    /**
     * Limits a neighbouring canopy sample. A forest edge should create a clear
     * crown rim, but the renderer must not treat the full tree-to-ground drop as
     * a mountain cliff.
     */
    static int canopyNeighbourHeight(int current, int neighbour, boolean neighbourCanopy) {
        if (!neighbourCanopy) {
            return current - 1;
        }
        return Math.max(current - 3, Math.min(current + 3, neighbour));
    }

    /**
     * Adds a restrained north-west highlight and south-east shadow at canopy
     * boundaries. This turns leaf patches into coherent tree crowns instead of
     * pale dots while keeping dense forests calm.
     */
    static double canopyEdgeLight(
            boolean northCanopy,
            boolean westCanopy,
            boolean eastCanopy,
            boolean southCanopy
    ) {
        double factor = 1.0D;
        if (!northCanopy) {
            factor *= 1.020D;
        }
        if (!westCanopy) {
            factor *= 1.016D;
        }
        if (!eastCanopy) {
            factor *= 0.955D;
        }
        if (!southCanopy) {
            factor *= 0.945D;
        }
        return clamp(factor, 0.86D, 1.06D);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
