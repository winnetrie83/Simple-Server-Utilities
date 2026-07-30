package be.winnetrie.mod.simpleserverutilities.client.map;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

/**
 * Shared, resource-pack-aware persistent atlas for every SSU map.
 *
 * <p>Each loaded chunk is converted once to a 16x16 aerial tile: one
 * high-quality composite pixel per world block. A gamma-correct mip chain is built
 * alongside it, allowing the world map to select an appropriate detail level
 * instead of repeatedly averaging sharp pixels at every zoom. The minimap,
 * claim map and world map therefore share exactly the same terrain image.</p>
 */
public final class AerialMapAtlas {

    private static final int TILE_BLOCKS = 16;
    /** Bump whenever persisted pixels must be rebuilt by a new renderer. */
    private static final String RENDERER_FINGERPRINT = "atlas-topographic-v4-";
    private static final int DETAIL = TerrainColorSampler.DETAIL;
    private static final int BASE_TILE_PIXELS = TILE_BLOCKS * DETAIL;
    private static final int MAX_TILES_PER_DIMENSION = 2048;
    private static final int BACKGROUND_SCAN_RADIUS = 9;
    private static final int RELIEF_MARGIN = 8;
    private static final int RELIEF_GRID_SIZE = TILE_BLOCKS + RELIEF_MARGIN * 2;
    private static final int BACKGROUND_TILES_PER_TICK = 2;
    private static final long TILE_REFRESH_TICKS = 200L;
    private static final long ESTIMATED_TILE_BYTES = estimatedTileBytes();

    private static final float[] LINEAR = buildLinearTable();
    private static final Map<String, DimensionAtlas> DIMENSIONS = new LinkedHashMap<>();
    private static final Queue<Long> SCAN_QUEUE = new ArrayDeque<>();

    private static String scanDimension = "";
    private static int scanCenterChunkX = Integer.MIN_VALUE;
    private static int scanCenterChunkZ = Integer.MIN_VALUE;
    private static long atlasTick;
    private static int paletteGeneration = Integer.MIN_VALUE;
    private static String paletteFingerprint = "";
    private static String serverKey = "";
    private static ClientLevel observedLevel;
    private static long capturedTiles;
    private static long captureNanos;

    private AerialMapAtlas() {
    }

    public static void tick() {
        atlasTick++;
        Minecraft minecraft = Minecraft.getInstance();
        int currentPaletteGeneration = BlockTexturePalette.ensureCurrent();
        String currentFingerprint = RENDERER_FINGERPRINT + BlockTexturePalette.resourceFingerprint();

        if (currentPaletteGeneration != paletteGeneration) {
            if (paletteGeneration != Integer.MIN_VALUE) {
                if (!serverKey.isBlank() && currentFingerprint.equals(paletteFingerprint)) {
                    AerialMapStorage.invalidatePalette(minecraft, serverKey, paletteFingerprint);
                } else {
                    AerialMapStorage.closeSession();
                }
            }
            paletteGeneration = currentPaletteGeneration;
            paletteFingerprint = currentFingerprint;
            clearTilesOnly();
        }

        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }

        if (level != observedLevel) {
            observedLevel = level;
            String currentServerKey = AerialMapStorage.serverKey(minecraft);
            if (!currentServerKey.equals(serverKey)) {
                serverKey = currentServerKey;
                clearTilesOnly();
                AerialMapStorage.closeSession();
                AerialMapStorage.refreshStatistics(minecraft);
            }
        }

        installCompletedDiskTiles();

        String dimension = dimension(level);
        int centerX = minecraft.player.chunkPosition().x();
        int centerZ = minecraft.player.chunkPosition().z();
        if (!dimension.equals(scanDimension)
                || centerX != scanCenterChunkX
                || centerZ != scanCenterChunkZ
                || SCAN_QUEUE.isEmpty()) {
            rebuildScanQueue(dimension, centerX, centerZ);
        }

