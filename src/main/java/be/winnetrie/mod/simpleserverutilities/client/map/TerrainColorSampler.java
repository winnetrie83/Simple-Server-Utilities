package be.winnetrie.mod.simpleserverutilities.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Resource-pack-aware aerial column sampler shared by all SSU maps.
 *
 * <p>The renderer keeps two independent heights for every map pixel. The
 * terrain height ignores decorative plants and tree crowns and is used for
 * broad hill shading. The mapped-surface height includes solid blocks, water
 * and leaf canopies and is used for local object/canopy relief. This keeps
 * mountains readable underneath forests while allowing trees to remain dark,
 * coherent and visibly three-dimensional.</p>
 */
public final class TerrainColorSampler {

    public static final int VOID_COLOR = 0xFF15191E;
    public static final int DETAIL = BlockTexturePalette.DETAIL;
    public static final int DETAIL_PIXELS = DETAIL * DETAIL;

    static final byte SURFACE_SOLID = 0;
    static final byte SURFACE_CANOPY = 1;
    static final byte SURFACE_WATER = 2;

    private static final int MAX_VERTICAL_SCAN = 72;
    private static final int MAX_LAYERS = 7;
    private static final int ALMOST_OPAQUE = 250;
    private static final ThreadLocal<BlockPos.MutableBlockPos> RELIEF_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private TerrainColorSampler() {
    }

    /**
     * Samples one complete top-down column into {@code destination}. The
     * destination receives {@link #DETAIL_PIXELS} colours starting at offset.
     * Height metadata can be read from {@code scratch} after this call.
     */
    static int sampleColumn(
            ClientLevel level,
            LevelChunk chunk,
            BlockPos.MutableBlockPos pos,
            int worldX,
            int worldZ,
            int[] destination,
            int offset,
            Scratch scratch
    ) {
        scratch.reset();

        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        int rawSurface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        int minY = level.getMinY();
        int y = Math.min(rawSurface, level.getMaxY());
        int terrainHeight = Integer.MIN_VALUE;
        int mappedSurfaceHeight = Integer.MIN_VALUE;
        byte mappedSurfaceKind = SURFACE_SOLID;
        boolean canopyAbove = false;
        boolean visualComplete = false;
        int accumulatedOpacity = 0;

        for (int attempts = 0; attempts < MAX_VERTICAL_SCAN && y >= minY; attempts++, y--) {
            pos.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            boolean water = state.getFluidState().is(FluidTags.WATER);
            boolean leaves = state.is(BlockTags.LEAVES);
            MapColor mapColor = safeMapColor(state, level, pos);
            boolean decorative = !water && !leaves
                    && isDecorativeSurface(state, level, pos, mapColor);

            if (mappedSurfaceHeight == Integer.MIN_VALUE && !decorative) {
                mappedSurfaceHeight = y;
                mappedSurfaceKind = leaves
                        ? SURFACE_CANOPY
                        : water ? SURFACE_WATER : SURFACE_SOLID;
            }

            if (leaves) {
                canopyAbove = true;
            } else if (terrainHeight == Integer.MIN_VALUE
                    && !decorative
                    && !(canopyAbove && state.is(BlockTags.LOGS))) {
                terrainHeight = y;
            }

            if (visualComplete || decorative || scratch.layerCount >= MAX_LAYERS) {
                if (visualComplete && terrainHeight != Integer.MIN_VALUE) {
                    break;
                }
                continue;
            }

            BlockTexturePalette.TextureProfile profile = water
                    ? null
                    : BlockTexturePalette.profile(state, level, pos);
            int tint = water
                    ? 0xFFFFFFFF
                    : BlockTexturePalette.tint(state, level, pos);

            if (!water && mapColor == MapColor.NONE && profile.alphaCoverage() < 0.035F) {
                continue;
            }

            int depth = 0;
            int waterSurfaceY = y;
            if (water) {
                int waterY = y;
                int minimumWaterY = Math.max(minY, y - 24);
                while (waterY >= minimumWaterY) {
                    pos.set(worldX, waterY, worldZ);
                    if (!chunk.getBlockState(pos).getFluidState().is(FluidTags.WATER)) {
                        break;
                    }
                    depth++;
                    waterY--;
                }
                y = waterY + 1;
                pos.set(worldX, waterSurfaceY, worldZ);
            }

            int alpha = layerAlpha(profile, water, leaves, depth);
            if (alpha <= 0) {
                continue;
            }

            if (water) {
                int biomeWaterColor = 0xFF000000 | BiomeColors.getAverageWaterColor(level, pos);
                scratch.pushWater(biomeWaterColor, alpha, depth);
            } else {
                scratch.push(profile, tint, alpha);
            }
            accumulatedOpacity = alpha + accumulatedOpacity * (255 - alpha) / 255;
            visualComplete = alpha >= ALMOST_OPAQUE || accumulatedOpacity >= ALMOST_OPAQUE;
            if (visualComplete && terrainHeight != Integer.MIN_VALUE) {
                break;
            }
        }

        if (terrainHeight == Integer.MIN_VALUE) {
            terrainHeight = rawSurface;
        }
        if (mappedSurfaceHeight == Integer.MIN_VALUE) {
            mappedSurfaceHeight = terrainHeight;
            mappedSurfaceKind = SURFACE_SOLID;
        }
        scratch.setHeightMetadata(terrainHeight, mappedSurfaceHeight, mappedSurfaceKind);

        if (scratch.layerCount == 0) {
            for (int index = 0; index < DETAIL_PIXELS; index++) {
                destination[offset + index] = VOID_COLOR;
            }
            return terrainHeight;
        }

        for (int detailZ = 0; detailZ < DETAIL; detailZ++) {
            for (int detailX = 0; detailX < DETAIL; detailX++) {
                int detailIndex = detailZ * DETAIL + detailX;
                int result = VOID_COLOR;
                for (int layer = scratch.layerCount - 1; layer >= 0; layer--) {
                    int color = scratch.colors[layer * DETAIL_PIXELS + detailIndex];
                    if (scratch.water[layer]) {
                        color = waterGrade(color, scratch.depth[layer]);
                    }
                    result = alphaOver(result, color, scratch.alpha[layer]);
                }
                destination[offset + detailIndex] = gradeForSurface(result, mappedSurfaceKind);
            }
        }

        return terrainHeight;
    }

