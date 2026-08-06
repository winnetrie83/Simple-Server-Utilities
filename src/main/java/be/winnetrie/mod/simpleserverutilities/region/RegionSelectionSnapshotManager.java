package be.winnetrie.mod.simpleserverutilities.region;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Portable administrator selection snapshots.
 *
 * <p>Unlike the ordinary block-only clipboard templates, these snapshots also
 * preserve block-entity NBT (including inventories) and structural entities
 * such as item frames, paintings and armour stands. Placement is deliberately
 * server-authoritative and clears destination containers without drops before
 * restoring the captured data.</p>
 */
public final class RegionSelectionSnapshotManager {
    public static final String FILE_EXTENSION = ".ssuselshot";
    public static final long MAX_VOLUME = RegionSelectionSchematicManager.MAX_VOLUME;
    public static final int MAX_PREVIEW_BLOCKS = 4096;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 192 * 1024 * 1024;
    private static final int MAX_STRUCTURAL_ENTITIES = 8192;
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private RegionSelectionSnapshotManager() {
    }

    public static CaptureJob createCaptureJob(ServerLevel level, RegionSelection selection) {
        RegionSelectionSchematicManager.Bounds bounds = RegionSelectionSchematicManager.bounds(selection);
        validateVolume(bounds.volume());
        return new CaptureJob(level, bounds, operationLocks(level, bounds));
    }

    public static PasteJob createPasteJob(ServerLevel level, BlockPos origin, SnapshotTemplate template) {
        if (level == null || origin == null || template == null) throw new IllegalArgumentException("No snapshot placement is available.");
        Bounds destination = destination(origin, template);
        validateWorldBounds(level, destination);
        return new PasteJob(level, destination, template, operationLocks(level, destination));
    }

    public static SnapshotTemplate transform(SnapshotTemplate template,
                                             RegionSelectionSchematicManager.SelectionTransform transform) {
        if (template == null || transform == null) throw new IllegalArgumentException("No snapshot transform is available.");
        int sx = template.sizeX(), sy = template.sizeY(), sz = template.sizeZ();
        int tx = transform.swapsHorizontalAxes() ? sz : sx;
        int ty = sy;
        int tz = transform.swapsHorizontalAxes() ? sx : sz;

        List<BlockState> sourcePalette = template.palette().stream()
                .map(RegionSelectionSnapshotManager::blockStateFromJsonString).toList();
        Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        List<String> palette = new ArrayList<>();
        List<SnapshotBlock> blocks = new ArrayList<>(template.blocks().size());
        for (SnapshotBlock block : template.blocks()) {
            Decoded decoded = decode(template, block.relativeIndex());
            DiscretePosition position = transformDiscrete(decoded.x(), decoded.y(), decoded.z(), sx, sy, sz, transform);
            BlockState transformedState = transform.state(sourcePalette.get(block.paletteIndex()));
            String stateJson = blockStateJson(transformedState);
            int paletteIndex = paletteIndexes.computeIfAbsent(stateJson, ignored -> {
                palette.add(stateJson);
                return palette.size() - 1;
            });
            int relative = ((position.x() * ty) + position.y()) * tz + position.z();
            blocks.add(new SnapshotBlock(relative, paletteIndex, block.blockEntitySnbt()));
        }
        blocks.sort(Comparator.comparingInt(SnapshotBlock::relativeIndex));

        List<SnapshotEntity> entities = new ArrayList<>(template.entities().size());
        for (SnapshotEntity entity : template.entities()) {
            ContinuousPosition pos = transform(entity.relX(), entity.relY(), entity.relZ(), sx, sy, sz, transform);
            entities.add(new SnapshotEntity(pos.x(), pos.y(), pos.z(), transformYaw(entity.yaw(), transform),
                    entity.pitch(), entity.entitySnbt()));
        }
        return new SnapshotTemplate(tx, ty, tz, List.copyOf(palette), List.copyOf(blocks), List.copyOf(entities));
    }

