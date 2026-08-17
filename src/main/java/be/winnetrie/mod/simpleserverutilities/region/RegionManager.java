package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.core.performance.RegionSpatialIndex;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public class RegionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Region> regions = new HashMap<>();
    private final DirtyJsonRecordStore regionRecordStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore settingsRecordStore = new DirtyJsonRecordStore();
    private final RegionSpatialIndex spatialIndex = new RegionSpatialIndex();
    private boolean rentingEnabled = true;
    private final RegionRentEconomySettings rentEconomySettings = new RegionRentEconomySettings();

    private Path rootFolder;
    private Path regionsFolder;
    private Path regionEntriesFolder;
    private Path legacySaveFile;
    private Path veryOldLegacySaveFile;

    public void load(MinecraftServer server) {
        this.rootFolder = StoragePaths.root(server);
        this.regionsFolder = StoragePaths.regions(rootFolder);
        this.regionEntriesFolder = StoragePaths.regionEntries(rootFolder);
        this.legacySaveFile = rootFolder.resolve("regions.json");
        this.veryOldLegacySaveFile = StoragePaths.legacyRoot(server).resolve("regions.json");

        regions.clear();
        rentingEnabled = true;
        rentEconomySettings.setPlayerCancelRefundPermille(0);
        rentEconomySettings.setAdminCancelRefundPermille(1_000);
        regionRecordStore.reset();
        settingsRecordStore.reset();
        spatialIndex.clear();

        try {
            Files.createDirectories(rootFolder);
            regionRecordStore.discover(regionEntriesFolder);

            if (JsonStorage.hasJsonFiles(regionsFolder)) {
                loadSplitRegions();
            } else if (Files.exists(legacySaveFile)) {
                loadLegacyRegions(legacySaveFile);
                save();
                if (SimpleServerUtilities.STORAGE.flush(java.time.Duration.ofSeconds(10))) {
                    Path archived = JsonStorage.archiveLegacyFile(legacySaveFile);
                    if (archived != null) {
                        SimpleServerUtilities.LOGGER.info(
                                "Migrated legacy regions to per-region storage. Legacy file archived as: {}",
                                archived
                        );
                    }
                } else {
                    SimpleServerUtilities.LOGGER.error(
                            "Region migration writes did not flush successfully; the legacy file was kept in place."
                    );
                }
            } else if (Files.exists(veryOldLegacySaveFile)) {
                loadLegacyRegions(veryOldLegacySaveFile);
                save();
                if (SimpleServerUtilities.STORAGE.flush(java.time.Duration.ofSeconds(10))) {
                    Path archived = JsonStorage.archiveLegacyFile(veryOldLegacySaveFile);
                    if (archived != null) {
                        SimpleServerUtilities.LOGGER.info(
                                "Migrated very old regions path to per-region storage. Legacy file archived as: {}",
                                archived
                        );
                    }
                } else {
                    SimpleServerUtilities.LOGGER.error(
                            "Very old region migration writes did not flush successfully; the legacy file was kept in place."
                    );
                }
            } else {
                Files.createDirectories(regionEntriesFolder);
                save();
            }

            spatialIndex.rebuild(regions.values());
            SimpleServerUtilities.LOGGER.info("Loaded {} regions into {} spatial cells.", regions.size(), spatialIndex.statistics().cells());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load regions.", e);
        }
    }

    public void save() {
        if (regionsFolder == null || regionEntriesFolder == null) {
            return;
        }

        // Region permission overrides are part of permission resolution.
        if (SsuModuleAccess.active("permissions")) SimpleServerUtilities.PERMISSIONS.invalidateResolutionCache();

        try {
            Files.createDirectories(regionEntriesFolder);

            JsonObject settings = new JsonObject();
            settings.addProperty("schemaVersion", 3);
            settings.addProperty("rentingEnabled", rentingEnabled);
            rentEconomySettings.normalize();
            settings.addProperty("playerCancelRefundPermille", rentEconomySettings.getPlayerCancelRefundPermille());
            settings.addProperty("adminCancelRefundPermille", rentEconomySettings.getAdminCancelRefundPermille());
            settingsRecordStore.queueJson(GSON, regionsFolder.resolve("_settings.json"), settings);

            Set<Path> keptFiles = new HashSet<>();

            for (Region region : regions.values()) {
                Path file = StoragePaths.jsonFile(regionEntriesFolder, region.getName());
                regionRecordStore.queueJson(GSON, file, regionToJson(region));
                keptFiles.add(file);
            }

            regionRecordStore.queueDeleteMissing(keptFiles);
            if (SsuModuleAccess.active("visualization")) SimpleServerUtilities.BORDER_VISUALIZATIONS.markRegionsChanged();
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save regions.", e);
        }
    }

    public void clear() {
        regions.clear();
        regionRecordStore.reset();
        settingsRecordStore.reset();
        spatialIndex.clear();
        rentingEnabled = true;
        rootFolder = null;
        regionsFolder = null;
        regionEntriesFolder = null;
        legacySaveFile = null;
        veryOldLegacySaveFile = null;
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

        Region region = new Region(name, dimension, point1, point2);
        regions.put(key, region);
        spatialIndex.add(region);
        save();
        return RegionOperationResult.success();
    }

    public boolean delete(String name) {
        String key = normalizeName(name);

        Region removed = regions.remove(key);
        if (removed == null) {
            return false;
        }

        spatialIndex.remove(removed);
        save();
        return true;
    }

    public Region get(String name) {
        return regions.get(normalizeName(name));
    }

    public Region getAt(ResourceKey<Level> dimension, BlockPos pos) {
        RegionSpatialIndex.CandidateResult candidates = spatialIndex.candidatesAt(dimension, pos);
        SimpleServerUtilities.PERFORMANCE.recordRegionLookup(candidates.regions().size(), candidates.fallback());

        Region bestRegion = null;
        for (Region region : candidates.regions()) {
            if (!region.contains(dimension, pos)) {
                continue;
            }

            if (bestRegion == null
                    || region.getPriority() > bestRegion.getPriority()
                    || (region.getPriority() == bestRegion.getPriority()
                    && region.getVolume() < bestRegion.getVolume())) {
                bestRegion = region;
            }
        }
        return bestRegion;
    }

    public Collection<Region> getAll() {
        return regions.values();
    }

    public boolean isRentingEnabled() {
        return rentingEnabled;
    }

    public void setRentingEnabled(boolean rentingEnabled) {
        this.rentingEnabled = rentingEnabled;
        save();
    }

    public RegionRentEconomySettings rentEconomySettings() {
        return rentEconomySettings;
    }

    public boolean exists(String name) {
        return regions.containsKey(normalizeName(name));
    }

    public boolean setBorderVisible(String name, boolean visible) {
        Region region = get(name);
        if (region == null || region.isBorderVisible() == visible) {
            return false;
        }
        region.setBorderVisible(visible);
        save();
        return true;
    }

    public int setAllBordersVisible(boolean visible) {
        int changed = 0;
        for (Region region : regions.values()) {
            if (region.isBorderVisible() != visible) {
                region.setBorderVisible(visible);
                changed++;
            }
        }
        if (changed > 0) {
            save();
        }
        return changed;
    }

    public boolean overlaps2D(ResourceKey<Level> dimension, int minX, int minZ, int maxX, int maxZ) {
        RegionSpatialIndex.CandidateResult candidates = spatialIndex.query2D(dimension, minX, minZ, maxX, maxZ);
        SimpleServerUtilities.PERFORMANCE.recordRegionLookup(candidates.regions().size(), candidates.fallback());

        for (Region region : candidates.regions()) {
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

    public Collection<Region> getIntersecting2D(
            ResourceKey<Level> dimension,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        RegionSpatialIndex.CandidateResult candidates = spatialIndex.query2D(dimension, minX, minZ, maxX, maxZ);
        SimpleServerUtilities.PERFORMANCE.recordRegionLookup(candidates.regions().size(), candidates.fallback());

        java.util.List<Region> result = new java.util.ArrayList<>();
        for (Region region : candidates.regions()) {
            if (minX <= region.getMaxX()
                    && maxX >= region.getMinX()
                    && minZ <= region.getMaxZ()
                    && maxZ >= region.getMinZ()) {
                result.add(region);
            }
        }
        return java.util.List.copyOf(result);
    }

    public RegionSpatialIndex.Statistics spatialIndexStatistics() {
        return spatialIndex.statistics();
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
        newRegion.setBorderVisible(oldRegion.isBorderVisible());

        newRegion.getManagers().addAll(oldRegion.getManagers());
        newRegion.getMembers().addAll(oldRegion.getMembers());
        newRegion.getPermissionOverrides().putAll(oldRegion.getPermissionOverrides());

        newRegion.getRentData().setRentable(oldRegion.getRentData().isRentable());
        newRegion.getRentData().setAmount(oldRegion.getRentData().getAmount());
        newRegion.getRentData().setPeriodDays(oldRegion.getRentData().getPeriodDays());
        newRegion.getRentData().setRenter(oldRegion.getRentData().getRenter());
        newRegion.getRentData().setRenterName(oldRegion.getRentData().getRenterName());
        newRegion.getRentData().setRentEndTime(oldRegion.getRentData().getRentEndTime());
        newRegion.getRentData().setRentPaused(oldRegion.getRentData().isRentPaused());
        newRegion.getRentData().setPausedRemainingMillis(oldRegion.getRentData().getPausedRemainingMillis());
        newRegion.getRentData().setResetOnExpire(oldRegion.getRentData().isResetOnExpire());
        newRegion.getRentData().setResetOnUnrent(oldRegion.getRentData().isResetOnUnrent());
        if (oldRegion.getRentData().getStoredPriceMinor() >= 0L) {
            newRegion.getRentData().loadPriceMinor(oldRegion.getRentData().getStoredPriceMinor());
        }
        newRegion.getRentData().setRentalSequence(oldRegion.getRentData().getRentalSequence());
        newRegion.getRentData().setCurrentTermPaidMinor(oldRegion.getRentData().getCurrentTermPaidMinor());
        newRegion.getRentData().setTotalPaidMinor(oldRegion.getRentData().getTotalPaidMinor());
        newRegion.getRentData().setRefundableAmountMinor(oldRegion.getRentData().getRefundableAmountMinor());
        newRegion.getRentData().setRefundableWindowStartTime(oldRegion.getRentData().getRefundableWindowStartTime());
        newRegion.getRentData().setRefundableWindowEndTime(oldRegion.getRentData().getRefundableWindowEndTime());
        newRegion.getRentData().setLastPaymentTransactionId(oldRegion.getRentData().getLastPaymentTransactionId());
        newRegion.setWelcomeMessage(oldRegion.getWelcomeMessage());
        newRegion.setLeaveMessage(oldRegion.getLeaveMessage());

        copySettings(oldRegion, newRegion);
        newRegion.getResetSettings().copyFrom(oldRegion.getResetSettings());

        if (oldRegion.getSpawnPos() != null
                && newRegion.contains(newRegion.getDimension(), oldRegion.getSpawnPos())) {
            newRegion.setSpawn(oldRegion.getSpawnPos(), oldRegion.getSpawnYaw(), oldRegion.getSpawnPitch());
        }

        regions.put(key, newRegion);
        spatialIndex.replace(oldRegion, newRegion);
        save();
        return RegionOperationResult.success();
    }

    private void loadSplitRegions() throws IOException {
        Path settingsFile = regionsFolder.resolve("_settings.json");

        if (Files.exists(settingsFile)) {
            try {
                JsonObject settings = JsonParser.parseString(Files.readString(settingsFile)).getAsJsonObject();
                rentingEnabled = getBoolean(settings, "rentingEnabled", true);
                rentEconomySettings.setPlayerCancelRefundPermille(getInt(settings, "playerCancelRefundPermille", 0));
                rentEconomySettings.setAdminCancelRefundPermille(getInt(settings, "adminCancelRefundPermille", 1_000));
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(settingsFile);
                SimpleServerUtilities.LOGGER.error("Failed to load region settings file. Broken file archived as: {}", archived, e);
            }
        }

        Files.createDirectories(regionEntriesFolder);

        for (Path file : JsonStorage.listJsonFiles(regionEntriesFolder)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                Region region = regionFromJson(json);
                regions.put(normalizeName(region.getName()), region);
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load region file. Broken file archived as: {}", archived, e);
            }
        }
    }

    private void loadLegacyRegions(Path loadPath) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(loadPath)).getAsJsonObject();
            rentingEnabled = getBoolean(root, "rentingEnabled", true);
            rentEconomySettings.setPlayerCancelRefundPermille(getInt(root, "playerCancelRefundPermille", 0));
            rentEconomySettings.setAdminCancelRefundPermille(getInt(root, "adminCancelRefundPermille", 1_000));

            JsonArray array = root.getAsJsonArray("regions");

            if (array == null) {
                return;
            }

            for (int i = 0; i < array.size(); i++) {
                JsonObject json = array.get(i).getAsJsonObject();
                Region region = regionFromJson(json);
                regions.put(normalizeName(region.getName()), region);
            }
        } catch (Exception e) {
            Path archived = JsonStorage.archiveBrokenFile(loadPath);
            SimpleServerUtilities.LOGGER.error("Failed to read legacy region file. Broken file archived as: {}", archived, e);
        }
    }

    private Region regionFromJson(JsonObject json) {
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

        loadUuidSet(json, "managers", region.getManagers());
        // One-time forward migration: pre-dev2.1 region owners were administrative managers.
        loadUuidSet(json, "owners", region.getManagers());
        loadUuidSet(json, "members", region.getMembers());

        if (json.has("permissions")) {
            JsonObject permissions = json.getAsJsonObject("permissions");

            for (Entry<String, JsonElement> entry : permissions.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isJsonNull()) {
                    continue;
                }

                region.setPermissionOverride(entry.getKey(), entry.getValue().getAsString());
            }
        }

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

        region.setBorderVisible(getBoolean(json, "borderVisible", false));
        region.setWelcomeMessage(getString(json, "welcomeMessage", ""));
        region.setLeaveMessage(getString(json, "leaveMessage", ""));

        if (json.has("rent")) {
            JsonObject rent = json.getAsJsonObject("rent");

            region.getRentData().setRentable(getBoolean(rent, "rentable", false));
            region.getRentData().setAmount(getInt(rent, "amount", 0));
            if (rent.has("priceMinor")) {
                region.getRentData().loadPriceMinor(getLong(rent, "priceMinor", 0L));
            }
            region.getRentData().setPeriodDays(getInt(rent, "periodDays", -1));
            region.getRentData().setRentEndTime(getLong(rent, "rentEndTime", -1L));
            region.getRentData().setRenterName(getString(rent, "renterName", ""));
            region.getRentData().setRentPaused(getBoolean(rent, "rentPaused", false));
            region.getRentData().setPausedRemainingMillis(getLong(rent, "pausedRemainingMillis", -1L));
            region.getRentData().setResetOnExpire(getBoolean(rent, "resetOnExpire", true));
            region.getRentData().setResetOnUnrent(getBoolean(rent, "resetOnUnrent", true));
            region.getRentData().setRentalSequence(getLong(rent, "rentalSequence", 0L));
            region.getRentData().setCurrentTermPaidMinor(getLong(rent, "currentTermPaidMinor", 0L));
            region.getRentData().setTotalPaidMinor(getLong(rent, "totalPaidMinor", 0L));
            region.getRentData().setRefundableAmountMinor(getLong(rent, "refundableAmountMinor", 0L));
            region.getRentData().setRefundableWindowStartTime(getLong(rent, "refundableWindowStartTime", -1L));
            region.getRentData().setRefundableWindowEndTime(getLong(rent, "refundableWindowEndTime", -1L));
            if (rent.has("lastPaymentTransactionId") && !rent.get("lastPaymentTransactionId").isJsonNull()) {
                region.getRentData().setLastPaymentTransactionId(UUID.fromString(rent.get("lastPaymentTransactionId").getAsString()));
            }

            if (rent.has("renter")) {
                region.getRentData().setRenter(UUID.fromString(rent.get("renter").getAsString()));
            }
        }

        if (json.has("scheduledReset")) {
            JsonObject reset = json.getAsJsonObject("scheduledReset");
            RegionResetSettings settings = region.getResetSettings();
            settings.setEnabled(getBoolean(reset, "enabled", false));
            settings.setIntervalSeconds(getLong(reset, "intervalSeconds", RegionResetSettings.DEFAULT_INTERVAL_SECONDS));
            settings.setMode(RegionResetMode.parse(getString(reset, "mode", RegionResetMode.SNAPSHOT.name())));
            settings.setOnlyWhenEmpty(getBoolean(reset, "onlyWhenEmpty", true));
            settings.setWeightedPreset(getString(reset, "weightedPreset", ""));
            settings.setNextResetAt(getLong(reset, "nextResetAt", -1L));
            settings.setLastResetAt(getLong(reset, "lastResetAt", -1L));
            settings.normalize(System.currentTimeMillis());
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

        return region;
    }

    private JsonObject regionToJson(Region region) {
        JsonObject json = new JsonObject();

        json.addProperty("schemaVersion", 5);
        json.addProperty("name", region.getName());
        json.addProperty("dimension", region.getDimension().identifier().toString());
        json.addProperty("priority", region.getPriority());
        json.addProperty("borderVisible", region.isBorderVisible());

        json.addProperty("minX", region.getMinX());
        json.addProperty("minY", region.getMinY());
        json.addProperty("minZ", region.getMinZ());
        json.addProperty("maxX", region.getMaxX());
        json.addProperty("maxY", region.getMaxY());
        json.addProperty("maxZ", region.getMaxZ());

        json.add("managers", saveUuidSet(region.getManagers()));
        json.add("members", saveUuidSet(region.getMembers()));

        if (!region.getWelcomeMessage().isBlank()) {
            json.addProperty("welcomeMessage", region.getWelcomeMessage());
        }

        if (!region.getLeaveMessage().isBlank()) {
            json.addProperty("leaveMessage", region.getLeaveMessage());
        }

        if (!region.getPermissionOverrides().isEmpty()) {
            JsonObject permissions = new JsonObject();

            for (Entry<String, String> entry : region.getPermissionOverrides().entrySet()) {
                permissions.addProperty(entry.getKey(), entry.getValue());
            }

            json.add("permissions", permissions);
        }

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
        long storedPriceMinor = region.getRentData().getStoredPriceMinor();
        if (storedPriceMinor >= 0L) {
            rent.addProperty("priceMinor", storedPriceMinor);
        } else if (SsuModuleAccess.active("economy")) {
            // Legacy whole-unit prices are migrated only when the active provider's
            // decimal semantics are actually loaded. This avoids rewriting old rent
            // data with default assumptions while Economy is intentionally disabled.
            rent.addProperty("priceMinor", region.getRentData().getPriceMinor(SimpleServerUtilities.ECONOMY.settings()));
        }
        rent.addProperty("periodDays", region.getRentData().getPeriodDays());
        rent.addProperty("rentEndTime", region.getRentData().getRentEndTime());
        rent.addProperty("renterName", region.getRentData().getRenterName());
        rent.addProperty("rentPaused", region.getRentData().isRentPaused());
        rent.addProperty("pausedRemainingMillis", region.getRentData().getPausedRemainingMillis());
        rent.addProperty("resetOnExpire", region.getRentData().isResetOnExpire());
        rent.addProperty("resetOnUnrent", region.getRentData().isResetOnUnrent());
        rent.addProperty("rentalSequence", region.getRentData().getRentalSequence());
        rent.addProperty("currentTermPaidMinor", region.getRentData().getCurrentTermPaidMinor());
        rent.addProperty("totalPaidMinor", region.getRentData().getTotalPaidMinor());
        rent.addProperty("refundableAmountMinor", region.getRentData().getRefundableAmountMinor());
        rent.addProperty("refundableWindowStartTime", region.getRentData().getRefundableWindowStartTime());
        rent.addProperty("refundableWindowEndTime", region.getRentData().getRefundableWindowEndTime());
        if (region.getRentData().getLastPaymentTransactionId() != null) {
            rent.addProperty("lastPaymentTransactionId", region.getRentData().getLastPaymentTransactionId().toString());
        }

        if (region.getRentData().getRenter() != null) {
            rent.addProperty("renter", region.getRentData().getRenter().toString());
        }

        json.add("rent", rent);

        RegionResetSettings scheduled = region.getResetSettings();
        scheduled.normalize(System.currentTimeMillis());
        JsonObject scheduledReset = new JsonObject();
        scheduledReset.addProperty("enabled", scheduled.isEnabled());
        scheduledReset.addProperty("intervalSeconds", scheduled.getIntervalSeconds());
        scheduledReset.addProperty("mode", scheduled.getMode().name());
        scheduledReset.addProperty("onlyWhenEmpty", scheduled.isOnlyWhenEmpty());
        scheduledReset.addProperty("weightedPreset", scheduled.getWeightedPreset());
        scheduledReset.addProperty("nextResetAt", scheduled.getNextResetAt());
        scheduledReset.addProperty("lastResetAt", scheduled.getLastResetAt());
        json.add("scheduledReset", scheduledReset);

        if (region.getSpawnPos() != null) {
            JsonObject spawn = new JsonObject();
            spawn.addProperty("x", region.getSpawnPos().getX());
            spawn.addProperty("y", region.getSpawnPos().getY());
            spawn.addProperty("z", region.getSpawnPos().getZ());
            spawn.addProperty("yaw", region.getSpawnYaw());
            spawn.addProperty("pitch", region.getSpawnPitch());
            json.add("spawn", spawn);
        }

        return json;
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

        if (!SsuModuleAccess.active("claims")) return null;
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

    private String normalizeName(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private String getString(JsonObject json, String key, String defaultValue) {
        if (!json.has(key)) {
            return defaultValue;
        }

        return json.get(key).getAsString();
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
}
