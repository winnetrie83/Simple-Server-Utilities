package be.winnetrie.mod.simpleserverutilities.region;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Safe, server-authoritative clipboard and portable selection-template support
 * for the region tool. Templates deliberately contain only block states.
 * Block-entity data, inventories and world entities are never copied, which
 * prevents selection templates from duplicating container contents.
 */
public final class RegionSelectionSchematicManager {
    public static final long MAX_VOLUME = 1_000_000L;
    public static final int MAX_TRANSFER_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;
    public static final String FILE_EXTENSION = ".ssusel";
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Map<UUID, SelectionTemplate> CLIPBOARDS = new ConcurrentHashMap<>();

    private RegionSelectionSchematicManager() {
    }

    public static void clearRuntimeState() {
        CLIPBOARDS.clear();
    }

    public static boolean hasClipboard(UUID playerId) {
        return playerId != null && CLIPBOARDS.containsKey(playerId);
    }

    public static SelectionTemplate clipboard(UUID playerId) {
        return playerId == null ? null : CLIPBOARDS.get(playerId);
    }

    public static void setClipboard(UUID playerId, SelectionTemplate template) {
        if (playerId == null || template == null) return;
        CLIPBOARDS.put(playerId, template);
    }

    public static void clearClipboard(UUID playerId) {
        if (playerId != null) CLIPBOARDS.remove(playerId);
    }

    public static CaptureJob createCaptureJob(ServerLevel level, RegionSelection selection) {
        Bounds bounds = bounds(selection);
        validateVolume(bounds.volume());
        return new CaptureJob(level, bounds, operationLocks(level, bounds));
    }

