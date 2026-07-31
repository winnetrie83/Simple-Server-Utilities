package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.EnumMap;
import java.util.Map;

public final class BorderVisualizationSettings {

    private int schemaVersion = 2;
    private Map<BorderCategory, Integer> colors = new EnumMap<>(BorderCategory.class);
    /** Legacy schema-1 value, retained for transparent migration only. */
    private int viewDistanceChunks = 16;
    private int claimRenderDistanceBlocks = 128;
    private int regionRenderDistanceBlocks = 128;
    private int claimVerticalRange = 64;

    public BorderVisualizationSettings() {
        ensureDefaults();
    }

    public void ensureDefaults() {
        if (colors == null) colors = new EnumMap<>(BorderCategory.class);
        for (BorderCategory category : BorderCategory.values()) {
            colors.putIfAbsent(category, category.defaultRgb());
        }
        if (schemaVersion < 2) {
            int migrated = Math.max(32, Math.min(512, viewDistanceChunks * 16));
            claimRenderDistanceBlocks = migrated;
            regionRenderDistanceBlocks = migrated;
            schemaVersion = 2;
        }
        claimRenderDistanceBlocks = clampDistance(claimRenderDistanceBlocks);
        regionRenderDistanceBlocks = clampDistance(regionRenderDistanceBlocks);
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

    public int getStrokeArgb(BorderCategory category) { return 0xFF000000 | getRgb(category); }
    public int getFillArgb(BorderCategory category) { return 0x28000000 | getRgb(category); }

    public int getClaimRenderDistanceBlocks() {
        ensureDefaults();
        return claimRenderDistanceBlocks;
    }

    public void setClaimRenderDistanceBlocks(int blocks) {
        claimRenderDistanceBlocks = clampDistance(blocks);
        schemaVersion = 2;
    }

    public int getRegionRenderDistanceBlocks() {
        ensureDefaults();
        return regionRenderDistanceBlocks;
    }

    public void setRegionRenderDistanceBlocks(int blocks) {
        regionRenderDistanceBlocks = clampDistance(blocks);
        schemaVersion = 2;
    }

    /** Compatibility helper for older callers. */
    @Deprecated
    public int getViewDistanceChunks() {
        return Math.max(2, Math.max(getClaimRenderDistanceBlocks(), getRegionRenderDistanceBlocks()) / 16);
    }

    public int getClaimVerticalRange() {
        ensureDefaults();
        return claimVerticalRange;
    }

    public int getSchemaVersion() { return schemaVersion; }

    private static int clampDistance(int blocks) {
        return Math.max(16, Math.min(512, blocks));
    }
}
