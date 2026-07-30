package be.winnetrie.mod.simpleserverutilities.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainReliefTest {

    @Test
    void flatTerrainHasNeutralLightingAtEveryScale() {
        assertEquals(1.0D, TerrainReliefMath.localReliefLight(
                64, 64, 64, 64, 64, 64, 64, 64, 64
        ), 0.000001D);
        assertEquals(1.0D, TerrainReliefMath.directionalTerraceLight(
                64, 64, 64, 64, 64, 64, 64, 64, 64
        ), 0.000001D);
        assertEquals(1.0D, TerrainReliefMath.broadReliefLight(
                64, 64, 64, 64, 64
        ), 0.000001D);
        assertEquals(1.0D, TerrainReliefMath.macroReliefLight(
                64, 64, 64, 64, 64
        ), 0.000001D);
        assertEquals(1.0D, TerrainReliefMath.canopyEdgeLight(
                true, true, true, true
        ), 0.000001D);
    }

    @Test
    void identicalSlopeDoesNotChangeWithAbsoluteElevation() {
        double lowerSlope = TerrainReliefMath.localReliefLight(
                62, 63, 64, 63, 64, 65, 64, 65, 66
        );
        double higherSlope = TerrainReliefMath.localReliefLight(
                82, 83, 84, 83, 84, 85, 84, 85, 86
        );
        assertEquals(lowerSlope, higherSlope, 0.000001D);

        double lowerTerrace = TerrainReliefMath.directionalTerraceLight(
                64, 63, 63, 62, 62, 62, 60, 61, 61
        );
        double higherTerrace = TerrainReliefMath.directionalTerraceLight(
                84, 83, 83, 82, 82, 82, 80, 81, 81
        );
        assertEquals(lowerTerrace, higherTerrace, 0.000001D);

        double lowerLandform = TerrainReliefMath.macroReliefLight(58, 58, 64, 70, 70);
        double higherLandform = TerrainReliefMath.macroReliefLight(78, 78, 84, 90, 90);
        assertEquals(lowerLandform, higherLandform, 0.000001D);
    }

    @Test
    void oppositeSlopesReceiveDifferentDirectionalShading() {
        double northWestFacing = TerrainReliefMath.macroReliefLight(70, 70, 64, 58, 58);
        double southEastFacing = TerrainReliefMath.macroReliefLight(58, 58, 64, 70, 70);
        assertTrue(northWestFacing > southEastFacing);
    }

    @Test
    void canopyNeighbourHeightDoesNotTurnTreeEdgesIntoCliffs() {
        assertEquals(79, TerrainReliefMath.canopyNeighbourHeight(80, 64, false));
        assertEquals(77, TerrainReliefMath.canopyNeighbourHeight(80, 60, true));
        assertEquals(83, TerrainReliefMath.canopyNeighbourHeight(80, 100, true));
        assertEquals(81, TerrainReliefMath.canopyNeighbourHeight(80, 81, true));
    }

    @Test
    void canopyEdgesUseAConsistentNorthWestLightDirection() {
        double exposedNorthWest = TerrainReliefMath.canopyEdgeLight(false, false, true, true);
        double exposedSouthEast = TerrainReliefMath.canopyEdgeLight(true, true, false, false);
        assertTrue(exposedNorthWest > 1.0D);
        assertTrue(exposedSouthEast < 1.0D);
        assertTrue(exposedNorthWest > exposedSouthEast);
    }
}