    static int surfaceHeight(ClientLevel level, int worldX, int worldZ, int fallback) {
        return terrainHeight(heightSample(level, worldX, worldZ, fallback, fallback, SURFACE_SOLID));
    }

    /**
     * Returns packed terrain height, mapped-surface height and mapped-surface
     * kind. It is used only for a narrow neighbour margin and never force-loads
     * chunks.
     */
    static long heightSample(
            ClientLevel level,
            int worldX,
            int worldZ,
            int fallbackTerrain,
            int fallbackSurface,
            byte fallbackKind
    ) {
        LevelChunk chunk = level.getChunkSource().getChunk(
                worldX >> 4,
                worldZ >> 4,
                ChunkStatus.FULL,
                false
        );
        if (chunk == null) {
            return packHeightSample(fallbackTerrain, fallbackSurface, fallbackKind);
        }
        return findHeightSample(level, chunk, RELIEF_POS.get(), worldX, worldZ);
    }

    static int terrainHeight(long packed) {
        return (short) (packed >> 24);
    }

    static int mappedSurfaceHeight(long packed) {
        return (short) (packed >> 8);
    }

    static byte mappedSurfaceKind(long packed) {
        return (byte) packed;
    }

    private static long findHeightSample(
            ClientLevel level,
            LevelChunk chunk,
            BlockPos.MutableBlockPos pos,
            int worldX,
            int worldZ
    ) {
        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        int raw = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        int minY = level.getMinY();
        int y = Math.min(raw, level.getMaxY());
        int terrainHeight = Integer.MIN_VALUE;
        int surfaceHeight = Integer.MIN_VALUE;
        byte surfaceKind = SURFACE_SOLID;
        boolean canopyAbove = false;

        for (int attempts = 0; attempts < MAX_VERTICAL_SCAN && y >= minY; attempts++, y--) {
            pos.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            boolean water = state.getFluidState().is(FluidTags.WATER);
            boolean leaves = state.is(BlockTags.LEAVES);
            MapColor mapColor = safeMapColor(state, level, pos);
            boolean decorative = !water && !leaves
                    && isDecorativeSurface(state, level, pos, mapColor);

            if (surfaceHeight == Integer.MIN_VALUE && !decorative) {
                surfaceHeight = y;
                surfaceKind = leaves ? SURFACE_CANOPY : water ? SURFACE_WATER : SURFACE_SOLID;
            }

            if (water) {
                if (terrainHeight == Integer.MIN_VALUE) {
                    terrainHeight = y;
                }
                break;
            }
            if (leaves) {
                canopyAbove = true;
                continue;
            }
            if (decorative) {
                continue;
            }
            if (canopyAbove && state.is(BlockTags.LOGS)) {
                continue;
            }
            terrainHeight = y;
            break;
        }

        if (terrainHeight == Integer.MIN_VALUE) {
            terrainHeight = raw;
        }
        if (surfaceHeight == Integer.MIN_VALUE) {
            surfaceHeight = terrainHeight;
        }
        return packHeightSample(terrainHeight, surfaceHeight, surfaceKind);
    }

