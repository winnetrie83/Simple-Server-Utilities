package be.winnetrie.mod.simpleserverutilities.client.minimap;

import java.util.UUID;

/** Pure connected-claim perimeter calculation used by the HUD minimap. */
final class ClaimOutlineMath {

    private ClaimOutlineMath() {
    }

    static boolean isOuterClaimEdge(
            UUID current,
            UUID west,
            UUID east,
            UUID north,
            UUID south,
            int localX,
            int localZ
    ) {
        return (localX == 0 && !current.equals(west))
                || (localX == 15 && !current.equals(east))
                || (localZ == 0 && !current.equals(north))
                || (localZ == 15 && !current.equals(south));
    }
}
