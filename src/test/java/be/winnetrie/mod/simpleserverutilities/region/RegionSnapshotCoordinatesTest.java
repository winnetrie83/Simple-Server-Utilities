package be.winnetrie.mod.simpleserverutilities.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RegionSnapshotCoordinatesTest {

    @Test
    void roundTripsNegativeWorldCoordinates() {
        int packed = RegionSnapshotCoordinates.pack(-128, -64, 32, 384, 48, -97, 17, 63);
        RegionSnapshotCoordinates.Decoded decoded = RegionSnapshotCoordinates.unpack(
                -128, -64, 32, 384, 48, packed
        );
        assertEquals(-97, decoded.x());
        assertEquals(17, decoded.y());
        assertEquals(63, decoded.z());
    }

    @Test
    void preservesFirstAndLastCoordinates() {
        int first = RegionSnapshotCoordinates.pack(10, 20, 30, 4, 5, 10, 20, 30);
        int last = RegionSnapshotCoordinates.pack(10, 20, 30, 4, 5, 12, 23, 34);
        assertEquals(new RegionSnapshotCoordinates.Decoded(10, 20, 30),
                RegionSnapshotCoordinates.unpack(10, 20, 30, 4, 5, first));
        assertEquals(new RegionSnapshotCoordinates.Decoded(12, 23, 34),
                RegionSnapshotCoordinates.unpack(10, 20, 30, 4, 5, last));
    }

    @Test
    void rejectsCoordinatesOutsideTheSnapshot() {
        assertThrows(IllegalArgumentException.class,
                () -> RegionSnapshotCoordinates.pack(0, 0, 0, 16, 16, 0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> RegionSnapshotCoordinates.pack(0, 0, 0, 16, 16, 0, 16, 0));
        assertThrows(IllegalArgumentException.class,
                () -> RegionSnapshotCoordinates.unpack(0, 0, 0, 16, 16, -1));
    }
}
