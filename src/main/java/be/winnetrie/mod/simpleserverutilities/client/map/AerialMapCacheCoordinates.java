package be.winnetrie.mod.simpleserverutilities.client.map;

/** Pure cache key helpers kept separate so negative coordinates are testable. */
public final class AerialMapCacheCoordinates {

    public static final int REGION_SIZE = 32;

    private AerialMapCacheCoordinates() {
    }

    public static int regionCoordinate(int chunkCoordinate) {
        return Math.floorDiv(chunkCoordinate, REGION_SIZE);
    }

    public static String safeFileComponent(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String safe = value.replace(':', '_')
                .replace('/', '_')
                .replace('\\', '_')
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            return "unknown";
        }
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }
}
