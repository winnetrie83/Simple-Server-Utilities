package be.winnetrie.mod.simpleserverutilities.client.gui;

/** Pure drag-distance to chunk-pan conversion shared by both full-screen maps. */
final class MapPanMath {

    private MapPanMath() {
    }

    static int chunkDelta(double pixelDrag, double pixelsPerChunk) {
        return (int) Math.round(-pixelDrag / Math.max(1.0D, pixelsPerChunk));
    }
}