    private static DiscretePosition transformDiscrete(int x, int y, int z, int sx, int sy, int sz,
                                                      RegionSelectionSchematicManager.SelectionTransform transform) {
        return switch (transform) {
            case ROTATE_LEFT -> new DiscretePosition(z, y, sx - 1 - x);
            case ROTATE_RIGHT -> new DiscretePosition(sz - 1 - z, y, x);
            case ROTATE_180 -> new DiscretePosition(sx - 1 - x, y, sz - 1 - z);
            case MIRROR_X -> new DiscretePosition(sx - 1 - x, y, z);
            case MIRROR_Z -> new DiscretePosition(x, y, sz - 1 - z);
            case FLIP_VERTICAL -> new DiscretePosition(x, sy - 1 - y, z);
        };
    }

    private static ContinuousPosition transform(double x, double y, double z, int sx, int sy, int sz,
                                                RegionSelectionSchematicManager.SelectionTransform transform) {
        return switch (transform) {
            case ROTATE_LEFT -> new ContinuousPosition(z, y, sx - x);
            case ROTATE_RIGHT -> new ContinuousPosition(sz - z, y, x);
            case ROTATE_180 -> new ContinuousPosition(sx - x, y, sz - z);
            case MIRROR_X -> new ContinuousPosition(sx - x, y, z);
            case MIRROR_Z -> new ContinuousPosition(x, y, sz - z);
            case FLIP_VERTICAL -> new ContinuousPosition(x, sy - y, z);
        };
    }

    private static float transformYaw(float yaw, RegionSelectionSchematicManager.SelectionTransform transform) {
        return switch (transform) {
            case ROTATE_LEFT -> yaw - 90.0F;
            case ROTATE_RIGHT -> yaw + 90.0F;
            case ROTATE_180 -> yaw + 180.0F;
            case MIRROR_X -> 180.0F - yaw;
            case MIRROR_Z -> -yaw;
            case FLIP_VERTICAL -> yaw;
        };
    }

    public static void save(MinecraftServer server, String rawName, SnapshotTemplate template) throws IOException {
        String name = validateName(rawName);
        Path folder = folder(server);
        Files.createDirectories(folder);
        Path file = folder.resolve(StoragePaths.sanitizeFileName(name) + FILE_EXTENSION);
        writeAtomically(file, encode(template));
    }

    public static SnapshotTemplate load(MinecraftServer server, String rawName) throws IOException {
        String name = validateName(rawName);
        Path file = folder(server).resolve(StoragePaths.sanitizeFileName(name) + FILE_EXTENSION);
        if (!Files.isRegularFile(file)) throw new IOException("Selection snapshot not found: " + name);
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Selection snapshot exceeds the 32 MiB limit.");
        return decode(Files.readAllBytes(file));
    }

