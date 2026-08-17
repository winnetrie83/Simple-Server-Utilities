package be.winnetrie.mod.simpleserverutilities.dimension;

import java.util.Locale;

import net.minecraft.resources.ResourceLocation;

/** Persistent, admin-owned definition for one SSU datapack dimension. */
public final class ManagedDimensionDefinition {
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "new_dimension";
    public String displayName = "New Dimension";
    public String preset = DimensionPreset.OVERWORLD.name();
    public boolean enabled = true;

    // Dimension type options.
    public boolean piglinSafe;
    public boolean natural = true;
    public boolean ultrawarm;
    public boolean hasSkylight = true;
    public boolean hasCeiling;
    public boolean bedWorks = true;
    public boolean respawnAnchorWorks;
    public boolean hasRaids = true;
    public double coordinateScale = 1.0D;
    public float ambientLight;
    public int minY = -64;
    public int height = 384;
    public int logicalHeight = 384;
    public long fixedTime = -1L;
    public int monsterSpawnBlockLightLimit;
    public int monsterSpawnLightLevel = 7;
    public String effects = "minecraft:overworld";
    public String infiniburn = "#minecraft:infiniburn_overworld";

    // Flat / empty generator options.
    public String biome = "minecraft:plains";
    public boolean generateFeatures = true;
    public boolean generateLakes;
    public String bottomBlock = "minecraft:bedrock";
    public int bottomLayers = 1;
    public String middleBlock = "minecraft:dirt";
    public int middleLayers = 2;
    public String topBlock = "minecraft:grass_block";
    public int topLayers = 1;

    // Empty preset platform.
    public String platformBlock = "minecraft:stone_bricks";
    public int platformSize = 9;
    public int platformY = 64;
    public boolean platformInitialized;

    public ManagedDimensionDefinition() {
    }

    public static ManagedDimensionDefinition preset(String rawId, String rawDisplayName, DimensionPreset preset) {
        ManagedDimensionDefinition value = new ManagedDimensionDefinition();
        value.id = rawId;
        value.displayName = rawDisplayName;
        value.applyPreset(preset);
        value.normalize();
        return value;
    }

    public void applyPreset(DimensionPreset requested) {
        DimensionPreset value = requested == null ? DimensionPreset.OVERWORLD : requested;
        preset = value.name();
        fixedTime = -1L;
        monsterSpawnBlockLightLimit = 0;
        monsterSpawnLightLevel = 7;
        platformSize = 9;
        platformY = 64;
        platformBlock = "minecraft:stone_bricks";
        switch (value) {
            case NETHER -> {
                piglinSafe = true;
                natural = false;
                ultrawarm = true;
                hasSkylight = false;
                hasCeiling = true;
                bedWorks = false;
                respawnAnchorWorks = true;
                hasRaids = false;
                coordinateScale = 8.0D;
                ambientLight = 0.1F;
                minY = 0;
                height = 256;
                logicalHeight = 128;
                monsterSpawnBlockLightLimit = 15;
                monsterSpawnLightLevel = 7;
                effects = "minecraft:the_nether";
                infiniburn = "#minecraft:infiniburn_nether";
                biome = "minecraft:nether_wastes";
                generateFeatures = true;
                generateLakes = false;
            }
            case END -> {
                piglinSafe = false;
                natural = false;
                ultrawarm = false;
                hasSkylight = true;
                hasCeiling = false;
                bedWorks = false;
                respawnAnchorWorks = false;
                hasRaids = true;
                coordinateScale = 1.0D;
                ambientLight = 0.25F;
                minY = 0;
                height = 256;
                logicalHeight = 256;
                fixedTime = -1L;
                monsterSpawnBlockLightLimit = 0;
                monsterSpawnLightLevel = 15;
                effects = "minecraft:the_end";
                infiniburn = "#minecraft:infiniburn_end";
                biome = "minecraft:the_end";
                generateFeatures = true;
                generateLakes = false;
            }
            case FLAT -> {
                setOverworldTypeDefaults();
                biome = "minecraft:plains";
                generateFeatures = true;
                generateLakes = false;
                bottomBlock = "minecraft:bedrock";
                bottomLayers = 1;
                middleBlock = "minecraft:dirt";
                middleLayers = 2;
                topBlock = "minecraft:grass_block";
                topLayers = 1;
            }
            case EMPTY -> {
                setOverworldTypeDefaults();
                biome = "minecraft:the_void";
                generateFeatures = false;
                generateLakes = false;
                bottomLayers = 0;
                middleLayers = 0;
                topLayers = 0;
            }
            case OVERWORLD -> {
                setOverworldTypeDefaults();
                biome = "minecraft:plains";
                generateFeatures = true;
                generateLakes = false;
            }
        }
    }

