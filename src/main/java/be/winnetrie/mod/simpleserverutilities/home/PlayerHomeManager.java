package be.winnetrie.mod.simpleserverutilities.home;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.policy.HomePolicy;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class PlayerHomeManager {

    private static final String DEFAULT_HOME_NAME = "home";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, Map<String, PlayerHome>> homesByOwner = new HashMap<>();
    private final Map<UUID, Path> knownOwnerFiles = new HashMap<>();
    private final Set<UUID> loadedOwners = new HashSet<>();
    private final Set<UUID> dirtyOwners = new HashSet<>();
    private final Map<UUID, Long> ownerAccess = new HashMap<>();
    private long accessSequence;
    private static final int MAX_LOADED_OWNER_RECORDS = 256;
    private final DirtyJsonRecordStore homeRecordStore = new DirtyJsonRecordStore();

    private Path rootFolder;
    private Path playersFolder;
    private Path legacySaveFile;

    public void load(MinecraftServer server) {
        this.rootFolder = StoragePaths.root(server);
        this.playersFolder = StoragePaths.homePlayers(rootFolder);
        this.legacySaveFile = rootFolder.resolve("homes.json");

        homesByOwner.clear();
        knownOwnerFiles.clear();
        loadedOwners.clear();
        dirtyOwners.clear();
        ownerAccess.clear();
        accessSequence = 0L;
        homeRecordStore.reset();

        try {
            Files.createDirectories(rootFolder);

            if (JsonStorage.hasJsonFiles(playersFolder)) {
                indexSplitHomes();
            } else if (Files.exists(legacySaveFile)) {
                loadLegacyHomes();
                save();
                if (SimpleServerUtilities.STORAGE.flush(java.time.Duration.ofSeconds(10))) {
                    Path archived = JsonStorage.archiveLegacyFile(legacySaveFile);
                    if (archived != null) {
                        SimpleServerUtilities.LOGGER.info("Migrated legacy homes to per-player storage. Legacy file archived as: {}", archived);
                    }
                } else {
                    SimpleServerUtilities.LOGGER.error("Home migration writes did not flush; the legacy file was kept in place.");
                }
            } else {
                Files.createDirectories(playersFolder);
                save();
            }

            SimpleServerUtilities.LOGGER.info(
                    "Indexed SSU home records for {} player(s); records are loaded on demand.",
                    knownOwnerFiles.size() + loadedOwners.size());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load player homes.", e);
        }
    }

    public synchronized void save() {
        if (playersFolder == null || dirtyOwners.isEmpty()) return;
        try {
            Files.createDirectories(playersFolder);
            for (UUID owner : Set.copyOf(dirtyOwners)) {
                Path file = StoragePaths.jsonFile(playersFolder, owner.toString());
                Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);
                if (ownerHomes == null || ownerHomes.isEmpty()) {
                    SimpleServerUtilities.STORAGE.queueDelete(file);
                    homeRecordStore.forget(file);
                    knownOwnerFiles.remove(owner);
                } else {
                    HomePlayerSaveData data = new HomePlayerSaveData();
                    data.player = owner.toString();
                    data.homes = new ArrayList<>(ownerHomes.values());
                    data.homes.sort(Comparator.comparing(PlayerHome::getDisplayName, String::compareToIgnoreCase));
                    homeRecordStore.queueJson(GSON, file, data);
                    knownOwnerFiles.put(owner, file);
                }
                dirtyOwners.remove(owner);
            }
            trimLoadedOwnerCache();
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to queue player home saves.", e);
        }
    }

    public void clear() {
        save();
        homesByOwner.clear();
        knownOwnerFiles.clear();
        loadedOwners.clear();
        dirtyOwners.clear();
        ownerAccess.clear();
        accessSequence = 0L;
        homeRecordStore.reset();
        rootFolder = null;
        playersFolder = null;
        legacySaveFile = null;
    }

    public boolean setHome(ServerPlayer player, String rawName) {
        String name = sanitizeName(rawName);
        UUID owner = player.getUUID();

        Map<String, PlayerHome> ownerHomes = ensureOwnerLoaded(owner);
        String normalizedName = normalizeName(name);

        PlayerHome existingHome = ownerHomes.get(normalizedName);
        long now = System.currentTimeMillis();

        if (existingHome != null) {
            existingHome.update(
                    getDimensionId(player),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    now
            );

            dirtyOwners.add(owner);
            save();
            return true;
        }

        if (ownerHomes.size() >= getMaxHomes(player)) {
            return false;
        }

        PlayerHome home = new PlayerHome(
                owner,
                name,
                getDimensionId(player),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot(),
                now
        );

        ownerHomes.put(normalizedName, home);
        dirtyOwners.add(owner);
        save();

        return true;
    }

    public boolean deleteHome(UUID owner, String rawName) {
        Map<String, PlayerHome> ownerHomes = ensureOwnerLoaded(owner);

        if (ownerHomes.isEmpty()) {
            return false;
        }

        PlayerHome removed = ownerHomes.remove(normalizeName(sanitizeName(rawName)));

        if (removed == null) {
            return false;
        }

        dirtyOwners.add(owner);
        save();
        return true;
    }

    /**
     * Removes every home physically linked to the supplied claim. This is called
     * by the claim manager before the claim itself disappears, so no orphaned
     * teleport destinations survive a claim deletion.
     */
    public synchronized int deleteHomesInClaim(UUID owner, PlayerClaim claim) {
        if (owner == null || claim == null) {
            return 0;
        }

        Map<String, PlayerHome> ownerHomes = ensureOwnerLoaded(owner);
        if (ownerHomes.isEmpty()) {
            return 0;
        }

        int before = ownerHomes.size();
        ownerHomes.entrySet().removeIf(entry -> ClaimHomeSupport.contains(claim, entry.getValue()));
        int removed = before - ownerHomes.size();
        if (removed > 0) {
            dirtyOwners.add(owner);
            save();
        }
        return removed;
    }

    /**
     * Removes every home located in one of the supplied claim chunks. The claim
     * manager calls this only after validating that the chunk removal itself is
     * allowed, so rejected or disconnecting selections never delete homes.
     */
    public synchronized int deleteHomesInChunks(
            UUID owner,
            String dimension,
            Collection<ClaimChunk> chunks
    ) {
        if (owner == null || dimension == null || dimension.isBlank() || chunks == null || chunks.isEmpty()) {
            return 0;
        }

        Set<ClaimChunk> removedChunks = new HashSet<>(chunks);
        Map<String, PlayerHome> ownerHomes = ensureOwnerLoaded(owner);
        if (ownerHomes.isEmpty()) {
            return 0;
        }

        int before = ownerHomes.size();
        ownerHomes.entrySet().removeIf(entry -> {
            PlayerHome home = entry.getValue();
            if (home == null || !dimension.equals(home.getDimension())) {
                return false;
            }
            int blockX = (int) Math.floor(home.getX());
            int blockZ = (int) Math.floor(home.getZ());
            return removedChunks.contains(new ClaimChunk(
                    Math.floorDiv(blockX, 16),
                    Math.floorDiv(blockZ, 16)
            ));
        });

        int removed = before - ownerHomes.size();
        if (removed > 0) {
            dirtyOwners.add(owner);
            save();
        }
        return removed;
    }

    public PlayerHome getHome(UUID owner, String rawName) {
        Map<String, PlayerHome> ownerHomes = ensureOwnerLoaded(owner);

        if (ownerHomes.isEmpty()) {
            return null;
        }

        return ownerHomes.get(normalizeName(sanitizeName(rawName)));
    }

    public Collection<PlayerHome> getHomes(UUID owner) {
        Map<String, PlayerHome> ownerHomes = ensureOwnerLoaded(owner);

        if (ownerHomes.isEmpty()) {
            return java.util.List.of();
        }

        ArrayList<PlayerHome> homes = new ArrayList<>(ownerHomes.values());
        homes.sort(Comparator.comparing(PlayerHome::getDisplayName, String::compareToIgnoreCase));
        return homes;
    }

    public int countHomes(UUID owner) {
        return ensureOwnerLoaded(owner).size();
    }

    public int getMaxHomes(ServerPlayer player) {
        return HomePolicy.getMaxHomes(player);
    }

    /**
     * Fallback for places that only know a UUID. Prefer getMaxHomes(ServerPlayer) when possible,
     * because the permission/rank system is player-context aware.
     */
    public int getMaxHomes(UUID owner) {
        return Config.MAX_PLAYER_HOMES.get();
    }

    public String getDefaultHomeName() {
        return DEFAULT_HOME_NAME;
    }

    private void indexSplitHomes() throws IOException {
        for (Path file : JsonStorage.listJsonFiles(playersFolder)) {
            try {
                UUID owner = UUID.fromString(StoragePaths.fileBaseName(file));
                knownOwnerFiles.put(owner, file);
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error(
                        "Invalid player home filename. Broken file archived as: {}", archived, exception);
            }
        }
    }

    private Map<String, PlayerHome> ensureOwnerLoaded(UUID owner) {
        if (owner == null) return Map.of();
        if (loadedOwners.contains(owner)) {
            markOwnerAccess(owner);
            return homesByOwner.computeIfAbsent(owner, ignored -> new HashMap<>());
        }
        loadedOwners.add(owner);
        markOwnerAccess(owner);
        Map<String, PlayerHome> result = homesByOwner.computeIfAbsent(owner, ignored -> new HashMap<>());
        Path file = knownOwnerFiles.get(owner);
        if (file == null || !Files.exists(file)) {
            trimLoadedOwnerCache();
            return result;
        }
        try {
            homeRecordStore.discoverFile(file);
            HomePlayerSaveData data = JsonStorage.read(GSON, file, HomePlayerSaveData.class);
            if (data != null && data.homes != null) loadOwnerHomes(owner, data.homes);
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(file);
            knownOwnerFiles.remove(owner);
            result.clear();
            SimpleServerUtilities.LOGGER.error(
                    "Failed to load player home file on demand. Broken file archived as: {}", archived, exception);
        }
        trimLoadedOwnerCache();
        return homesByOwner.computeIfAbsent(owner, ignored -> new HashMap<>());
    }

    private void markOwnerAccess(UUID owner) {
        if (owner != null) ownerAccess.put(owner, ++accessSequence);
    }

    private void trimLoadedOwnerCache() {
        while (loadedOwners.size() > MAX_LOADED_OWNER_RECORDS) {
            UUID oldest = null;
            long oldestAccess = Long.MAX_VALUE;
            for (UUID owner : loadedOwners) {
                if (dirtyOwners.contains(owner)) continue;
                Path file = knownOwnerFiles.getOrDefault(owner,
                        playersFolder == null ? null : StoragePaths.jsonFile(playersFolder, owner.toString()));
                if (file != null && (SimpleServerUtilities.STORAGE.hasPending(file)
                        || SimpleServerUtilities.STORAGE.requiresRetry(file))) continue;
                long accessed = ownerAccess.getOrDefault(owner, Long.MIN_VALUE);
                if (oldest == null || accessed < oldestAccess) {
                    oldest = owner;
                    oldestAccess = accessed;
                }
            }
            if (oldest == null) return;
            loadedOwners.remove(oldest);
            homesByOwner.remove(oldest);
            ownerAccess.remove(oldest);
        }
    }

    private void loadLegacyHomes() {
        try {
            HomeSaveData data = JsonStorage.read(GSON, legacySaveFile, HomeSaveData.class);

            if (data == null || data.homes == null) {
                return;
            }

            for (PlayerHome home : data.homes) {
                if (home.getOwner() == null || home.getName() == null) {
                    continue;
                }

                homesByOwner
                        .computeIfAbsent(home.getOwner(), uuid -> new HashMap<>())
                        .put(normalizeName(home.getName()), home);
                loadedOwners.add(home.getOwner());
                markOwnerAccess(home.getOwner());
                dirtyOwners.add(home.getOwner());
            }
        } catch (Exception e) {
            Path archived = JsonStorage.archiveBrokenFile(legacySaveFile);
            SimpleServerUtilities.LOGGER.error("Failed to read legacy homes file. Broken file archived as: {}", archived, e);
        }
    }

    private void loadOwnerHomes(UUID owner, Collection<PlayerHome> homes) {
        loadedOwners.add(owner);
        Map<String, PlayerHome> ownerHomes = homesByOwner.computeIfAbsent(owner, uuid -> new HashMap<>());

        for (PlayerHome home : homes) {
            if (home == null || home.getName() == null) {
                continue;
            }

            UUID actualOwner = home.getOwner() == null ? owner : home.getOwner();

            if (!actualOwner.equals(owner)) {
                SimpleServerUtilities.LOGGER.warn("Skipping home '{}' in wrong owner file. Expected owner: {}, found owner: {}", home.getDisplayName(), owner, actualOwner);
                continue;
            }

            ownerHomes.put(normalizeName(home.getName()), home);
        }

        if (ownerHomes.isEmpty()) {
            homesByOwner.remove(owner);
        }
    }

    private String getDimensionId(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_HOME_NAME;
        }

        return name.trim();
    }

    private String normalizeName(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private static class HomeSaveData {
        private ArrayList<PlayerHome> homes = new ArrayList<>();
    }

    private static class HomePlayerSaveData {
        private int schemaVersion = 1;
        private String player = "";
        private String lastKnownName = "";
        private ArrayList<PlayerHome> homes = new ArrayList<>();
    }
}
