package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import be.winnetrie.mod.simpleserverutilities.client.map.AerialMapAtlas;
import be.winnetrie.mod.simpleserverutilities.client.map.MapLighting;
import be.winnetrie.mod.simpleserverutilities.client.map.TerrainColorSampler;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Double-buffered terrain renderer for the claim map.
 *
 * <p>The last complete map remains visible while another zoom level or map
 * centre is prepared. It is spatially remapped to the requested world bounds,
 * so zooming and panning never clear the complete canvas.</p>
 */
final class ClaimTerrainMap implements AutoCloseable {

    private static final int BLOCKS_PER_CHUNK = 16;
    private static final int PIXELS_PER_BLOCK = 1;
    static final int PIXELS_PER_CHUNK = BLOCKS_PER_CHUNK * PIXELS_PER_BLOCK;

    private static final int CHUNKS_PER_TICK = 12;
    private static final int UNKNOWN_DARK = 0xFF20252A;
    private static final int UNKNOWN_LIGHT = 0xFF292F35;

    private @Nullable DynamicTexture publishedTexture;
    private @Nullable ResourceLocation publishedTextureLocation;
    private int publishedWidth;
    private int publishedHeight;
    private int publishedCenterChunkX = Integer.MIN_VALUE;
    private int publishedCenterChunkZ = Integer.MIN_VALUE;
    private int publishedRadius = -1;
    private String publishedDimension = "";
    private int publishedNightBucket = -1;

    private @Nullable DynamicTexture buildingTexture;
    private @Nullable ResourceLocation buildingTextureLocation;
    private int buildingWidth;
    private int buildingHeight;
    private int buildingCenterChunkX = Integer.MIN_VALUE;
    private int buildingCenterChunkZ = Integer.MIN_VALUE;
    private int buildingRadius = -1;
    private String buildingDimension = "";
    private int buildingNightBucket = -1;
    private List<ChunkCoordinate> pendingChunks = List.of();
    private int nextPendingChunk;
    private boolean buildingDirty;

    void ensureView(ClaimMapDataPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        String currentDimension = level == null ? "" : level.dimension().location().toString();
        int gridSize = payload.radius() * 2 + 1;
        int requiredWidth = gridSize * PIXELS_PER_CHUNK;
        int requiredHeight = requiredWidth;
        int nightBucket = level == null ? 0 : MapLighting.nightBucket(level);

        boolean publishedMatches = matchesPublished(
                payload, currentDimension, requiredWidth, requiredHeight, nightBucket
        );
        boolean buildingMatches = matchesBuilding(
                payload, currentDimension, requiredWidth, requiredHeight, nightBucket
        );
        if (publishedMatches) {
            if (buildingTexture != null && !buildingMatches) {
                cancelBuild();
            }
            return;
        }
        if (buildingMatches) {
            return;
        }

        beginBuild(payload, currentDimension, requiredWidth, requiredHeight, nightBucket);
    }

    void tick(ClaimMapDataPayload payload) {
        ensureView(payload);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || buildingTexture == null || nextPendingChunk >= pendingChunks.size()) {
            return;
        }

        int processed = 0;
        while (nextPendingChunk < pendingChunks.size() && processed < CHUNKS_PER_TICK) {
            ChunkCoordinate coordinate = pendingChunks.get(nextPendingChunk++);
            renderChunk(level, coordinate.x(), coordinate.z());
            processed++;
        }

        if (publishedTexture == null && buildingDirty) {
            buildingTexture.upload();
            buildingDirty = false;
        }

