package be.winnetrie.mod.simpleserverutilities.client.minimap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
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

/** Incrementally creates the minimap terrain texture from chunks already loaded by the client. */
final class MinimapTerrainMap implements AutoCloseable {

    static final int TEXTURE_SIZE = 128;

    private static final int ROWS_PER_TICK = 12;
    private static final int HALF = TEXTURE_SIZE / 2;
    private static final int UNKNOWN_DARK = 0xFF20252A;
    private static final int UNKNOWN_LIGHT = 0xFF292F35;
    private static final int VOID_COLOR = 0xFF111419;
    private static final int CIRCLE_RADIUS = HALF - 1;

    private @Nullable DynamicTexture texture;
    private int centerBlockX = Integer.MIN_VALUE;
    private int centerBlockZ = Integer.MIN_VALUE;
    private String dimension = "";
    private String shape = "CIRCLE";
    private int overlayHash;
    private int nextRow = TEXTURE_SIZE;
    private MinimapDataPayload data;
    private Map<Long, ClaimChunkStatus> claimLookup = Map.of();
    private List<MinimapDataPayload.RegionOverlay> regions = List.of();

    void tick(MinimapDataPayload updated) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !updated.enabled()) {
            return;
        }

        String currentDimension = level.dimension().identifier().toString();
        int playerX = (int) Math.floor(minecraft.player.getX());
        int playerZ = (int) Math.floor(minecraft.player.getZ());
        int updatedOverlayHash = overlayHash(updated);
        int movedX = centerBlockX == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(playerX - centerBlockX);
        int movedZ = centerBlockZ == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(playerZ - centerBlockZ);
        boolean moved = centerBlockX == Integer.MIN_VALUE
                || movedX >= 16
                || movedZ >= 16
                || (nextRow >= TEXTURE_SIZE && (movedX >= 4 || movedZ >= 4));
        boolean changed = texture == null
                || !currentDimension.equals(dimension)
                || !updated.shape().equalsIgnoreCase(shape)
                || updatedOverlayHash != overlayHash;

        if (moved || changed) {
            beginRebuild(level, updated, playerX, playerZ, currentDimension, updatedOverlayHash);
        }

        if (texture == null || nextRow >= TEXTURE_SIZE) {
            return;
        }

        NativeImage pixels = texture.getPixels();
        int endRow = Math.min(TEXTURE_SIZE, nextRow + ROWS_PER_TICK);
        for (int pixelZ = nextRow; pixelZ < endRow; pixelZ++) {
            renderRow(level, pixels, pixelZ);
        }
        nextRow = endRow;
        texture.upload();
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

    void invalidate() {
        overlayHash = Integer.MIN_VALUE;
    }

    private void beginRebuild(
            ClientLevel level,
            MinimapDataPayload updated,
            int playerX,
            int playerZ,
            String currentDimension,
            int updatedOverlayHash
    ) {
        ensureTexture();
        centerBlockX = playerX;
        centerBlockZ = playerZ;
        dimension = currentDimension;
        shape = updated.shape();
        overlayHash = updatedOverlayHash;
        data = updated;
        regions = updated.showRegions() ? updated.regions() : List.of();

        if (updated.showClaims()) {
            Map<Long, ClaimChunkStatus> lookup = new HashMap<>();
            for (MinimapDataPayload.ClaimOverlay claim : updated.claims()) {
                lookup.put(chunkKey(claim.chunkX(), claim.chunkZ()), claim.status());
            }
            claimLookup = Map.copyOf(lookup);
        } else {
            claimLookup = Map.of();
        }

        fillUnknown(texture.getPixels());
        texture.upload();
        nextRow = 0;
    }

    private void renderRow(ClientLevel level, NativeImage pixels, int pixelZ) {
        int offsetZ = pixelZ - HALF;
        int worldZ = centerBlockZ + offsetZ;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int pixelX = 0; pixelX < TEXTURE_SIZE; pixelX++) {
            int offsetX = pixelX - HALF;
            if (isOutsideShape(offsetX, offsetZ)) {
                pixels.setPixel(pixelX, pixelZ, 0x00000000);
                continue;
            }

            int worldX = centerBlockX + offsetX;
            int color = sampleSurfaceColor(level, pos, worldX, worldZ);
            color = applyClaimOverlay(color, worldX, worldZ);
            color = applyRegionOverlay(color, worldX, worldZ);
            color = applyShapeBorder(color, offsetX, offsetZ);
            pixels.setPixel(pixelX, pixelZ, color);
        }
    }

    private int applyClaimOverlay(int base, int worldX, int worldZ) {
        ClaimChunkStatus status = claimLookup.get(chunkKey(worldX >> 4, worldZ >> 4));
        if (status == null || status == ClaimChunkStatus.WILDERNESS || status == ClaimChunkStatus.REGION) {
            return base;
        }

        int rgb = switch (status) {
            case OWNED_BY_SELF -> data.ownClaimColor();
            case OWNED_BY_TRUSTED -> lighten(data.ownClaimColor(), 36);
            case OWNED_BY_OTHER -> data.otherClaimColor();
            default -> data.otherClaimColor();
        };
        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        boolean edge = localX == 0 || localX == 15 || localZ == 0 || localZ == 15;
        return blend(base, withAlpha(rgb, edge ? 210 : 46));
    }

    private int applyRegionOverlay(int base, int worldX, int worldZ) {
        int color = base;
        for (MinimapDataPayload.RegionOverlay region : regions) {
            if (worldX < region.minX() || worldX > region.maxX()
                    || worldZ < region.minZ() || worldZ > region.maxZ()) {
                continue;
            }
            boolean edge = worldX == region.minX() || worldX == region.maxX()
                    || worldZ == region.minZ() || worldZ == region.maxZ();
            color = blend(color, withAlpha(data.regionColor(), edge ? 220 : 34));
        }
        return color;
    }

    private int applyShapeBorder(int color, int offsetX, int offsetZ) {
        if (!"CIRCLE".equalsIgnoreCase(shape)) {
            return color;
        }
        int distanceSquared = offsetX * offsetX + offsetZ * offsetZ;
        int inner = CIRCLE_RADIUS - 2;
        if (distanceSquared >= inner * inner) {
            return blend(color, 0xD9000000);
        }
        return color;
    }

    private boolean isOutsideShape(int offsetX, int offsetZ) {
        return "CIRCLE".equalsIgnoreCase(shape)
                && offsetX * offsetX + offsetZ * offsetZ > CIRCLE_RADIUS * CIRCLE_RADIUS;
    }

    private static int sampleSurfaceColor(
            ClientLevel level,
            BlockPos.MutableBlockPos pos,
            int worldX,
            int worldZ
    ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return checkerColor(worldX, worldZ);
        }

        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        int minY = level.getMinY();
        int y = Math.min(surfaceY, level.getMaxY());
        MapColor.Brightness brightness = surfaceBrightness(chunk, localX, localZ, surfaceY);

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

    private static MapColor.Brightness surfaceBrightness(LevelChunk chunk, int localX, int localZ, int current) {
        int total = 0;
        int count = 0;
        if (localX > 0) {
            total += chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX - 1, localZ);
            count++;
        }
        if (localZ > 0) {
            total += chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ - 1);
            count++;
        }
        if (count == 0) {
            return MapColor.Brightness.NORMAL;
        }
        double delta = current - total / (double) count;
        if (delta >= 2.0D) {
            return MapColor.Brightness.HIGH;
        }
        if (delta <= -2.0D) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    private void ensureTexture() {
        if (texture == null) {
            texture = new DynamicTexture("SSU minimap terrain", TEXTURE_SIZE, TEXTURE_SIZE, true);
        }
    }

    private void fillUnknown(NativeImage image) {
        for (int z = 0; z < image.getHeight(); z++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int offsetX = x - HALF;
                int offsetZ = z - HALF;
                image.setPixel(x, z, isOutsideShape(offsetX, offsetZ)
                        ? 0x00000000
                        : checkerColor(x, z));
            }
        }
    }

    private static int checkerColor(int x, int z) {
        return (((x >> 4) + (z >> 4)) & 1) == 0 ? UNKNOWN_DARK : UNKNOWN_LIGHT;
    }

    private static int overlayHash(MinimapDataPayload payload) {
        int result = payload.dimension().hashCode();
        result = 31 * result + payload.shape().toUpperCase(java.util.Locale.ROOT).hashCode();
        result = 31 * result + Boolean.hashCode(payload.showClaims());
        result = 31 * result + Boolean.hashCode(payload.showRegions());
        result = 31 * result + payload.ownClaimColor();
        result = 31 * result + payload.otherClaimColor();
        result = 31 * result + payload.regionColor();
        result = 31 * result + payload.claims().hashCode();
        result = 31 * result + payload.regions().hashCode();
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
        if (texture != null) {
            texture.close();
            texture = null;
        }
        nextRow = TEXTURE_SIZE;
        claimLookup = Map.of();
        regions = List.of();
        data = null;
    }
}
