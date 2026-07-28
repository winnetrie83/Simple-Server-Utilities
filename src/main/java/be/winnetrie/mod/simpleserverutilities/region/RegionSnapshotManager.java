package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import be.winnetrie.mod.simpleserverutilities.core.job.SsuJob;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;

public class RegionSnapshotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path snapshotFolder;

    public void load(MinecraftServer server) {
        this.snapshotFolder = StoragePaths.root(server)
                .resolve("region_snapshots");
    }

    public boolean hasSnapshot(String regionName) {
        if (snapshotFolder == null) {
            return false;
        }

        return Files.exists(getSnapshotPath(regionName));
    }

    public int save(ServerLevel level, Region region) throws IOException {
        ensureInitialized();

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("region", region.getName());
        root.addProperty("dimension", region.getDimension().identifier().toString());

        root.addProperty("minX", region.getMinX());
        root.addProperty("minY", region.getMinY());
        root.addProperty("minZ", region.getMinZ());
        root.addProperty("maxX", region.getMaxX());
        root.addProperty("maxY", region.getMaxY());
        root.addProperty("maxZ", region.getMaxZ());

        JsonArray blocks = new JsonArray();

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int savedBlocks = 0;

        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    mutablePos.set(x, y, z);

                    if (level.isEmptyBlock(mutablePos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(mutablePos);

                    JsonObject blockJson = blockStateToJson(state);
                    blockJson.addProperty("x", x - region.getMinX());
                    blockJson.addProperty("y", y - region.getMinY());
                    blockJson.addProperty("z", z - region.getMinZ());

                    blocks.add(blockJson);
                    savedBlocks++;
                }
            }
        }

        root.add("blocks", blocks);

        JsonStorage.write(GSON, getSnapshotPath(region.getName()), root);

        return savedBlocks;
    }

    public RegionSnapshotResetJob createResetJob(ServerLevel level, Region region) throws IOException {
        ensureInitialized();

        Path path = getSnapshotPath(region.getName());
        if (!Files.exists(path)) {
            throw new IOException("No snapshot exists for region '" + region.getName() + "'.");
        }

        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        validateSnapshot(root, region);

        List<SavedBlock> savedBlocks = new ArrayList<>();
        JsonArray blocks = root.getAsJsonArray("blocks");
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                JsonObject blockJson = blocks.get(i).getAsJsonObject();
                int x = region.getMinX() + blockJson.get("x").getAsInt();
                int y = region.getMinY() + blockJson.get("y").getAsInt();
                int z = region.getMinZ() + blockJson.get("z").getAsInt();

                if (x < region.getMinX() || x > region.getMaxX()
                        || y < region.getMinY() || y > region.getMaxY()
                        || z < region.getMinZ() || z > region.getMaxZ()) {
                    continue;
                }
                savedBlocks.add(new SavedBlock(x, y, z, blockStateFromJson(blockJson)));
            }
        }

        return new RegionSnapshotResetJob(level, region, List.copyOf(savedBlocks));
    }

    public int reset(ServerLevel level, Region region) throws IOException {
        ensureInitialized();

        Path path = getSnapshotPath(region.getName());

        if (!Files.exists(path)) {
            throw new IOException("No snapshot exists for region '" + region.getName() + "'.");
        }

        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        validateSnapshot(root, region);

        clearRegion(level, region);

        JsonArray blocks = root.getAsJsonArray("blocks");

        if (blocks == null) {
            return 0;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int restoredBlocks = 0;

        for (int i = 0; i < blocks.size(); i++) {
            JsonObject blockJson = blocks.get(i).getAsJsonObject();

            int x = region.getMinX() + blockJson.get("x").getAsInt();
            int y = region.getMinY() + blockJson.get("y").getAsInt();
            int z = region.getMinZ() + blockJson.get("z").getAsInt();

            if (x < region.getMinX() || x > region.getMaxX()
                    || y < region.getMinY() || y > region.getMaxY()
                    || z < region.getMinZ() || z > region.getMaxZ()) {
                continue;
            }

            BlockState state = blockStateFromJson(blockJson);

            mutablePos.set(x, y, z);
            level.setBlock(mutablePos, state, 3);

            restoredBlocks++;
        }

        return restoredBlocks;
    }

    private void clearRegion(ServerLevel level, Region region) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    mutablePos.set(x, y, z);

                    if (level.isEmptyBlock(mutablePos)) {
                        continue;
                    }

                    level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
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

    private BlockState blockStateFromJson(JsonObject json) {
        Identifier blockId = Identifier.parse(json.get("block").getAsString());
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(Blocks.AIR);

        BlockState state = block.defaultBlockState();

        if (!json.has("properties")) {
            return state;
        }

        JsonObject properties = json.getAsJsonObject("properties");

        for (Entry<String, com.google.gson.JsonElement> entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());

            if (property == null) {
                continue;
            }

            String valueName = entry.getValue().getAsString();
            state = applyProperty(state, property, valueName);
        }

        return state;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String getPropertyValueName(BlockState state, Property property) {
        return property.getName((Comparable) state.getValue(property));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private BlockState applyProperty(BlockState state, Property property, String valueName) {
        Optional value = property.getValue(valueName);

        if (value.isEmpty()) {
            return state;
        }

        return state.setValue(property, (Comparable) value.get());
    }

    private void validateSnapshot(JsonObject root, Region region) {
        String snapshotDimension = root.get("dimension").getAsString();
        String regionDimension = region.getDimension().identifier().toString();

        if (!snapshotDimension.equals(regionDimension)) {
            throw new IllegalStateException("Snapshot dimension does not match the current region dimension.");
        }

        if (root.get("minX").getAsInt() != region.getMinX()
                || root.get("minY").getAsInt() != region.getMinY()
                || root.get("minZ").getAsInt() != region.getMinZ()
                || root.get("maxX").getAsInt() != region.getMaxX()
                || root.get("maxY").getAsInt() != region.getMaxY()
                || root.get("maxZ").getAsInt() != region.getMaxZ()) {
            throw new IllegalStateException("Snapshot bounds do not match the current region bounds. Save the region again first.");
        }
    }

    private void ensureInitialized() throws IOException {
        if (snapshotFolder == null) {
            throw new IOException("Region snapshot manager is not initialized yet.");
        }

        Files.createDirectories(snapshotFolder);
    }

    private Path getSnapshotPath(String regionName) {
        return snapshotFolder.resolve(StoragePaths.sanitizeFileName(normalizeName(regionName)) + ".json");
    }

    private String normalizeName(String regionName) {
        return regionName.toLowerCase(Locale.ROOT);
    }
    private record SavedBlock(int x, int y, int z, BlockState state) {
    }

    public static final class RegionSnapshotResetJob implements SsuJob {
        private enum Phase {
            CLEAR,
            RESTORE,
            COMPLETE
        }

        private final ServerLevel level;
        private final Region region;
        private final List<SavedBlock> savedBlocks;
        private final long clearVolume;
        private final long totalOperations;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private Phase phase = Phase.CLEAR;
        private int x;
        private int y;
        private int z;
        private int restoreIndex;
        private long completedOperations;
        private int restoredBlocks;

        private RegionSnapshotResetJob(ServerLevel level, Region region, List<SavedBlock> savedBlocks) {
            this.level = level;
            this.region = region;
            this.savedBlocks = savedBlocks;
            this.clearVolume = region.getVolume();
            this.totalOperations = clearVolume + savedBlocks.size();
            this.x = region.getMinX();
            this.y = region.getMinY();
            this.z = region.getMinZ();
        }

        @Override
        public String description() {
            return "Reset region '" + region.getName() + "' from snapshot";
        }

        @Override
        public int runStep(MinecraftServer server, int operationBudget) {
            int used = 0;
            while (used < operationBudget && phase != Phase.COMPLETE) {
                if (phase == Phase.CLEAR) {
                    mutablePos.set(x, y, z);
                    if (!level.isEmptyBlock(mutablePos)) {
                        level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), 3);
                    }
                    completedOperations++;
                    used++;
                    advanceClear();
                    continue;
                }

                SavedBlock saved = savedBlocks.get(restoreIndex++);
                mutablePos.set(saved.x(), saved.y(), saved.z());
                level.setBlock(mutablePos, saved.state(), 3);
                restoredBlocks++;
                completedOperations++;
                used++;
                if (restoreIndex >= savedBlocks.size()) {
                    phase = Phase.COMPLETE;
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
                phase = savedBlocks.isEmpty() ? Phase.COMPLETE : Phase.RESTORE;
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
            return totalOperations == 0L ? 1.0D : Math.min(1.0D, completedOperations / (double) totalOperations);
        }

        public int restoredBlocks() {
            return restoredBlocks;
        }
    }

}