    private void setOverworldTypeDefaults() {
        piglinSafe = false;
        natural = true;
        ultrawarm = false;
        hasSkylight = true;
        hasCeiling = false;
        bedWorks = true;
        respawnAnchorWorks = false;
        hasRaids = true;
        coordinateScale = 1.0D;
        ambientLight = 0.0F;
        minY = -64;
        height = 384;
        logicalHeight = 384;
        effects = "minecraft:overworld";
        infiniburn = "#minecraft:infiniburn_overworld";
    }

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = normalizePath(id);
        displayName = clean(displayName, 64);
        if (displayName.isBlank()) displayName = title(id);
        preset = DimensionPreset.parse(preset).name();
        coordinateScale = clamp(coordinateScale, 0.00001D, 3.0E7D);
        ambientLight = (float) clamp(ambientLight, 0.0D, 1.0D);
        minY = align16(Math.max(-2032, Math.min(2016, minY)));
        height = align16(Math.max(16, Math.min(4064, height)));
        if (minY + height > 2032) height = align16(Math.max(16, 2032 - minY));
        logicalHeight = Math.max(1, Math.min(height, logicalHeight));
        fixedTime = fixedTime < 0 ? -1L : Math.floorMod(fixedTime, 24000L);
        monsterSpawnBlockLightLimit = Math.max(0, Math.min(15, monsterSpawnBlockLightLimit));
        monsterSpawnLightLevel = Math.max(0, Math.min(15, monsterSpawnLightLevel));
        effects = normalizeIdentifier(effects, "minecraft:overworld");
        infiniburn = normalizeTag(infiniburn, "#minecraft:infiniburn_overworld");
        biome = normalizeIdentifier(biome, "minecraft:plains");
        bottomBlock = normalizeIdentifier(bottomBlock, "minecraft:bedrock");
        middleBlock = normalizeIdentifier(middleBlock, "minecraft:dirt");
        topBlock = normalizeIdentifier(topBlock, "minecraft:grass_block");
        bottomLayers = Math.max(0, Math.min(256, bottomLayers));
        middleLayers = Math.max(0, Math.min(256, middleLayers));
        topLayers = Math.max(0, Math.min(256, topLayers));
        platformBlock = normalizeIdentifier(platformBlock, "minecraft:stone_bricks");
        platformSize = Math.max(1, Math.min(63, platformSize));
        if ((platformSize & 1) == 0) platformSize++;
        platformY = Math.max(minY + 1, Math.min(minY + height - 2, platformY));
    }

    public DimensionPreset presetValue() {
        return DimensionPreset.parse(preset);
    }

    public String resourceId() {
        return "simpleserverutilities:" + id;
    }

    public boolean isEmptyPreset() {
        return presetValue() == DimensionPreset.EMPTY;
    }

    private static String normalizePath(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        value = value.replaceAll("[^a-z0-9_./-]", "_");
        while (value.contains("..")) value = value.replace("..", ".");
        while (value.startsWith("/")) value = value.substring(1);
        if (value.isBlank()) value = "new_dimension";
        if (value.length() > 64) value = value.substring(0, 64);
        ResourceLocation.parse("simpleserverutilities:" + value);
        return value;
    }

    private static String normalizeIdentifier(String raw, String fallback) {
        try {
            return ResourceLocation.parse(raw == null ? fallback : raw.trim().toLowerCase(Locale.ROOT)).toString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String normalizeTag(String raw, String fallback) {
        String value = raw == null ? fallback : raw.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("#")) value = "#" + value;
        try {
            ResourceLocation.parse(value.substring(1));
            return value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String clean(String raw, int maximum) {
        String value = raw == null ? "" : raw.trim();
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static String title(String raw) {
        String[] parts = raw.replace('/', ' ').replace('_', ' ').replace('-', ' ').split(" +");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private static int align16(int value) {
        return Math.floorDiv(value, 16) * 16;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
