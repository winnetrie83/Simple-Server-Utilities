package be.winnetrie.mod.simpleserverutilities.client.hologram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramImageDecoder.ImageAsset;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import be.winnetrie.mod.simpleserverutilities.network.HologramSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-only asynchronous cache for local resource and remote hologram images.
 * Remote bytes are never downloaded by the dedicated server.
 */
public final class HologramImageCache {
    private static final int MAX_SOURCE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_CACHE_ENTRIES = 64;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 12_000;
    private static final long RETRY_AFTER_NANOS = Duration.ofSeconds(30).toNanos();

    private static final ConcurrentHashMap<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, new LoaderThreadFactory());

    private HologramImageCache() {
    }

    public static ImageView getOrRequest(Minecraft minecraft, String rawSource) {
        String source = normalize(rawSource);
        if (source.isEmpty()) return new ImageView(Status.ERROR, null, "No image source", 0L);

        long now = System.nanoTime();
        CacheEntry entry = CACHE.get(source);
        if (entry == null) {
            enforceCacheLimit(source);
            entry = CACHE.computeIfAbsent(source, ignored -> new CacheEntry());
        }
        entry.lastAccessNanos = now;
        if (entry.shouldLoad(now)) beginLoad(minecraft, source, entry);
        return entry.view();
    }

    /** Preloads new synchronized images and evicts no-longer-used old entries. */
    public static void synchronize(List<HologramSyncPayload.Entry> entries) {
        Minecraft minecraft = Minecraft.getInstance();
        HashSet<String> active = new HashSet<>();
        int preloaded = 0;
        if (entries != null) {
            for (HologramSyncPayload.Entry entry : entries) {
                if (entry == null || entry.type() != HologramType.IMAGE) continue;
                String source = normalize(entry.imageSource());
                if (source.isEmpty()) continue;
                active.add(source);
                if (preloaded < MAX_CACHE_ENTRIES) {
                    getOrRequest(minecraft, source);
                    preloaded++;
                }
            }
        }

        CACHE.keySet().removeIf(source -> !active.contains(source));
    }

    public static void clear() {
        CACHE.clear();
    }


    private static void enforceCacheLimit(String requestedSource) {
        while (CACHE.size() >= MAX_CACHE_ENTRIES && !CACHE.containsKey(requestedSource)) {
            String oldestKey = null;
            long oldestAccess = Long.MAX_VALUE;
            for (var candidate : CACHE.entrySet()) {
                CacheEntry value = candidate.getValue();
                if (value.status == Status.LOADING) continue;
                if (value.lastAccessNanos < oldestAccess) {
                    oldestAccess = value.lastAccessNanos;
                    oldestKey = candidate.getKey();
                }
            }
            if (oldestKey == null || CACHE.remove(oldestKey) == null) return;
        }
    }

    private static void beginLoad(Minecraft minecraft, String source, CacheEntry entry) {
        synchronized (entry) {
            long now = System.nanoTime();
            if (!entry.shouldLoad(now)) return;
            entry.status = Status.LOADING;
            entry.message = "Loading image";
            entry.lastAttemptNanos = now;
        }

        LOADER.execute(() -> {
            try {
                byte[] bytes = isRemote(source)
                        ? downloadRemote(URI.create(source), 0)
                        : readResource(minecraft, source);
                ImageAsset asset = HologramImageDecoder.decode(bytes);
                synchronized (entry) {
                    entry.asset = asset;
                    entry.message = asset.animated()
                            ? "Animated GIF: " + asset.frames().size() + " frames"
                            : asset.format().toUpperCase(Locale.ROOT) + " "
                            + asset.sourceWidth() + "x" + asset.sourceHeight();
                    entry.animationEpochNanos = System.nanoTime();
                    entry.status = Status.READY;
                }
            } catch (Exception exception) {
                String message = conciseMessage(exception);
                synchronized (entry) {
                    entry.asset = null;
                    entry.message = message;
                    entry.status = Status.ERROR;
                }
                SimpleServerUtilities.LOGGER.warn("Could not load hologram image {}: {}",
                        safeSource(source), message);
            }
        });
    }

    private static byte[] readResource(Minecraft minecraft, String source) throws IOException {
        ResourceLocation identifier;
        try {
            identifier = ResourceLocation.parse(source);
        } catch (Exception exception) {
            throw new IOException("Invalid internal resource identifier.", exception);
        }
        validateExtension(identifier.getPath());
        try (InputStream input = minecraft.getResourceManager().open(identifier)) {
            return readLimited(input, MAX_SOURCE_BYTES);
        }
    }

    private static byte[] downloadRemote(URI uri, int redirects) throws IOException {
        if (redirects > MAX_REDIRECTS) throw new IOException("Too many HTTP redirects.");
        validateRemoteUri(uri);

        URLConnection rawConnection = uri.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection connection)) {
            throw new IOException("Only HTTP and HTTPS image links are supported.");
        }
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif;q=0.9,*/*;q=0.1");
        connection.setRequestProperty("User-Agent", "SimpleServerUtilities-HologramImage/1.6");

        try {
            int status = connection.getResponseCode();
            if (status >= 300 && status <= 399) {
                String location = connection.getHeaderField("Location");
                if (location == null || location.isBlank()) throw new IOException("HTTP redirect has no destination.");
                return downloadRemote(uri.resolve(location), redirects + 1);
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Image server returned HTTP " + status + ".");
            }

            long declaredLength = connection.getContentLengthLong();
            if (declaredLength > MAX_SOURCE_BYTES) {
                throw new IOException("Image is larger than 8 MiB.");
            }
            String contentType = connection.getContentType();
            if (contentType != null) {
                String lower = contentType.toLowerCase(Locale.ROOT);
                if (!(lower.startsWith("image/") || lower.startsWith("application/octet-stream"))) {
                    throw new IOException("URL did not return image data.");
                }
            }
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input, MAX_SOURCE_BYTES);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void validateRemoteUri(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            throw new IOException("Only HTTP and HTTPS links are supported.");
        }
        if (uri.getUserInfo() != null) throw new IOException("Image links may not contain login credentials.");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IOException("Image URL has no valid host.");
        for (InetAddress address : resolve(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || carrierGradeNat(address)) {
                throw new IOException("Private or local image addresses are not allowed.");
            }
        }
    }

    private static InetAddress[] resolve(String host) throws IOException {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IOException("Image host could not be resolved.", exception);
        }
    }

    private static boolean carrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) return false;
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    private static byte[] readLimited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maximum) throw new IOException("Image is larger than 8 MiB.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateExtension(String path) throws IOException {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            throw new IOException("Internal image must end in .png, .gif, .jpg or .jpeg.");
        }
    }

    private static boolean isRemote(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        message = message.trim();
        return message.length() <= 120 ? message : message.substring(0, 120);
    }

    private static String safeSource(String source) {
        if (!isRemote(source)) return source;
        try {
            URI uri = URI.create(source);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
        } catch (Exception ignored) {
            return "remote image";
        }
    }

    public enum Status {
        LOADING,
        READY,
        ERROR
    }

    public record ImageView(Status status, ImageAsset asset, String message, long animationEpochNanos) {
    }

    private static final class CacheEntry {
        private volatile Status status;
        private volatile ImageAsset asset;
        private volatile String message = "Waiting to load image";
        private volatile long animationEpochNanos;
        private volatile long lastAttemptNanos;
        private volatile long lastAccessNanos = System.nanoTime();

        private boolean shouldLoad(long now) {
            if (status == Status.LOADING || status == Status.READY) return false;
            return lastAttemptNanos == 0L || now - lastAttemptNanos >= RETRY_AFTER_NANOS;
        }

        private ImageView view() {
            Status visibleStatus = status == null ? Status.LOADING : status;
            return new ImageView(visibleStatus, asset, message, animationEpochNanos);
        }
    }

    private static final class LoaderThreadFactory implements ThreadFactory {
        private int index;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "SSU-Hologram-Image-" + (++index));
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    }
}
