package be.winnetrie.mod.simpleserverutilities.client.map;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Resource-pack-aware colour palette for SSU's aerial maps.
 *
 * <p>The palette deliberately uses only public Minecraft rendering data. It
 * resolves a block state's top/model/particle representative sprites, reduces the
 * actual resource-pack pixels to one representative aerial colour and applies
 * the normal in-world block tint at the sampled position. The result is much
 * closer to the blocks a player sees than a flat {@link MapColor} alone.</p>
 */
final class BlockTexturePalette {

    static final int DETAIL = 1;
    private static final int CELLS = DETAIL * DETAIL;
    private static final int OPAQUE_ALPHA = 255;

    private static final Map<BlockState, TextureProfile> CACHE = new IdentityHashMap<>();
    private static ModelManager observedModelManager;
    private static int generation;
    private static int fingerprintGeneration = Integer.MIN_VALUE;
    private static String cachedFingerprint = "";

    private BlockTexturePalette() {
    }

    /**
     * Detects resource/model reloads. Existing atlas tiles can use the returned
     * generation to decide whether they need to be rebuilt.
     */
    static int ensureCurrent() {
        ModelManager current = Minecraft.getInstance().getModelManager();
        if (current != observedModelManager) {
            observedModelManager = current;
            CACHE.clear();
            generation++;
        }
        return generation;
    }

    static void clear() {
        CACHE.clear();
        observedModelManager = null;
        generation++;
        fingerprintGeneration = Integer.MIN_VALUE;
        cachedFingerprint = "";
    }

