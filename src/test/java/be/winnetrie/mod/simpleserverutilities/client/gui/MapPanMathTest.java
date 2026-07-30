package be.winnetrie.mod.simpleserverutilities.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapPanMathTest {

    @Test
    void convertsRightDragToOppositeMapCentreMovement() {
        assertEquals(-2, MapPanMath.chunkDelta(31.0D, 16.0D));
        assertEquals(2, MapPanMath.chunkDelta(-31.0D, 16.0D));
    }

    @Test
    void ignoresSubHalfChunkMovement() {
        assertEquals(0, MapPanMath.chunkDelta(7.0D, 16.0D));
    }
}
