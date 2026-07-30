package be.winnetrie.mod.simpleserverutilities.region;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJob;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Versioned region snapshots.
 *
 * <p>Version 3 captures blocks and structural hanging entities in bounded
 * server-tick slices, serializes on the SSU storage worker, uses a palette and
 * gzip compression, verifies a checksum, and preserves block-entity NBT.
 * Version 1 JSON and version 2 compressed snapshots remain readable.</p>
 */
public final class RegionSnapshotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int FORMAT_VERSION = 3;
    private static final String COMPLETE_STATUS = "complete";
    private static final long CHECKPOINT_INTERVAL = 65_536L;
    private static final int UPDATE_CLIENTS_NO_NEIGHBOURS_NO_DROPS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final AtomicBoolean BLOCK_ENTITY_CAPTURE_WARNING = new AtomicBoolean();
    private static final AtomicBoolean BLOCK_ENTITY_RESTORE_WARNING = new AtomicBoolean();
    private static final AtomicBoolean ENTITY_CAPTURE_WARNING = new AtomicBoolean();
    private static final AtomicBoolean ENTITY_RESTORE_WARNING = new AtomicBoolean();

    private Path snapshotFolder;
    private Path archiveFolder;
    private Path jobFolder;
    private final Set<String> unresolvedResetRegions = ConcurrentHashMap.newKeySet();

    public void load(MinecraftServer server) {
        Path root = StoragePaths.root(server);
        this.snapshotFolder = root.resolve("region_snapshots");
        this.archiveFolder = snapshotFolder.resolve("archive");
        this.jobFolder = root.resolve("region_snapshot_jobs");
        recoverInterruptedCheckpoints();
    }

    public boolean hasSnapshot(String regionName) {
        if (snapshotFolder == null) {
            return false;
        }
        return Files.exists(getCompressedSnapshotPath(regionName))
                || Files.exists(backupPath(getCompressedSnapshotPath(regionName)))
                || Files.exists(getLegacySnapshotPath(regionName))
                || Files.exists(backupPath(getLegacySnapshotPath(regionName)));
    }

    /** True when an earlier destructive reset stopped before a verified completion. */
    public boolean hasUnresolvedReset(String regionName) {
        return regionName != null && unresolvedResetRegions.contains(normalizeName(regionName));
    }

    public RegionSnapshotCaptureJob createCaptureJob(ServerLevel level, Region region) throws IOException {
        ensureInitialized();
        return new RegionSnapshotCaptureJob(this, level, region);
    }

    public RegionSnapshotResetJob createResetJob(ServerLevel level, Region region) throws IOException {
        ensureInitialized();
        if (!hasSnapshot(region.getName())) {
            throw new IOException("No snapshot exists for region '" + region.getName() + "'.");
        }
        CompletableFuture<LoadedSnapshot> loadFuture = SimpleServerUtilities.STORAGE.submitTask(
                () -> loadSnapshot(region)
        );
        return new RegionSnapshotResetJob(this, level, region, loadFuture);
    }

    /**
     * Archives every snapshot generation associated with a region. This is used
     * before a region name can be reused with different bounds.
     */
    public synchronized int archiveSnapshot(String regionName, String reason) throws IOException {
        ensureInitialized();
        Files.createDirectories(archiveFolder);
        int archived = 0;
        Path v2 = getCompressedSnapshotPath(regionName);
        Path legacy = getLegacySnapshotPath(regionName);
        archived += archiveIfPresent(v2, regionName, reason);
        archived += archiveIfPresent(backupPath(v2), regionName, reason);
        archived += archiveIfPresent(legacy, regionName, reason);
        archived += archiveIfPresent(backupPath(legacy), regionName, reason);
        Files.deleteIfExists(tempPath(v2));
        Files.deleteIfExists(tempPath(legacy));
        return archived;
    }

    private int archiveIfPresent(Path source, String regionName, String reason) throws IOException {
        if (!Files.exists(source)) {
            return 0;
        }
        String safeReason = StoragePaths.sanitizeFileName(reason == null ? "invalidated" : reason);
        String fileName = Instant.now().toEpochMilli()
                + "-" + StoragePaths.sanitizeFileName(normalizeName(regionName))
                + "-" + safeReason + "-" + source.getFileName();
        Files.move(source, archiveFolder.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return 1;
    }

    private int writeCurrentSnapshot(
            Region region,
            List<String> palette,
            List<CapturedBlock> blocks,
            List<CapturedEntity> entities,
            AtomicBoolean cancelled
    ) throws IOException {
        ensureNotCancelled(cancelled);
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("status", COMPLETE_STATUS);
        root.addProperty("region", region.getName());
        root.addProperty("dimension", region.getDimension().identifier().toString());
        root.addProperty("minX", region.getMinX());
        root.addProperty("minY", region.getMinY());
        root.addProperty("minZ", region.getMinZ());
        root.addProperty("maxX", region.getMaxX());
        root.addProperty("maxY", region.getMaxY());
        root.addProperty("maxZ", region.getMaxZ());
        root.addProperty("capturedAt", System.currentTimeMillis());

        JsonArray paletteJson = new JsonArray();
        for (String stateJson : palette) {
            paletteJson.add(JsonParser.parseString(stateJson));
        }
        root.add("palette", paletteJson);

        JsonArray blocksJson = new JsonArray();
        for (CapturedBlock block : blocks) {
            JsonArray entry = new JsonArray();
            entry.add(block.relativeIndex());
            entry.add(block.paletteIndex());
            if (!block.blockEntitySnbt().isEmpty()) {
                entry.add(block.blockEntitySnbt());
            }
            blocksJson.add(entry);
        }
        root.add("blocks", blocksJson);

        JsonArray entitiesJson = new JsonArray();
        for (CapturedEntity entity : entities) {
            if (!entity.entitySnbt().isBlank()) {
                entitiesJson.add(entity.entitySnbt());
            }
        }
        root.add("entities", entitiesJson);
        root.addProperty("checksum", checksum(root));

        writeCompressedAtomic(getCompressedSnapshotPath(region.getName()), root, cancelled);
        archiveLegacyAfterCompressedPublication(region.getName());
        return blocks.size();
    }

    private LoadedSnapshot loadSnapshot(Region region) throws IOException {
        Path v2 = getCompressedSnapshotPath(region.getName());
        Path v2Backup = backupPath(v2);
        Path legacy = getLegacySnapshotPath(region.getName());
        Path legacyBackup = backupPath(legacy);
        boolean hasV2Generation = Files.exists(v2) || Files.exists(v2Backup);
        List<Path> candidates = hasV2Generation
                ? List.of(v2, v2Backup)
                : List.of(legacy, legacyBackup);
        IOException failure = null;
        for (Path candidate : candidates) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try {
                LoadedSnapshot loaded = candidate.getFileName().toString().contains(".ssusnap")
                        ? loadCompressedSnapshot(candidate, region)
                        : loadV1(candidate, region);
                recoverPrimarySnapshot(candidate, region.getName());
                return loaded;
            } catch (Exception e) {
                Path archived = candidate;
                try {
                    archived = archiveBrokenSnapshot(candidate);
                } catch (IOException archiveError) {
                    e.addSuppressed(archiveError);
                }
                SimpleServerUtilities.LOGGER.error(
                        "Could not load snapshot candidate for region '{}'. Broken file archived as {} when possible.",
                        region.getName(), archived, e
                );
                failure = new IOException("Snapshot candidate is corrupt or incompatible: " + candidate, e);
            }
        }
        if (failure != null) {
            throw new IOException("No valid snapshot generation remains for region '" + region.getName() + "'.", failure);
        }
        throw new IOException("No snapshot exists for region '" + region.getName() + "'.");
    }

    private void archiveLegacyAfterCompressedPublication(String regionName) {
        Path legacy = getLegacySnapshotPath(regionName);
        try {
            archiveIfPresent(legacy, regionName, "migrated-v1");
            archiveIfPresent(backupPath(legacy), regionName, "migrated-v1");
            Files.deleteIfExists(tempPath(legacy));
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.warn(
                    "Published current compressed snapshot for region '{}' but could not archive its legacy generation.",
                    regionName, e
            );
        }
    }

    private void recoverPrimarySnapshot(Path candidate, String regionName) {
        Path primary = candidate.getFileName().toString().contains(".ssusnap")
                ? getCompressedSnapshotPath(regionName)
                : getLegacySnapshotPath(regionName);
        if (candidate.equals(primary)) {
            return;
        }
        Path temp = tempPath(primary);
        try {
            Files.copy(candidate, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, primary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, primary, StandardCopyOption.REPLACE_EXISTING);
            }
            SimpleServerUtilities.LOGGER.warn(
                    "Recovered snapshot for region '{}' from backup generation {}.",
                    regionName, candidate.getFileName()
            );
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.warn(
                    "Loaded snapshot backup for region '{}' but could not restore the primary file.",
                    regionName, e
            );
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // A later recovery or snapshot save will replace the temporary file.
            }
        }
    }

    private LoadedSnapshot loadCompressedSnapshot(Path path, Region region) throws IOException {
        JsonObject root;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8))) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        int version = getInt(root, "version", 0);
        if (version < 2 || version > FORMAT_VERSION) {
            throw new IOException("Unsupported snapshot version: " + version);
        }
        if (!COMPLETE_STATUS.equalsIgnoreCase(getString(root, "status", ""))) {
            throw new IOException("Snapshot was not published as complete.");
        }
        validateSnapshot(root, region);

        String storedChecksum = getString(root, "checksum", "");
        JsonObject checksumCopy = root.deepCopy();
        checksumCopy.remove("checksum");
        String actualChecksum = checksum(checksumCopy);
        if (storedChecksum.isBlank() || !MessageDigest.isEqual(
                storedChecksum.getBytes(StandardCharsets.US_ASCII),
                actualChecksum.getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("Snapshot checksum verification failed.");
        }

        List<BlockState> palette = new ArrayList<>();
        JsonArray paletteJson = root.getAsJsonArray("palette");
        if (paletteJson != null) {
            for (JsonElement element : paletteJson) {
                palette.add(blockStateFromJson(element.getAsJsonObject()));
            }
        }

        int sizeY = region.getMaxY() - region.getMinY() + 1;
        int sizeZ = region.getMaxZ() - region.getMinZ() + 1;
        List<SavedBlock> savedBlocks = new ArrayList<>();
        JsonArray blocks = root.getAsJsonArray("blocks");
        if (blocks != null) {
            for (JsonElement element : blocks) {
                JsonArray entry = element.getAsJsonArray();
                if (entry.size() < 2) {
                    continue;
                }
                int relativeIndex = entry.get(0).getAsInt();
                int paletteIndex = entry.get(1).getAsInt();
                if (paletteIndex < 0 || paletteIndex >= palette.size() || relativeIndex < 0) {
                    continue;
                }
                RegionSnapshotCoordinates.Decoded decoded = RegionSnapshotCoordinates.unpack(
                        region.getMinX(), region.getMinY(), region.getMinZ(),
                        sizeY, sizeZ, relativeIndex
                );
                int x = decoded.x();
                int y = decoded.y();
                int z = decoded.z();
                if (!within(region, x, y, z)) {
                    continue;
                }
                String snbt = entry.size() >= 3 ? entry.get(2).getAsString() : "";
                savedBlocks.add(new SavedBlock(x, y, z, palette.get(paletteIndex), snbt));
            }
        }
        List<SavedEntity> savedEntities = new ArrayList<>();
        JsonArray entities = root.getAsJsonArray("entities");
        if (entities != null) {
            for (JsonElement element : entities) {
                if (element.isJsonPrimitive()) {
                    String snbt = element.getAsString();
                    if (!snbt.isBlank()) {
                        savedEntities.add(new SavedEntity(snbt));
                    }
                }
            }
        }
        return new LoadedSnapshot(List.copyOf(savedBlocks), List.copyOf(savedEntities), version);
    }

    private LoadedSnapshot loadV1(Path path, Region region) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("No snapshot exists for region '" + region.getName() + "'.");
        }
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        validateSnapshot(root, region);
        List<SavedBlock> savedBlocks = new ArrayList<>();
        JsonArray blocks = root.getAsJsonArray("blocks");
        if (blocks != null) {
            for (JsonElement element : blocks) {
                JsonObject blockJson = element.getAsJsonObject();
                int x = region.getMinX() + blockJson.get("x").getAsInt();
                int y = region.getMinY() + blockJson.get("y").getAsInt();
                int z = region.getMinZ() + blockJson.get("z").getAsInt();
                if (within(region, x, y, z)) {
                    savedBlocks.add(new SavedBlock(x, y, z, blockStateFromJson(blockJson), ""));
                }
            }
        }
        return new LoadedSnapshot(List.copyOf(savedBlocks), List.of(), 1);
    }

    private void validateSnapshot(JsonObject root, Region region) {
        String snapshotDimension = getString(root, "dimension", "");
        String regionDimension = region.getDimension().identifier().toString();
        if (!snapshotDimension.equals(regionDimension)) {
            throw new IllegalStateException("Snapshot dimension does not match the current region dimension.");
        }
        if (getInt(root, "minX", Integer.MIN_VALUE) != region.getMinX()
                || getInt(root, "minY", Integer.MIN_VALUE) != region.getMinY()
                || getInt(root, "minZ", Integer.MIN_VALUE) != region.getMinZ()
                || getInt(root, "maxX", Integer.MAX_VALUE) != region.getMaxX()
                || getInt(root, "maxY", Integer.MAX_VALUE) != region.getMaxY()
                || getInt(root, "maxZ", Integer.MAX_VALUE) != region.getMaxZ()) {
            throw new IllegalStateException(
                    "Snapshot bounds do not match the current region bounds. Save the region again first."
            );
        }
    }

    private static boolean within(Region region, int x, int y, int z) {
        return x >= region.getMinX() && x <= region.getMaxX()
                && y >= region.getMinY() && y <= region.getMaxY()
                && z >= region.getMinZ() && z <= region.getMaxZ();
    }

    private static String captureBlockEntitySnbt(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return "";
        }
        try {
            return blockEntity.saveWithFullMetadata(level.registryAccess()).toString();
        } catch (RuntimeException e) {
            if (BLOCK_ENTITY_CAPTURE_WARNING.compareAndSet(false, true)) {
                SimpleServerUtilities.LOGGER.warn(
                        "Could not preserve one or more block entities in a region snapshot. "
                                + "Snapshot capture will stop to avoid publishing incomplete data.", e
                );
            }
            return "";
        }
    }

    private static boolean restoreBlockEntity(ServerLevel level, SavedBlock saved, BlockPos pos) {
        if (saved.blockEntitySnbt().isEmpty()) {
            return true;
        }
        try {
            CompoundTag tag = parseCompoundTag(saved.blockEntitySnbt());
            BlockEntity restored = BlockEntity.loadStatic(pos, saved.state(), tag, level.registryAccess());
            if (restored != null) {
                level.setBlockEntity(restored);
                restored.setChanged();
                // Mark the position for a normal client block-entity update as
                // well. This is important for visible data such as sign text
                // and for clients that already had the restored chunk loaded.
                level.sendBlockUpdated(pos, saved.state(), saved.state(), Block.UPDATE_CLIENTS);
                return true;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (BLOCK_ENTITY_RESTORE_WARNING.compareAndSet(false, true)) {
                SimpleServerUtilities.LOGGER.warn(
                        "Could not restore one or more block entities from a region snapshot. "
                                + "Their block states were restored normally.", e
                );
            }
            return false;
        }
    }

    private static List<Entity> findSnapshotEntities(ServerLevel level, Region region) {
        AABB bounds = regionBounds(region);
        return new ArrayList<>(level.getEntitiesOfClass(
                HangingEntity.class,
                bounds,
                Entity::isAlive
        ));
    }

    private static String captureEntitySnbt(ServerLevel level, Entity entity) {
        try {
            TagValueOutput output = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING,
                    level.registryAccess()
            );
            if (!entity.save(output)) {
                return "";
            }
            return output.buildResult().toString();
        } catch (RuntimeException e) {
            if (ENTITY_CAPTURE_WARNING.compareAndSet(false, true)) {
                SimpleServerUtilities.LOGGER.warn(
                        "Could not preserve one or more hanging entities in a region snapshot.", e
                );
            }
            return "";
        }
    }

    private static List<SavedEntity> captureSnapshotEntities(ServerLevel level, List<Entity> source)
            throws IOException {
        List<SavedEntity> captured = new ArrayList<>();
        for (Entity entity : source) {
            String snbt = captureEntitySnbt(level, entity);
            if (snbt.isBlank()) {
                throw new IOException("Could not serialize hanging entity " + entity.getStringUUID() + ".");
            }
            captured.add(new SavedEntity(snbt));
        }
        return List.copyOf(captured);
    }

    private static boolean restoreSnapshotEntity(ServerLevel level, Region region, SavedEntity saved) {
        if (saved.entitySnbt().isBlank()) {
            return false;
        }
        try {
            CompoundTag tag = parseCompoundTag(saved.entitySnbt());
            /*
             * The old hanging entity is discarded earlier in the reset. Removing
             * its UUID avoids a same-tick duplicate-UUID rejection when a small
             * region reaches the restore phase before end-of-tick cleanup.
             */
            tag.remove("UUID");
            Entity entity = EntityType.loadEntityRecursive(
                    tag,
                    level,
                    new EntitySpawnRequest(EntitySpawnReason.LOAD, false),
                    EntityProcessor.NOP
            );
            if (entity == null || !intersects(region, entity.getBoundingBox())) {
                return false;
            }
            return level.addFreshEntity(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (ENTITY_RESTORE_WARNING.compareAndSet(false, true)) {
                SimpleServerUtilities.LOGGER.warn(
                        "Could not restore one or more hanging entities from a region snapshot.", e
                );
            }
            return false;
        }
    }

    private static void clearBlockWithoutDrops(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            // Empty inventory-bearing block entities before their block state is
            // removed. This prevents vanilla on-remove hooks from ejecting a
            // second copy of the stored items into the world.
            container.clearContent();
            blockEntity.setChanged();
        }
        if (blockEntity != null) {
            // Detach the block entity before replacing its block. Combined with
            // the no-drop flag this makes reset a silent structural operation.
            level.removeBlockEntity(pos);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_CLIENTS_NO_NEIGHBOURS_NO_DROPS);
    }

    private static boolean intersects(Region region, AABB entityBounds) {
        return entityBounds.intersects(regionBounds(region));
    }

    private static AABB regionBounds(Region region) {
        return new AABB(
                region.getMinX(),
                region.getMinY(),
                region.getMinZ(),
                region.getMaxX() + 1.0D,
                region.getMaxY() + 1.0D,
                region.getMaxZ() + 1.0D
        );
    }

    private static CompoundTag parseCompoundTag(String snbt) throws ReflectiveOperationException {
        Class<?> parserClass = Class.forName("net.minecraft.nbt.TagParser");
        for (Method method : parserClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !CompoundTag.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0] == String.class) {
                Object result = method.invoke(null, snbt);
                if (result instanceof CompoundTag tag) {
                    return tag;
                }
            }
        }
        throw new NoSuchMethodException("No public SNBT compound parser is available.");
    }

    private JsonObject blockStateToJson(BlockState state) {
        JsonObject json = new JsonObject();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        json.addProperty("block", blockId.toString());
        if (!state.getProperties().isEmpty()) {
            JsonObject properties = new JsonObject();
            for (Property<?> property : state.getProperties()) {
                properties.addProperty(property.getName(), getPropertyValueName(state, property));
            }
            json.add("properties", properties);
        }
        return json;
    }

    private static BlockState blockStateFromJson(JsonObject json) {
        Identifier blockId = Identifier.parse(json.get("block").getAsString());
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(Blocks.AIR);
        BlockState state = block.defaultBlockState();
        if (!json.has("properties")) {
            return state;
        }
        JsonObject properties = json.getAsJsonObject("properties");
        for (Entry<String, JsonElement> entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property != null) {
                state = applyProperty(state, property, entry.getValue().getAsString());
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String getPropertyValueName(BlockState state, Property property) {
        return property.getName((Comparable) state.getValue(property));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String valueName) {
        Optional value = property.getValue(valueName);
        return value.isEmpty() ? state : state.setValue(property, (Comparable) value.get());
    }

    private static String checksum(JsonObject root) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(COMPACT_GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable.", e);
        }
    }

    private static void writeCompressedAtomic(Path path, JsonObject root, AtomicBoolean cancelled) throws IOException {
        ensureNotCancelled(cancelled);
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(temp)), StandardCharsets.UTF_8))) {
                COMPACT_GSON.toJson(root, writer);
            }
            ensureNotCancelled(cancelled);
            if (Files.exists(path)) {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            synchronized (cancelled) {
                ensureNotCancelled(cancelled);
                try {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void ensureNotCancelled(AtomicBoolean cancelled) throws IOException {
        if (cancelled != null && cancelled.get()) {
            throw new IOException("Snapshot capture was cancelled before publication.");
        }
    }

    private Path archiveBrokenSnapshot(Path path) throws IOException {
        Files.createDirectories(archiveFolder);
        Path target = archiveFolder.resolve(
                Instant.now().toEpochMilli() + "-broken-" + path.getFileName()
        );
        return Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void ensureInitialized() throws IOException {
        if (snapshotFolder == null || jobFolder == null) {
            throw new IOException("Region snapshot manager is not initialized yet.");
        }
        Files.createDirectories(snapshotFolder);
        Files.createDirectories(jobFolder);
    }

    private Path getCompressedSnapshotPath(String regionName) {
        return snapshotFolder.resolve(StoragePaths.sanitizeFileName(normalizeName(regionName)) + ".ssusnap");
    }

    private Path getLegacySnapshotPath(String regionName) {
        return snapshotFolder.resolve(StoragePaths.sanitizeFileName(normalizeName(regionName)) + ".json");
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    private static Path tempPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".tmp");
    }

    private static String normalizeName(String regionName) {
        return regionName.toLowerCase(Locale.ROOT);
    }

    private void recoverInterruptedCheckpoints() {
        unresolvedResetRegions.clear();
        if (jobFolder == null) {
            return;
        }
        try {
            Files.createDirectories(jobFolder);
            for (Path file : JsonStorage.listJsonFiles(jobFolder)) {
                try {
                    JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    String status = getString(root, "status", "");
                    String regionName = getString(root, "region", "unknown");
                    if ("running".equalsIgnoreCase(status)) {
                        root.addProperty("status", "interrupted");
                        root.addProperty("interruptedAt", System.currentTimeMillis());
                        root.addProperty("message", "Server stopped before the destructive snapshot job completed.");
                        JsonStorage.write(GSON, file, root);
                        status = "interrupted";
                        SimpleServerUtilities.LOGGER.error(
                                "Region snapshot job {} for region '{}' was interrupted. The rental/region state was not "
                                        + "silently finalized; inspect the region before retrying.",
                                getString(root, "jobId", file.getFileName().toString()),
                                regionName
                        );
                    }
                    if (isUnresolvedStatus(status)) {
                        unresolvedResetRegions.add(normalizeName(regionName));
                    }
                } catch (Exception e) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error(
                            "Could not recover region snapshot checkpoint. Broken file archived as {}.", archived, e
                    );
                }
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Could not initialize region snapshot job recovery.", e);
        }
    }

    private static boolean isUnresolvedStatus(String status) {
        return "interrupted".equalsIgnoreCase(status)
                || "cancelled".equalsIgnoreCase(status)
                || "failed".equalsIgnoreCase(status);
    }

    private void markRecoveredAfterSuccessfulReset(Region region) {
        String normalizedRegion = normalizeName(region.getName());
        SimpleServerUtilities.STORAGE.submitTask(() -> {
            for (Path file : JsonStorage.listJsonFiles(jobFolder)) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!normalizedRegion.equals(normalizeName(getString(root, "region", "")))
                        || !isUnresolvedStatus(getString(root, "status", ""))) {
                    continue;
                }
                root.addProperty("status", "recovered");
                root.addProperty("recoveredAt", System.currentTimeMillis());
                root.addProperty("message", "A later snapshot reset completed successfully.");
                JsonStorage.write(GSON, file, root);
            }
            return null;
        }).whenComplete((ignored, error) -> {
            if (error == null) {
                unresolvedResetRegions.remove(normalizedRegion);
            } else {
                SimpleServerUtilities.LOGGER.error(
                        "Could not resolve old snapshot checkpoints for region '{}'.", region.getName(), error
                );
            }
        });
    }

    private Path checkpointPath(UUID jobId) {
        return jobFolder.resolve(jobId + ".json");
    }

    private void writeCheckpointNow(UUID jobId, Region region, String phase, long completed, long total)
            throws IOException {
        JsonObject root = checkpoint(jobId, region, "running", phase, completed, total, "");
        JsonStorage.write(GSON, checkpointPath(jobId), root);
    }

    private void queueCheckpoint(UUID jobId, Region region, String phase, long completed, long total) {
        JsonObject root = checkpoint(jobId, region, "running", phase, completed, total, "");
        SimpleServerUtilities.STORAGE.queueJson(GSON, checkpointPath(jobId), root);
    }

    private void finishCheckpoint(UUID jobId, Region region, String status, String message, long completed, long total) {
        if (isUnresolvedStatus(status)) {
            unresolvedResetRegions.add(normalizeName(region.getName()));
        }
        JsonObject root = checkpoint(jobId, region, status, status, completed, total, message);
        root.addProperty("finishedAt", System.currentTimeMillis());
        SimpleServerUtilities.STORAGE.queueJson(GSON, checkpointPath(jobId), root);
    }

    private static JsonObject checkpoint(
            UUID jobId,
            Region region,
            String status,
            String phase,
            long completed,
            long total,
            String message
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("jobId", jobId.toString());
        root.addProperty("region", region.getName());
        root.addProperty("dimension", region.getDimension().identifier().toString());
        root.addProperty("status", status);
        root.addProperty("phase", phase);
        root.addProperty("completedOperations", completed);
        root.addProperty("totalOperations", total);
        root.addProperty("message", message == null ? "" : message);
        root.addProperty("updatedAt", System.currentTimeMillis());
        return root;
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    private static String getString(JsonObject root, String key, String fallback) {
        return root.has(key) ? root.get(key).getAsString() : fallback;
    }

    private record CapturedBlock(int relativeIndex, int paletteIndex, String blockEntitySnbt) {
    }

    private record CapturedEntity(String entitySnbt) {
    }

    private record SavedBlock(int x, int y, int z, BlockState state, String blockEntitySnbt) {
    }

    private record SavedEntity(String entitySnbt) {
    }

    private record LoadedSnapshot(List<SavedBlock> blocks, List<SavedEntity> entities, int version) {
    }

    public static final class RegionSnapshotCaptureJob implements SsuJob {
        private enum Phase {
            CAPTURE_BLOCKS,
            CAPTURE_ENTITIES,
            WRITE,
            COMPLETE
        }

        private final RegionSnapshotManager manager;
        private final ServerLevel level;
        private final Region region;
        private final Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        private final List<String> palette = new ArrayList<>();
        private final List<CapturedBlock> blocks = new ArrayList<>();
        private final List<CapturedEntity> entities = new ArrayList<>();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private final long volume;
        private Phase phase = Phase.CAPTURE_BLOCKS;
        private List<Entity> entityCandidates = List.of();
        private int entityIndex;
        private int x;
        private int y;
        private int z;
        private long scanned;
        private int savedBlocks;
        private CompletableFuture<Integer> writeFuture;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private RegionSnapshotCaptureJob(RegionSnapshotManager manager, ServerLevel level, Region region) {
            this.manager = manager;
            this.level = level;
            this.region = region;
            this.volume = region.getVolume();
            this.x = region.getMinX();
            this.y = region.getMinY();
            this.z = region.getMinZ();
        }

        @Override
        public String description() {
            return "Capture snapshot for region '" + region.getName() + "'";
        }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) throws Exception {
            if (phase == Phase.WRITE) {
                finishWriteIfReady();
                return 0;
            }
            int used = 0;
            while (used < operationBudget
                    && phase != Phase.WRITE
                    && phase != Phase.COMPLETE) {
                if (phase == Phase.CAPTURE_BLOCKS) {
                    mutablePos.set(x, y, z);
                    BlockState state = level.getBlockState(mutablePos);
                    if (!state.isAir()) {
                        String stateJson = COMPACT_GSON.toJson(manager.blockStateToJson(state));
                        int paletteIndex = paletteIndexes.computeIfAbsent(stateJson, key -> {
                            palette.add(key);
                            return palette.size() - 1;
                        });
                        int relativeIndex = relativeIndex(region, x, y, z);
                        boolean hasBlockEntity = level.getBlockEntity(mutablePos) != null;
                        String blockEntitySnbt = captureBlockEntitySnbt(level, mutablePos);
                        if (hasBlockEntity && blockEntitySnbt.isBlank()) {
                            throw new IOException("Could not serialize block entity at " + mutablePos + ".");
                        }
                        blocks.add(new CapturedBlock(relativeIndex, paletteIndex, blockEntitySnbt));
                    }
                    scanned++;
                    used++;
                    advanceBlock();
                    continue;
                }

                Entity entity = entityCandidates.get(entityIndex++);
                String snbt = captureEntitySnbt(level, entity);
                if (snbt.isBlank()) {
                    throw new IOException("Could not serialize hanging entity " + entity.getStringUUID() + ".");
                }
                entities.add(new CapturedEntity(snbt));
                used++;
                if (entityIndex >= entityCandidates.size()) {
                    beginWrite();
                }
            }
            return used;
        }

        private void advanceBlock() {
            z++;
            if (z <= region.getMaxZ()) {
                return;
            }
            z = region.getMinZ();
            y++;
            if (y <= region.getMaxY()) {
                return;
            }
            y = region.getMinY();
            x++;
            if (x > region.getMaxX()) {
                entityCandidates = findSnapshotEntities(level, region);
                entityIndex = 0;
                if (entityCandidates.isEmpty()) {
                    beginWrite();
                } else {
                    phase = Phase.CAPTURE_ENTITIES;
                }
            }
        }

        private void beginWrite() {
            phase = Phase.WRITE;
            List<String> immutablePalette = List.copyOf(palette);
            List<CapturedBlock> immutableBlocks = List.copyOf(blocks);
            List<CapturedEntity> immutableEntities = List.copyOf(entities);
            writeFuture = SimpleServerUtilities.STORAGE.submitTask(
                    () -> manager.writeCurrentSnapshot(
                            region, immutablePalette, immutableBlocks, immutableEntities, cancelled
                    )
            );
        }

        private void finishWriteIfReady() throws Exception {
            if (writeFuture == null || !writeFuture.isDone()) {
                return;
            }
            try {
                savedBlocks = writeFuture.get();
                phase = Phase.COMPLETE;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw new IOException("Snapshot serialization failed.", cause);
            }
        }

        @Override
        public Set<String> resourceLocks() {
            return Set.of(SsuJobLocks.region(region.getDimension(), region.getName()));
        }

        @Override
        public boolean isComplete() {
            return phase == Phase.COMPLETE;
        }

        @Override
        public double progress() {
            if (phase == Phase.COMPLETE) {
                return 1.0D;
            }
            if (phase == Phase.WRITE) {
                return 0.98D;
            }
            if (phase == Phase.CAPTURE_ENTITIES) {
                double entityProgress = entityCandidates.isEmpty()
                        ? 1.0D
                        : entityIndex / (double) entityCandidates.size();
                return 0.95D + Math.min(0.03D, entityProgress * 0.03D);
            }
            return volume == 0L ? 0.95D : Math.min(0.95D, scanned / (double) volume * 0.95D);
        }

        @Override
        public void cancel() {
            synchronized (cancelled) {
                cancelled.set(true);
            }
            if (writeFuture != null) {
                writeFuture.cancel(false);
            }
        }

        public int savedBlocks() {
            return savedBlocks;
        }
    }

    public static final class RegionSnapshotResetJob implements SsuJob {
        private enum Phase {
            LOAD,
            REMOVE_ENTITIES,
            CLEAR,
            RESTORE_BLOCKS,
            RECONCILE_BLOCKS,
            RESTORE_ENTITIES,
            COMPLETE
        }

        private final RegionSnapshotManager manager;
        private final ServerLevel level;
        private final Region region;
        private final CompletableFuture<LoadedSnapshot> loadFuture;
        private final UUID checkpointId = UUID.randomUUID();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private Phase phase = Phase.LOAD;
        private List<SavedBlock> savedBlocks = List.of();
        private List<SavedEntity> savedEntities = List.of();
        private List<Entity> existingEntities = List.of();
        private long totalOperations = 1L;
        private int x;
        private int y;
        private int z;
        private int restoreIndex;
        private int reconcileIndex;
        private int removeEntityIndex;
        private int restoreEntityIndex;
        private long completedOperations;
        private long nextCheckpoint = CHECKPOINT_INTERVAL;
        private int restoredBlocks;
        private int restoredEntities;
        private boolean checkpointStarted;

        private RegionSnapshotResetJob(
                RegionSnapshotManager manager,
                ServerLevel level,
                Region region,
                CompletableFuture<LoadedSnapshot> loadFuture
        ) {
            this.manager = manager;
            this.level = level;
            this.region = region;
            this.loadFuture = loadFuture;
            this.x = region.getMinX();
            this.y = region.getMinY();
            this.z = region.getMinZ();
        }

        @Override
        public String description() {
            return "Reset region '" + region.getName() + "' from snapshot";
        }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) throws Exception {
            try {
                return runStepChecked(server, operationBudget);
            } catch (Exception e) {
                if (checkpointStarted) {
                    manager.finishCheckpoint(
                            checkpointId,
                            region,
                            "failed",
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                            completedOperations,
                            totalOperations
                    );
                }
                throw e;
            }
        }

        private int runStepChecked(MinecraftServer server, int operationBudget) throws Exception {
            if (phase == Phase.LOAD) {
                if (!loadFuture.isDone()) {
                    return 0;
                }
                try {
                    LoadedSnapshot loaded = loadFuture.get();
                    savedBlocks = loaded.blocks();
                    existingEntities = findSnapshotEntities(level, region);
                    savedEntities = loaded.version() >= 3
                            ? loaded.entities()
                            : captureSnapshotEntities(level, existingEntities);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception exception) {
                        throw exception;
                    }
                    throw new IOException("Snapshot loading failed.", cause);
                }
                totalOperations = existingEntities.size()
                        + region.getVolume()
                        + (savedBlocks.size() * 2L)
                        + savedEntities.size();
                manager.writeCheckpointNow(
                        checkpointId, region, "remove_entities", completedOperations, totalOperations
                );
                checkpointStarted = true;
                phase = existingEntities.isEmpty() ? Phase.CLEAR : Phase.REMOVE_ENTITIES;
            }

            int used = 0;
            while (used < operationBudget && phase != Phase.COMPLETE) {
                if (phase == Phase.REMOVE_ENTITIES) {
                    Entity entity = existingEntities.get(removeEntityIndex++);
                    entity.discard();
                    completedOperations++;
                    used++;
                    checkpointIfNeeded();
                    if (removeEntityIndex >= existingEntities.size()) {
                        phase = Phase.CLEAR;
                        manager.queueCheckpoint(
                                checkpointId, region, "clear", completedOperations, totalOperations
                        );
                    }
                    continue;
                }

                if (phase == Phase.CLEAR) {
                    mutablePos.set(x, y, z);
                    if (!level.isEmptyBlock(mutablePos)) {
                        clearBlockWithoutDrops(level, mutablePos);
                    }
                    completedOperations++;
                    used++;
                    checkpointIfNeeded();
                    advanceClear();
                    continue;
                }

                if (phase == Phase.RESTORE_BLOCKS) {
                    SavedBlock saved = savedBlocks.get(restoreIndex++);
                    mutablePos.set(saved.x(), saved.y(), saved.z());
                    /*
                     * Restore the complete structure first, without neighbour
                     * reactions. Fragile blocks such as crops, torches and
                     * wall-mounted blocks must not pop while their support is
                     * still waiting later in the snapshot stream.
                     */
                    level.setBlock(
                            mutablePos,
                            saved.state(),
                            UPDATE_CLIENTS_NO_NEIGHBOURS_NO_DROPS
                    );
                    if (!restoreBlockEntity(level, saved, mutablePos.immutable())) {
                        throw new IOException("Could not restore block entity at " + mutablePos + ".");
                    }
                    restoredBlocks++;
                    completedOperations++;
                    used++;
                    checkpointIfNeeded();
                    if (restoreIndex >= savedBlocks.size()) {
                        phase = Phase.RECONCILE_BLOCKS;
                        manager.queueCheckpoint(
                                checkpointId, region, "reconcile_blocks", completedOperations, totalOperations
                        );
                    }
                    continue;
                }

                if (phase == Phase.RECONCILE_BLOCKS) {
                    SavedBlock saved = savedBlocks.get(reconcileIndex++);
                    mutablePos.set(saved.x(), saved.y(), saved.z());
                    BlockState current = level.getBlockState(mutablePos);
                    level.updateNeighborsAt(mutablePos, current.getBlock());
                    completedOperations++;
                    used++;
                    checkpointIfNeeded();
                    if (reconcileIndex >= savedBlocks.size()) {
                        if (savedEntities.isEmpty()) {
                            completeSuccessfully();
                        } else {
                            phase = Phase.RESTORE_ENTITIES;
                            manager.queueCheckpoint(
                                    checkpointId, region, "restore_entities", completedOperations, totalOperations
                            );
                        }
                    }
                    continue;
                }

                SavedEntity savedEntity = savedEntities.get(restoreEntityIndex++);
                if (!restoreSnapshotEntity(level, region, savedEntity)) {
                    throw new IOException("Could not restore hanging entity " + restoreEntityIndex + ".");
                }
                restoredEntities++;
                completedOperations++;
                used++;
                checkpointIfNeeded();
                if (restoreEntityIndex >= savedEntities.size()) {
                    completeSuccessfully();
                }
            }
            return used;
        }

        private void advanceClear() {
            z++;
            if (z <= region.getMaxZ()) {
                return;
            }
            z = region.getMinZ();
            y++;
            if (y <= region.getMaxY()) {
                return;
            }
            y = region.getMinY();
            x++;
            if (x > region.getMaxX()) {
                if (savedBlocks.isEmpty()) {
                    if (savedEntities.isEmpty()) {
                        completeSuccessfully();
                    } else {
                        phase = Phase.RESTORE_ENTITIES;
                        manager.queueCheckpoint(
                                checkpointId, region, "restore_entities", completedOperations, totalOperations
                        );
                    }
                } else {
                    phase = Phase.RESTORE_BLOCKS;
                    manager.queueCheckpoint(
                            checkpointId, region, "restore_blocks", completedOperations, totalOperations
                    );
                }
            }
        }

        private void completeSuccessfully() {
            phase = Phase.COMPLETE;
            manager.finishCheckpoint(
                    checkpointId, region, "completed", "", completedOperations, totalOperations
            );
            manager.markRecoveredAfterSuccessfulReset(region);
        }

        private void checkpointIfNeeded() {
            if (completedOperations < nextCheckpoint) {
                return;
            }
            nextCheckpoint = completedOperations + CHECKPOINT_INTERVAL;
            manager.queueCheckpoint(
                    checkpointId,
                    region,
                    phase.name().toLowerCase(Locale.ROOT),
                    completedOperations,
                    totalOperations
            );
        }

        @Override
        public Set<String> resourceLocks() {
            return Set.of(SsuJobLocks.region(region.getDimension(), region.getName()));
        }

        @Override
        public boolean isComplete() {
            return phase == Phase.COMPLETE;
        }

        @Override
        public double progress() {
            if (phase == Phase.LOAD) {
                return 0.0D;
            }
            return totalOperations == 0L
                    ? 1.0D
                    : Math.min(1.0D, completedOperations / (double) totalOperations);
        }

        @Override
        public void cancel() {
            loadFuture.cancel(false);
            if (checkpointStarted) {
                manager.finishCheckpoint(
                        checkpointId,
                        region,
                        "cancelled",
                        "Snapshot reset was cancelled before completion.",
                        completedOperations,
                        totalOperations
                );
            }
        }

        public int restoredBlocks() {
            return restoredBlocks;
        }

        public int restoredEntities() {
            return restoredEntities;
        }
    }

    static int relativeIndex(Region region, int x, int y, int z) {
        int sizeY = region.getMaxY() - region.getMinY() + 1;
        int sizeZ = region.getMaxZ() - region.getMinZ() + 1;
        return RegionSnapshotCoordinates.pack(
                region.getMinX(), region.getMinY(), region.getMinZ(),
                sizeY, sizeZ, x, y, z
        );
    }
}