    /** Stable cache namespace for the currently selected resource packs. */
    static String resourceFingerprint() {
        if (fingerprintGeneration == generation && !cachedFingerprint.isBlank()) {
            return cachedFingerprint;
        }
        Minecraft minecraft = Minecraft.getInstance();
        StringBuilder source = new StringBuilder("ssu-aerial-format-2");
        try {
            Method repositoryMethod = minecraft.getClass().getMethod("getResourcePackRepository");
            Object repository = repositoryMethod.invoke(minecraft);
            if (repository != null) {
                for (String methodName : List.of("getSelectedIds", "getSelectedPacks")) {
                    try {
                        Method selectedMethod = repository.getClass().getMethod(methodName);
                        Object selected = selectedMethod.invoke(repository);
                        if (selected instanceof Collection<?> collection) {
                            for (Object value : collection) {
                                source.append('|').append(String.valueOf(value));
                            }
                            break;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // Try the other stable repository accessor.
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            source.append("|default");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int index = 0; index < 12; index++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            cachedFingerprint = result.toString();
        } catch (NoSuchAlgorithmException e) {
            cachedFingerprint = Integer.toUnsignedString(source.toString().hashCode(), 16);
        }
        fingerprintGeneration = generation;
        return cachedFingerprint;
    }

    static TextureProfile profile(BlockState state, ClientLevel level, BlockPos pos) {
        ensureCurrent();
        TextureProfile profile = CACHE.get(state);
        if (profile == null) {
            profile = buildProfile(state, level, pos);
            CACHE.put(state, profile);
        }
        return profile;
    }

    /**
     * Returns Minecraft's live in-world tint without allocating a temporary
     * tinted profile. Grass, foliage and water can therefore vary per biome
     * while the expensive texture fingerprint remains cached per block state.
     */
    static int tint(BlockState state, ClientLevel level, BlockPos pos) {
        return resolveTint(state, level, pos);
    }

    private static TextureProfile buildProfile(BlockState state, ClientLevel level, BlockPos pos) {
        int fallback = fallbackColor(state, level, pos);
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);

            // 1.21.1 still exposes the classic baked-model API. Prefer top-facing
            // quads for an aerial map, then general quads, then the particle icon.
            List<TextureAtlasSprite> sprites = modelSprites(model, state, Direction.UP);
            if (sprites.isEmpty()) {
                sprites = modelSprites(model, state, null);
            }
            if (!sprites.isEmpty()) {
                TextureProfile modelProfile = fromSprites(sprites, fallback);
                if (modelProfile != null) {
                    return modelProfile;
                }
            }

            TextureAtlasSprite sprite = model.getParticleIcon();
            NativeImage image = usableImage(sprite);
            if (image != null) {
                return fromImage(image, fallback);
            }
        } catch (Throwable ignored) {
            // Modded/dynamic models may not expose stable baked quads. Keep the
            // map usable by falling back to Minecraft's map colour.
        }

        int[] cells = new int[CELLS];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = fallback;
        }
        return new TextureProfile(cells, fallback, 1.0F, false);
    }

    private static List<TextureAtlasSprite> modelSprites(
            BakedModel model,
            BlockState state,
            Direction direction
    ) {
        List<TextureAtlasSprite> sprites = new ArrayList<>();
        Map<TextureAtlasSprite, Boolean> seen = new IdentityHashMap<>();
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create());
        if (quads == null) {
            return sprites;
        }
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            if (sprite != null && usableImage(sprite) != null && seen.put(sprite, Boolean.TRUE) == null) {
                sprites.add(sprite);
                if (sprites.size() >= 12) {
                    break;
                }
            }
        }
        return sprites;
    }

    private static NativeImage usableImage(TextureAtlasSprite sprite) {
        if (sprite == null || sprite.contents() == null || sprite.contents().byMipLevel.length == 0) {
            return null;
        }
        NativeImage image = sprite.contents().byMipLevel[0];
        return image != null && image.getWidth() > 0 && image.getHeight() > 0 ? image : null;
    }

    private static TextureProfile fromSprites(List<TextureAtlasSprite> sprites, int fallback) {
        List<TextureProfile> profiles = new ArrayList<>(sprites.size());
        for (TextureAtlasSprite sprite : sprites) {
            NativeImage image = usableImage(sprite);
            if (image != null) {
                profiles.add(fromImage(image, fallback));
            }
        }
        if (profiles.isEmpty()) {
            return null;
        }
        if (profiles.size() == 1) {
            return profiles.getFirst();
        }

        int[] cells = new int[CELLS];
        for (int cell = 0; cell < CELLS; cell++) {
            cells[cell] = blendProfiles(profiles, cell, fallback);
        }
        int average = blendProfiles(profiles, -1, fallback);
        double transparentProduct = 1.0D;
        for (TextureProfile profile : profiles) {
            transparentProduct *= 1.0D - clamp(profile.alphaCoverage(), 0.0F, 1.0F);
        }
        float coverage = (float) (1.0D - transparentProduct);
        return new TextureProfile(cells, average, coverage, true);
    }

    private static int blendProfiles(List<TextureProfile> profiles, int cell, int fallback) {
        double totalWeight = 0.0D;
        double red = 0.0D;
        double green = 0.0D;
        double blue = 0.0D;
        for (TextureProfile profile : profiles) {
            double weight = Math.max(0.04D, profile.alphaCoverage());
            int color = cell < 0 ? profile.average() : profile.cells()[cell];
            red += ((color >>> 16) & 0xFF) * weight;
            green += ((color >>> 8) & 0xFF) * weight;
            blue += (color & 0xFF) * weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0D) {
            return fallback;
        }
        return 0xFF000000
                | (clampChannel((int) Math.round(red / totalWeight)) << 16)
                | (clampChannel((int) Math.round(green / totalWeight)) << 8)
                | clampChannel((int) Math.round(blue / totalWeight));
    }

    private static TextureProfile fromImage(NativeImage image, int fallback) {
        int[] cells = new int[CELLS];
        long totalAlpha = 0L;
        long possibleAlpha = (long) image.getWidth() * image.getHeight() * OPAQUE_ALPHA;

        for (int cellZ = 0; cellZ < DETAIL; cellZ++) {
            int minY = cellZ * image.getHeight() / DETAIL;
            int maxY = Math.max(minY + 1, (cellZ + 1) * image.getHeight() / DETAIL);
            for (int cellX = 0; cellX < DETAIL; cellX++) {
                int minX = cellX * image.getWidth() / DETAIL;
                int maxX = Math.max(minX + 1, (cellX + 1) * image.getWidth() / DETAIL);
                ColorAccumulator accumulator = new ColorAccumulator();

                for (int y = minY; y < maxY && y < image.getHeight(); y++) {
                    for (int x = minX; x < maxX && x < image.getWidth(); x++) {
                        int pixel = image.getPixelRGBA(x, y);
                        int alpha = (pixel >>> 24) & 0xFF;
                        totalAlpha += alpha;
                        accumulator.add(pixel, alpha);
                    }
                }
                cells[cellZ * DETAIL + cellX] = accumulator.finish(fallback);
            }
        }

        ColorAccumulator whole = new ColorAccumulator();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                whole.add(pixel, (pixel >>> 24) & 0xFF);
            }
        }

        float alphaCoverage = possibleAlpha <= 0L
                ? 1.0F
                : Math.max(0.0F, Math.min(1.0F, totalAlpha / (float) possibleAlpha));
        return new TextureProfile(cells, whole.finish(fallback), alphaCoverage, true);
    }

    private static int resolveTint(BlockState state, ClientLevel level, BlockPos pos) {
        try {
            int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            if (tint != -1) {
                return 0xFF000000 | (tint & 0x00FFFFFF);
            }
        } catch (Throwable ignored) {
            // A broken third-party tint provider should not break the map.
        }
        return 0xFFFFFFFF;
    }

    private static int fallbackColor(BlockState state, ClientLevel level, BlockPos pos) {
        try {
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != MapColor.NONE) {
                return mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
            }
        } catch (Throwable ignored) {
            // Keep a neutral fallback for unusual modded states.
        }
        return 0xFF7F7F7F;
    }

    record TextureProfile(int[] cells, int average, float alphaCoverage, boolean textureBacked) {

        int detail(int x, int z, int tint) {
            int base = cells[Math.max(0, Math.min(DETAIL - 1, z)) * DETAIL
                    + Math.max(0, Math.min(DETAIL - 1, x))];
            return isWhiteTint(tint) ? base : multiply(base, tint);
        }

        int tintedAverage(int tint) {
            return isWhiteTint(tint) ? average : multiply(average, tint);
        }

        private static boolean isWhiteTint(int tint) {
            return (tint & 0x00FFFFFF) == 0x00FFFFFF;
        }

        private static int multiply(int base, int tint) {
            int alpha = (base >>> 24) & 0xFF;
            int red = ((base >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 255;
            int green = ((base >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 255;
            int blue = (base & 0xFF) * (tint & 0xFF) / 255;
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class ColorAccumulator {
        private long alpha;
        private long red;
        private long green;
        private long blue;

        private void add(int argb, int pixelAlpha) {
            if (pixelAlpha <= 0) {
                return;
            }
            alpha += pixelAlpha;
            red += (long) ((argb >>> 16) & 0xFF) * pixelAlpha;
            green += (long) ((argb >>> 8) & 0xFF) * pixelAlpha;
            blue += (long) (argb & 0xFF) * pixelAlpha;
        }

        private int finish(int fallback) {
            if (alpha <= 0L) {
                return fallback;
            }
            int r = (int) Math.max(0L, Math.min(255L, red / alpha));
            int g = (int) Math.max(0L, Math.min(255L, green / alpha));
            int b = (int) Math.max(0L, Math.min(255L, blue / alpha));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }
}
