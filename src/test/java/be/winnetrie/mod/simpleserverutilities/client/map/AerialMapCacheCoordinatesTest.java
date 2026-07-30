package be.winnetrie.mod.simpleserverutilities.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AerialMapCacheCoordinatesTest {

    @Test
    void groupsNegativeChunksWithFloorDivision() {
        assertEquals(0, AerialMapCacheCoordinates.regionCoordinate(0));
        assertEquals(0, AerialMapCacheCoordinates.regionCoordinate(31));
        assertEquals(1, AerialMapCacheCoordinates.regionCoordinate(32));
        assertEquals(-1, AerialMapCacheCoordinates.regionCoordinate(-1));
        assertEquals(-1, AerialMapCacheCoordinates.regionCoordinate(-32));
        assertEquals(-2, AerialMapCacheCoordinates.regionCoordinate(-33));
    }

    @Test
    void sanitizesDimensionAndPackNames() {
        assertEquals("minecraft_overworld", AerialMapCacheCoordinates.safeFileComponent("minecraft:overworld"));
        assertEquals("pack_name", AerialMapCacheCoordinates.safeFileComponent("pack / name"));
        assertEquals("unknown", AerialMapCacheCoordinates.safeFileComponent("  "));
        assertEquals("unknown", AerialMapCacheCoordinates.safeFileComponent(".."));
    }
}