    private static long packHeightSample(int terrainHeight, int surfaceHeight, byte kind) {
        return ((long) (terrainHeight & 0xFFFF) << 24)
                | ((long) (surfaceHeight & 0xFFFF) << 8)
                | (kind & 0xFFL);
    }

    private static boolean isDecorativeSurface(
            BlockState state,
            ClientLevel level,
            BlockPos pos,
            MapColor mapColor
    ) {
        if (state.getBlock() instanceof BushBlock) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        boolean emptyCollision = state.getCollisionShape(level, pos).isEmpty();
        if (emptyCollision && mapColor == MapColor.PLANT) {
            return true;
        }
        return emptyCollision && mapColor == MapColor.NONE && !state.canOcclude();
    }

    static int shade(int argb, double factor) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = clampChannel((int) Math.round(((argb >>> 16) & 0xFF) * factor));
        int green = clampChannel((int) Math.round(((argb >>> 8) & 0xFF) * factor));
        int blue = clampChannel((int) Math.round((argb & 0xFF) * factor));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    static double reliefLight(
            int northWest,
            int north,
            int northEast,
            int west,
            int current,
            int east,
            int southWest,
            int south,
            int southEast
    ) {
        return TerrainReliefMath.localReliefLight(
                northWest, north, northEast, west, current, east, southWest, south, southEast
        );
    }

    static double directionalTerraceLight(
            int current,
            int west,
            int north,
            int northWest,
            int westTwo,
            int northTwo,
            int northWestTwo,
            int westTwoNorthOne,
            int westOneNorthTwo
    ) {
        return TerrainReliefMath.directionalTerraceLight(
                current, west, north, northWest,
                westTwo, northTwo, northWestTwo, westTwoNorthOne, westOneNorthTwo
        );
    }

    static double broadReliefLight(int west, int north, int current, int east, int south) {
        return TerrainReliefMath.broadReliefLight(west, north, current, east, south);
    }

    static double macroReliefLight(int west, int north, int current, int east, int south) {
        return TerrainReliefMath.macroReliefLight(west, north, current, east, south);
    }

    static int canopyNeighbourHeight(int current, int neighbour, boolean neighbourCanopy) {
        return TerrainReliefMath.canopyNeighbourHeight(current, neighbour, neighbourCanopy);
    }

    static double canopyEdgeLight(
            boolean northCanopy,
            boolean westCanopy,
            boolean eastCanopy,
            boolean southCanopy
    ) {
        return TerrainReliefMath.canopyEdgeLight(
                northCanopy, westCanopy, eastCanopy, southCanopy
        );
    }

    private static int layerAlpha(
            BlockTexturePalette.TextureProfile profile,
            boolean water,
            boolean leaves,
            int depth
    ) {
        if (water) {
            return Math.max(96, Math.min(224, 92 + Math.min(18, depth) * 8));
        }
        if (leaves) {
            // A top-down map should show one continuous crown, not cutout holes.
            return 255;
        }

        float coverage = profile.alphaCoverage();
        if (coverage >= 0.985F) {
            return 255;
        }
        if (coverage >= 0.76F) {
            return 225;
        }
        if (coverage >= 0.48F) {
            return 188;
        }
        if (coverage >= 0.22F) {
            return 142;
        }
        if (coverage >= 0.06F) {
            return 92;
        }
        return 0;
    }

    private static int waterGrade(int color, int depth) {
        double depthShade = clamp(1.04D - Math.min(18, depth) * 0.016D, 0.74D, 1.02D);
        return shade(color, depthShade);
    }