        int processed = 0;
        while (!SCAN_QUEUE.isEmpty() && processed < BACKGROUND_TILES_PER_TICK) {
            long key = SCAN_QUEUE.remove();
            int chunkX = keyX(key);
            int chunkZ = keyZ(key);
            SurfaceTile tile = captureIfLoaded(level, chunkX, chunkZ, false);
            if (tile == null) {
                requestDiskTile(level, chunkX, chunkZ);
            }
            processed++;
        }

        if (atlasTick % 1_200L == 0L) {
            Statistics statistics = statistics();
            SimpleServerUtilities.LOGGER.debug(
                    "SSU aerial atlas: memoryTiles={}, estimatedMemoryMiB={}, diskTiles={}, diskMiB={}, "
                            + "diskHits={}, diskMisses={}, pendingReads={}, queuedWrites={}, averageCaptureMicros={}",
                    statistics.memoryTiles(),
                    String.format(java.util.Locale.ROOT, "%.2f", statistics.estimatedMemoryBytes() / 1048576.0D),
                    statistics.disk().diskFiles(),
                    String.format(java.util.Locale.ROOT, "%.2f", statistics.disk().diskBytes() / 1048576.0D),
                    statistics.disk().readHits(), statistics.disk().readMisses(),
                    statistics.disk().pendingReads(), statistics.disk().queuedWrites(),
                    String.format(java.util.Locale.ROOT, "%.1f", statistics.averageCaptureMicros())
            );
        }
    }

    /** Returns one gamma-filtered colour per world block. */
    public static int sample(ClientLevel level, int worldX, int worldZ) {
        SurfaceTile tile = getOrCapture(level, worldX >> 4, worldZ >> 4);
        if (tile == null) {
            return TerrainColorSampler.VOID_COLOR;
        }
        return tile.blockColor(Math.floorMod(worldX, TILE_BLOCKS), Math.floorMod(worldZ, TILE_BLOCKS));
    }

    /** Returns a texture-derived subpixel for maps rendering several pixels per block. */
    public static int sampleDetail(
            ClientLevel level,
            int worldX,
            int worldZ,
            int pixelXWithinBlock,
            int pixelZWithinBlock,
            int outputPixelsPerBlock
    ) {
        SurfaceTile tile = getOrCapture(level, worldX >> 4, worldZ >> 4);
        if (tile == null) {
            return TerrainColorSampler.VOID_COLOR;
        }
        int detailX = Math.max(0, Math.min(DETAIL - 1,
                pixelXWithinBlock * DETAIL / Math.max(1, outputPixelsPerBlock)));
        int detailZ = Math.max(0, Math.min(DETAIL - 1,
                pixelZWithinBlock * DETAIL / Math.max(1, outputPixelsPerBlock)));
        return tile.detailColor(
                Math.floorMod(worldX, TILE_BLOCKS),
                Math.floorMod(worldZ, TILE_BLOCKS),
                detailX,
                detailZ
        );
    }

    /**
     * Samples the atlas at a fractional world coordinate using the mip level
     * appropriate for the requested world-block footprint of one screen pixel.
     */
    public static int sampleAtScale(
            ClientLevel level,
            double worldX,
            double worldZ,
            double blocksPerPixel
    ) {
        int blockX = floor(worldX);
        int blockZ = floor(worldZ);
        SurfaceTile tile = getOrCapture(level, blockX >> 4, blockZ >> 4);
        if (tile == null) {
            return TerrainColorSampler.VOID_COLOR;
        }

        double localX = Math.floorMod(blockX, TILE_BLOCKS) + fractional(worldX);
        double localZ = Math.floorMod(blockZ, TILE_BLOCKS) + fractional(worldZ);
        int levelIndex = mipLevel(blocksPerPixel, tile.mipmaps.length);
        return tile.sampleMip(levelIndex, localX, localZ);
    }

    /** Returns a colour only when it was explored/cached or is loaded now. */
    public static int sampleAvailable(ClientLevel level, int worldX, int worldZ) {
        return sample(level, worldX, worldZ);
    }

    /** Refreshes a loaded chunk tile after a visible map explicitly needs it. */
    public static void refreshLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        captureIfLoaded(level, chunkX, chunkZ, true);
    }

    public static void clear() {
        clearTilesOnly();
        AerialMapStorage.closeSession();
        BlockTexturePalette.clear();
        paletteGeneration = Integer.MIN_VALUE;
        paletteFingerprint = "";
        serverKey = "";
        observedLevel = null;
        atlasTick = 0L;
        capturedTiles = 0L;
        captureNanos = 0L;
    }

    private static void clearTilesOnly() {
        DIMENSIONS.clear();
        SCAN_QUEUE.clear();
        scanDimension = "";
        scanCenterChunkX = Integer.MIN_VALUE;
        scanCenterChunkZ = Integer.MIN_VALUE;
    }

    private static @Nullable SurfaceTile getOrCapture(ClientLevel level, int chunkX, int chunkZ) {
        SurfaceTile tile = tile(level, chunkX, chunkZ);
        if (tile == null || tile.paletteGeneration != paletteGeneration) {
            tile = captureIfLoaded(level, chunkX, chunkZ, false);
            if (tile == null) {
                requestDiskTile(level, chunkX, chunkZ);
            }
        }
        return tile;
    }

    private static @Nullable SurfaceTile tile(ClientLevel level, int chunkX, int chunkZ) {
        DimensionAtlas atlas = DIMENSIONS.get(dimension(level));
        return atlas == null ? null : atlas.tiles.get(key(chunkX, chunkZ));
    }

    private static @Nullable SurfaceTile captureIfLoaded(
            ClientLevel level,
            int chunkX,
            int chunkZ,
            boolean force
    ) {
        String dimension = dimension(level);
        DimensionAtlas atlas = DIMENSIONS.computeIfAbsent(dimension, ignored -> new DimensionAtlas());
        long key = key(chunkX, chunkZ);
        SurfaceTile existing = atlas.tiles.get(key);
        if (existing != null
                && existing.paletteGeneration == paletteGeneration
                && !force
                && atlasTick - existing.capturedTick < TILE_REFRESH_TICKS) {
            return existing;
        }

        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return existing;
        }

        long captureStarted = System.nanoTime();
        int[] blockDetails = new int[TILE_BLOCKS * TILE_BLOCKS * TerrainColorSampler.DETAIL_PIXELS];
        short[] terrainHeights = new short[TILE_BLOCKS * TILE_BLOCKS];
        short[] surfaceHeights = new short[TILE_BLOCKS * TILE_BLOCKS];
        byte[] surfaceKinds = new byte[TILE_BLOCKS * TILE_BLOCKS];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        TerrainColorSampler.Scratch scratch = new TerrainColorSampler.Scratch();

        for (int localZ = 0; localZ < TILE_BLOCKS; localZ++) {
            for (int localX = 0; localX < TILE_BLOCKS; localX++) {
                int blockIndex = localZ * TILE_BLOCKS + localX;
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;
                TerrainColorSampler.sampleColumn(
                        level,
                        chunk,
                        pos,
                        worldX,
                        worldZ,
                        blockDetails,
                        blockIndex * TerrainColorSampler.DETAIL_PIXELS,
                        scratch
                );
                terrainHeights[blockIndex] = clampedShort(scratch.terrainHeight());
                surfaceHeights[blockIndex] = clampedShort(scratch.mappedSurfaceHeight());
                surfaceKinds[blockIndex] = scratch.mappedSurfaceKind();
            }
        }

        HeightGrids grids = buildHeightGrids(
                level, chunkX, chunkZ, terrainHeights, surfaceHeights, surfaceKinds
        );
        int[] base = new int[BASE_TILE_PIXELS * BASE_TILE_PIXELS];
        for (int localZ = 0; localZ < TILE_BLOCKS; localZ++) {
            for (int localX = 0; localX < TILE_BLOCKS; localX++) {
                int terrain = heightAt(grids.terrain(), localX, localZ);
                double localLight = TerrainColorSampler.reliefLight(
                        heightAt(grids.terrain(), localX - 1, localZ - 1),
                        heightAt(grids.terrain(), localX, localZ - 1),
                        heightAt(grids.terrain(), localX + 1, localZ - 1),
                        heightAt(grids.terrain(), localX - 1, localZ),
                        terrain,
                        heightAt(grids.terrain(), localX + 1, localZ),
                        heightAt(grids.terrain(), localX - 1, localZ + 1),
                        heightAt(grids.terrain(), localX, localZ + 1),
                        heightAt(grids.terrain(), localX + 1, localZ + 1)
                );
                double terraceLight = TerrainColorSampler.directionalTerraceLight(
                        terrain,
                        heightAt(grids.terrain(), localX - 1, localZ),
                        heightAt(grids.terrain(), localX, localZ - 1),
                        heightAt(grids.terrain(), localX - 1, localZ - 1),
                        heightAt(grids.terrain(), localX - 2, localZ),
                        heightAt(grids.terrain(), localX, localZ - 2),
                        heightAt(grids.terrain(), localX - 2, localZ - 2),
                        heightAt(grids.terrain(), localX - 2, localZ - 1),
                        heightAt(grids.terrain(), localX - 1, localZ - 2)
                );
                double broadLight = TerrainColorSampler.broadReliefLight(
                        heightAt(grids.terrain(), localX - 3, localZ),
                        heightAt(grids.terrain(), localX, localZ - 3),
                        terrain,
                        heightAt(grids.terrain(), localX + 3, localZ),
                        heightAt(grids.terrain(), localX, localZ + 3)
                );
                double macroLight = TerrainColorSampler.macroReliefLight(
                        heightAt(grids.terrain(), localX - 8, localZ),
                        heightAt(grids.terrain(), localX, localZ - 8),
                        terrain,
                        heightAt(grids.terrain(), localX + 8, localZ),
                        heightAt(grids.terrain(), localX, localZ + 8)
                );
                double terrainLight = terraceLight * (1.0D
                        + (localLight - 1.0D) * 0.34D
                        + (broadLight - 1.0D) * 0.96D
                        + (macroLight - 1.0D) * 1.22D);

                byte kind = kindAt(grids.kinds(), localX, localZ);
                double light;
                if (kind == TerrainColorSampler.SURFACE_CANOPY) {
                    int surface = heightAt(grids.surface(), localX, localZ);
                    int northWest = canopyHeight(grids, localX - 1, localZ - 1, surface);
                    int north = canopyHeight(grids, localX, localZ - 1, surface);
                    int northEast = canopyHeight(grids, localX + 1, localZ - 1, surface);
                    int west = canopyHeight(grids, localX - 1, localZ, surface);
                    int east = canopyHeight(grids, localX + 1, localZ, surface);
                    int southWest = canopyHeight(grids, localX - 1, localZ + 1, surface);
                    int south = canopyHeight(grids, localX, localZ + 1, surface);
                    int southEast = canopyHeight(grids, localX + 1, localZ + 1, surface);
                    double crownRelief = TerrainColorSampler.reliefLight(
                            northWest, north, northEast, west, surface, east,
                            southWest, south, southEast
                    );
                    double crownEdge = TerrainColorSampler.canopyEdgeLight(
                            isCanopy(grids, localX, localZ - 1),
                            isCanopy(grids, localX - 1, localZ),
                            isCanopy(grids, localX + 1, localZ),
                            isCanopy(grids, localX, localZ + 1)
                    );
                    light = terrainLight * (1.0D + (crownRelief - 1.0D) * 0.82D) * crownEdge;
                    light = Math.max(0.60D, Math.min(1.14D, light));
                } else if (kind == TerrainColorSampler.SURFACE_WATER) {
                    light = 1.0D
                            + (broadLight - 1.0D) * 0.42D
                            + (macroLight - 1.0D) * 0.58D;
                    light = Math.max(0.78D, Math.min(1.10D, light));
                } else {
                    light = Math.max(0.72D, Math.min(1.18D, terrainLight));
                }

                int sourceBase = (localZ * TILE_BLOCKS + localX) * TerrainColorSampler.DETAIL_PIXELS;
                for (int detailZ = 0; detailZ < DETAIL; detailZ++) {
                    int targetRow = (localZ * DETAIL + detailZ) * BASE_TILE_PIXELS;
                    for (int detailX = 0; detailX < DETAIL; detailX++) {
                        int color = blockDetails[sourceBase + detailZ * DETAIL + detailX];
                        base[targetRow + localX * DETAIL + detailX] = color == TerrainColorSampler.VOID_COLOR
                                ? color
                                : TerrainColorSampler.shade(color, light);
                    }
                }
            }
        }

        SurfaceTile captured = new SurfaceTile(
                buildMipmaps(base, BASE_TILE_PIXELS),
                terrainHeights,
                surfaceHeights,
                surfaceKinds,
                atlasTick,
                paletteGeneration
        );
        atlas.tiles.put(key, captured);
        trim(atlas.tiles);
        capturedTiles++;
        captureNanos += Math.max(0L, System.nanoTime() - captureStarted);
        AerialMapStorage.write(
                Minecraft.getInstance(),
                serverKey,
                dimension,
                paletteFingerprint,
                chunkX,
                chunkZ,
                base,
                terrainHeights,
                surfaceHeights,
                surfaceKinds
        );
        return captured;
    }

    private static void requestDiskTile(ClientLevel level, int chunkX, int chunkZ) {
        if (serverKey.isBlank() || paletteFingerprint.isBlank()) {
            return;
        }
        AerialMapStorage.requestLoad(
                Minecraft.getInstance(), serverKey, dimension(level), paletteFingerprint, chunkX, chunkZ
        );
    }

    private static void installCompletedDiskTiles() {
        for (AerialMapStorage.LoadedTile loaded :
                AerialMapStorage.drainCompleted(serverKey, paletteFingerprint)) {
            if (loaded.basePixels().length != BASE_TILE_PIXELS * BASE_TILE_PIXELS
                    || loaded.terrainHeights().length != TILE_BLOCKS * TILE_BLOCKS
                    || loaded.surfaceHeights().length != TILE_BLOCKS * TILE_BLOCKS
                    || loaded.surfaceKinds().length != TILE_BLOCKS * TILE_BLOCKS) {
                continue;
            }
            DimensionAtlas atlas = DIMENSIONS.computeIfAbsent(loaded.dimension(), ignored -> new DimensionAtlas());
            long key = key(loaded.chunkX(), loaded.chunkZ());
            if (atlas.tiles.containsKey(key)) {
                continue;
            }
            atlas.tiles.put(
                    key,
                    new SurfaceTile(
                            buildMipmaps(loaded.basePixels(), BASE_TILE_PIXELS),
                            loaded.terrainHeights(),
                            loaded.surfaceHeights(),
                            loaded.surfaceKinds(),
                            atlasTick,
                            paletteGeneration
                    )
            );
            trim(atlas.tiles);
        }
    }

    public static Statistics statistics() {
        int memoryTiles = 0;
        for (DimensionAtlas atlas : DIMENSIONS.values()) {
            memoryTiles += atlas.tiles.size();
        }
        double averageCaptureMicros = capturedTiles == 0L
                ? 0.0D
                : captureNanos / 1_000.0D / capturedTiles;
        AerialMapStorage.Statistics disk = AerialMapStorage.statistics();
        return new Statistics(
                memoryTiles,
                memoryTiles * ESTIMATED_TILE_BYTES,
                DIMENSIONS.size(),
                capturedTiles,
                averageCaptureMicros,
                new DiskStatistics(
                        disk.readHits(),
                        disk.readMisses(),
                        disk.readFailures(),
                        disk.writes(),
                        disk.writeFailures(),
                        disk.prunedFiles(),
                        disk.pendingReads(),
                        disk.queuedWrites(),
                        disk.diskFiles(),
                        disk.diskBytes(),
                        disk.cacheLimitBytes()
                )
        );
    }

    private static HeightGrids buildHeightGrids(
            ClientLevel level,
            int chunkX,
            int chunkZ,
            short[] localTerrainHeights,
            short[] localSurfaceHeights,
            byte[] localSurfaceKinds
    ) {
        int[] terrain = new int[RELIEF_GRID_SIZE * RELIEF_GRID_SIZE];
        int[] surface = new int[RELIEF_GRID_SIZE * RELIEF_GRID_SIZE];
        byte[] kinds = new byte[RELIEF_GRID_SIZE * RELIEF_GRID_SIZE];
        for (int gridZ = 0; gridZ < RELIEF_GRID_SIZE; gridZ++) {
            int localZ = gridZ - RELIEF_MARGIN;
            for (int gridX = 0; gridX < RELIEF_GRID_SIZE; gridX++) {
                int localX = gridX - RELIEF_MARGIN;
                int terrainHeight;
                int surfaceHeight;
                byte surfaceKind;
                if (localX >= 0 && localX < TILE_BLOCKS
                        && localZ >= 0 && localZ < TILE_BLOCKS) {
                    int index = localZ * TILE_BLOCKS + localX;
                    terrainHeight = localTerrainHeights[index];
                    surfaceHeight = localSurfaceHeights[index];
                    surfaceKind = localSurfaceKinds[index];
                } else {
                    int worldX = (chunkX << 4) + localX;
                    int worldZ = (chunkZ << 4) + localZ;
                    int neighbourChunkX = worldX >> 4;
                    int neighbourChunkZ = worldZ >> 4;
                    SurfaceTile neighbour = tile(level, neighbourChunkX, neighbourChunkZ);
                    if (neighbour != null && neighbour.paletteGeneration == paletteGeneration) {
                        int index = Math.floorMod(worldZ, TILE_BLOCKS) * TILE_BLOCKS
                                + Math.floorMod(worldX, TILE_BLOCKS);
                        terrainHeight = neighbour.terrainHeights[index];
                        surfaceHeight = neighbour.surfaceHeights[index];
                        surfaceKind = neighbour.surfaceKinds[index];
                    } else {
                        int fallbackX = Math.max(0, Math.min(TILE_BLOCKS - 1, localX));
                        int fallbackZ = Math.max(0, Math.min(TILE_BLOCKS - 1, localZ));
                        int fallbackIndex = fallbackZ * TILE_BLOCKS + fallbackX;
                        long sample = TerrainColorSampler.heightSample(
                                level,
                                worldX,
                                worldZ,
                                localTerrainHeights[fallbackIndex],
                                localSurfaceHeights[fallbackIndex],
                                localSurfaceKinds[fallbackIndex]
                        );
                        terrainHeight = TerrainColorSampler.terrainHeight(sample);
                        surfaceHeight = TerrainColorSampler.mappedSurfaceHeight(sample);
                        surfaceKind = TerrainColorSampler.mappedSurfaceKind(sample);
                    }
                }
                int index = gridZ * RELIEF_GRID_SIZE + gridX;
                terrain[index] = terrainHeight;
                surface[index] = surfaceHeight;
                kinds[index] = surfaceKind;
            }
        }
        return new HeightGrids(terrain, surface, kinds);
    }

    private static int heightAt(int[] grid, int localX, int localZ) {
        return grid[(localZ + RELIEF_MARGIN) * RELIEF_GRID_SIZE
                + localX + RELIEF_MARGIN];
    }

    private static byte kindAt(byte[] grid, int localX, int localZ) {
        return grid[(localZ + RELIEF_MARGIN) * RELIEF_GRID_SIZE
                + localX + RELIEF_MARGIN];
    }

    private static boolean isCanopy(HeightGrids grids, int localX, int localZ) {
        return kindAt(grids.kinds(), localX, localZ) == TerrainColorSampler.SURFACE_CANOPY;
    }

    private static int canopyHeight(HeightGrids grids, int localX, int localZ, int current) {
        boolean canopy = isCanopy(grids, localX, localZ);
        return TerrainColorSampler.canopyNeighbourHeight(
                current,
                heightAt(grids.surface(), localX, localZ),
                canopy
        );
    }

    private static short clampedShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    private static int[][] buildMipmaps(int[] base, int baseSize) {
        int levels = 1;
        for (int size = baseSize; size > 1; size >>= 1) {
            levels++;
        }

        int[][] mipmaps = new int[levels][];
        mipmaps[0] = base;
        int previousSize = baseSize;
        for (int level = 1; level < levels; level++) {
            int size = Math.max(1, previousSize >> 1);
            int[] previous = mipmaps[level - 1];
            int[] next = new int[size * size];
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int sourceX = x << 1;
                    int sourceZ = z << 1;
                    next[z * size + x] = gammaAverage(
                            previous[sourceZ * previousSize + sourceX],
                            previous[sourceZ * previousSize + Math.min(previousSize - 1, sourceX + 1)],
                            previous[Math.min(previousSize - 1, sourceZ + 1) * previousSize + sourceX],
                            previous[Math.min(previousSize - 1, sourceZ + 1) * previousSize
                                    + Math.min(previousSize - 1, sourceX + 1)]
                    );
                }
            }
            mipmaps[level] = next;
            previousSize = size;
        }
        return mipmaps;
    }

    private static int gammaAverage(int first, int second, int third, int fourth) {
        int[] colors = {first, second, third, fourth};
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        int count = 0;
        for (int color : colors) {
            int alpha = (color >>> 24) & 0xFF;
            if (alpha == 0 || color == TerrainColorSampler.VOID_COLOR) {
                continue;
            }
            red += LINEAR[(color >>> 16) & 0xFF];
            green += LINEAR[(color >>> 8) & 0xFF];
            blue += LINEAR[color & 0xFF];
            count++;
        }
        if (count == 0) {
            return TerrainColorSampler.VOID_COLOR;
        }
        int r = linearToChannel(red / count);
        int g = linearToChannel(green / count);
        int b = linearToChannel(blue / count);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static long estimatedTileBytes() {
        long pixels = 0L;
        for (int size = BASE_TILE_PIXELS; size > 0; size >>= 1) {
            pixels += (long) size * size;
            if (size == 1) {
                break;
            }
        }
        return pixels * Integer.BYTES
                + (long) TILE_BLOCKS * TILE_BLOCKS * (Short.BYTES * 2L + Byte.BYTES)
                + 256L;
    }

    private static float[] buildLinearTable() {
        float[] values = new float[256];
        for (int index = 0; index < values.length; index++) {
            values[index] = (float) Math.pow(index / 255.0D, 2.2D);
        }
        return values;
    }

    private static int linearToChannel(float value) {
        return Math.max(0, Math.min(255,
                (int) Math.round(Math.pow(Math.max(0.0F, value), 1.0D / 2.2D) * 255.0D)));
    }

    private static int mipLevel(double blocksPerPixel, int levels) {
        double footprint = Math.max(1.0D, blocksPerPixel * DETAIL);
        int level = (int) Math.floor(Math.log(footprint) / Math.log(2.0D));
        return Math.max(0, Math.min(levels - 1, level));
    }

    private static void rebuildScanQueue(String dimension, int centerX, int centerZ) {
        scanDimension = dimension;
        scanCenterChunkX = centerX;
        scanCenterChunkZ = centerZ;
        SCAN_QUEUE.clear();

        SCAN_QUEUE.add(key(centerX, centerZ));
        for (int radius = 1; radius <= BACKGROUND_SCAN_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                SCAN_QUEUE.add(key(centerX + dx, centerZ - radius));
                SCAN_QUEUE.add(key(centerX + dx, centerZ + radius));
            }
            for (int dz = -radius + 1; dz <= radius - 1; dz++) {
                SCAN_QUEUE.add(key(centerX - radius, centerZ + dz));
                SCAN_QUEUE.add(key(centerX + radius, centerZ + dz));
            }
        }
    }

    private static void trim(LinkedHashMap<Long, SurfaceTile> tiles) {
        while (tiles.size() > MAX_TILES_PER_DIMENSION) {
            Long eldest = tiles.keySet().iterator().next();
            tiles.remove(eldest);
        }
    }

    private static String dimension(ClientLevel level) {
        return level.dimension().identifier().toString();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyZ(long key) {
        return (int) key;
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fractional(double value) {
        return value - Math.floor(value);
    }

    public record Statistics(
            int memoryTiles,
            long estimatedMemoryBytes,
            int dimensions,
            long capturedTiles,
            double averageCaptureMicros,
            DiskStatistics disk
    ) {
    }

    public record DiskStatistics(
            long readHits,
            long readMisses,
            long readFailures,
            long writes,
            long writeFailures,
            long prunedFiles,
            int pendingReads,
            int queuedWrites,
            long diskFiles,
            long diskBytes,
            long cacheLimitBytes
    ) {
    }

    private static final class DimensionAtlas {
        private final LinkedHashMap<Long, SurfaceTile> tiles = new LinkedHashMap<>(256, 0.75F, true);
    }

    private record HeightGrids(int[] terrain, int[] surface, byte[] kinds) {
    }

    private static final class SurfaceTile {
        private final int[][] mipmaps;
        private final short[] terrainHeights;
        private final short[] surfaceHeights;
        private final byte[] surfaceKinds;
        private final long capturedTick;
        private final int paletteGeneration;

        private SurfaceTile(
                int[][] mipmaps,
                short[] terrainHeights,
                short[] surfaceHeights,
                byte[] surfaceKinds,
                long capturedTick,
                int paletteGeneration
        ) {
            this.mipmaps = mipmaps;
            this.terrainHeights = terrainHeights;
            this.surfaceHeights = surfaceHeights;
            this.surfaceKinds = surfaceKinds;
            this.capturedTick = capturedTick;
            this.paletteGeneration = paletteGeneration;
        }

        private int blockColor(int blockX, int blockZ) {
            int blockLevel = Math.min(Integer.numberOfTrailingZeros(DETAIL), mipmaps.length - 1);
            int[] onePixelPerBlock = mipmaps[blockLevel];
            return onePixelPerBlock[blockZ * TILE_BLOCKS + blockX];
        }

        private int detailColor(int blockX, int blockZ, int detailX, int detailZ) {
            int x = blockX * DETAIL + detailX;
            int z = blockZ * DETAIL + detailZ;
            return mipmaps[0][z * BASE_TILE_PIXELS + x];
        }

        private int sampleMip(int level, double localBlockX, double localBlockZ) {
            int[] mip = mipmaps[level];
            int size = Math.max(1, BASE_TILE_PIXELS >> level);
            double scale = DETAIL / (double) (1 << level);
            int x = Math.max(0, Math.min(size - 1, (int) Math.floor(localBlockX * scale)));
            int z = Math.max(0, Math.min(size - 1, (int) Math.floor(localBlockZ * scale)));
            return mip[z * size + x];
        }
    }
}
