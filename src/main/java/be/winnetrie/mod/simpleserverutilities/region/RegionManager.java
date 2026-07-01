package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;


public class RegionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Region> regions = new HashMap<>();

    //private MinecraftServer server;
    private Path savePath;

    public void load(MinecraftServer server) {
        //this.server = server;
        this.savePath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("simple_server_utilities")
                .resolve("regions.json");

        regions.clear();

        if (!Files.exists(savePath)) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(savePath)).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("regions");

            if (array == null) {
                return;
            }

            for (int i = 0; i < array.size(); i++) {
                JsonObject json = array.get(i).getAsJsonObject();

                String name = json.get("name").getAsString();
                Identifier dimensionId = Identifier.parse(json.get("dimension").getAsString());
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

                BlockPos point1 = new BlockPos(
                        json.get("minX").getAsInt(),
                        json.get("minY").getAsInt(),
                        json.get("minZ").getAsInt()
                );

                BlockPos point2 = new BlockPos(
                        json.get("maxX").getAsInt(),
                        json.get("maxY").getAsInt(),
                        json.get("maxZ").getAsInt()
                );

                Region region = new Region(name, dimension, point1, point2);

                region.setPriority(getInt(json, "priority", 0));

                loadUuidSet(json, "owners", region.getOwners());
                loadUuidSet(json, "members", region.getMembers());

                if (json.has("settings")) {
                    JsonObject settings = json.getAsJsonObject("settings");

                    region.getSettings().setAllowBlockBreak(getBoolean(settings, "allowBlockBreak", false));
                    region.getSettings().setAllowBlockPlace(getBoolean(settings, "allowBlockPlace", false));
                    region.getSettings().setAllowInteract(getBoolean(settings, "allowInteract", false));
                    region.getSettings().setAllowPvp(getBoolean(settings, "allowPvp", false));
                    region.getSettings().setAllowExplosions(getBoolean(settings, "allowExplosions", false));
                    region.getSettings().setAllowPistons(getBoolean(settings, "allowPistons", false));
                    region.getSettings().setAllowWaterFlow(getBoolean(settings, "allowWaterFlow", false));
                    region.getSettings().setAllowLavaFlow(getBoolean(settings, "allowLavaFlow", false));
                    region.getSettings().setAllowRedstone(getBoolean(settings, "allowRedstone", true));
                    region.getSettings().setAllowHoppers(getBoolean(settings, "allowHoppers", false));
                    region.getSettings().setAllowFireSpread(getBoolean(settings, "allowFireSpread", false));
                }

                if (json.has("rent")) {
                    JsonObject rent = json.getAsJsonObject("rent");

                    region.getRentData().setRentable(getBoolean(rent, "rentable", false));
                    region.getRentData().setAmount(getInt(rent, "amount", 0));
                    region.getRentData().setPeriodDays(getInt(rent, "periodDays", -1));
                    region.getRentData().setRentEndTime(getLong(rent, "rentEndTime", -1L));

                    if (rent.has("renter")) {
                        region.getRentData().setRenter(UUID.fromString(rent.get("renter").getAsString()));
                    }
                }

                if (json.has("spawn")) {
                    JsonObject spawn = json.getAsJsonObject("spawn");
                    BlockPos spawnPos = new BlockPos(
                            spawn.get("x").getAsInt(),
                            spawn.get("y").getAsInt(),
                            spawn.get("z").getAsInt()
                    );

                    region.setSpawn(
                            spawnPos,
                            spawn.get("yaw").getAsFloat(),
                            spawn.get("pitch").getAsFloat()
                    );
                }

                regions.put(normalizeName(name), region);
            }

            SimpleServerUtilities.LOGGER.info("Loaded {} regions.", regions.size());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load regions.", e);
        }
    }

    public void save() {
        if (savePath == null) {
            return;
        }

        try {
            Files.createDirectories(savePath.getParent());

            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();

            for (Region region : regions.values()) {
                JsonObject json = new JsonObject();

                json.addProperty("name", region.getName());
                json.addProperty("dimension", region.getDimension().identifier().toString());
                json.addProperty("priority", region.getPriority());

                json.addProperty("minX", region.getMinX());
                json.addProperty("minY", region.getMinY());
                json.addProperty("minZ", region.getMinZ());
                json.addProperty("maxX", region.getMaxX());
                json.addProperty("maxY", region.getMaxY());
                json.addProperty("maxZ", region.getMaxZ());

                json.add("owners", saveUuidSet(region.getOwners()));
                json.add("members", saveUuidSet(region.getMembers()));

                JsonObject settings = new JsonObject();
                settings.addProperty("allowBlockBreak", region.getSettings().isAllowBlockBreak());
                settings.addProperty("allowBlockPlace", region.getSettings().isAllowBlockPlace());
                settings.addProperty("allowInteract", region.getSettings().isAllowInteract());
                settings.addProperty("allowPvp", region.getSettings().isAllowPvp());
                settings.addProperty("allowExplosions", region.getSettings().isAllowExplosions());
                settings.addProperty("allowPistons", region.getSettings().isAllowPistons());
                settings.addProperty("allowWaterFlow", region.getSettings().isAllowWaterFlow());
                settings.addProperty("allowLavaFlow", region.getSettings().isAllowLavaFlow());
                settings.addProperty("allowRedstone", region.getSettings().isAllowRedstone());
                settings.addProperty("allowHoppers", region.getSettings().isAllowHoppers());
                settings.addProperty("allowFireSpread", region.getSettings().isAllowFireSpread());
                json.add("settings", settings);

                JsonObject rent = new JsonObject();
                rent.addProperty("rentable", region.getRentData().isRentable());
                rent.addProperty("amount", region.getRentData().getAmount());
                rent.addProperty("periodDays", region.getRentData().getPeriodDays());
                rent.addProperty("rentEndTime", region.getRentData().getRentEndTime());

                if (region.getRentData().getRenter() != null) {
                    rent.addProperty("renter", region.getRentData().getRenter().toString());
                }

                json.add("rent", rent);

                if (region.getSpawnPos() != null) {
                    JsonObject spawn = new JsonObject();
                    spawn.addProperty("x", region.getSpawnPos().getX());
                    spawn.addProperty("y", region.getSpawnPos().getY());
                    spawn.addProperty("z", region.getSpawnPos().getZ());
                    spawn.addProperty("yaw", region.getSpawnYaw());
                    spawn.addProperty("pitch", region.getSpawnPitch());
                    json.add("spawn", spawn);
                }

                array.add(json);
            }

            root.add("regions", array);
            Files.writeString(savePath, GSON.toJson(root));
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save regions.", e);
        }
    }

    public RegionOperationResult create(String name, ResourceKey<Level> dimension, BlockPos point1, BlockPos point2) {
        String key = normalizeName(name);

        if (regions.containsKey(key)) {
            return RegionOperationResult.fail(
                    RegionOperationResult.Type.NAME_ALREADY_EXISTS,
                    name
            );
        }

        PlayerClaim overlapClaim = findOverlappingPlayerClaim(dimension, point1, point2);

        if (overlapClaim != null) {
            return RegionOperationResult.fail(
                    RegionOperationResult.Type.OVERLAPS_PLAYER_CLAIM,
                    describeClaim(overlapClaim)
            );
        }

        regions.put(key, new Region(name, dimension, point1, point2));
        save();
        return RegionOperationResult.success();
    }

    public boolean delete(String name) {
        String key = normalizeName(name);

        if (regions.remove(key) == null) {
            return false;
        }

        save();
        return true;
    }

    public Region get(String name) {
        return regions.get(normalizeName(name));
    }

    public Region getAt(ResourceKey<Level> dimension, BlockPos pos) {
        Region bestRegion = null;

        for (Region region : regions.values()) {
            if (!region.contains(dimension, pos)) {
                continue;
            }

            if (bestRegion == null) {
                bestRegion = region;
                continue;
            }

            if (region.getPriority() > bestRegion.getPriority()) {
                bestRegion = region;
                continue;
            }

            if (region.getPriority() == bestRegion.getPriority()
                    && region.getVolume() < bestRegion.getVolume()) {
                bestRegion = region;
            }
        }

        return bestRegion;
    }

    public Collection<Region> getAll() {
        return regions.values();
    }

    public boolean exists(String name) {
        return regions.containsKey(normalizeName(name));
    }

    private JsonArray saveUuidSet(Collection<UUID> uuids) {
        JsonArray array = new JsonArray();

        for (UUID uuid : uuids) {
            array.add(uuid.toString());
        }

        return array;
    }

    private void loadUuidSet(JsonObject json, String key, Collection<UUID> target) {
        if (!json.has(key)) {
            return;
        }

        JsonArray array = json.getAsJsonArray(key);

        for (int i = 0; i < array.size(); i++) {
            target.add(UUID.fromString(array.get(i).getAsString()));
        }
    }

    public boolean overlaps2D(ResourceKey<Level> dimension, int minX, int minZ, int maxX, int maxZ) {
        for (Region region : regions.values()) {
            if (!region.getDimension().equals(dimension)) {
                continue;
            }

            boolean overlaps =
                    minX <= region.getMaxX()
                && maxX >= region.getMinX()
                && minZ <= region.getMaxZ()
                && maxZ >= region.getMinZ();

            if (overlaps) {
                return true;
            }
        }

        return false;
    }

    private String normalizeName(String name) {
        return name.toLowerCase();
    }

    private boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        return json.get(key).getAsBoolean();
    }

    private int getInt(JsonObject json, String key, int defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        return json.get(key).getAsInt();
    }

    private long getLong(JsonObject json, String key, long defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        return json.get(key).getAsLong();
    }

    public RegionOperationResult redefine(String name, ResourceKey<Level> dimension, BlockPos point1, BlockPos point2) {
        String key = normalizeName(name);

        Region oldRegion = regions.get(key);

        if (oldRegion == null) {
            return RegionOperationResult.fail(
                    RegionOperationResult.Type.REGION_NOT_FOUND,
                    name
            );
        }

        PlayerClaim overlapClaim = findOverlappingPlayerClaim(dimension, point1, point2);

        if (overlapClaim != null) {
            return RegionOperationResult.fail(
                    RegionOperationResult.Type.OVERLAPS_PLAYER_CLAIM,
                    describeClaim(overlapClaim)
            );
        }

        Region newRegion = new Region(oldRegion.getName(), dimension, point1, point2);
        newRegion.setPriority(oldRegion.getPriority());

        newRegion.getOwners().addAll(oldRegion.getOwners());
        newRegion.getMembers().addAll(oldRegion.getMembers());

        newRegion.getRentData().setRentable(oldRegion.getRentData().isRentable());
        newRegion.getRentData().setAmount(oldRegion.getRentData().getAmount());
        newRegion.getRentData().setPeriodDays(oldRegion.getRentData().getPeriodDays());
        newRegion.getRentData().setRenter(oldRegion.getRentData().getRenter());
        newRegion.getRentData().setRentEndTime(oldRegion.getRentData().getRentEndTime());

        copySettings(oldRegion, newRegion);

        if (oldRegion.getSpawnPos() != null) {
            newRegion.setSpawn(oldRegion.getSpawnPos(), oldRegion.getSpawnYaw(), oldRegion.getSpawnPitch());
        }

        regions.put(key, newRegion);
        save();
        return RegionOperationResult.success();
    }

    private void copySettings(Region from, Region to) {
        to.getSettings().setAllowBlockBreak(from.getSettings().isAllowBlockBreak());
        to.getSettings().setAllowBlockPlace(from.getSettings().isAllowBlockPlace());
        to.getSettings().setAllowInteract(from.getSettings().isAllowInteract());
        to.getSettings().setAllowPvp(from.getSettings().isAllowPvp());
        to.getSettings().setAllowExplosions(from.getSettings().isAllowExplosions());
        to.getSettings().setAllowPistons(from.getSettings().isAllowPistons());
        to.getSettings().setAllowWaterFlow(from.getSettings().isAllowWaterFlow());
        to.getSettings().setAllowLavaFlow(from.getSettings().isAllowLavaFlow());
        to.getSettings().setAllowRedstone(from.getSettings().isAllowRedstone());
        to.getSettings().setAllowHoppers(from.getSettings().isAllowHoppers());
        to.getSettings().setAllowFireSpread(from.getSettings().isAllowFireSpread());
    }


    private PlayerClaim findOverlappingPlayerClaim(ResourceKey<Level> dimension, BlockPos point1, BlockPos point2) {
        int minX = Math.min(point1.getX(), point2.getX());
        int maxX = Math.max(point1.getX(), point2.getX());
        int minZ = Math.min(point1.getZ(), point2.getZ());
        int maxZ = Math.max(point1.getZ(), point2.getZ());

        for (PlayerClaim claim : SimpleServerUtilities.PLAYER_CLAIMS.getClaims()) {
            if (!claim.getDimension().equals(dimension.identifier().toString())) {
                continue;
            }

            for (ClaimChunk chunk : claim.getChunks()) {
                int chunkMinX = chunk.getX() << 4;
                int chunkMaxX = chunkMinX + 15;
                int chunkMinZ = chunk.getZ() << 4;
                int chunkMaxZ = chunkMinZ + 15;

                boolean overlaps =
                        minX <= chunkMaxX
                    && maxX >= chunkMinX
                    && minZ <= chunkMaxZ
                    && maxZ >= chunkMinZ;

                if (overlaps) {
                    return claim;
                }
            }
        }

        return null;
    }

    private String describeClaim(PlayerClaim claim) {
        return "'" + claim.getDisplayName() + "' owned by " + claim.getOwner()
                + " in " + claim.getDimension()
                + " (" + claim.getChunkCount() + " chunks)";
    }
}