package be.winnetrie.mod.simpleserverutilities.client.minimap;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.platform.NativeImage;

import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.client.map.AerialMapAtlas;
import be.winnetrie.mod.simpleserverutilities.client.map.MapLighting;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Smooth double-buffered minimap terrain cache.
 *
 * <p>The visible texture is never cleared during a refresh. A larger terrain
 * cache is rebuilt off-screen and swapped in atomically when complete. The
 * 128-block viewport then moves inside that cache as the player walks, which
 * removes the grey refresh flashes and keeps movement smooth.</p>
 */
final class MinimapTerrainMap implements AutoCloseable {

    static final int VISIBLE_BLOCKS = 128;
    static final int PIXELS_PER_BLOCK = 1;
    static final int DISPLAY_SIZE = VISIBLE_BLOCKS * PIXELS_PER_BLOCK;

    private static final int CACHE_BLOCKS = 192;
    private static final int CACHE_SIZE = CACHE_BLOCKS * PIXELS_PER_BLOCK;
    private static final int CACHE_HALF_BLOCKS = CACHE_BLOCKS / 2;
    private static final int VISIBLE_HALF_BLOCKS = VISIBLE_BLOCKS / 2;
    private static final int SAFE_REBUILD_DISTANCE = 24;
    private static final int BLOCK_ROWS_PER_TICK = 24;
    private static final int UNKNOWN_DARK = 0xFF20252A;
    private static final int UNKNOWN_LIGHT = 0xFF292F35;
    private static final int CIRCLE_RADIUS = DISPLAY_SIZE / 2 - 1;

    private int[] publishedCachePixels;
    private int[] buildingCachePixels;
    private @Nullable DynamicTexture displayTexture;
    private @Nullable ResourceLocation displayTextureLocation;

    private int publishedCenterX = Integer.MIN_VALUE;
    private int publishedCenterZ = Integer.MIN_VALUE;
    private String publishedDimension = "";
    private int publishedTerrainHash = Integer.MIN_VALUE;
    private int publishedGeneration;

    private int buildCenterX = Integer.MIN_VALUE;
    private int buildCenterZ = Integer.MIN_VALUE;
    private String buildDimension = "";
    private int buildTerrainHash = Integer.MIN_VALUE;
    private int nextBuildBlockRow = CACHE_BLOCKS;
    private MinimapDataPayload buildData;
    private Map<Long, MinimapDataPayload.ClaimOverlay> buildClaimLookup = Map.of();
    private List<MinimapDataPayload.RegionOverlay> buildRegions = List.of();

    private int displayPlayerX = Integer.MIN_VALUE;
    private int displayPlayerZ = Integer.MIN_VALUE;
    private String displayShape = "";
    private int displayGeneration = Integer.MIN_VALUE;
    private boolean forceRebuild;

