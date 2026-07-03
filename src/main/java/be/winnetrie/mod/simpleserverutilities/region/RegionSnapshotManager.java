package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;

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
import net.minecraft.world.level.storage.LevelResource;

public class RegionSnapshotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path snapshotFolder;

    public void load(MinecraftServer server) {
        this.snapshotFolder = server.getWorldPath(LevelResource.ROOT)
                .resolve("simpleserverutilities")
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

        Files.writeString(getSnapshotPath(region.getName()), GSON.toJson(root));

        return savedBlocks;
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
        return snapshotFolder.resolve(normalizeName(regionName) + ".json");
    }

    private String normalizeName(String regionName) {
        return regionName.toLowerCase(Locale.ROOT);
    }
}