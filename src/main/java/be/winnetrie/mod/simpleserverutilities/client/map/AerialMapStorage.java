package be.winnetrie.mod.simpleserverutilities.client.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.client.Minecraft;

/** Asynchronous, server-isolated disk storage for explored aerial-map tiles. */
final class AerialMapStorage {

    private static final int MAGIC = 0x53535541; // SSUA
    private static final int VERSION = 5;
    private static final long DEFAULT_MAX_DISK_BYTES = 512L * 1024L * 1024L;
    private static final int PRUNE_EVERY_WRITES = 256;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SSU-Aerial-Map");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<TileRequest> PENDING_READS = ConcurrentHashMap.newKeySet();
    private static final Set<TileRequest> MISSING_READS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<LoadedTile> COMPLETED_READS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SESSION = new AtomicInteger();
    private static final AtomicLong READ_HITS = new AtomicLong();
    private static final AtomicLong READ_MISSES = new AtomicLong();
    private static final AtomicLong READ_FAILURES = new AtomicLong();
    private static final AtomicLong WRITES = new AtomicLong();
    private static final AtomicLong WRITE_FAILURES = new AtomicLong();
    private static final AtomicLong PRUNED_FILES = new AtomicLong();
    private static final AtomicLong DISK_BYTES = new AtomicLong();
    private static final AtomicLong DISK_FILES = new AtomicLong();
    private static final AtomicBoolean STATS_REFRESH_QUEUED = new AtomicBoolean();
    private static final ConcurrentHashMap<Path, PendingWrite> PENDING_WRITES = new ConcurrentHashMap<>();
    private static final Set<Path> ACTIVE_WRITES = ConcurrentHashMap.newKeySet();

    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> flush(Duration.ofSeconds(5)),
                    "SSU-Aerial-Map-Shutdown"
            ));
        } catch (IllegalStateException | SecurityException ignored) {
            // The cache is opportunistic; Minecraft can still shut down safely.
        }
    }

    private AerialMapStorage() {
    }

    static String serverKey(Minecraft minecraft) {
        String identity = discoverServerIdentity(minecraft);
        return hash(identity.isBlank() ? "unknown-server" : identity);
    }

    static void refreshStatistics(Minecraft minecraft) {
        Path root = cacheRoot(minecraft);
        if (!STATS_REFRESH_QUEUED.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                prune(root);
            } finally {
                STATS_REFRESH_QUEUED.set(false);
            }
        });
    }

    static void requestLoad(
            Minecraft minecraft,
            String serverKey,
            String dimension,
            String paletteFingerprint,
            int chunkX,
            int chunkZ
    ) {
        int session = SESSION.get();
        TileRequest request = new TileRequest(session, serverKey, dimension, paletteFingerprint, chunkX, chunkZ);
        if (MISSING_READS.contains(request) || !PENDING_READS.add(request)) {
            return;
        }
        Path path = tilePath(minecraft, request);
        EXECUTOR.execute(() -> {
            try {
                if (!Files.exists(path)) {
                    if (request.session() == SESSION.get()) {
                        READ_MISSES.incrementAndGet();
                        MISSING_READS.add(request);
                    }
                    return;
                }
                LoadedTile tile = read(path, request);
                if (tile != null && request.session() == SESSION.get()) {
                    READ_HITS.incrementAndGet();
                    COMPLETED_READS.add(tile);
                    try {
                        Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
                    } catch (IOException ignored) {
                        // Cache recency is best effort.
                    }
                }
            } catch (Exception e) {
                if (request.session() == SESSION.get()) {
                    READ_FAILURES.incrementAndGet();
                    archiveBroken(path);
                    SimpleServerUtilities.LOGGER.warn("Could not read SSU aerial map tile {}.", path, e);
                }
            } finally {
                PENDING_READS.remove(request);
            }
        });
    }

    static void write(
            Minecraft minecraft,
            String serverKey,
            String dimension,
            String paletteFingerprint,
            int chunkX,
            int chunkZ,
            int[] basePixels,
            short[] terrainHeights,
            short[] surfaceHeights,
            byte[] surfaceKinds,
            byte[] blockLights,
            String[] surfaceBlockIds,
            String[] biomeIds
    ) {
        TileRequest request = new TileRequest(
                SESSION.get(), serverKey, dimension, paletteFingerprint, chunkX, chunkZ
        );
        Path path = tilePath(minecraft, request);
        Path cacheRoot = cacheRoot(minecraft);
        MISSING_READS.remove(request);
        PENDING_WRITES.put(path, new PendingWrite(
                request,
                basePixels.clone(),
                terrainHeights.clone(),
                surfaceHeights.clone(),
                surfaceKinds.clone(),
                blockLights.clone(),
                surfaceBlockIds.clone(),
                biomeIds.clone(),
                cacheRoot
        ));
        scheduleWrite(path);
    }


    private static void scheduleWrite(Path path) {
        PendingWrite next = PENDING_WRITES.get(path);
        if (next == null || !ACTIVE_WRITES.add(path)) {
            return;
        }
        int scheduledSession = next.request().session();
        EXECUTOR.execute(() -> drainWrites(path, scheduledSession));
    }

    private static void drainWrites(Path path, int scheduledSession) {
        try {
            PendingWrite pending;
            while ((pending = PENDING_WRITES.remove(path)) != null) {
                if (pending.request().session() != scheduledSession) {
                    PENDING_WRITES.merge(path, pending, AerialMapStorage::newerWrite);
                    break;
                }
                try {
                    boolean existed = Files.exists(path);
                    long previousSize = existed ? Files.size(path) : 0L;
                    writeAtomic(
                            path,
                            pending.request(),
                            pending.basePixels(),
                            pending.terrainHeights(),
                            pending.surfaceHeights(),
                            pending.surfaceKinds(),
                            pending.blockLights(),
                            pending.surfaceBlockIds(),
                            pending.biomeIds()
                    );
                    long newSize = Files.size(path);
                    DISK_BYTES.updateAndGet(current -> Math.max(0L, current + newSize - previousSize));
                    if (!existed) {
                        DISK_FILES.incrementAndGet();
                    }
                    long completed = WRITES.incrementAndGet();
                    if (completed % PRUNE_EVERY_WRITES == 0L) {
                        prune(pending.cacheRoot());
                    }
                } catch (Exception e) {
                    WRITE_FAILURES.incrementAndGet();
                    SimpleServerUtilities.LOGGER.warn("Could not persist SSU aerial map tile {}.", path, e);
                }
            }
        } finally {
            ACTIVE_WRITES.remove(path);
            if (PENDING_WRITES.containsKey(path)) {
                scheduleWrite(path);
            }
        }
    }

    private static PendingWrite newerWrite(PendingWrite first, PendingWrite second) {
        return first.request().session() >= second.request().session() ? first : second;
    }

    static List<LoadedTile> drainCompleted(String serverKey, String paletteFingerprint) {
        int session = SESSION.get();
        List<LoadedTile> result = new ArrayList<>();
        LoadedTile tile;
        while ((tile = COMPLETED_READS.poll()) != null) {
            if (tile.session() == session
                    && tile.serverKey().equals(serverKey)
                    && tile.paletteFingerprint().equals(paletteFingerprint)) {
                result.add(tile);
            }
        }
        return result;
    }

    static void invalidatePalette(Minecraft minecraft, String serverKey, String paletteFingerprint) {
        newSession();
        Path root = cacheRoot(minecraft);
        Path paletteRoot = root
                .resolve(serverKey)
                .resolve(AerialMapCacheCoordinates.safeFileComponent(paletteFingerprint));
        EXECUTOR.execute(() -> {
            deleteTree(paletteRoot);
            scanCache(root);
        });
    }

    static void closeSession() {
        newSession();
    }


    static boolean flush(Duration timeout) {
        long deadline = System.nanoTime() + Math.max(1L, timeout.toNanos());
        try {
            while (System.nanoTime() < deadline) {
                for (Path path : List.copyOf(PENDING_WRITES.keySet())) {
                    scheduleWrite(path);
                }
                Future<?> barrier = EXECUTOR.submit(() -> {
                });
                long remaining = Math.max(1L, deadline - System.nanoTime());
                barrier.get(remaining, TimeUnit.NANOSECONDS);
                if (PENDING_WRITES.isEmpty() && ACTIVE_WRITES.isEmpty()) {
                    return true;
                }
            }
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.debug("Could not flush the SSU aerial map cache cleanly.", e);
            return false;
        }
        return PENDING_WRITES.isEmpty() && ACTIVE_WRITES.isEmpty();
    }

    static Statistics statistics() {
        return new Statistics(
                READ_HITS.get(),
                READ_MISSES.get(),
                READ_FAILURES.get(),
                WRITES.get(),
                WRITE_FAILURES.get(),
                PRUNED_FILES.get(),
                PENDING_READS.size(),
                PENDING_WRITES.size() + ACTIVE_WRITES.size(),
                DISK_FILES.get(),
                DISK_BYTES.get(),
                cacheLimitBytes()
        );
    }

    private static void newSession() {
        SESSION.incrementAndGet();
        PENDING_READS.clear();
        MISSING_READS.clear();
        COMPLETED_READS.clear();
    }

    private static LoadedTile read(Path path, TileRequest request) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(path))))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid aerial tile magic.");
            }
            int version = input.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported aerial tile version " + version + ".");
            }
            int chunkX = input.readInt();
            int chunkZ = input.readInt();
            String fingerprint = input.readUTF();
            long capturedAt = input.readLong();
            if (chunkX != request.chunkX() || chunkZ != request.chunkZ()
                    || !fingerprint.equals(request.paletteFingerprint())) {
                throw new IOException("Aerial tile metadata does not match its cache key.");
            }
            int pixelCount = input.readInt();
            if (pixelCount <= 0 || pixelCount > 65_536) {
                throw new IOException("Invalid aerial tile pixel count " + pixelCount + ".");
            }
            int[] pixels = new int[pixelCount];
            for (int index = 0; index < pixels.length; index++) {
                pixels[index] = input.readInt();
            }
            short[] terrainHeights = readShortArray(input, "terrain height");
            short[] surfaceHeights = readShortArray(input, "surface height");
            int kindCount = input.readInt();
            if (kindCount < 0 || kindCount > 4_096) {
                throw new IOException("Invalid aerial tile surface-kind count " + kindCount + ".");
            }
            byte[] surfaceKinds = new byte[kindCount];
            input.readFully(surfaceKinds);
            int lightCount = input.readInt();
            if (lightCount < 0 || lightCount > 4_096) {
                throw new IOException("Invalid aerial tile block-light count " + lightCount + ".");
            }
            byte[] blockLights = new byte[lightCount];
            input.readFully(blockLights);
            String[] surfaceBlockIds = readStringArray(input, "surface block");
            String[] biomeIds = readStringArray(input, "biome");
            if (terrainHeights.length != surfaceHeights.length
                    || terrainHeights.length != surfaceKinds.length
                    || terrainHeights.length != blockLights.length
                    || terrainHeights.length != surfaceBlockIds.length
                    || terrainHeights.length != biomeIds.length) {
                throw new IOException("Aerial tile metadata arrays do not have matching lengths.");
            }
            return new LoadedTile(
                    request.session(), request.serverKey(), request.dimension(), request.paletteFingerprint(),
                    chunkX, chunkZ, pixels, terrainHeights, surfaceHeights, surfaceKinds, blockLights,
                    surfaceBlockIds, biomeIds, capturedAt
            );
        }
    }

    private static short[] readShortArray(DataInputStream input, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > 4_096) {
            throw new IOException("Invalid aerial tile " + label + " count " + count + ".");
        }
        short[] values = new short[count];
        for (int index = 0; index < values.length; index++) {
            values[index] = input.readShort();
        }
        return values;
    }

    private static String[] readStringArray(DataInputStream input, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > 4_096) {
            throw new IOException("Invalid aerial tile " + label + " count " + count + ".");
        }
        String[] values = new String[count];
        for (int index = 0; index < values.length; index++) {
            String value = input.readUTF();
            if (value.length() > 256) {
                throw new IOException("Aerial tile " + label + " id is too long.");
            }
            values[index] = value;
        }
        return values;
    }

    private static void writeAtomic(
            Path path,
            TileRequest request,
            int[] basePixels,
            short[] terrainHeights,
            short[] surfaceHeights,
            byte[] surfaceKinds,
            byte[] blockLights,
            String[] surfaceBlockIds,
            String[] biomeIds
    ) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(temp))))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(request.chunkX());
                output.writeInt(request.chunkZ());
                output.writeUTF(request.paletteFingerprint());
                output.writeLong(System.currentTimeMillis());
                output.writeInt(basePixels.length);
                for (int pixel : basePixels) {
                    output.writeInt(pixel);
                }
                writeShortArray(output, terrainHeights);
                writeShortArray(output, surfaceHeights);
                output.writeInt(surfaceKinds.length);
                output.write(surfaceKinds);
                output.writeInt(blockLights.length);
                output.write(blockLights);
                writeStringArray(output, surfaceBlockIds);
                writeStringArray(output, biomeIds);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeShortArray(DataOutputStream output, short[] values) throws IOException {
        output.writeInt(values.length);
        for (short value : values) {
            output.writeShort(value);
        }
    }

    private static void writeStringArray(DataOutputStream output, String[] values) throws IOException {
        output.writeInt(values.length);
        for (String value : values) {
            output.writeUTF(value == null ? "" : value);
        }
    }

    private static Path tilePath(Minecraft minecraft, TileRequest request) {
        int regionX = AerialMapCacheCoordinates.regionCoordinate(request.chunkX());
        int regionZ = AerialMapCacheCoordinates.regionCoordinate(request.chunkZ());
        return cacheRoot(minecraft)
                .resolve(request.serverKey())
                .resolve(AerialMapCacheCoordinates.safeFileComponent(request.paletteFingerprint()))
                .resolve(AerialMapCacheCoordinates.safeFileComponent(request.dimension()))
                .resolve("r." + regionX + "." + regionZ)
                .resolve("c." + request.chunkX() + "." + request.chunkZ() + ".ssuatlas");
    }

    private static Path cacheRoot(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath()
                .resolve("simpleserverutilities")
                .resolve("map-cache-v4");
    }

    private static void prune(Path root) {
        try {
            if (!Files.exists(root)) {
                DISK_BYTES.set(0L);
                DISK_FILES.set(0L);
                return;
            }
            List<Path> files;
            try (var stream = Files.walk(root)) {
                files = stream.filter(path -> Files.isRegularFile(path) && isCacheArtifact(path))
                        .toList();
            }
            long size = 0L;
            long validFiles = 0L;
            for (Path file : files) {
                size += Files.size(file);
                if (isValidTile(file)) {
                    validFiles++;
                }
            }
            long limit = cacheLimitBytes();
            if (size > limit) {
                files = new ArrayList<>(files);
                files.sort(Comparator.comparing(AerialMapStorage::modifiedTime));
                for (Path file : files) {
                    if (size <= limit) {
                        break;
                    }
                    long fileSize = Files.size(file);
                    if (Files.deleteIfExists(file)) {
                        size -= fileSize;
                        if (isValidTile(file)) {
                            validFiles--;
                        }
                        PRUNED_FILES.incrementAndGet();
                    }
                }
            }
            DISK_FILES.set(Math.max(0L, validFiles));
            DISK_BYTES.set(Math.max(0L, size));
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.debug("Could not prune SSU aerial map cache.", e);
        }
    }

    private static void scanCache(Path root) {
        long bytes = 0L;
        long files = 0L;
        try {
            if (Files.exists(root)) {
                try (var stream = Files.walk(root)) {
                    for (Path path : stream.filter(candidate -> Files.isRegularFile(candidate)
                            && isCacheArtifact(candidate)).toList()) {
                        bytes += Files.size(path);
                        if (isValidTile(path)) {
                            files++;
                        }
                    }
                }
            }
            DISK_BYTES.set(bytes);
            DISK_FILES.set(files);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.debug("Could not refresh SSU aerial map cache statistics.", e);
        }
    }


    private static boolean isCacheArtifact(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".ssuatlas") || name.contains(".ssuatlas.broken-");
    }

    private static boolean isValidTile(Path path) {
        return path.getFileName().toString().endsWith(".ssuatlas");
    }

    private static long cacheLimitBytes() {
        try {
            return Math.max(64L, Config.AERIAL_MAP_CACHE_MIB.get()) * 1024L * 1024L;
        } catch (RuntimeException ignored) {
            return DEFAULT_MAX_DISK_BYTES;
        }
    }

    private static FileTime modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException ignored) {
            return FileTime.fromMillis(0L);
        }
    }

    private static void archiveBroken(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.move(
                    path,
                    path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING
            );
            DISK_FILES.updateAndGet(current -> Math.max(0L, current - 1L));
        } catch (IOException ignored) {
            // A bad cache entry is non-critical; a later capture can replace it.
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort invalidation.
                }
            });
        } catch (IOException ignored) {
            // Best effort invalidation.
        }
    }

    private static String discoverServerIdentity(Minecraft minecraft) {
        Object serverData = invokeNoArg(minecraft, "getCurrentServer");
        if (serverData != null) {
            String address = readString(serverData, "ip", "getIp", "address", "getAddress");
            String name = readString(serverData, "name", "getName");
            if (!address.isBlank() || !name.isBlank()) {
                return "multiplayer:" + name + ":" + address;
            }
        }

        Object integrated = invokeNoArg(minecraft, "getSingleplayerServer");
        if (integrated != null) {
            Object worldData = invokeNoArg(integrated, "getWorldData");
            String levelName = readString(worldData, "getLevelName", "levelName");
            Object worldGenOptions = invokeNoArg(worldData, "worldGenOptions");
            Object seed = invokeNoArg(worldGenOptions, "seed");
            if (!levelName.isBlank()) {
                return "singleplayer:" + levelName + ":" + (seed == null ? "unknown-seed" : seed);
            }
            return "singleplayer:" + integrated.getClass().getName();
        }

        Object connection = invokeNoArg(minecraft, "getConnection");
        Object network = invokeNoArg(connection, "getConnection");
        Object remote = invokeNoArg(network, "getRemoteAddress");
        if (remote != null) {
            return "connection:" + remote;
        }
        return "unknown:" + minecraft.gameDirectory.getAbsolutePath();
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name);
            if (!Modifier.isPublic(method.getModifiers())) {
                return null;
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String readString(Object target, String... names) {
        if (target == null) {
            return "";
        }
        for (String name : names) {
            Object methodValue = invokeNoArg(target, name);
            if (methodValue != null) {
                return String.valueOf(methodValue);
            }
            try {
                Field field = target.getClass().getField(name);
                Object value = field.get(target);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next stable field or accessor name.
            }
        }
        return "";
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toUnsignedString(value.hashCode(), 16);
        }
    }

    record LoadedTile(
            int session,
            String serverKey,
            String dimension,
            String paletteFingerprint,
            int chunkX,
            int chunkZ,
            int[] basePixels,
            short[] terrainHeights,
            short[] surfaceHeights,
            byte[] surfaceKinds,
            byte[] blockLights,
            String[] surfaceBlockIds,
            String[] biomeIds,
            long capturedAt
    ) {
    }

    record Statistics(
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

    private record TileRequest(
            int session,
            String serverKey,
            String dimension,
            String paletteFingerprint,
            int chunkX,
            int chunkZ
    ) {
    }

    private record PendingWrite(
            TileRequest request,
            int[] basePixels,
            short[] terrainHeights,
            short[] surfaceHeights,
            byte[] surfaceKinds,
            byte[] blockLights,
            String[] surfaceBlockIds,
            String[] biomeIds,
            Path cacheRoot
    ) {
    }


}
