package be.winnetrie.mod.simpleserverutilities.client.minimap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MinimapTerrainMapClaimOutlineTest {

    private static final UUID CLAIM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void hidesBordersBetweenChunksOfTheSameClaim() {
        assertFalse(ClaimOutlineMath.isOuterClaimEdge(
                CLAIM, CLAIM, CLAIM, CLAIM, CLAIM, 0, 0
        ));
        assertFalse(ClaimOutlineMath.isOuterClaimEdge(
                CLAIM, CLAIM, CLAIM, CLAIM, CLAIM, 15, 15
        ));
    }

    @Test
    void drawsOnlyTheActualClaimPerimeter() {
        assertTrue(ClaimOutlineMath.isOuterClaimEdge(
                CLAIM, null, CLAIM, CLAIM, CLAIM, 0, 7
        ));
        assertTrue(ClaimOutlineMath.isOuterClaimEdge(
                CLAIM, CLAIM, OTHER, CLAIM, CLAIM, 15, 7
        ));
        assertFalse(ClaimOutlineMath.isOuterClaimEdge(
                CLAIM, null, CLAIM, CLAIM, CLAIM, 1, 7
        ));
    }
}
