package be.winnetrie.mod.simpleserverutilities.mapmarker;

import java.util.UUID;

/** One personal, server-persisted map marker. */
public final class MapMarker {
    public static final int CURRENT_SCHEMA = 1;

    private int schema = CURRENT_SCHEMA;
    private String id = UUID.randomUUID().toString();
    private String name = "Marker";
    private String dimension = "minecraft:overworld";
    private int x;
    private int y;
    private int z;
    private int colorArgb = 0xFFFFD54F;

    public MapMarker() {
        // Gson.
    }

    public MapMarker(UUID id, String name, String dimension, int x, int y, int z, int colorArgb) {
        this.id = (id == null ? UUID.randomUUID() : id).toString();
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.colorArgb = colorArgb;
        normalize();
    }

    public void normalize() {
        schema = CURRENT_SCHEMA;
        try {
            UUID.fromString(id);
        } catch (Exception ignored) {
            id = UUID.randomUUID().toString();
        }
        name = limit(name == null || name.isBlank() ? "Marker" : name.trim(), 40);
        dimension = limit(dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.trim(), 128);
        x = clamp(x, -30_000_000, 30_000_000);
        z = clamp(z, -30_000_000, 30_000_000);
        y = clamp(y, -4_096, 4_096);
        colorArgb = 0xFF000000 | (colorArgb & 0x00FFFFFF);
    }

    public UUID id() { return UUID.fromString(id); }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int colorArgb() { return colorArgb; }

    public void update(String name, int x, int y, int z, int colorArgb) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.colorArgb = colorArgb;
        normalize();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
