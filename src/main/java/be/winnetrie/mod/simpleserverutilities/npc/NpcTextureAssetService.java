package be.winnetrie.mod.simpleserverutilities.npc;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;

/**
 * Server-authoritative loader/cache for optional custom NPC skins.
 * Local paths are sandboxed below simpleserverutilities/npcs/textures and URLs are HTTPS-only.
 */
public final class NpcTextureAssetService {
    public static final int MAX_TEXTURE_BYTES = 512 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<CacheEntry>> pending = new ConcurrentHashMap<>();
    private volatile Path localRoot;

    public void configure(MinecraftServer server) {
        localRoot = StoragePaths.npcTextures(StoragePaths.root(server)).toAbsolutePath().normalize();
        try { Files.createDirectories(localRoot); }
        catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Could not create NPC texture folder {}.", localRoot, exception);
        }
        clear();
    }

    public void clear() {
        cache.clear();
        pending.clear();
    }

    public void invalidate(String definitionId) {
        if (definitionId == null) return;
        cache.remove(definitionId);
        pending.remove(definitionId);
    }

    public Asset asset(NpcDefinition definition) {
        if (definition == null || !definition.textureSource().custom()) return null;
        String sourceKey = sourceKey(definition);
        CacheEntry existing = cache.get(definition.id);
        if (existing != null && existing.sourceKey.equals(sourceKey)) return existing.asset;
        if (existing != null) cache.remove(definition.id, existing);
        String definitionId = definition.id;
        if (!pending.containsKey(definitionId)) {
            NpcDefinition snapshot = definition.copy();
            CompletableFuture<CacheEntry> future = CompletableFuture.supplyAsync(() -> load(snapshot, sourceKey));
            CompletableFuture<CacheEntry> prior = pending.putIfAbsent(definitionId, future);
            if (prior == null) {
                future.whenComplete((entry, failure) -> {
                    if (!pending.remove(definitionId, future)) return;
                    if (failure != null) {
                        cache.put(definitionId, new CacheEntry(sourceKey, null));
                        SimpleServerUtilities.LOGGER.warn("Failed loading custom NPC texture for {}: {}", definitionId, failure.getMessage());
                    } else {
                        cache.put(definitionId, entry);
                    }
                });
            }
        }
        return null;
    }

    public String localFolderHint() {
        Path root = localRoot;
        return root == null ? "simpleserverutilities/npcs/textures" : root.toString();
    }

    private CacheEntry load(NpcDefinition definition, String sourceKey) {
        try {
            byte[] bytes = switch (definition.textureSource()) {
                case LOCAL -> loadLocal(definition.textureValue);
                case URL -> loadUrl(definition.textureValue);
                default -> null;
            };
            if (bytes == null) return new CacheEntry(sourceKey, null);
            validatePng(bytes);
            String hash = sha256(bytes);
            return new CacheEntry(sourceKey, new Asset(definition.id, hash, definition.textureModel, bytes));
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.warn("Custom NPC texture '{}' for {} could not be loaded: {}",
                    definition.textureValue, definition.id, exception.getMessage());
            return new CacheEntry(sourceKey, null);
        }
    }

    private byte[] loadLocal(String raw) throws Exception {
        Path root = localRoot;
        if (root == null) throw new IllegalStateException("NPC texture storage is not initialized");
        String safe = raw == null ? "" : raw.trim();
        if (safe.isBlank()) throw new IllegalArgumentException("Local texture filename is empty");
        Path path = root.resolve(safe).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Local texture must stay inside the NPC texture folder");
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Local texture file does not exist: " + safe);
        long size = Files.size(path);
        if (size <= 0 || size > MAX_TEXTURE_BYTES) throw new IllegalArgumentException("Texture must be at most 512 KiB");
        return Files.readAllBytes(path);
    }

    private static byte[] loadUrl(String raw) throws Exception {
        URI uri = URI.create(raw == null ? "" : raw.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Only HTTPS texture URLs are allowed");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("Texture URL has no host");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "SimpleServerUtilities-NPC-Texture/1").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream ignored = response.body()) { /* close */ }
            throw new IllegalArgumentException("Texture URL returned HTTP " + response.statusCode());
        }
        try (InputStream input = response.body()) {
            byte[] bytes = input.readNBytes(MAX_TEXTURE_BYTES + 1);
            if (bytes.length > MAX_TEXTURE_BYTES) throw new IllegalArgumentException("Texture download exceeds 512 KiB");
            return bytes;
        }
    }

    public static void validatePng(byte[] bytes) {
        if (bytes == null || bytes.length < 24 || !Arrays.equals(Arrays.copyOf(bytes, 8), PNG_SIGNATURE)) {
            throw new IllegalArgumentException("Texture must be a PNG image");
        }
        ByteBuffer dimensions = ByteBuffer.wrap(bytes, 16, 8).order(ByteOrder.BIG_ENDIAN);
        int width = dimensions.getInt();
        int height = dimensions.getInt();
        if (width != 64 || height != 64) {
            throw new IllegalArgumentException("NPC custom skins must be 64x64 PNG files");
        }
    }

    private static String sourceKey(NpcDefinition definition) {
        return definition.textureSource + "\n" + definition.textureValue + "\n" + definition.textureModel;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record CacheEntry(String sourceKey, Asset asset) {}

    public record Asset(String definitionId, String hash, String model, byte[] bytes) {
        public Asset {
            definitionId = NpcDefinition.sanitizeId(definitionId);
            hash = hash == null ? "" : hash;
            model = "slim".equalsIgnoreCase(model) ? "slim" : "wide";
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
