package be.winnetrie.mod.simpleserverutilities.utilitymining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Persists log/leaf positions placed by players after this feature is installed.
 * World generation never enters this store, so these positions can be excluded
 * from natural-tree detection without relying only on shape heuristics.
 */
public final class PlacedTreeBlockTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Set<Long>> placed = new HashMap<>();
    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private Path saveFile;

    public synchronized void load(MinecraftServer server) {
        placed.clear();
        recordStore.reset();
        saveFile = StoragePaths.utilityMining(StoragePaths.root(server)).resolve("player_placed_tree_blocks.json");
        try {
            Files.createDirectories(saveFile.getParent());
            if (!Files.exists(saveFile)) {
                save();
                return;
            }
            recordStore.discoverFile(saveFile);
            SaveData data = JsonStorage.read(GSON, saveFile, SaveData.class);
            if (data == null || data.dimensions == null) return;
            for (DimensionData dimension : data.dimensions) {
                if (dimension == null || dimension.dimension == null || dimension.positions == null) continue;
                placed.put(dimension.dimension, new HashSet<>(dimension.positions));
            }
            SimpleServerUtilities.LOGGER.info("Loaded {} dimensions of player-placed tree blocks.", placed.size());
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(saveFile);
            SimpleServerUtilities.LOGGER.error("Failed to load player-placed tree block tracking. Broken file archived as {}", archived, exception);
        }
    }

    public synchronized void save() {
        if (saveFile == null) return;
        SaveData data = new SaveData();
        placed.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            DimensionData dimension = new DimensionData();
            dimension.dimension = entry.getKey();
            dimension.positions = new ArrayList<>(entry.getValue());
            dimension.positions.sort(Long::compareTo);
            data.dimensions.add(dimension);
        });
        recordStore.queueJson(GSON, saveFile, data);
    }

    public synchronized void clear() {
        placed.clear();
        recordStore.reset();
        saveFile = null;
    }

    public synchronized void markPlaced(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        placed.computeIfAbsent(dimension(level), ignored -> new HashSet<>()).add(pos.asLong());
        save();
    }

    public synchronized void forget(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        Set<Long> values = placed.get(dimension(level));
        if (values == null || !values.remove(pos.asLong())) return;
        if (values.isEmpty()) placed.remove(dimension(level));
        save();
    }

    public synchronized boolean isPlayerPlaced(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        Set<Long> values = placed.get(dimension(level));
        return values != null && values.contains(pos.asLong());
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static final class SaveData {
        private int schemaVersion = 1;
        private List<DimensionData> dimensions = new ArrayList<>();
    }

    private static final class DimensionData {
        private String dimension = "minecraft:overworld";
        private List<Long> positions = new ArrayList<>();
    }
}
