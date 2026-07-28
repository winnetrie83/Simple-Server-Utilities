package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Builds a lightweight top-down terrain texture from chunks that are already
 * available on the client. The server remains authoritative for claim data;
 * this class only turns locally received world data into a visual background.
 */
final class ClaimTerrainMap implements AutoCloseable {

    static final int SAMPLES_PER_CHUNK = 16;

    private static final int CHUNKS_PER_TICK = 8;
    private static final int UNKNOWN_DARK = 0xFF20252A;
    private static final int UNKNOWN_LIGHT = 0xFF292F35;
    private static final int VOID_COLOR = 0xFF111419;

    private @Nullable DynamicTexture texture;
    private int textureWidth;
    private int textureHeight;
    private int centerChunkX = Integer.MIN_VALUE;
    private int centerChunkZ = Integer.MIN_VALUE;
    private int radius = -1;
    private String dimension = "";
    private List<ClaimMapDataPayload.Entry> pendingChunks = List.of();
    private int nextPendingChunk;
    private boolean dirty;

    void ensureView(ClaimMapDataPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        String currentDimension = level == null ? "" : level.dimension().identifier().toString();
        int gridSize = payload.radius() * 2 + 1;
        int requiredWidth = gridSize * SAMPLES_PER_CHUNK;
        int requiredHeight = requiredWidth;

        if (texture != null
                && textureWidth == requiredWidth
                && textureHeight == requiredHeight
                && centerChunkX == payload.centerChunkX()
                && centerChunkZ == payload.centerChunkZ()
                && radius == payload.radius()
                && dimension.equals(currentDimension)) {
            return;
        }

        closeTexture();
        textureWidth = requiredWidth;
        textureHeight = requiredHeight;
        centerChunkX = payload.centerChunkX();
        centerChunkZ = payload.centerChunkZ();
        radius = payload.radius();
        dimension = currentDimension;

        texture = new DynamicTexture("SSU claim terrain map", textureWidth, textureHeight, true);
        fillUnknown(texture.getPixels());
        texture.upload();

        List<ClaimMapDataPayload.Entry> ordered = new ArrayList<>(payload.chunks());
        int originX = Minecraft.getInstance().player == null
                ? centerChunkX
                : Minecraft.getInstance().player.chunkPosition().x();
        int originZ = Minecraft.getInstance().player == null
                ? centerChunkZ
                : Minecraft.getInstance().player.chunkPosition().z();
        ordered.sort(Comparator.comparingInt(entry -> distanceSquared(entry.chunkX(), entry.chunkZ(), originX, originZ)));
        pendingChunks = List.copyOf(ordered);
        nextPendingChunk = 0;
        dirty = false;
    }

    void tick(ClaimMapDataPayload payload) {
        ensureView(payload);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || texture == null || nextPendingChunk >= pendingChunks.size()) {
            return;
        }

        int processed = 0;
        while (nextPendingChunk < pendingChunks.size() && processed < CHUNKS_PER_TICK) {
            ClaimMapDataPayload.Entry entry = pendingChunks.get(nextPendingChunk++);
            renderChunk(level, entry.chunkX(), entry.chunkZ());
            processed++;
        }

        if (dirty) {
            texture.upload();
            dirty = false;
        }
    }

    void render(GuiGraphicsExtractor graphics, int left, int top, int size) {
        if (texture == null) {
            graphics.fill(left, top, left + size, top + size, UNKNOWN_DARK);
            return;
        }
        graphics.blit(
                texture.getTextureView(),
                texture.getSampler(),
                left,
                top,
                left + size,
                top + size,
                0.0F,
                1.0F,
                0.0F,
                1.0F
        );
    }

    private void renderChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (texture == null) {
            return;
        }

        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return;
        }

        int[][] heights = new int[SAMPLES_PER_CHUNK][SAMPLES_PER_CHUNK];
        for (int sampleZ = 0; sampleZ < SAMPLES_PER_CHUNK; sampleZ++) {
            for (int sampleX = 0; sampleX < SAMPLES_PER_CHUNK; sampleX++) {
                int localX = sampleCoordinate(sampleX);
                int localZ = sampleCoordinate(sampleZ);
                heights[sampleZ][sampleX] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
            }
        }

        NativeImage pixels = texture.getPixels();
        int imageChunkX = chunkX - (centerChunkX - radius);
        int imageChunkZ = chunkZ - (centerChunkZ - radius);
        int pixelBaseX = imageChunkX * SAMPLES_PER_CHUNK;
        int pixelBaseZ = imageChunkZ * SAMPLES_PER_CHUNK;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int sampleZ = 0; sampleZ < SAMPLES_PER_CHUNK; sampleZ++) {
            for (int sampleX = 0; sampleX < SAMPLES_PER_CHUNK; sampleX++) {
                int localX = sampleCoordinate(sampleX);
                int localZ = sampleCoordinate(sampleZ);
                int worldX = chunkX * 16 + localX;
                int worldZ = chunkZ * 16 + localZ;
                int surfaceY = heights[sampleZ][sampleX];
                int argb = sampleSurfaceColor(level, chunk, pos, worldX, surfaceY, worldZ,
                        brightnessFor(heights, sampleX, sampleZ));
                pixels.setPixel(pixelBaseX + sampleX, pixelBaseZ + sampleZ, argb);
            }
        }
        dirty = true;
    }

    private static int sampleSurfaceColor(
            ClientLevel level,
            LevelChunk chunk,
            BlockPos.MutableBlockPos pos,
            int worldX,
            int surfaceY,
            int worldZ,
            MapColor.Brightness brightness
    ) {
        int minY = level.getMinY();
        int y = Math.min(surfaceY, level.getMaxY());
        for (int attempts = 0; attempts < 16 && y >= minY; attempts++, y--) {
            pos.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(pos);
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != MapColor.NONE) {
                return mapColor.calculateARGBColor(brightness);
            }
        }
        return VOID_COLOR;
    }

    private static MapColor.Brightness brightnessFor(int[][] heights, int x, int z) {
        int current = heights[z][x];
        int comparisonTotal = 0;
        int comparisonCount = 0;
        if (x > 0) {
            comparisonTotal += heights[z][x - 1];
            comparisonCount++;
        }
        if (z > 0) {
            comparisonTotal += heights[z - 1][x];
            comparisonCount++;
        }
        if (comparisonCount == 0) {
            return MapColor.Brightness.NORMAL;
        }

        double delta = current - (comparisonTotal / (double) comparisonCount);
        if (delta >= 2.0D) {
            return MapColor.Brightness.HIGH;
        }
        if (delta <= -2.0D) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    private static int sampleCoordinate(int sample) {
        if (SAMPLES_PER_CHUNK >= 16) {
            return Math.min(15, sample);
        }
        int step = 16 / SAMPLES_PER_CHUNK;
        return sample * step + step / 2;
    }

    private static int distanceSquared(int x, int z, int originX, int originZ) {
        int dx = x - originX;
        int dz = z - originZ;
        return dx * dx + dz * dz;
    }

    private static void fillUnknown(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int checker = ((x / SAMPLES_PER_CHUNK) + (y / SAMPLES_PER_CHUNK)) & 1;
                image.setPixel(x, y, checker == 0 ? UNKNOWN_DARK : UNKNOWN_LIGHT);
            }
        }
    }

    private void closeTexture() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    @Override
    public void close() {
        closeTexture();
        pendingChunks = List.of();
        nextPendingChunk = 0;
    }
}
