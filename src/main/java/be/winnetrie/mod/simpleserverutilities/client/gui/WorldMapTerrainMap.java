package be.winnetrie.mod.simpleserverutilities.client.gui;

import com.mojang.blaze3d.platform.NativeImage;

import be.winnetrie.mod.simpleserverutilities.client.map.AerialMapAtlas;
import be.winnetrie.mod.simpleserverutilities.client.map.MapLighting;
import be.winnetrie.mod.simpleserverutilities.client.map.TerrainColorSampler;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jspecify.annotations.Nullable;

/** Double-buffered, high-resolution terrain texture for the full world map. */
final class WorldMapTerrainMap implements AutoCloseable {

    private static final int MIN_TEXTURE_SIZE = 384;
    private static final int MAX_TEXTURE_SIZE = 768;
    private static final int ROWS_PER_TICK = 48;
    private static final int UNKNOWN_DARK = 0xFF20252A;
    private static final int UNKNOWN_LIGHT = 0xFF292F35;
    private static final float[] LINEAR = buildLinearTable();

    private @Nullable DynamicTexture publishedTexture;
    private @Nullable DynamicTexture buildingTexture;
    private int textureSize;
    private int buildRow;
    private int buildCenterChunkX = Integer.MIN_VALUE;
    private int buildCenterChunkZ = Integer.MIN_VALUE;
    private int buildRadius = -1;
    private String buildDimension = "";
    private int publishedCenterChunkX = Integer.MIN_VALUE;
    private int publishedCenterChunkZ = Integer.MIN_VALUE;
    private int publishedRadius = -1;
    private String publishedDimension = "";
    private int buildNightBucket = -1;
    private int publishedNightBucket = -1;
    private boolean forceRebuild = true;

    void ensureView(WorldMapDataPayload payload, int requestedPixels) {
        ClientLevel level = Minecraft.getInstance().level;
        String dimension = level == null ? "" : level.dimension().identifier().toString();
        int requiredSize = Math.max(MIN_TEXTURE_SIZE, Math.min(MAX_TEXTURE_SIZE, requestedPixels));
        int nightBucket = level == null ? 0 : MapLighting.nightBucket(level);

        boolean publishedMatches = publishedTexture != null
                && textureSize == requiredSize
                && publishedCenterChunkX == payload.centerChunkX()
                && publishedCenterChunkZ == payload.centerChunkZ()
                && publishedRadius == payload.radius()
                && publishedDimension.equals(dimension)
                && publishedNightBucket == nightBucket;
        boolean buildingMatches = buildingTexture != null
                && textureSize == requiredSize
                && buildCenterChunkX == payload.centerChunkX()
                && buildCenterChunkZ == payload.centerChunkZ()
                && buildRadius == payload.radius()
                && buildDimension.equals(dimension)
                && buildNightBucket == nightBucket
                && buildRow < textureSize;

        if (!forceRebuild && publishedMatches) {
            if (buildingTexture != null && !buildingMatches) {
                closeTexture(buildingTexture);
                buildingTexture = null;
                buildRow = textureSize;
            }
            return;
        }
        if (!forceRebuild && buildingMatches) {
            return;
        }

        beginBuild(payload, dimension, requiredSize, nightBucket);
    }

    void tick(WorldMapDataPayload payload, int requestedPixels) {
        ensureView(payload, requestedPixels);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || buildingTexture == null || buildRow >= textureSize) {
            return;
        }

        NativeImage image = buildingTexture.getPixels();
        int totalBlocks = (buildRadius * 2 + 1) * 16;
        int minimumBlockX = (buildCenterChunkX - buildRadius) << 4;
        int minimumBlockZ = (buildCenterChunkZ - buildRadius) << 4;
        double blocksPerPixel = totalBlocks / (double) textureSize;
        int rowsThisTick = blocksPerPixel > 1.25D ? Math.max(12, ROWS_PER_TICK / 2) : ROWS_PER_TICK;
        int endRow = Math.min(textureSize, buildRow + rowsThisTick);