    public static PasteJob createPasteJob(ServerLevel level, BlockPos origin, SelectionTemplate template) {
        if (origin == null || template == null) throw new IllegalArgumentException("No selection template is available.");
        Bounds destination = new Bounds(
                origin.getX(), origin.getY(), origin.getZ(),
                destinationMaximum(origin.getX(), template.sizeX(), "X"),
                destinationMaximum(origin.getY(), template.sizeY(), "Y"),
                destinationMaximum(origin.getZ(), template.sizeZ(), "Z")
        );
        validateVolume(destination.volume());
        if (destination.minY() < level.getMinY() || destination.maxY() > level.getMaxY()) {
            throw new IllegalArgumentException("The template would extend outside the build height.");
        }
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(destination.minX(), destination.minY(), destination.minZ()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(destination.maxX(), destination.maxY(), destination.maxZ()))) {
            throw new IllegalArgumentException("The template would extend outside the world border.");
        }
        return new PasteJob(level, destination, null, destination, template, operationLocks(level, destination));
    }

    /** Creates an in-place transform job that clears both the old and new footprint before placement. */
    public static PasteJob createTransformPasteJob(ServerLevel level, Bounds source, BlockPos origin, SelectionTemplate template) {
        if (source == null || origin == null || template == null) throw new IllegalArgumentException("No transformed selection is available.");
        Bounds destination = new Bounds(
                origin.getX(), origin.getY(), origin.getZ(),
                destinationMaximum(origin.getX(), template.sizeX(), "X"),
                destinationMaximum(origin.getY(), template.sizeY(), "Y"),
                destinationMaximum(origin.getZ(), template.sizeZ(), "Z")
        );
        Bounds clearBounds = new Bounds(
                Math.min(source.minX(), destination.minX()), Math.min(source.minY(), destination.minY()), Math.min(source.minZ(), destination.minZ()),
                Math.max(source.maxX(), destination.maxX()), Math.max(source.maxY(), destination.maxY()), Math.max(source.maxZ(), destination.maxZ())
        );
        validateVolume(clearBounds.volume());
        if (clearBounds.minY() < level.getMinY() || clearBounds.maxY() > level.getMaxY()) {
            throw new IllegalArgumentException("The transformed selection would extend outside the build height.");
        }
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(clearBounds.minX(), clearBounds.minY(), clearBounds.minZ()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(clearBounds.maxX(), clearBounds.maxY(), clearBounds.maxZ()))) {
            throw new IllegalArgumentException("The transformed selection would extend outside the world border.");
        }
        return new PasteJob(level, clearBounds, source, destination, template, operationLocks(level, clearBounds));
    }


    /** Returns a transformed copy of a block-state-only template. */
    public static SelectionTemplate transform(SelectionTemplate template, SelectionTransform transform) {
        if (template == null) throw new IllegalArgumentException("No selection template is available.");
        if (transform == null) throw new IllegalArgumentException("No selection transform was selected.");
        int sourceX = template.sizeX();
        int sourceY = template.sizeY();
        int sourceZ = template.sizeZ();
        int targetX = transform.swapsHorizontalAxes() ? sourceZ : sourceX;
        int targetY = sourceY;
        int targetZ = transform.swapsHorizontalAxes() ? sourceX : sourceZ;

        List<BlockState> sourcePalette = template.palette().stream()
                .map(RegionSelectionSchematicManager::blockStateFromJsonString).toList();
        Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        List<String> palette = new ArrayList<>();
        List<TemplateBlock> blocks = new ArrayList<>(template.blocks().size());
        for (TemplateBlock block : template.blocks()) {
            Decoded decoded = decodeRelative(template, block.relativeIndex());
            TransformedPosition position = transform.position(decoded.x(), decoded.y(), decoded.z(), sourceX, sourceY, sourceZ);
            BlockState state = transform.state(sourcePalette.get(block.paletteIndex()));
            String stateJson = blockStateJson(state);
            int paletteIndex = paletteIndexes.computeIfAbsent(stateJson, ignored -> {
                palette.add(stateJson);
                return palette.size() - 1;
            });
            int relative = ((position.x() * targetY) + position.y()) * targetZ + position.z();
            blocks.add(new TemplateBlock(relative, paletteIndex));
        }
        blocks.sort(Comparator.comparingInt(TemplateBlock::relativeIndex));
        return new SelectionTemplate(targetX, targetY, targetZ, List.copyOf(palette), List.copyOf(blocks));
    }

    public enum SelectionTransform {
        ROTATE_LEFT, ROTATE_RIGHT, ROTATE_180, MIRROR_X, MIRROR_Z, FLIP_VERTICAL;

        boolean swapsHorizontalAxes() {
            return this == ROTATE_LEFT || this == ROTATE_RIGHT;
        }

        TransformedPosition position(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
            return switch (this) {
                case ROTATE_LEFT -> new TransformedPosition(z, y, sizeX - 1 - x);
                case ROTATE_RIGHT -> new TransformedPosition(sizeZ - 1 - z, y, x);
                case ROTATE_180 -> new TransformedPosition(sizeX - 1 - x, y, sizeZ - 1 - z);
                case MIRROR_X -> new TransformedPosition(sizeX - 1 - x, y, z);
                case MIRROR_Z -> new TransformedPosition(x, y, sizeZ - 1 - z);
                case FLIP_VERTICAL -> new TransformedPosition(x, sizeY - 1 - y, z);
            };
        }

        BlockState state(BlockState state) {
            return switch (this) {
                case ROTATE_LEFT -> state.rotate(Rotation.COUNTERCLOCKWISE_90);
                case ROTATE_RIGHT -> state.rotate(Rotation.CLOCKWISE_90);
                case ROTATE_180 -> state.rotate(Rotation.CLOCKWISE_180);
                case MIRROR_X -> state.mirror(Mirror.FRONT_BACK);
                case MIRROR_Z -> state.mirror(Mirror.LEFT_RIGHT);
                case FLIP_VERTICAL -> flipVerticalState(state);
            };
        }
    }

    private static BlockState flipVerticalState(BlockState state) {
        // Minecraft has no generic vertical mirror transform, so invert the common
        // vertical block-state properties while retaining every unrelated property.
        state = swapPropertyValue(state, "facing", "up", "down");
        state = swapPropertyValue(state, "vertical_direction", "up", "down");
        state = swapPropertyValue(state, "half", "top", "bottom");
        state = swapPropertyValue(state, "half", "upper", "lower");
        state = swapPropertyValue(state, "type", "top", "bottom");
        state = swapPropertyValue(state, "face", "floor", "ceiling");
        Boolean up = booleanProperty(state, "up");
        Boolean down = booleanProperty(state, "down");
        if (up != null && down != null) {
            state = setPropertyValue(state, "up", Boolean.toString(down));
            state = setPropertyValue(state, "down", Boolean.toString(up));
        }
        return state;
    }

    private static BlockState swapPropertyValue(BlockState state, String propertyName, String first, String second) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (property == null) return state;
        String current = propertyValueName(state, property);
        if (current.equals(first)) return applyProperty(state, property, second);
        if (current.equals(second)) return applyProperty(state, property, first);
        return state;
    }

    private static Boolean booleanProperty(BlockState state, String propertyName) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (property == null) return null;
        String value = propertyValueName(state, property);
        if (value.equals("true")) return Boolean.TRUE;
        if (value.equals("false")) return Boolean.FALSE;
        return null;
    }

    private static BlockState setPropertyValue(BlockState state, String propertyName, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        return property == null ? state : applyProperty(state, property, value);
    }

    private record TransformedPosition(int x, int y, int z) {}

    public static Bounds bounds(RegionSelection selection) {
        if (selection == null || !selection.isComplete()) throw new IllegalArgumentException("Set both selection points first.");
        return new Bounds(
                Math.min(selection.getPoint1().getX(), selection.getPoint2().getX()),
                Math.min(selection.getPoint1().getY(), selection.getPoint2().getY()),
                Math.min(selection.getPoint1().getZ(), selection.getPoint2().getZ()),
                Math.max(selection.getPoint1().getX(), selection.getPoint2().getX()),
                Math.max(selection.getPoint1().getY(), selection.getPoint2().getY()),
                Math.max(selection.getPoint1().getZ(), selection.getPoint2().getZ())
        );
    }

    public static void saveServerTemplate(MinecraftServer server, String rawName, SelectionTemplate template) throws IOException {
        String name = validateName(rawName);
        Path folder = serverTemplateFolder(server);
        Files.createDirectories(folder);
        Path file = folder.resolve(StoragePaths.sanitizeFileName(name) + FILE_EXTENSION);
        writeAtomically(file, encode(template));
    }

    public static SelectionTemplate loadServerTemplate(MinecraftServer server, String rawName) throws IOException {
        String name = validateName(rawName);
        Path file = serverTemplateFolder(server).resolve(StoragePaths.sanitizeFileName(name) + FILE_EXTENSION);
        if (!Files.isRegularFile(file)) throw new IOException("Server template not found: " + name);
        if (Files.size(file) > MAX_TRANSFER_BYTES) throw new IOException("Template exceeds the 8 MiB transfer limit.");
        return decode(Files.readAllBytes(file));
    }

    private static void writeAtomically(Path file, byte[] data) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(temporary, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static List<String> listServerTemplates(MinecraftServer server) {
        Path folder = serverTemplateFolder(server);
        if (!Files.isDirectory(folder)) return List.of();
        try (var stream = Files.list(folder)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(FILE_EXTENSION))
                    .map(name -> name.substring(0, name.length() - FILE_EXTENSION.length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(256)
                    .toList();
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.warn("Could not list server region-selection templates.", exception);
            return List.of();
        }
    }

    public static byte[] encode(SelectionTemplate template) throws IOException {
        if (template == null) throw new IOException("No template data was supplied.");
        JsonObject root = toJson(template);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output);
             Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length > MAX_TRANSFER_BYTES) throw new IOException("Template exceeds the 8 MiB transfer limit.");
        return bytes;
    }

    public static SelectionTemplate decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("Template file is empty.");
        if (bytes.length > MAX_TRANSFER_BYTES) throw new IOException("Template exceeds the 8 MiB transfer limit.");
        JsonObject root;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream decompressed = new ByteArrayOutputStream(Math.min(bytes.length * 4, 1_048_576))) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = gzip.read(buffer)) != -1) {
                if (read == 0) continue;
                total += read;
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw new IOException("Template expands beyond the 64 MiB safety limit.");
                }
                decompressed.write(buffer, 0, read);
            }
            try (Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(decompressed.toByteArray()), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Template data is invalid.", exception);
        }
        try {
            return fromJson(root);
        } catch (RuntimeException exception) {
            throw new IOException("Template data is invalid: " + exception.getMessage(), exception);
        }
    }

    public static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!name.matches("[A-Za-z0-9._-]{1,64}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Use 1-64 letters, numbers, dots, underscores or dashes for the template name.");
        }
        return name;
    }

    private static Path serverTemplateFolder(MinecraftServer server) {
        return StoragePaths.regionSelectionTemplates(StoragePaths.root(server));
    }

    private static int destinationMaximum(int origin, int size, String axis) {
        long maximum = (long) origin + size - 1L;
        if (maximum < Integer.MIN_VALUE || maximum > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The template would exceed the supported " + axis + " coordinate range.");
        }
        return (int) maximum;
    }

    private static long validatedTemplateVolume(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Template dimensions must be positive.");
        }
        try {
            long volume = Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
            validateVolume(volume);
            return volume;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Template dimensions are too large.");
        }
    }

    private static void validateVolume(long volume) {
        if (volume <= 0L || volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Selection is too large: " + volume + " blocks. Limit: " + MAX_VOLUME + ".");
        }
    }

    private static Set<String> operationLocks(ServerLevel level, Bounds bounds) {
        Set<String> locks = new HashSet<>();
        locks.add(SsuJobLocks.cuboid(level.dimension(), bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        for (Region region : SimpleServerUtilities.REGIONS.getIntersecting2D(
                level.dimension(), bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ())) {
            if (bounds.minY() <= region.getMaxY() && bounds.maxY() >= region.getMinY()) {
                locks.add(SsuJobLocks.region(region.getDimension(), region.getName()));
            }
        }
        return Set.copyOf(locks);
    }

    private static JsonObject toJson(SelectionTemplate template) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("sizeX", template.sizeX());
        root.addProperty("sizeY", template.sizeY());
        root.addProperty("sizeZ", template.sizeZ());
        JsonArray palette = new JsonArray();
        template.palette().forEach(value -> palette.add(JsonParser.parseString(value)));
        root.add("palette", palette);
        JsonArray blocks = new JsonArray();
        for (TemplateBlock block : template.blocks()) {
            JsonArray entry = new JsonArray();
            entry.add(block.relativeIndex());
            entry.add(block.paletteIndex());
            blocks.add(entry);
        }
        root.add("blocks", blocks);
        return root;
    }

    private static SelectionTemplate fromJson(JsonObject root) throws IOException {
        int version = root.has("version") ? root.get("version").getAsInt() : 0;
        if (version != FORMAT_VERSION) throw new IOException("Unsupported selection-template version: " + version);
        int sizeX = root.get("sizeX").getAsInt();
        int sizeY = root.get("sizeY").getAsInt();
        int sizeZ = root.get("sizeZ").getAsInt();
        long volume = validatedTemplateVolume(sizeX, sizeY, sizeZ);
        List<String> palette = new ArrayList<>();
        JsonArray paletteJson = root.getAsJsonArray("palette");
        if (paletteJson == null || paletteJson.size() > 65_536) throw new IOException("Template palette is invalid.");
        for (JsonElement element : paletteJson) {
            JsonObject stateJson = element.getAsJsonObject();
            blockStateFromJson(stateJson); // Validate before accepting the file.
            palette.add(GSON.toJson(stateJson));
        }
        List<TemplateBlock> blocks = new ArrayList<>();
        Set<Integer> occupied = new HashSet<>();
        JsonArray blocksJson = root.getAsJsonArray("blocks");
        if (blocksJson != null) {
            if (blocksJson.size() > volume) throw new IOException("Template contains more blocks than its bounds.");
            for (JsonElement element : blocksJson) {
                JsonArray entry = element.getAsJsonArray();
                if (entry.size() < 2) continue;
                int relativeIndex = entry.get(0).getAsInt();
                int paletteIndex = entry.get(1).getAsInt();
                if (relativeIndex < 0 || relativeIndex >= volume || paletteIndex < 0 || paletteIndex >= palette.size()) {
                    throw new IOException("Template block index is invalid.");
                }
                if (!occupied.add(relativeIndex)) throw new IOException("Template contains a duplicate block position.");
                // Legacy third entries containing block-entity SNBT are intentionally ignored.
                blocks.add(new TemplateBlock(relativeIndex, paletteIndex));
            }
        }
        blocks.sort(Comparator.comparingInt(TemplateBlock::relativeIndex));
        return new SelectionTemplate(sizeX, sizeY, sizeZ, List.copyOf(palette), List.copyOf(blocks));
    }

    private static String blockStateJson(BlockState state) {
        JsonObject json = new JsonObject();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        json.addProperty("block", blockId.toString());
        if (!state.getProperties().isEmpty()) {
            JsonObject properties = new JsonObject();
            for (Property<?> property : state.getProperties()) {
                properties.addProperty(property.getName(), propertyValueName(state, property));
            }
            json.add("properties", properties);
        }
        return GSON.toJson(json);
    }

    private static BlockState blockStateFromJsonString(String json) {
        return blockStateFromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    private static BlockState blockStateFromJson(JsonObject json) {
        Identifier blockId = Identifier.parse(json.get("block").getAsString());
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElseThrow(
                () -> new IllegalArgumentException("Unknown block in template: " + blockId));
        BlockState state = block.defaultBlockState();
        if (!json.has("properties")) return state;
        JsonObject properties = json.getAsJsonObject("properties");
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                throw new IllegalArgumentException("Unknown block-state property '" + entry.getKey() + "' for " + blockId + ".");
            }
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
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Invalid value '" + valueName + "' for block-state property '" + property.getName() + "'.");
        }
        return state.setValue(property, (Comparable) value.get());
    }

    private static void clearBlockWithoutDrops(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            blockEntity.setChanged();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public long volume() {
            long sizeX = (long) maxX - minX + 1L;
            long sizeY = (long) maxY - minY + 1L;
            long sizeZ = (long) maxZ - minZ + 1L;
            if (sizeX <= 0L || sizeY <= 0L || sizeZ <= 0L) return Long.MAX_VALUE;
            try {
                return Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
    }

    public record TemplateBlock(int relativeIndex, int paletteIndex) {
    }

    public record SelectionTemplate(int sizeX, int sizeY, int sizeZ, List<String> palette, List<TemplateBlock> blocks) {
        public SelectionTemplate {
            validatedTemplateVolume(sizeX, sizeY, sizeZ);
            palette = palette == null ? List.of() : List.copyOf(palette);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
        public long volume() { return (long) sizeX * sizeY * sizeZ; }
    }

    public static final class CaptureJob implements SsuJob {
        private final ServerLevel level;
        private final Bounds bounds;
        private final Set<String> locks;
        private final Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
        private final List<String> palette = new ArrayList<>();
        private final List<TemplateBlock> blocks = new ArrayList<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int x;
        private int y;
        private int z;
        private long visited;
        private boolean complete;
        private SelectionTemplate template;

        private CaptureJob(ServerLevel level, Bounds bounds, Set<String> locks) {
            this.level = level;
            this.bounds = bounds;
            this.locks = locks;
            this.x = bounds.minX();
            this.y = bounds.minY();
            this.z = bounds.minZ();
        }

        @Override public String description() { return "Copy region selection (" + bounds.volume() + " blocks)"; }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) {
            int used = 0;
            while (!complete && used < operationBudget) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (!state.isAir()) {
                    String stateJson = blockStateJson(state);
                    int paletteIndex = paletteIndexes.computeIfAbsent(stateJson, ignored -> {
                        palette.add(stateJson);
                        return palette.size() - 1;
                    });
                    int relative = relativeIndex(bounds, x, y, z);
                    blocks.add(new TemplateBlock(relative, paletteIndex));
                }
                visited++;
                used++;
                advance();
            }
            return used;
        }

        private void advance() {
            z++;
            if (z <= bounds.maxZ()) return;
            z = bounds.minZ();
            y++;
            if (y <= bounds.maxY()) return;
            y = bounds.minY();
            x++;
            if (x > bounds.maxX()) {
                complete = true;
                template = new SelectionTemplate(
                        bounds.maxX() - bounds.minX() + 1,
                        bounds.maxY() - bounds.minY() + 1,
                        bounds.maxZ() - bounds.minZ() + 1,
                        List.copyOf(palette), List.copyOf(blocks));
            }
        }

        @Override public String ownerModule() { return "regions"; }
        @Override public Set<String> resourceLocks() { return locks; }
        @Override public boolean isComplete() { return complete; }
        @Override public double progress() { return Math.min(1.0D, visited / (double) bounds.volume()); }
        public SelectionTemplate template() { return template; }
        public int savedBlocks() { return blocks.size(); }
    }

    public static final class PasteJob implements SsuJob {
        private enum Phase { CLEAR, PLACE, COMPLETE }
        private final ServerLevel level;
        private final Bounds clearBounds;
        private final Bounds sourceBounds;
        private final Bounds destination;
        private final SelectionTemplate template;
        private final Set<String> locks;
        private final List<BlockState> palette;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int x;
        private int y;
        private int z;
        private int blockIndex;
        private long processed;
        private long changed;
        private Phase phase = Phase.CLEAR;

        private PasteJob(ServerLevel level, Bounds clearBounds, Bounds sourceBounds, Bounds destination,
                         SelectionTemplate template, Set<String> locks) {
            this.level = level;
            this.clearBounds = clearBounds;
            this.sourceBounds = sourceBounds;
            this.destination = destination;
            this.template = template;
            this.locks = locks;
            this.palette = template.palette().stream().map(RegionSelectionSchematicManager::blockStateFromJsonString).toList();
            this.x = clearBounds.minX();
            this.y = clearBounds.minY();
            this.z = clearBounds.minZ();
        }

        @Override public String description() { return "Paste region selection (" + template.volume() + " blocks)"; }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) {
            int used = 0;
            while (phase != Phase.COMPLETE && used < operationBudget) {
                if (phase == Phase.CLEAR) {
                    cursor.set(x, y, z);
                    boolean shouldClear = sourceBounds == null
                            || contains(sourceBounds, x, y, z)
                            || contains(destination, x, y, z);
                    if (shouldClear && !level.isEmptyBlock(cursor)) {
                        clearBlockWithoutDrops(level, cursor);
                        changed++;
                    }
                    processed++;
                    used++;
                    advanceClear();
                    continue;
                }
                TemplateBlock block = template.blocks().get(blockIndex++);
                Decoded relative = decodeRelative(template, block.relativeIndex());
                cursor.set(destination.minX() + relative.x(), destination.minY() + relative.y(), destination.minZ() + relative.z());
                BlockState state = palette.get(block.paletteIndex());
                level.setBlock(cursor, state, 3);
                changed++;
                processed++;
                used++;
                if (blockIndex >= template.blocks().size()) phase = Phase.COMPLETE;
            }
            return used;
        }

        private void advanceClear() {
            z++;
            if (z <= clearBounds.maxZ()) return;
            z = clearBounds.minZ();
            y++;
            if (y <= clearBounds.maxY()) return;
            y = clearBounds.minY();
            x++;
            if (x > clearBounds.maxX()) {
                phase = template.blocks().isEmpty() ? Phase.COMPLETE : Phase.PLACE;
            }
        }

        @Override public String ownerModule() { return "regions"; }
        @Override public Set<String> resourceLocks() { return locks; }
        @Override public boolean isComplete() { return phase == Phase.COMPLETE; }
        @Override public double progress() {
            long total = clearBounds.volume() + template.blocks().size();
            return total == 0L ? 1.0D : Math.min(1.0D, processed / (double) total);
        }
        public long changedBlocks() { return changed; }
        public Bounds destination() { return destination; }
    }

    private static boolean contains(Bounds bounds, int x, int y, int z) {
        return bounds != null
                && x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }

    private static int relativeIndex(Bounds bounds, int x, int y, int z) {
        int sizeY = bounds.maxY() - bounds.minY() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        return ((x - bounds.minX()) * sizeY + (y - bounds.minY())) * sizeZ + (z - bounds.minZ());
    }

    private static Decoded decodeRelative(SelectionTemplate template, int index) {
        int sizeYZ = template.sizeY() * template.sizeZ();
        int x = index / sizeYZ;
        int remainder = index % sizeYZ;
        int y = remainder / template.sizeZ();
        int z = remainder % template.sizeZ();
        return new Decoded(x, y, z);
    }

    private record Decoded(int x, int y, int z) {
    }
}
