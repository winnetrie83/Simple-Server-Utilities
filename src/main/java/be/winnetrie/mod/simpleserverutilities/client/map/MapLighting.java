package be.winnetrie.mod.simpleserverutilities.client.map;

import net.minecraft.client.multiplayer.ClientLevel;

/** Shared day/night tone and cached surface-light treatment for every SSU map. */
public final class MapLighting {
    private MapLighting() {
    }

    public static int nightBucket(ClientLevel level) {
        int darken = Math.max(0, Math.min(15, level.getSkyDarken()));
        if (darken >= 8) return 2;
        if (darken >= 3) return 1;
        return 0;
    }

    public static int apply(ClientLevel level, int worldX, int worldZ, int argb) {
        if (((argb >>> 24) & 0xFF) == 0 || argb == TerrainColorSampler.VOID_COLOR) return argb;
        int skyDarken = Math.max(0, Math.min(15, level.getSkyDarken()));
        if (skyDarken <= 2) return argb;

        int blockLight = AerialMapAtlas.blockLightAvailable(level, worldX, worldZ, 0);
        double night = (skyDarken - 2) / 13.0D;
        double ambient = 1.0D - night * 0.60D;
        double local = blockLight / 15.0D;
        double brightness = Math.max(ambient, 0.42D + local * 0.78D);
        brightness = Math.min(1.20D, brightness);

        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        // Strong block light remains easy to find at night without turning the map into a heatmap.
        double warm = local * night;
        red = clamp((int) Math.round(red * brightness + 34.0D * warm));
        green = clamp((int) Math.round(green * brightness + 20.0D * warm));
        blue = clamp((int) Math.round(blue * brightness + 5.0D * warm));
        return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
