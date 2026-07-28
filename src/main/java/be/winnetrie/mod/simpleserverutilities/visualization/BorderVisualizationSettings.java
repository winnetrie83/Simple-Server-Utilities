package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.EnumMap;
import java.util.Map;

public final class BorderVisualizationSettings {

    private int schemaVersion = 1;
    private Map<BorderCategory, Integer> colors = new EnumMap<>(BorderCategory.class);
    private int viewDistanceChunks = 16;
    private int claimVerticalRange = 64;

    public BorderVisualizationSettings() {
        ensureDefaults();
    }

    public void ensureDefaults() {
        if (colors == null) {
            colors = new EnumMap<>(BorderCategory.class);
        }
        for (BorderCategory category : BorderCategory.values()) {
            colors.putIfAbsent(category, category.defaultRgb());
        }
        viewDistanceChunks = Math.max(2, Math.min(32, viewDistanceChunks));
        claimVerticalRange = Math.max(8, Math.min(256, claimVerticalRange));
    }

    public int getRgb(BorderCategory category) {
        ensureDefaults();
        return colors.getOrDefault(category, category.defaultRgb()) & 0xFFFFFF;
    }

    public void setRgb(BorderCategory category, int rgb) {
        ensureDefaults();
        colors.put(category, rgb & 0xFFFFFF);
    }

    public void reset(BorderCategory category) {
        ensureDefaults();
        colors.put(category, category.defaultRgb());
    }

    public void resetAll() {
        colors = new EnumMap<>(BorderCategory.class);
        ensureDefaults();
    }

    public int getStrokeArgb(BorderCategory category) {
        return 0xFF000000 | getRgb(category);
    }

    public int getFillArgb(BorderCategory category) {
        return 0x28000000 | getRgb(category);
    }

    public int getViewDistanceChunks() {
        ensureDefaults();
        return viewDistanceChunks;
    }

    public int getClaimVerticalRange() {
        ensureDefaults();
        return claimVerticalRange;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }
}
