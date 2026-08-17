package be.winnetrie.mod.simpleserverutilities.dimension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Creates and maintains a world-local datapack containing admin-defined dimensions.
 * Registry-backed dimensions are loaded during world startup, so edits intentionally
 * become active after the next full server restart.
 */
public final class ManagedDimensionManager {
    public static final int SCHEMA_VERSION = 1;
    public static final int DATA_PACK_MAJOR = 107;
    public static final int DATA_PACK_MINOR = 1;
    public static final String PACK_FOLDER = "ssu_managed_dimensions";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, ManagedDimensionDefinition> definitions = new LinkedHashMap<>();
    private MinecraftServer server;
    private Path rootFolder;
    private Path definitionFolder;
    private Path datapackFolder;
    private boolean restartRequired;
    private long revision;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        rootFolder = StoragePaths.root(server);
        definitionFolder = StoragePaths.dimensionDefinitions(rootFolder);
        datapackFolder = server.getWorldPath(LevelResource.ROOT).resolve("datapacks").resolve(PACK_FOLDER);
        definitions.clear();
        restartRequired = false;
        revision = 0L;
        try {
            Files.createDirectories(definitionFolder);
            for (Path file : JsonStorage.listJsonFiles(definitionFolder)) {
                try {
                    ManagedDimensionDefinition definition = JsonStorage.read(GSON, file, ManagedDimensionDefinition.class);
                    if (definition == null) continue;
                    definition.normalize();
                    definitions.put(definition.id, definition);
                } catch (Exception exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load managed dimension. Broken file archived as {}", archived, exception);
                }
            }
            restartRequired = synchronizeDatapack();
            initializeEmptyPlatforms();
            SimpleServerUtilities.LOGGER.info("Loaded {} managed dimensions{}.", definitions.size(),
                    restartRequired ? " (restart required to apply generated datapack changes)" : "");
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU managed dimensions.", exception);
        }
    }

    public synchronized void clear() {
        definitions.clear();
        server = null;
        rootFolder = null;
        definitionFolder = null;
        datapackFolder = null;
        restartRequired = false;
        revision = 0L;
    }

    public synchronized List<ManagedDimensionDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(
                                (ManagedDimensionDefinition value) -> value.displayName,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(value -> value.id))
                .map(ManagedDimensionManager::copy)
                .toList();
    }

    public synchronized Optional<ManagedDimensionDefinition> find(String rawId) {
        String id = normalizeManagedId(rawId);
        ManagedDimensionDefinition definition = definitions.get(id);
        return Optional.ofNullable(definition == null ? null : copy(definition));
    }

    public synchronized ManagedDimensionDefinition create(ManagedDimensionDefinition submitted) {
        if (submitted == null) throw new IllegalArgumentException("Dimension definition is missing.");
        submitted.normalize();
        if (definitions.containsKey(submitted.id)) {
            throw new IllegalArgumentException("A managed dimension with that ID already exists.");
        }
        ResourceKey<Level> resourceKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(submitted.resourceId()));
        if (server != null && server.getLevel(resourceKey) != null) {
            throw new IllegalArgumentException("That dimension ID is already loaded by Minecraft or another datapack.");
        }
        definitions.put(submitted.id, copy(submitted));
        persistAndGenerate();
        return copy(submitted);
    }

    public synchronized ManagedDimensionDefinition save(String originalId, ManagedDimensionDefinition submitted) {
        if (submitted == null) throw new IllegalArgumentException("Dimension definition is missing.");
        submitted.normalize();
        String original = normalizeManagedId(originalId);
        if (!original.isBlank() && !original.equals(submitted.id)) {
            throw new IllegalArgumentException("A dimension ID cannot be renamed after creation. Create a new dimension instead.");
        }
        ManagedDimensionDefinition old = definitions.get(submitted.id);
        if (original.isBlank() && old != null) {
            throw new IllegalArgumentException("A managed dimension with that ID already exists.");
        }
        if (old == null && !original.isBlank()) {
            throw new IllegalArgumentException("Managed dimension not found.");
        }
        if (old != null && platformSettingsChanged(old, submitted)) {
            submitted.platformInitialized = false;
        }
        definitions.put(submitted.id, copy(submitted));
        persistAndGenerate();
        return copy(submitted);
    }

    public synchronized boolean delete(String rawId) {
        String id = normalizeManagedId(rawId);
        if (definitions.remove(id) == null) return false;
        persistAndGenerate();
        if (SsuModuleAccess.active("permissions")) {
            SimpleServerUtilities.PERMISSIONS.removeDimensionOverrides("simpleserverutilities:" + id);
        }
        return true;
    }

    public synchronized boolean restartRequired() {
        return restartRequired;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized String displayName(String dimensionId) {
        if (dimensionId == null) return "";
        String normalized = dimensionId.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(Level.OVERWORLD.location().toString())) return "Overworld";
        if (normalized.equals(Level.NETHER.location().toString())) return "Nether";
        if (normalized.equals(Level.END.location().toString())) return "The End";
        if (normalized.startsWith("simpleserverutilities:")) {
            ManagedDimensionDefinition definition = definitions.get(normalized.substring("simpleserverutilities:".length()));
            if (definition != null) return definition.displayName;
        }
        return normalized;
    }

    public synchronized List<DimensionInfo> dimensionInfos() {
        Map<String, DimensionInfo> result = new HashMap<>();
        result.put(Level.OVERWORLD.location().toString(), new DimensionInfo(
                Level.OVERWORLD.location().toString(), "Overworld", "VANILLA", true, true, false));
        result.put(Level.NETHER.location().toString(), new DimensionInfo(
                Level.NETHER.location().toString(), "Nether", "VANILLA", true, true, false));
        result.put(Level.END.location().toString(), new DimensionInfo(
                Level.END.location().toString(), "The End", "VANILLA", true, true, false));
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                String id = level.dimension().location().toString();
                result.putIfAbsent(id, new DimensionInfo(id, displayName(id), "EXTERNAL", true, false, false));
            }
        }
        for (ManagedDimensionDefinition definition : definitions.values()) {
            String id = definition.resourceId();
            boolean loaded = server != null && server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id))) != null;
            result.put(id, new DimensionInfo(id, definition.displayName, definition.presetValue().name(),
                    loaded, false, true));
        }
        return result.values().stream()
                .sorted(Comparator.comparing(DimensionInfo::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(DimensionInfo::id))
                .toList();
    }

    private void persistAndGenerate() {
        try {
            saveDefinitions();
            restartRequired |= synchronizeDatapack();
            revision++;
            if (SsuModuleAccess.active("permissions")) {
                SimpleServerUtilities.PERMISSIONS.invalidateResolutionCache();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save managed dimensions.", exception);
        }
    }

    private void saveDefinitions() throws IOException {
        Files.createDirectories(definitionFolder);
        Set<Path> keep = new HashSet<>();
        for (ManagedDimensionDefinition definition : definitions.values()) {
            definition.normalize();
            Path file = definitionFolder.resolve(definition.id.replace('/', '_') + ".json");
            writeAtomic(file, GSON.toJson(definition));
            keep.add(file.toAbsolutePath().normalize());
        }
        for (Path file : JsonStorage.listJsonFiles(definitionFolder)) {
            if (!keep.contains(file.toAbsolutePath().normalize())) Files.deleteIfExists(file);
        }
    }

    private boolean synchronizeDatapack() throws IOException {
        Map<Path, String> wanted = generatedFiles();
        boolean changed = false;
        Files.createDirectories(datapackFolder);
        for (Map.Entry<Path, String> entry : wanted.entrySet()) {
            Path file = entry.getKey();
            String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
            if (!entry.getValue().equals(existing)) {
                writeAtomic(file, entry.getValue());
                changed = true;
            }
        }
        Path dataRoot = datapackFolder.resolve("data").resolve("simpleserverutilities");
        for (String registryFolder : List.of("dimension", "dimension_type")) {
            Path folder = dataRoot.resolve(registryFolder);
            if (!Files.isDirectory(folder)) continue;
            try (var stream = Files.walk(folder)) {
                for (Path file : stream.filter(path -> path.toString().endsWith(".json")).toList()) {
                    if (!wanted.containsKey(file)) {
                        Files.deleteIfExists(file);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private Map<Path, String> generatedFiles() {
        Map<Path, String> files = new LinkedHashMap<>();
        JsonObject pack = new JsonObject();
        JsonObject packBody = new JsonObject();
        JsonArray min = new JsonArray();
        min.add(DATA_PACK_MAJOR);
        min.add(DATA_PACK_MINOR);
        packBody.add("min_format", min);
        JsonArray max = new JsonArray();
        max.add(DATA_PACK_MAJOR);
        max.add(DATA_PACK_MINOR);
        packBody.add("max_format", max);
        packBody.addProperty("description", "Dimensions managed by Simple Server Utilities");
        pack.add("pack", packBody);
        files.put(datapackFolder.resolve("pack.mcmeta"), GSON.toJson(pack));

        for (ManagedDimensionDefinition definition : definitions.values()) {
            if (!definition.enabled) continue;
            Path typeFile = datapackFolder.resolve("data/simpleserverutilities/dimension_type")
                    .resolve(definition.id + ".json");
            Path dimensionFile = datapackFolder.resolve("data/simpleserverutilities/dimension")
                    .resolve(definition.id + ".json");
            files.put(typeFile, GSON.toJson(dimensionTypeJson(definition)));
            files.put(dimensionFile, GSON.toJson(dimensionJson(definition)));
        }
        return files;
    }

    private JsonObject dimensionTypeJson(ManagedDimensionDefinition value) {
        JsonObject json = new JsonObject();
        String profile = dimensionVisualProfile(value);
        boolean fixed = value.fixedTime >= 0L || profile.equals("nether") || profile.equals("end");

        json.addProperty("ambient_light", value.ambientLight);
        json.add("attributes", dimensionAttributes(value, profile));
        json.addProperty("coordinate_scale", value.coordinateScale);
        if (profile.equals("nether")) json.addProperty("cardinal_light", "nether");
        if (!profile.equals("overworld")) json.addProperty("skybox", profile.equals("end") ? "end" : "none");
        if (profile.equals("overworld")) json.addProperty("default_clock", "minecraft:overworld");
        if (profile.equals("end")) json.addProperty("default_clock", "minecraft:the_end");
        json.addProperty("has_ceiling", value.hasCeiling);
        json.addProperty("has_ender_dragon_fight", value.presetValue() == DimensionPreset.END);
        json.addProperty("has_fixed_time", fixed);
        json.addProperty("has_skylight", value.hasSkylight);
        json.addProperty("height", value.height);
        json.addProperty("infiniburn", value.infiniburn);
        json.addProperty("logical_height", value.logicalHeight);
        json.addProperty("min_y", value.minY);
        json.addProperty("monster_spawn_block_light_limit", value.monsterSpawnBlockLightLimit);
        if (profile.equals("overworld")) {
            JsonObject light = new JsonObject();
            light.addProperty("type", "minecraft:uniform");
            light.addProperty("min_inclusive", 0);
            light.addProperty("max_inclusive", value.monsterSpawnLightLevel);
            json.add("monster_spawn_light_level", light);
        } else {
            json.addProperty("monster_spawn_light_level", value.monsterSpawnLightLevel);
        }
        if (!fixed) {
            json.addProperty("timelines", "#minecraft:in_overworld");
        } else if (profile.equals("nether") && value.fixedTime < 0L) {
            json.addProperty("timelines", "#minecraft:in_nether");
        } else if (profile.equals("end") && value.fixedTime < 0L) {
            json.addProperty("timelines", "#minecraft:in_end");
        }
        return json;
    }

    private JsonObject dimensionAttributes(ManagedDimensionDefinition value, String profile) {
        JsonObject attributes = new JsonObject();

        JsonObject bedRule = new JsonObject();
        if (value.bedWorks) {
            bedRule.addProperty("can_set_spawn", "always");
            bedRule.addProperty("can_sleep", "when_dark");
            JsonObject error = new JsonObject();
            error.addProperty("translate", "block.minecraft.bed.no_sleep");
            bedRule.add("error_message", error);
        } else {
            bedRule.addProperty("can_set_spawn", "never");
            bedRule.addProperty("can_sleep", "never");
            bedRule.addProperty("explodes", true);
        }
        attributes.add("minecraft:gameplay/bed_rule", bedRule);
        attributes.addProperty("minecraft:gameplay/can_start_raid", value.hasRaids);
        attributes.addProperty("minecraft:gameplay/fast_lava", value.ultrawarm);
        attributes.addProperty("minecraft:gameplay/nether_portal_spawns_piglin", value.natural);
        attributes.addProperty("minecraft:gameplay/piglins_zombify", !value.piglinSafe);
        attributes.addProperty("minecraft:gameplay/respawn_anchor_works", value.respawnAnchorWorks);
        attributes.addProperty("minecraft:gameplay/water_evaporates", value.ultrawarm);

        if (value.ultrawarm) {
            attributes.addProperty("minecraft:gameplay/snow_golem_melts", true);
            JsonObject drip = new JsonObject();
            drip.addProperty("type", "minecraft:dripping_dripstone_lava");
            attributes.add("minecraft:visual/default_dripstone_particle", drip);
        }

        switch (profile) {
            case "nether" -> {
                attributes.addProperty("minecraft:gameplay/sky_light_level", 4.0F);
                attributes.addProperty("minecraft:visual/ambient_light_color", "#302821");
                attributes.addProperty("minecraft:visual/fog_end_distance", 96.0F);
                attributes.addProperty("minecraft:visual/fog_start_distance", 10.0F);
                attributes.addProperty("minecraft:visual/sky_light_color", "#7a7aff");
                attributes.addProperty("minecraft:visual/sky_light_factor", 0.0F);
            }
            case "end" -> {
                attributes.addProperty("minecraft:visual/ambient_light_color", "#3f473f");
                attributes.addProperty("minecraft:visual/fog_color", "#181318");
                attributes.addProperty("minecraft:visual/sky_color", "#000000");
                attributes.addProperty("minecraft:visual/sky_light_color", "#ac60cd");
                attributes.addProperty("minecraft:visual/sky_light_factor", 0.0F);
            }
            default -> {
                attributes.addProperty("minecraft:visual/ambient_light_color", "#0a0a0a");
                attributes.addProperty("minecraft:visual/cloud_color", "#ccffffff");
                attributes.addProperty("minecraft:visual/cloud_height", 192.33F);
                attributes.addProperty("minecraft:visual/fog_color", "#c0d8ff");
                attributes.addProperty("minecraft:visual/sky_color", "#78a7ff");
            }
        }

        if (value.fixedTime >= 0L) {
            float sunAngle = fixedTimeAngle(value.fixedTime);
            attributes.addProperty("minecraft:visual/sun_angle", sunAngle);
            attributes.addProperty("minecraft:visual/moon_angle", (sunAngle + 180.0F) % 360.0F);
            attributes.addProperty("minecraft:visual/star_angle", sunAngle);
            attributes.addProperty("minecraft:visual/star_brightness", fixedStarBrightness(value.fixedTime));
        }
        return attributes;
    }

    private static String dimensionVisualProfile(ManagedDimensionDefinition value) {
        String effects = value.effects == null ? "" : value.effects.trim().toLowerCase(Locale.ROOT);
        if (effects.endsWith("the_nether") || value.presetValue() == DimensionPreset.NETHER) return "nether";
        if (effects.endsWith("the_end") || value.presetValue() == DimensionPreset.END) return "end";
        return "overworld";
    }

    private static float fixedTimeAngle(long time) {
        long normalized = Math.floorMod(time, 24000L);
        return (float) (Math.floorMod(normalized - 6000L, 24000L) * 360.0D / 24000.0D);
    }

    private static float fixedStarBrightness(long time) {
        double angle = Math.floorMod(time - 6000L, 24000L) / 24000.0D;
        double daylight = Math.cos(angle * Math.PI * 2.0D);
        return (float) Math.max(0.0D, Math.min(1.0D, (-daylight - 0.2D) / 0.8D));
    }

    private JsonObject dimensionJson(ManagedDimensionDefinition value) {
        JsonObject json = new JsonObject();
        json.addProperty("type", value.resourceId());
        JsonObject generator = new JsonObject();
        switch (value.presetValue()) {
            case FLAT, EMPTY -> {
                generator.addProperty("type", "minecraft:flat");
                JsonObject settings = new JsonObject();
                settings.addProperty("biome", value.biome);
                settings.addProperty("features", value.generateFeatures);
                settings.addProperty("lakes", value.generateLakes);
                JsonArray layers = new JsonArray();
                addLayer(layers, value.bottomBlock, value.bottomLayers);
                addLayer(layers, value.middleBlock, value.middleLayers);
                addLayer(layers, value.topBlock, value.topLayers);
                settings.add("layers", layers);
                generator.add("settings", settings);
            }
            case END -> {
                generator.addProperty("type", "minecraft:noise");
                JsonObject biomeSource = new JsonObject();
                biomeSource.addProperty("type", "minecraft:the_end");
                generator.add("biome_source", biomeSource);
                generator.addProperty("settings", "minecraft:end");
            }
            case NETHER -> {
                generator.addProperty("type", "minecraft:noise");
                JsonObject biomeSource = new JsonObject();
                biomeSource.addProperty("type", "minecraft:multi_noise");
                biomeSource.addProperty("preset", "minecraft:nether");
                generator.add("biome_source", biomeSource);
                generator.addProperty("settings", "minecraft:nether");
            }
            case OVERWORLD -> {
                generator.addProperty("type", "minecraft:noise");
                JsonObject biomeSource = new JsonObject();
                biomeSource.addProperty("type", "minecraft:multi_noise");
                biomeSource.addProperty("preset", "minecraft:overworld");
                generator.add("biome_source", biomeSource);
                generator.addProperty("settings", "minecraft:overworld");
            }
        }
        json.add("generator", generator);
        return json;
    }

    private static void addLayer(JsonArray layers, String block, int height) {
        if (height <= 0) return;
        JsonObject layer = new JsonObject();
        layer.addProperty("block", block);
        layer.addProperty("height", height);
        layers.add(layer);
    }

    private void initializeEmptyPlatforms() {
        if (server == null) return;
        boolean changed = false;
        for (ManagedDimensionDefinition definition : definitions.values()) {
            if (!definition.enabled || !definition.isEmptyPreset() || definition.platformInitialized) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(definition.resourceId())));
            if (level == null) continue;
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(definition.platformBlock));
            if (block.isEmpty()) {
                SimpleServerUtilities.LOGGER.warn("Cannot initialize platform for {}: unknown block {}",
                        definition.resourceId(), definition.platformBlock);
                continue;
            }
            BlockState state = block.get().defaultBlockState();
            int radius = definition.platformSize / 2;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, definition.platformY, z), state);
                }
            }
            definition.platformInitialized = true;
            changed = true;
            SimpleServerUtilities.LOGGER.info("Initialized {}x{} platform in {} at Y {}.",
                    definition.platformSize, definition.platformSize, definition.resourceId(), definition.platformY);
        }
        if (changed) {
            try {
                saveDefinitions();
            } catch (IOException exception) {
                SimpleServerUtilities.LOGGER.error("Failed to persist empty-dimension platform initialization.", exception);
            }
        }
    }

    private static boolean platformSettingsChanged(ManagedDimensionDefinition oldValue, ManagedDimensionDefinition newValue) {
        return oldValue.presetValue() != newValue.presetValue()
                || !oldValue.platformBlock.equals(newValue.platformBlock)
                || oldValue.platformSize != newValue.platformSize
                || oldValue.platformY != newValue.platformY;
    }

    private static ManagedDimensionDefinition copy(ManagedDimensionDefinition source) {
        return GSON.fromJson(GSON.toJson(source), ManagedDimensionDefinition.class);
    }

    private static String normalizeManagedId(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon >= 0) value = value.substring(colon + 1);
        return value;
    }

    private static void writeAtomic(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + Instant.now().toEpochMilli());
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record DimensionInfo(
            String id,
            String displayName,
            String preset,
            boolean loaded,
            boolean vanilla,
            boolean managed
    ) {
    }
}