        for (int pixelZ = buildRow; pixelZ < endRow; pixelZ++) {
            for (int pixelX = 0; pixelX < textureSize; pixelX++) {
                int color = samplePixel(
                        level,
                        minimumBlockX,
                        minimumBlockZ,
                        totalBlocks,
                        pixelX,
                        pixelZ,
                        blocksPerPixel
                );
                image.setPixel(pixelX, pixelZ, color == TerrainColorSampler.VOID_COLOR
                        ? checkerColor(pixelX, pixelZ)
                        : color);
            }
        }
        buildRow = endRow;

        if (buildRow >= textureSize) {
            buildingTexture.upload();
            publish();
        }
    }


    private int samplePixel(
            ClientLevel level,
            int minimumBlockX,
            int minimumBlockZ,
            int totalBlocks,
            int pixelX,
            int pixelZ,
            double blocksPerPixel
    ) {
        if (blocksPerPixel <= 0.85D) {
            return sampleAt(
                    level,
                    minimumBlockX,
                    minimumBlockZ,
                    totalBlocks,
                    pixelX,
                    pixelZ,
                    0.5D,
                    0.5D,
                    blocksPerPixel
            );
        }

        int first = sampleAt(level, minimumBlockX, minimumBlockZ, totalBlocks,
                pixelX, pixelZ, 0.25D, 0.25D, blocksPerPixel);
        int second = sampleAt(level, minimumBlockX, minimumBlockZ, totalBlocks,
                pixelX, pixelZ, 0.75D, 0.25D, blocksPerPixel);
        int third = sampleAt(level, minimumBlockX, minimumBlockZ, totalBlocks,
                pixelX, pixelZ, 0.25D, 0.75D, blocksPerPixel);
        int fourth = sampleAt(level, minimumBlockX, minimumBlockZ, totalBlocks,
                pixelX, pixelZ, 0.75D, 0.75D, blocksPerPixel);
        return gammaAverage(first, second, third, fourth);
    }

    private int sampleAt(
            ClientLevel level,
            int minimumBlockX,
            int minimumBlockZ,
            int totalBlocks,
            int pixelX,
            int pixelZ,
            double offsetX,
            double offsetZ,
            double blocksPerPixel
    ) {
        double worldX = minimumBlockX + ((pixelX + offsetX) * totalBlocks) / textureSize;
        double worldZ = minimumBlockZ + ((pixelZ + offsetZ) * totalBlocks) / textureSize;
        int color = AerialMapAtlas.sampleAtScale(level, worldX, worldZ, blocksPerPixel);
        return MapLighting.apply(level, (int) Math.floor(worldX), (int) Math.floor(worldZ), color);
    }

    private static int gammaAverage(int first, int second, int third, int fourth) {
        int[] colors = {first, second, third, fourth};
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        int count = 0;
        for (int color : colors) {
            if (color == TerrainColorSampler.VOID_COLOR || ((color >>> 24) & 0xFF) == 0) {
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

    void render(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            int size,
            WorldMapDataPayload view
    ) {
        graphics.fill(left, top, left + size, top + size, UNKNOWN_DARK);
        if (publishedTexture == null
                || publishedRadius < 0
                || !publishedDimension.equals(view.dimension())) {
            return;
        }

        int targetMinX = (view.centerChunkX() - view.radius()) << 4;
        int targetMinZ = (view.centerChunkZ() - view.radius()) << 4;
        int targetMaxX = (view.centerChunkX() + view.radius() + 1) << 4;
        int targetMaxZ = (view.centerChunkZ() + view.radius() + 1) << 4;

        int publishedMinX = (publishedCenterChunkX - publishedRadius) << 4;
        int publishedMinZ = (publishedCenterChunkZ - publishedRadius) << 4;
        int publishedMaxX = (publishedCenterChunkX + publishedRadius + 1) << 4;
        int publishedMaxZ = (publishedCenterChunkZ + publishedRadius + 1) << 4;

        int intersectionMinX = Math.max(targetMinX, publishedMinX);
        int intersectionMinZ = Math.max(targetMinZ, publishedMinZ);
        int intersectionMaxX = Math.min(targetMaxX, publishedMaxX);
        int intersectionMaxZ = Math.min(targetMaxZ, publishedMaxZ);
        if (intersectionMinX >= intersectionMaxX || intersectionMinZ >= intersectionMaxZ) {
            return;
        }

        double targetWidth = targetMaxX - targetMinX;
        double targetHeight = targetMaxZ - targetMinZ;
        double publishedWidth = publishedMaxX - publishedMinX;
        double publishedHeight = publishedMaxZ - publishedMinZ;

        int destinationLeft = left + (int) Math.floor(
                (intersectionMinX - targetMinX) * size / targetWidth
        );
        int destinationTop = top + (int) Math.floor(
                (intersectionMinZ - targetMinZ) * size / targetHeight
        );
        int destinationRight = left + (int) Math.ceil(
                (intersectionMaxX - targetMinX) * size / targetWidth
        );
        int destinationBottom = top + (int) Math.ceil(
                (intersectionMaxZ - targetMinZ) * size / targetHeight
        );

        float u0 = (float) ((intersectionMinX - publishedMinX) / publishedWidth);
        float v0 = (float) ((intersectionMinZ - publishedMinZ) / publishedHeight);
        float u1 = (float) ((intersectionMaxX - publishedMinX) / publishedWidth);
        float v1 = (float) ((intersectionMaxZ - publishedMinZ) / publishedHeight);

        graphics.blit(
                publishedTexture.getTextureView(),
                publishedTexture.getSampler(),
                destinationLeft,
                destinationTop,
                destinationRight,
                destinationBottom,
                u0,
                u1,
                v0,
                v1
        );
    }

    int progressPercent() {
        if (buildingTexture == null || textureSize <= 0 || buildRow >= textureSize) {
            return 100;
        }
        return Math.max(0, Math.min(99, buildRow * 100 / textureSize));
    }

    void invalidate() {
        forceRebuild = true;
    }

    private void beginBuild(WorldMapDataPayload payload, String dimension, int requiredSize, int nightBucket) {
        if (textureSize != requiredSize) {
            closeTexture(publishedTexture);
            closeTexture(buildingTexture);
            publishedTexture = null;
            buildingTexture = null;
            textureSize = requiredSize;
        }
        if (buildingTexture == null) {
            buildingTexture = new DynamicTexture("SSU high resolution world map build", textureSize, textureSize, true);
        }
        fillUnknown(buildingTexture.getPixels());
        buildCenterChunkX = payload.centerChunkX();
        buildCenterChunkZ = payload.centerChunkZ();
        buildRadius = payload.radius();
        buildDimension = dimension;
        buildNightBucket = nightBucket;
        buildRow = 0;
        forceRebuild = false;
    }

    private void publish() {
        DynamicTexture oldPublished = publishedTexture;
        publishedTexture = buildingTexture;
        buildingTexture = oldPublished;
        publishedCenterChunkX = buildCenterChunkX;
        publishedCenterChunkZ = buildCenterChunkZ;
        publishedRadius = buildRadius;
        publishedDimension = buildDimension;
        publishedNightBucket = buildNightBucket;
        buildRow = textureSize;
    }

    private static void fillUnknown(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixel(x, y, checkerColor(x, y));
            }
        }
    }

    private static int checkerColor(int x, int y) {
        return (((x >> 5) + (y >> 5)) & 1) == 0 ? UNKNOWN_DARK : UNKNOWN_LIGHT;
    }

    @Override
    public void close() {
        closeTexture(publishedTexture);
        closeTexture(buildingTexture);
        publishedTexture = null;
        buildingTexture = null;
        textureSize = 0;
        buildNightBucket = -1;
        publishedNightBucket = -1;
        forceRebuild = true;
    }

    private static void closeTexture(@Nullable DynamicTexture texture) {
        if (texture != null) {
            texture.close();
        }
    }
}