    public static List<String> list(MinecraftServer server) {
        Path folder = folder(server);
        if (!Files.isDirectory(folder)) return List.of();
        try (var stream = Files.list(folder)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(FILE_EXTENSION))
                    .map(name -> name.substring(0, name.length() - FILE_EXTENSION.length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(256)
                    .toList();
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.warn("Could not list region selection snapshots.", exception);
            return List.of();
        }
    }

    public static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!name.matches("[A-Za-z0-9._-]{1,64}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Use 1-64 letters, numbers, dots, underscores or dashes for the snapshot name.");
        }
        return name;
    }

    private static Path folder(MinecraftServer server) {
        return StoragePaths.regionSelectionTemplates(StoragePaths.root(server)).resolve("snapshots");
    }

    private static byte[] encode(SnapshotTemplate template) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("sizeX", template.sizeX());
        root.addProperty("sizeY", template.sizeY());
        root.addProperty("sizeZ", template.sizeZ());
        JsonArray palette = new JsonArray();
        template.palette().forEach(value -> palette.add(JsonParser.parseString(value)));
        root.add("palette", palette);
        JsonArray blocks = new JsonArray();
        for (SnapshotBlock block : template.blocks()) {
            JsonArray entry = new JsonArray();
            entry.add(block.relativeIndex());
            entry.add(block.paletteIndex());
            if (!block.blockEntitySnbt().isBlank()) entry.add(block.blockEntitySnbt());
            blocks.add(entry);
        }
        root.add("blocks", blocks);
        JsonArray entities = new JsonArray();
        for (SnapshotEntity entity : template.entities()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("x", entity.relX());
            entry.addProperty("y", entity.relY());
            entry.addProperty("z", entity.relZ());
            entry.addProperty("yaw", entity.yaw());
            entry.addProperty("pitch", entity.pitch());
            entry.addProperty("snbt", entity.entitySnbt());
            entities.add(entry);
        }
        root.add("entities", entities);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output);
             Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        byte[] result = output.toByteArray();
        if (result.length > MAX_FILE_BYTES) throw new IOException("Selection snapshot exceeds the 32 MiB limit.");
        return result;
    }

    private static SnapshotTemplate decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("Selection snapshot is empty.");
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Selection snapshot exceeds the 32 MiB limit.");
        JsonObject root;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream decompressed = new ByteArrayOutputStream(Math.min(bytes.length * 4, 1_048_576))) {
            byte[] buffer = new byte[8192];
            int read, total = 0;
            while ((read = gzip.read(buffer)) != -1) {
                if (read == 0) continue;
                total += read;
                if (total > MAX_DECOMPRESSED_BYTES) throw new IOException("Selection snapshot expands beyond the safety limit.");
                decompressed.write(buffer, 0, read);
            }
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(decompressed.toByteArray()), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Selection snapshot data is invalid.", exception);
        }
        int version = root.has("version") ? root.get("version").getAsInt() : 0;
        if (version != FORMAT_VERSION) throw new IOException("Unsupported selection snapshot version: " + version);
        int sx = root.get("sizeX").getAsInt(), sy = root.get("sizeY").getAsInt(), sz = root.get("sizeZ").getAsInt();
        validateVolume((long) sx * sy * sz);
        List<String> palette = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("palette")) {
            JsonObject state = element.getAsJsonObject();
            blockStateFromJson(state);
            palette.add(GSON.toJson(state));
        }
        List<SnapshotBlock> blocks = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>();
        JsonArray blockArray = root.getAsJsonArray("blocks");
        if (blockArray != null && blockArray.size() > (long) sx * sy * sz) throw new IOException("Selection snapshot contains too many blocks.");
        if (blockArray != null) for (JsonElement element : blockArray) {
            JsonArray entry = element.getAsJsonArray();
            int relative = entry.get(0).getAsInt(), paletteIndex = entry.get(1).getAsInt();
            if (relative < 0 || relative >= (long) sx * sy * sz || paletteIndex < 0 || paletteIndex >= palette.size()) {
                throw new IOException("Selection snapshot contains an invalid block index.");
            }
            if (!occupied.add(relative)) throw new IOException("Selection snapshot contains duplicate block positions.");
            blocks.add(new SnapshotBlock(relative, paletteIndex, entry.size() >= 3 ? entry.get(2).getAsString() : ""));
        }
        blocks.sort(Comparator.comparingInt(SnapshotBlock::relativeIndex));
        List<SnapshotEntity> entities = new ArrayList<>();
        JsonArray entityArray = root.getAsJsonArray("entities");
        if (entityArray != null && entityArray.size() > MAX_STRUCTURAL_ENTITIES) throw new IOException("Selection snapshot contains too many structural entities.");
        if (entityArray != null) for (JsonElement element : entityArray) {
            JsonObject entry = element.getAsJsonObject();
            entities.add(new SnapshotEntity(entry.get("x").getAsDouble(), entry.get("y").getAsDouble(),
                    entry.get("z").getAsDouble(), entry.get("yaw").getAsFloat(), entry.get("pitch").getAsFloat(),
                    entry.get("snbt").getAsString()));
        }
        return new SnapshotTemplate(sx, sy, sz, List.copyOf(palette), List.copyOf(blocks), List.copyOf(entities));
    }

    private static void writeAtomically(Path file, byte[] bytes) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Bounds destination(BlockPos origin, SnapshotTemplate template) {
        return new Bounds(origin.getX(), origin.getY(), origin.getZ(),
                Math.addExact(origin.getX(), template.sizeX() - 1),
                Math.addExact(origin.getY(), template.sizeY() - 1),
                Math.addExact(origin.getZ(), template.sizeZ() - 1));
    }

    private static void validateWorldBounds(ServerLevel level, Bounds bounds) {
        validateVolume(bounds.volume());
        if (bounds.minY() < level.getMinY() || bounds.maxY() > level.getMaxY()) {
            throw new IllegalArgumentException("The snapshot would extend outside the build height.");
        }
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))) {
            throw new IllegalArgumentException("The snapshot would extend outside the world border.");
        }
    }

    private static void validateVolume(long volume) {
        if (volume <= 0 || volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Selection is too large: " + volume + " blocks. Limit: " + MAX_VOLUME + ".");
        }
    }

    private static Set<String> operationLocks(ServerLevel level, RegionSelectionSchematicManager.Bounds bounds) {
        return operationLocks(level, new Bounds(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }

    private static Set<String> operationLocks(ServerLevel level, Bounds bounds) {
        Set<String> locks = new HashSet<>();
        locks.add(SsuJobLocks.cuboid(level.dimension(), bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        for (Region region : SimpleServerUtilities.REGIONS.getIntersecting2D(level.dimension(), bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ())) {
            if (bounds.minY() <= region.getMaxY() && bounds.maxY() >= region.getMinY()) {
                locks.add(SsuJobLocks.region(region.getDimension(), region.getName()));
            }
        }
        return Set.copyOf(locks);
    }

    private static String blockStateJson(BlockState state) {
        JsonObject json = new JsonObject();
        json.addProperty("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (!state.getProperties().isEmpty()) {
            JsonObject properties = new JsonObject();
            for (Property<?> property : state.getProperties()) properties.addProperty(property.getName(), propertyValueName(state, property));
            json.add("properties", properties);
        }
        return GSON.toJson(json);
    }

    private static BlockState blockStateFromJsonString(String raw) {
        return blockStateFromJson(JsonParser.parseString(raw).getAsJsonObject());
    }

    private static BlockState blockStateFromJson(JsonObject json) {
        Identifier blockId = Identifier.parse(json.get("block").getAsString());
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElseThrow(
                () -> new IllegalArgumentException("Unknown block in snapshot: " + blockId));
        BlockState state = block.defaultBlockState();
        if (!json.has("properties")) return state;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("properties").entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalArgumentException("Unknown block-state property '" + entry.getKey() + "'.");
            state = applyProperty(state, property, entry.getValue().getAsString());
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(BlockState state, Property property) {
        return property.getName((Comparable) state.getValue(property));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, Property property, String valueName) {
        Optional value = property.getValue(valueName);
        if (value.isEmpty()) throw new IllegalArgumentException("Invalid block-state property value '" + valueName + "'.");
        return state.setValue(property, (Comparable) value.get());
    }

    private static String captureBlockEntity(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return "";
        return blockEntity.saveWithFullMetadata(level.registryAccess()).toString();
    }

    private static String captureEntity(ServerLevel level, Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        return entity.save(output) ? output.buildResult().toString() : "";
    }

    private static List<Entity> structuralEntities(ServerLevel level, Bounds bounds) {
        AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);
        List<Entity> entities = new ArrayList<>();
        entities.addAll(level.getEntitiesOfClass(HangingEntity.class, box, Entity::isAlive));
        entities.addAll(level.getEntitiesOfClass(ArmorStand.class, box, Entity::isAlive));
        return entities;
    }

    private static void clearBlock(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            blockEntity.setChanged();
        }
        if (blockEntity != null) level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
    }

    private static boolean restoreBlockEntity(ServerLevel level, SnapshotBlock block, BlockPos pos, BlockState state) throws Exception {
        if (block.blockEntitySnbt().isBlank()) return true;
        CompoundTag tag = parseCompoundTag(block.blockEntitySnbt());
        BlockEntity restored = BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
        if (restored == null) return false;
        level.setBlockEntity(restored);
        restored.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    private static boolean restoreEntity(ServerLevel level, Bounds bounds, SnapshotEntity saved) throws Exception {
        CompoundTag tag = parseCompoundTag(saved.entitySnbt());
        tag.remove("UUID");
        Entity entity = EntityType.loadEntityRecursive(tag, level,
                new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP);
        if (entity == null) return false;
        entity.setPos(bounds.minX() + saved.relX(), bounds.minY() + saved.relY(), bounds.minZ() + saved.relZ());
        entity.setYRot(saved.yaw());
        entity.setXRot(saved.pitch());
        return level.addFreshEntity(entity);
    }

    private static CompoundTag parseCompoundTag(String snbt) throws ReflectiveOperationException {
        Class<?> parserClass = Class.forName("net.minecraft.nbt.TagParser");
        for (Method method : parserClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !CompoundTag.class.isAssignableFrom(method.getReturnType())) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == String.class) {
                method.setAccessible(true);
                return (CompoundTag) method.invoke(null, snbt);
            }
        }
        throw new NoSuchMethodException("No compatible TagParser compound method is available.");
    }

    private static Decoded decode(SnapshotTemplate template, int relative) {
        int yz = template.sizeY() * template.sizeZ();
        int x = relative / yz;
        int remainder = relative % yz;
        return new Decoded(x, remainder / template.sizeZ(), remainder % template.sizeZ());
    }

    public record SnapshotBlock(int relativeIndex, int paletteIndex, String blockEntitySnbt) {
        public SnapshotBlock { blockEntitySnbt = blockEntitySnbt == null ? "" : blockEntitySnbt; }
    }

    public record SnapshotEntity(double relX, double relY, double relZ, float yaw, float pitch, String entitySnbt) {
        public SnapshotEntity { entitySnbt = entitySnbt == null ? "" : entitySnbt; }
    }

    public record SnapshotTemplate(int sizeX, int sizeY, int sizeZ, List<String> palette,
                                   List<SnapshotBlock> blocks, List<SnapshotEntity> entities) {
        public SnapshotTemplate {
            long volume = (long) sizeX * sizeY * sizeZ;
            validateVolume(volume);
            palette = palette == null ? List.of() : List.copyOf(palette);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            entities = entities == null ? List.of() : List.copyOf(entities);
            if (blocks.size() > volume) throw new IllegalArgumentException("A snapshot cannot contain more blocks than its volume.");
            if (entities.size() > MAX_STRUCTURAL_ENTITIES) throw new IllegalArgumentException("A snapshot contains too many structural entities.");
            for (SnapshotBlock block : blocks) {
                if (block.relativeIndex() < 0 || block.relativeIndex() >= volume
                        || block.paletteIndex() < 0 || block.paletteIndex() >= palette.size()) {
                    throw new IllegalArgumentException("A snapshot contains an invalid block or palette index.");
                }
            }
            for (SnapshotEntity entity : entities) {
                if (!Double.isFinite(entity.relX()) || !Double.isFinite(entity.relY()) || !Double.isFinite(entity.relZ())
                        || entity.relX() < -1.0D || entity.relY() < -1.0D || entity.relZ() < -1.0D
                        || entity.relX() > sizeX + 1.0D || entity.relY() > sizeY + 1.0D || entity.relZ() > sizeZ + 1.0D) {
                    throw new IllegalArgumentException("A snapshot contains an invalid structural entity position.");
                }
            }
        }
        public long volume() { return (long) sizeX * sizeY * sizeZ; }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public long volume() { return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1); }
    }

    private record Decoded(int x, int y, int z) { }
    private record DiscretePosition(int x, int y, int z) { }
    private record ContinuousPosition(double x, double y, double z) { }

    public static final class CaptureJob implements SsuJob {
        private enum Phase { BLOCKS, ENTITIES, COMPLETE }
        private final ServerLevel level;
        private final RegionSelectionSchematicManager.Bounds bounds;
        private final Set<String> locks;
        private final Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        private final List<String> palette = new ArrayList<>();
        private final List<SnapshotBlock> blocks = new ArrayList<>();
        private final List<SnapshotEntity> entities = new ArrayList<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int x, y, z, entityIndex;
        private long visited;
        private List<Entity> entityCandidates = List.of();
        private Phase phase = Phase.BLOCKS;
        private SnapshotTemplate template;

        private CaptureJob(ServerLevel level, RegionSelectionSchematicManager.Bounds bounds, Set<String> locks) {
            this.level = level; this.bounds = bounds; this.locks = locks;
            x = bounds.minX(); y = bounds.minY(); z = bounds.minZ();
        }

        @Override public String description() { return "Capture full region selection snapshot (" + bounds.volume() + " blocks)"; }
        @Override public int runStep(MinecraftServer server, int budget) throws Exception {
            int used = 0;
            while (used < budget && phase != Phase.COMPLETE) {
                if (phase == Phase.BLOCKS) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.isAir()) {
                        String stateJson = blockStateJson(state);
                        int paletteIndex = paletteIndexes.computeIfAbsent(stateJson, ignored -> {
                            palette.add(stateJson); return palette.size() - 1;
                        });
                        int relative = ((x - bounds.minX()) * (bounds.maxY() - bounds.minY() + 1)
                                + (y - bounds.minY())) * (bounds.maxZ() - bounds.minZ() + 1) + (z - bounds.minZ());
                        blocks.add(new SnapshotBlock(relative, paletteIndex, captureBlockEntity(level, cursor)));
                    }
                    visited++; used++; advance();
                    continue;
                }
                Entity entity = entityCandidates.get(entityIndex++);
                String snbt = captureEntity(level, entity);
                if (snbt.isBlank()) throw new IOException("Could not serialize structural entity " + entity.getStringUUID() + ".");
                entities.add(new SnapshotEntity(entity.getX() - bounds.minX(), entity.getY() - bounds.minY(),
                        entity.getZ() - bounds.minZ(), entity.getYRot(), entity.getXRot(), snbt));
                used++;
                if (entityIndex >= entityCandidates.size()) finish();
            }
            return used;
        }
        private void advance() {
            z++;
            if (z <= bounds.maxZ()) return;
            z = bounds.minZ(); y++;
            if (y <= bounds.maxY()) return;
            y = bounds.minY(); x++;
            if (x > bounds.maxX()) {
                entityCandidates = structuralEntities(level, new Bounds(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()));
                if (entityCandidates.isEmpty()) finish(); else phase = Phase.ENTITIES;
            }
        }
        private void finish() {
            phase = Phase.COMPLETE;
            template = new SnapshotTemplate(bounds.maxX() - bounds.minX() + 1,
                    bounds.maxY() - bounds.minY() + 1, bounds.maxZ() - bounds.minZ() + 1,
                    List.copyOf(palette), List.copyOf(blocks), List.copyOf(entities));
        }
        @Override public boolean isComplete() { return phase == Phase.COMPLETE; }
        @Override public double progress() {
            if (phase == Phase.COMPLETE) return 1.0;
            if (phase == Phase.ENTITIES) return 0.96 + (entityCandidates.isEmpty() ? 0 : 0.04 * entityIndex / entityCandidates.size());
            return Math.min(0.96, visited / (double) bounds.volume() * 0.96);
        }
        @Override public Set<String> resourceLocks() { return locks; }
        public SnapshotTemplate template() { return template; }
    }

    public static final class PasteJob implements SsuJob {
        private enum Phase { REMOVE_ENTITIES, CLEAR, RESTORE_BLOCKS, RECONCILE, RESTORE_ENTITIES, COMPLETE }
        private final ServerLevel level;
        private final Bounds bounds;
        private final SnapshotTemplate template;
        private final Set<String> locks;
        private final List<BlockState> palette;
        private final List<Entity> existingEntities;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private Phase phase;
        private int entityRemoveIndex, blockIndex, reconcileIndex, entityRestoreIndex;
        private int x, y, z;
        private long operations;

        private PasteJob(ServerLevel level, Bounds bounds, SnapshotTemplate template, Set<String> locks) {
            this.level = level; this.bounds = bounds; this.template = template; this.locks = locks;
            this.palette = template.palette().stream().map(RegionSelectionSnapshotManager::blockStateFromJsonString).toList();
            this.existingEntities = structuralEntities(level, bounds);
            this.phase = existingEntities.isEmpty() ? Phase.CLEAR : Phase.REMOVE_ENTITIES;
            this.x = bounds.minX(); this.y = bounds.minY(); this.z = bounds.minZ();
        }

        @Override public String description() { return "Place full region selection snapshot"; }
        @Override public int runStep(MinecraftServer server, int budget) throws Exception {
            int used = 0;
            while (used < budget && phase != Phase.COMPLETE) {
                switch (phase) {
                    case REMOVE_ENTITIES -> {
                        existingEntities.get(entityRemoveIndex++).discard();
                        if (entityRemoveIndex >= existingEntities.size()) phase = Phase.CLEAR;
                    }
                    case CLEAR -> {
                        cursor.set(x, y, z);
                        if (!level.isEmptyBlock(cursor)) clearBlock(level, cursor);
                        advanceClear();
                    }
                    case RESTORE_BLOCKS -> {
                        SnapshotBlock block = template.blocks().get(blockIndex++);
                        Decoded decoded = decode(template, block.relativeIndex());
                        cursor.set(bounds.minX() + decoded.x(), bounds.minY() + decoded.y(), bounds.minZ() + decoded.z());
                        BlockState state = palette.get(block.paletteIndex());
                        level.setBlock(cursor, state, UPDATE_FLAGS);
                        if (!restoreBlockEntity(level, block, cursor.immutable(), state)) {
                            throw new IOException("Could not restore block entity at " + cursor + ".");
                        }
                        if (blockIndex >= template.blocks().size()) phase = Phase.RECONCILE;
                    }
                    case RECONCILE -> {
                        SnapshotBlock block = template.blocks().get(reconcileIndex++);
                        Decoded decoded = decode(template, block.relativeIndex());
                        cursor.set(bounds.minX() + decoded.x(), bounds.minY() + decoded.y(), bounds.minZ() + decoded.z());
                        level.updateNeighborsAt(cursor, level.getBlockState(cursor).getBlock());
                        if (reconcileIndex >= template.blocks().size()) {
                            phase = template.entities().isEmpty() ? Phase.COMPLETE : Phase.RESTORE_ENTITIES;
                        }
                    }
                    case RESTORE_ENTITIES -> {
                        if (!restoreEntity(level, bounds, template.entities().get(entityRestoreIndex++))) {
                            throw new IOException("Could not restore structural entity " + entityRestoreIndex + ".");
                        }
                        if (entityRestoreIndex >= template.entities().size()) phase = Phase.COMPLETE;
                    }
                    case COMPLETE -> { }
                }
                operations++; used++;
            }
            return used;
        }
        private void advanceClear() {
            z++;
            if (z <= bounds.maxZ()) return;
            z = bounds.minZ(); y++;
            if (y <= bounds.maxY()) return;
            y = bounds.minY(); x++;
            if (x > bounds.maxX()) phase = template.blocks().isEmpty()
                    ? (template.entities().isEmpty() ? Phase.COMPLETE : Phase.RESTORE_ENTITIES)
                    : Phase.RESTORE_BLOCKS;
        }
        @Override public boolean isComplete() { return phase == Phase.COMPLETE; }
        @Override public double progress() {
            long total = existingEntities.size() + bounds.volume() + template.blocks().size() * 2L + template.entities().size();
            return total <= 0 ? 1.0 : Math.min(1.0, operations / (double) total);
        }
        @Override public Set<String> resourceLocks() { return locks; }
        public Bounds destination() { return bounds; }
    }
}