    private static int gradeForSurface(int argb, byte surfaceKind) {
        if (surfaceKind == SURFACE_CANOPY) {
            // Biome foliage tints can be extremely bright. Compressing their
            // luminance makes forests read as coherent dark crowns while still
            // preserving the active resource pack's hue.
            int graded = grade(argb, 0.66D, 0.69D);
            double luminance = luminance(graded);
            if (luminance > 86.0D) {
                graded = shade(graded, 86.0D / luminance);
            } else if (luminance < 24.0D && luminance > 0.0D) {
                graded = shade(graded, 24.0D / luminance);
            }
            return graded;
        }
        if (surfaceKind == SURFACE_WATER) {
            return grade(argb, 0.74D, 0.90D);
        }
        return grade(argb, 0.72D, 0.90D);
    }

    private static double luminance(int argb) {
        return ((argb >>> 16) & 0xFF) * 0.2126D
                + ((argb >>> 8) & 0xFF) * 0.7152D
                + (argb & 0xFF) * 0.0722D;
    }

    private static MapColor safeMapColor(BlockState state, ClientLevel level, BlockPos pos) {
        try {
            return state.getMapColor(level, pos);
        } catch (Throwable ignored) {
            return MapColor.NONE;
        }
    }

    private static int alphaOver(int background, int foreground, int alpha) {
        if (alpha <= 0) {
            return background;
        }
        if (alpha >= 255 || background == VOID_COLOR) {
            return 0xFF000000 | (foreground & 0x00FFFFFF);
        }
        int inverse = 255 - alpha;
        int red = ((((foreground >>> 16) & 0xFF) * alpha)
                + (((background >>> 16) & 0xFF) * inverse)) / 255;
        int green = ((((foreground >>> 8) & 0xFF) * alpha)
                + (((background >>> 8) & 0xFF) * inverse)) / 255;
        int blue = (((foreground & 0xFF) * alpha)
                + ((background & 0xFF) * inverse)) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int grade(int argb, double saturation, double brightness) {
        int alpha = (argb >>> 24) & 0xFF;
        double red = ((argb >>> 16) & 0xFF) * brightness;
        double green = ((argb >>> 8) & 0xFF) * brightness;
        double blue = (argb & 0xFF) * brightness;
        double luminance = red * 0.2126D + green * 0.7152D + blue * 0.0722D;
        red = luminance + (red - luminance) * saturation;
        green = luminance + (green - luminance) * saturation;
        blue = luminance + (blue - luminance) * saturation;
        return (alpha << 24)
                | (clampChannel((int) Math.round(red)) << 16)
                | (clampChannel((int) Math.round(green)) << 8)
                | clampChannel((int) Math.round(blue));
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class Scratch {
        private final int[] colors = new int[MAX_LAYERS * DETAIL_PIXELS];
        private final int[] alpha = new int[MAX_LAYERS];
        private final int[] depth = new int[MAX_LAYERS];
        private final boolean[] water = new boolean[MAX_LAYERS];
        private int layerCount;
        private int terrainHeight;
        private int mappedSurfaceHeight;
        private byte mappedSurfaceKind;

        private void reset() {
            layerCount = 0;
            terrainHeight = Integer.MIN_VALUE;
            mappedSurfaceHeight = Integer.MIN_VALUE;
            mappedSurfaceKind = SURFACE_SOLID;
        }

        private void setHeightMetadata(int terrainHeight, int mappedSurfaceHeight, byte mappedSurfaceKind) {
            this.terrainHeight = terrainHeight;
            this.mappedSurfaceHeight = mappedSurfaceHeight;
            this.mappedSurfaceKind = mappedSurfaceKind;
        }

        int terrainHeight() {
            return terrainHeight;
        }

        int mappedSurfaceHeight() {
            return mappedSurfaceHeight;
        }

        byte mappedSurfaceKind() {
            return mappedSurfaceKind;
        }

        private void push(
                BlockTexturePalette.TextureProfile profile,
                int tint,
                int layerAlpha
        ) {
            int base = layerCount * DETAIL_PIXELS;
            int simplified = profile.tintedAverage(tint);
            for (int index = 0; index < DETAIL_PIXELS; index++) {
                colors[base + index] = simplified;
            }
            alpha[layerCount] = layerAlpha;
            water[layerCount] = false;
            depth[layerCount] = 0;
            layerCount++;
        }

        private void pushWater(int biomeWaterColor, int layerAlpha, int waterDepth) {
            int base = layerCount * DETAIL_PIXELS;
            for (int z = 0; z < DETAIL; z++) {
                for (int x = 0; x < DETAIL; x++) {
                    colors[base + z * DETAIL + x] = biomeWaterColor;
                }
            }
            alpha[layerCount] = layerAlpha;
            water[layerCount] = true;
            depth[layerCount] = waterDepth;
            layerCount++;
        }
    }
}