    void tick(MinimapDataPayload updated) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !updated.enabled()) {
            return;
        }

        String dimension = level.dimension().location().toString();
        int playerX = (int) Math.floor(minecraft.player.getX());
        int playerZ = (int) Math.floor(minecraft.player.getZ());
        int terrainHash = terrainHash(updated);

        boolean publishedUsable = publishedCachePixels != null
                && dimension.equals(publishedDimension)
                && publishedTerrainHash == terrainHash;
        boolean outsideComfortZone = publishedUsable
                && (Math.abs(playerX - publishedCenterX) >= SAFE_REBUILD_DISTANCE
                    || Math.abs(playerZ - publishedCenterZ) >= SAFE_REBUILD_DISTANCE);
        boolean needsBuild = forceRebuild || !publishedUsable || outsideComfortZone;

        if (needsBuild && !matchingBuild(playerX, playerZ, dimension, terrainHash)) {
            beginBuild(updated, playerX, playerZ, dimension, terrainHash);
        }

        processBuild(level);

        if (publishedCachePixels != null && dimension.equals(publishedDimension)) {
            refreshDisplay(playerX, playerZ, updated.shape());
        }
    }

    void render(GuiGraphics graphics, int left, int top, int size) {
        if (displayTexture == null || displayTextureLocation == null) {
            graphics.fill(left, top, left + size, top + size, UNKNOWN_DARK);
            return;
        }
        graphics.blit(
                displayTextureLocation,
                left,
                top,
                size,
                size,
                0.0F,
                0.0F,
                DISPLAY_SIZE,
                DISPLAY_SIZE,
                DISPLAY_SIZE,
                DISPLAY_SIZE
        );
    }

    /** Requests a background rebuild without discarding the currently visible map. */
    void invalidate() {
        forceRebuild = true;
    }

    private boolean matchingBuild(int playerX, int playerZ, String dimension, int terrainHash) {
        if (nextBuildBlockRow >= CACHE_BLOCKS || buildingCachePixels == null) {
            return false;
        }
        return dimension.equals(buildDimension)
                && terrainHash == buildTerrainHash
                && Math.abs(playerX - buildCenterX) < SAFE_REBUILD_DISTANCE
                && Math.abs(playerZ - buildCenterZ) < SAFE_REBUILD_DISTANCE;
    }

    private void beginBuild(
            MinimapDataPayload updated,
            int playerX,
            int playerZ,
            String dimension,
            int terrainHash
    ) {
        ensureBuildingPixels();
        buildCenterX = playerX;
        buildCenterZ = playerZ;
        buildDimension = dimension;
        buildTerrainHash = terrainHash;
        buildData = updated;
        buildRegions = updated.showRegions() ? updated.regions() : List.of();

        if (updated.showClaims()) {
            Map<Long, MinimapDataPayload.ClaimOverlay> lookup = new HashMap<>();
            for (MinimapDataPayload.ClaimOverlay claim : updated.claims()) {
                lookup.put(chunkKey(claim.chunkX(), claim.chunkZ()), claim);
            }
            buildClaimLookup = Map.copyOf(lookup);
        } else {
            buildClaimLookup = Map.of();
        }

        fillUnknown(buildingCachePixels);
        nextBuildBlockRow = 0;
        forceRebuild = false;
    }

    private void processBuild(ClientLevel level) {
        if (buildingCachePixels == null || nextBuildBlockRow >= CACHE_BLOCKS || buildData == null) {
            return;
        }

        int[] pixels = buildingCachePixels;
        int endRow = Math.min(CACHE_BLOCKS, nextBuildBlockRow + BLOCK_ROWS_PER_TICK);
        for (int blockRow = nextBuildBlockRow; blockRow < endRow; blockRow++) {
            int worldZ = buildCenterZ + blockRow - CACHE_HALF_BLOCKS;
            for (int blockColumn = 0; blockColumn < CACHE_BLOCKS; blockColumn++) {
                int worldX = buildCenterX + blockColumn - CACHE_HALF_BLOCKS;
                writeDetailedBlock(pixels, level, blockColumn, blockRow, worldX, worldZ);
            }
        }
        nextBuildBlockRow = endRow;

        if (nextBuildBlockRow >= CACHE_BLOCKS) {
            publishBuild();
        }
    }

    private void publishBuild() {
        if (buildingCachePixels == null) {
            return;
        }

        int[] oldPublished = publishedCachePixels;
        publishedCachePixels = buildingCachePixels;
        buildingCachePixels = oldPublished;

        publishedCenterX = buildCenterX;
        publishedCenterZ = buildCenterZ;
        publishedDimension = buildDimension;
        publishedTerrainHash = buildTerrainHash;
        publishedGeneration++;

        nextBuildBlockRow = CACHE_BLOCKS;
        buildData = null;
        buildClaimLookup = Map.of();
        buildRegions = List.of();
        displayGeneration = Integer.MIN_VALUE;
    }

    private void refreshDisplay(int playerX, int playerZ, String rawShape) {
        if (publishedCachePixels == null) {
            return;
        }

        String shape = rawShape == null ? "CIRCLE" : rawShape.toUpperCase(Locale.ROOT);
        if (displayTexture != null
                && displayPlayerX == playerX
                && displayPlayerZ == playerZ
                && displayShape.equals(shape)
                && displayGeneration == publishedGeneration) {
            return;
        }

        int deltaX = playerX - publishedCenterX;
        int deltaZ = playerZ - publishedCenterZ;
        int maximumOffset = (CACHE_BLOCKS - VISIBLE_BLOCKS) / 2;
        if (Math.abs(deltaX) > maximumOffset || Math.abs(deltaZ) > maximumOffset) {
            // Keep the last complete image during a long teleport; the new cache
            // will replace it atomically after the background build finishes.
            return;
        }

        ensureDisplayTexture();
        int[] source = publishedCachePixels;
        NativeImage target = displayTexture.getPixels();
        int sourceStartX = (CACHE_HALF_BLOCKS - VISIBLE_HALF_BLOCKS + deltaX) * PIXELS_PER_BLOCK;
        int sourceStartZ = (CACHE_HALF_BLOCKS - VISIBLE_HALF_BLOCKS + deltaZ) * PIXELS_PER_BLOCK;
        boolean circle = "CIRCLE".equals(shape);

        for (int z = 0; z < DISPLAY_SIZE; z++) {
            int offsetZ = z - DISPLAY_SIZE / 2;
            for (int x = 0; x < DISPLAY_SIZE; x++) {
                int offsetX = x - DISPLAY_SIZE / 2;
                if (circle && outsideCircle(offsetX, offsetZ)) {
                    target.setPixelRGBA(x, z, 0x00000000);
                    continue;
                }

                int color = source[(sourceStartZ + z) * CACHE_SIZE + sourceStartX + x];
                if (circle) {
                    color = applyCircleBorder(color, offsetX, offsetZ);
                }
                target.setPixelRGBA(x, z, color);
            }
        }

        displayTexture.upload();
        displayPlayerX = playerX;
        displayPlayerZ = playerZ;
        displayShape = shape;
        displayGeneration = publishedGeneration;
    }

    private int applyClaimOverlay(int base, int worldX, int worldZ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        MinimapDataPayload.ClaimOverlay overlay = buildClaimLookup.get(chunkKey(chunkX, chunkZ));
        if (overlay == null
                || overlay.status() == ClaimChunkStatus.WILDERNESS
                || overlay.status() == ClaimChunkStatus.REGION) {
            return base;
        }

        int rgb = switch (overlay.status()) {
            case OWNED_BY_SELF -> buildData.ownClaimColor();
            case OWNED_BY_TRUSTED -> lighten(buildData.ownClaimColor(), 36);
            case OWNED_BY_OTHER -> buildData.otherClaimColor();
            default -> buildData.otherClaimColor();
        };

        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        boolean edge = isOuterClaimEdge(
                overlay.claimId(),
                claimIdAt(chunkX - 1, chunkZ),
                claimIdAt(chunkX + 1, chunkZ),
                claimIdAt(chunkX, chunkZ - 1),
                claimIdAt(chunkX, chunkZ + 1),
                localX,
                localZ
        );
        return blend(base, withAlpha(rgb, edge ? 210 : 34));
    }

    private UUID claimIdAt(int chunkX, int chunkZ) {
        MinimapDataPayload.ClaimOverlay neighbour = buildClaimLookup.get(chunkKey(chunkX, chunkZ));
        return neighbour == null ? null : neighbour.claimId();
    }

    static boolean isOuterClaimEdge(
            UUID current,
            UUID west,
            UUID east,
            UUID north,
            UUID south,
            int localX,
            int localZ
    ) {
        return ClaimOutlineMath.isOuterClaimEdge(
                current, west, east, north, south, localX, localZ
        );
    }

    private int applyRegionOverlay(int base, int worldX, int worldZ) {
        int color = base;
        for (MinimapDataPayload.RegionOverlay region : buildRegions) {
            if (worldX < region.minX() || worldX > region.maxX()
                    || worldZ < region.minZ() || worldZ > region.maxZ()) {
                continue;
            }
            boolean edge = worldX == region.minX() || worldX == region.maxX()
                    || worldZ == region.minZ() || worldZ == region.maxZ();
            color = blend(color, withAlpha(buildData.regionColor(), edge ? 200 : 28));
        }
        return color;
    }

    private void writeDetailedBlock(
            int[] image,
            ClientLevel level,
            int blockX,
            int blockZ,
            int worldX,
            int worldZ
    ) {
        int pixelX = blockX * PIXELS_PER_BLOCK;
        int pixelZ = blockZ * PIXELS_PER_BLOCK;
        for (int dz = 0; dz < PIXELS_PER_BLOCK; dz++) {
            int row = (pixelZ + dz) * CACHE_SIZE;
            for (int dx = 0; dx < PIXELS_PER_BLOCK; dx++) {
                int color = AerialMapAtlas.sampleDetail(
                        level,
                        worldX,
                        worldZ,
                        dx,
                        dz,
                        PIXELS_PER_BLOCK
                );
                if (color == be.winnetrie.mod.simpleserverutilities.client.map.TerrainColorSampler.VOID_COLOR) {
                    color = checkerColor(pixelX + dx, pixelZ + dz);
                } else {
                    color = MapLighting.apply(level, worldX, worldZ, color);
                }
                color = applyClaimOverlay(color, worldX, worldZ);
                color = applyRegionOverlay(color, worldX, worldZ);
                image[row + pixelX + dx] = color;
            }
        }
    }

    private static int applyCircleBorder(int color, int offsetX, int offsetZ) {
        int innerRadius = CIRCLE_RADIUS - 2;
        if (offsetX * offsetX + offsetZ * offsetZ >= innerRadius * innerRadius) {
            return blend(color, 0xD9000000);
        }
        return color;
    }

    private static boolean outsideCircle(int offsetX, int offsetZ) {
        return offsetX * offsetX + offsetZ * offsetZ > CIRCLE_RADIUS * CIRCLE_RADIUS;
    }

    private void ensureBuildingPixels() {
        if (buildingCachePixels == null) {
            buildingCachePixels = new int[CACHE_SIZE * CACHE_SIZE];
        }
    }

    private void ensureDisplayTexture() {
        if (displayTexture == null) {
            displayTexture = new DynamicTexture(DISPLAY_SIZE, DISPLAY_SIZE, true);
            displayTextureLocation = Minecraft.getInstance().getTextureManager()
                    .register("ssu_minimap_viewport", displayTexture);
        }
    }

    private static void fillUnknown(int[] image) {
        for (int z = 0; z < CACHE_SIZE; z++) {
            int row = z * CACHE_SIZE;
            for (int x = 0; x < CACHE_SIZE; x++) {
                image[row + x] = checkerColor(x, z);
            }
        }
    }

    private static int checkerColor(int x, int z) {
        return (((x >> 5) + (z >> 5)) & 1) == 0 ? UNKNOWN_DARK : UNKNOWN_LIGHT;
    }

    private static int terrainHash(MinimapDataPayload payload) {
        int result = payload.dimension().hashCode();
        result = 31 * result + Boolean.hashCode(payload.showClaims());
        result = 31 * result + Boolean.hashCode(payload.showRegions());
        result = 31 * result + payload.ownClaimColor();
        result = 31 * result + payload.otherClaimColor();
        result = 31 * result + payload.regionColor();
        result = 31 * result + payload.claims().hashCode();
        result = 31 * result + payload.regions().hashCode();
        ClientLevel level = Minecraft.getInstance().level;
        result = 31 * result + (level == null ? 0 : MapLighting.nightBucket(level));
        return result;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    private static int lighten(int argb, int amount) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int blend(int base, int overlay) {
        int alpha = (overlay >>> 24) & 0xFF;
        if (alpha <= 0) {
            return base;
        }
        if (alpha >= 255) {
            return overlay;
        }
        int inverse = 255 - alpha;
        int r = ((((overlay >> 16) & 0xFF) * alpha) + (((base >> 16) & 0xFF) * inverse)) / 255;
        int g = ((((overlay >> 8) & 0xFF) * alpha) + (((base >> 8) & 0xFF) * inverse)) / 255;
        int b = (((overlay & 0xFF) * alpha) + ((base & 0xFF) * inverse)) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void close() {
        closeTexture(displayTexture, displayTextureLocation);
        publishedCachePixels = null;
        buildingCachePixels = null;
        displayTexture = null;
        displayTextureLocation = null;
        publishedCenterX = Integer.MIN_VALUE;
        publishedCenterZ = Integer.MIN_VALUE;
        publishedDimension = "";
        publishedTerrainHash = Integer.MIN_VALUE;
        publishedGeneration = 0;
        nextBuildBlockRow = CACHE_BLOCKS;
        buildData = null;
        buildClaimLookup = Map.of();
        buildRegions = List.of();
        displayPlayerX = Integer.MIN_VALUE;
        displayPlayerZ = Integer.MIN_VALUE;
        displayShape = "";
        displayGeneration = Integer.MIN_VALUE;
        forceRebuild = false;
    }

    private static void closeTexture(@Nullable DynamicTexture texture, @Nullable ResourceLocation location) {
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        } else if (texture != null) {
            texture.close();
        }
    }
}