        if (nextPendingChunk >= pendingChunks.size()) {
            if (buildingDirty) {
                buildingTexture.upload();
                buildingDirty = false;
            }
            publish();
        }
    }

    void render(
            GuiGraphics graphics,
            int left,
            int top,
            int size,
            ClaimMapDataPayload view
    ) {
        graphics.fill(left, top, left + size, top + size, UNKNOWN_DARK);

        if (publishedTexture != null
                && publishedRadius >= 0
                && publishedDimension.equals(currentDimension())) {
            renderMapped(
                    graphics,
                    publishedTextureLocation,
                    publishedWidth,
                    publishedHeight,
                    publishedCenterChunkX,
                    publishedCenterChunkZ,
                    publishedRadius,
                    left,
                    top,
                    size,
                    view
            );
            return;
        }

        if (buildingTexture != null
                && buildingRadius == view.radius()
                && buildingCenterChunkX == view.centerChunkX()
                && buildingCenterChunkZ == view.centerChunkZ()
                && buildingDimension.equals(currentDimension())) {
            if (buildingTextureLocation != null) {
                graphics.blit(
                        buildingTextureLocation,
                        left,
                        top,
                        size,
                        size,
                        0.0F,
                        0.0F,
                        buildingWidth,
                        buildingHeight,
                        buildingWidth,
                        buildingHeight
                );
            }
        }
    }

    private void renderMapped(
            GuiGraphics graphics,
            @Nullable ResourceLocation sourceLocation,
            int sourceTextureWidth,
            int sourceTextureHeight,
            int sourceCenterChunkX,
            int sourceCenterChunkZ,
            int sourceRadius,
            int left,
            int top,
            int size,
            ClaimMapDataPayload target
    ) {
        if (sourceLocation == null) return;
        int targetMinX = (target.centerChunkX() - target.radius()) << 4;
        int targetMinZ = (target.centerChunkZ() - target.radius()) << 4;
        int targetMaxX = (target.centerChunkX() + target.radius() + 1) << 4;
        int targetMaxZ = (target.centerChunkZ() + target.radius() + 1) << 4;

        int sourceMinX = (sourceCenterChunkX - sourceRadius) << 4;
        int sourceMinZ = (sourceCenterChunkZ - sourceRadius) << 4;
        int sourceMaxX = (sourceCenterChunkX + sourceRadius + 1) << 4;
        int sourceMaxZ = (sourceCenterChunkZ + sourceRadius + 1) << 4;

        int intersectionMinX = Math.max(targetMinX, sourceMinX);
        int intersectionMinZ = Math.max(targetMinZ, sourceMinZ);
        int intersectionMaxX = Math.min(targetMaxX, sourceMaxX);
        int intersectionMaxZ = Math.min(targetMaxZ, sourceMaxZ);
        if (intersectionMinX >= intersectionMaxX || intersectionMinZ >= intersectionMaxZ) {
            return;
        }

        double targetWidth = targetMaxX - targetMinX;
        double targetHeight = targetMaxZ - targetMinZ;
        double sourceWidth = sourceMaxX - sourceMinX;
        double sourceHeight = sourceMaxZ - sourceMinZ;

        int destinationLeft = left + (int) Math.floor((intersectionMinX - targetMinX) * size / targetWidth);
        int destinationTop = top + (int) Math.floor((intersectionMinZ - targetMinZ) * size / targetHeight);
        int destinationRight = left + (int) Math.ceil((intersectionMaxX - targetMinX) * size / targetWidth);
        int destinationBottom = top + (int) Math.ceil((intersectionMaxZ - targetMinZ) * size / targetHeight);

        float u0 = (float) ((intersectionMinX - sourceMinX) / sourceWidth);
        float v0 = (float) ((intersectionMinZ - sourceMinZ) / sourceHeight);
        float u1 = (float) ((intersectionMaxX - sourceMinX) / sourceWidth);
        float v1 = (float) ((intersectionMaxZ - sourceMinZ) / sourceHeight);

        int sourceU = Math.max(0, Math.min(sourceTextureWidth - 1, Math.round(u0 * sourceTextureWidth)));
        int sourceV = Math.max(0, Math.min(sourceTextureHeight - 1, Math.round(v0 * sourceTextureHeight)));
        int sourceUWidth = Math.max(1, Math.min(sourceTextureWidth - sourceU, Math.round((u1 - u0) * sourceTextureWidth)));
        int sourceVHeight = Math.max(1, Math.min(sourceTextureHeight - sourceV, Math.round((v1 - v0) * sourceTextureHeight)));
        graphics.blit(
                sourceLocation,
                destinationLeft,
                destinationTop,
                destinationRight - destinationLeft,
                destinationBottom - destinationTop,
                sourceU,
                sourceV,
                sourceUWidth,
                sourceVHeight,
                sourceTextureWidth,
                sourceTextureHeight
        );
    }

    private boolean matchesPublished(
            ClaimMapDataPayload payload,
            String dimension,
            int requiredWidth,
            int requiredHeight,
            int nightBucket
    ) {
        return publishedTexture != null
                && publishedWidth == requiredWidth
                && publishedHeight == requiredHeight
                && publishedCenterChunkX == payload.centerChunkX()
                && publishedCenterChunkZ == payload.centerChunkZ()
                && publishedRadius == payload.radius()
                && publishedDimension.equals(dimension)
                && publishedNightBucket == nightBucket;
    }

    private boolean matchesBuilding(
            ClaimMapDataPayload payload,
            String dimension,
            int requiredWidth,
            int requiredHeight,
            int nightBucket
    ) {
        return buildingTexture != null
                && buildingWidth == requiredWidth
                && buildingHeight == requiredHeight
                && buildingCenterChunkX == payload.centerChunkX()
                && buildingCenterChunkZ == payload.centerChunkZ()
                && buildingRadius == payload.radius()
                && buildingDimension.equals(dimension)
                && buildingNightBucket == nightBucket
                && nextPendingChunk < pendingChunks.size();
    }

    private void beginBuild(
            ClaimMapDataPayload payload,
            String dimension,
            int requiredWidth,
            int requiredHeight,
            int nightBucket
    ) {
        closeTexture(buildingTexture, buildingTextureLocation);
        buildingTexture = new DynamicTexture(requiredWidth, requiredHeight, true);
        buildingTextureLocation = Minecraft.getInstance().getTextureManager()
                .register("ssu_claim_terrain_build", buildingTexture);
        buildingWidth = requiredWidth;
        buildingHeight = requiredHeight;
        buildingCenterChunkX = payload.centerChunkX();
        buildingCenterChunkZ = payload.centerChunkZ();
        buildingRadius = payload.radius();
        buildingDimension = dimension;
        buildingNightBucket = nightBucket;
        fillUnknown(buildingTexture.getPixels());
        buildingTexture.upload();

        List<ChunkCoordinate> ordered = new ArrayList<>(
                (payload.radius() * 2 + 1) * (payload.radius() * 2 + 1)
        );
        for (int chunkZ = payload.centerChunkZ() - payload.radius();
                chunkZ <= payload.centerChunkZ() + payload.radius();
                chunkZ++) {
            for (int chunkX = payload.centerChunkX() - payload.radius();
                    chunkX <= payload.centerChunkX() + payload.radius();
                    chunkX++) {
                ordered.add(new ChunkCoordinate(chunkX, chunkZ));
            }
        }
        int originX = Minecraft.getInstance().player == null
                ? payload.centerChunkX()
                : Minecraft.getInstance().player.chunkPosition().x;
        int originZ = Minecraft.getInstance().player == null
                ? payload.centerChunkZ()
                : Minecraft.getInstance().player.chunkPosition().z;
        ordered.sort(Comparator.comparingInt(entry -> distanceSquared(entry.x(), entry.z(), originX, originZ)));
        pendingChunks = List.copyOf(ordered);
        nextPendingChunk = 0;
        buildingDirty = false;
    }

    private void renderChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (buildingTexture == null) {
            return;
        }

        AerialMapAtlas.refreshLoadedChunk(level, chunkX, chunkZ);
        NativeImage pixels = buildingTexture.getPixels();
        int imageChunkX = chunkX - (buildingCenterChunkX - buildingRadius);
        int imageChunkZ = chunkZ - (buildingCenterChunkZ - buildingRadius);
        int pixelBaseX = imageChunkX * PIXELS_PER_CHUNK;
        int pixelBaseZ = imageChunkZ * PIXELS_PER_CHUNK;
        for (int localZ = 0; localZ < BLOCKS_PER_CHUNK; localZ++) {
            for (int localX = 0; localX < BLOCKS_PER_CHUNK; localX++) {
                int worldX = chunkX * BLOCKS_PER_CHUNK + localX;
                int worldZ = chunkZ * BLOCKS_PER_CHUNK + localZ;
                int pixelX = pixelBaseX + localX;
                int pixelZ = pixelBaseZ + localZ;
                int color = AerialMapAtlas.sample(level, worldX, worldZ);
                color = color == TerrainColorSampler.VOID_COLOR
                        ? checkerColor(pixelX, pixelZ)
                        : MapLighting.apply(level, worldX, worldZ, color);
                pixels.setPixelRGBA(pixelX, pixelZ, color);
            }
        }
        buildingDirty = true;
    }

    private void cancelBuild() {
        closeTexture(buildingTexture, buildingTextureLocation);
        buildingTexture = null;
        buildingTextureLocation = null;
        buildingWidth = 0;
        buildingHeight = 0;
        buildingCenterChunkX = Integer.MIN_VALUE;
        buildingCenterChunkZ = Integer.MIN_VALUE;
        buildingRadius = -1;
        buildingDimension = "";
        buildingNightBucket = -1;
        pendingChunks = List.of();
        nextPendingChunk = 0;
        buildingDirty = false;
    }

    private void publish() {
        if (buildingTexture == null) {
            return;
        }
        closeTexture(publishedTexture, publishedTextureLocation);
        publishedTexture = buildingTexture;
        publishedTextureLocation = buildingTextureLocation;
        publishedWidth = buildingWidth;
        publishedHeight = buildingHeight;
        publishedCenterChunkX = buildingCenterChunkX;
        publishedCenterChunkZ = buildingCenterChunkZ;
        publishedRadius = buildingRadius;
        publishedDimension = buildingDimension;
        publishedNightBucket = buildingNightBucket;

        buildingTexture = null;
        buildingTextureLocation = null;
        buildingWidth = 0;
        buildingHeight = 0;
        buildingCenterChunkX = Integer.MIN_VALUE;
        buildingCenterChunkZ = Integer.MIN_VALUE;
        buildingRadius = -1;
        buildingDimension = "";
        buildingNightBucket = -1;
        pendingChunks = List.of();
        nextPendingChunk = 0;
    }

    private static int distanceSquared(int x, int z, int originX, int originZ) {
        int dx = x - originX;
        int dz = z - originZ;
        return dx * dx + dz * dz;
    }

    private static int checkerColor(int x, int y) {
        int checker = ((x / PIXELS_PER_CHUNK) + (y / PIXELS_PER_CHUNK)) & 1;
        return checker == 0 ? UNKNOWN_DARK : UNKNOWN_LIGHT;
    }

    private static void fillUnknown(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixelRGBA(x, y, checkerColor(x, y));
            }
        }
    }

    private static String currentDimension() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? "" : level.dimension().location().toString();
    }

    private static void closeTexture(@Nullable DynamicTexture texture, @Nullable ResourceLocation location) {
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        } else if (texture != null) {
            texture.close();
        }
    }

    @Override
    public void close() {
        closeTexture(publishedTexture, publishedTextureLocation);
        closeTexture(buildingTexture, buildingTextureLocation);
        publishedTexture = null;
        buildingTexture = null;
        publishedTextureLocation = null;
        buildingTextureLocation = null;
        pendingChunks = List.of();
        nextPendingChunk = 0;
    }

    private record ChunkCoordinate(int x, int z) {
    }
}
