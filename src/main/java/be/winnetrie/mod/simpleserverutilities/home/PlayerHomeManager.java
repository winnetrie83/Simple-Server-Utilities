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
    private final DirtyJsonRecordStore homeRecordStore = new DirtyJsonRecordStore();

    private Path rootFolder;
    private Path playersFolder;
    private Path legacySaveFile;

    public void load(MinecraftServer server) {
        this.rootFolder = StoragePaths.root(server);
        this.playersFolder = StoragePaths.homePlayers(rootFolder);
        this.legacySaveFile = rootFolder.resolve("homes.json");

        homesByOwner.clear();
        homeRecordStore.reset();

        try {
            Files.createDirectories(rootFolder);

            if (JsonStorage.hasJsonFiles(playersFolder)) {
                homeRecordStore.discover(playersFolder);
                loadSplitHomes();
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
                homeRecordStore.discover(playersFolder);
                save();
            }

            SimpleServerUtilities.LOGGER.info("Loaded {} player homes for {} players.", countAllHomes(), homesByOwner.size());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load player homes.", e);
        }
    }

    public void save() {
        if (playersFolder == null) {
            return;
        }

        try {
            Files.createDirectories(playersFolder);
            Set<Path> keptFiles = new HashSet<>();

            for (Map.Entry<UUID, Map<String, PlayerHome>> entry : homesByOwner.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }

                UUID owner = entry.getKey();
                HomePlayerSaveData data = new HomePlayerSaveData();
                data.player = owner.toString();
                data.homes = new ArrayList<>(entry.getValue().values());
                data.homes.sort(Comparator.comparing(PlayerHome::getDisplayName, String::compareToIgnoreCase));

                Path file = StoragePaths.jsonFile(playersFolder, owner.toString());
                homeRecordStore.queueJson(GSON, file, data);
                keptFiles.add(file);
            }

            homeRecordStore.queueDeleteMissing(keptFiles);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to queue player home saves.", e);
        }
    }

    public boolean setHome(ServerPlayer player, String rawName) {
        String name = sanitizeName(rawName);
        UUID owner = player.getUUID();

        Map<String, PlayerHome> ownerHomes = homesByOwner.computeIfAbsent(owner, uuid -> new HashMap<>());
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
        save();

        return true;
    }

    public boolean deleteHome(UUID owner, String rawName) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);

        if (ownerHomes == null) {
            return false;
        }

        PlayerHome removed = ownerHomes.remove(normalizeName(sanitizeName(rawName)));

        if (ownerHomes.isEmpty()) {
            homesByOwner.remove(owner);
        }

        if (removed == null) {
            return false;
        }

        save();
        return true;
    }

    public PlayerHome getHome(UUID owner, String rawName) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);

        if (ownerHomes == null) {
            return null;
        }

        return ownerHomes.get(normalizeName(sanitizeName(rawName)));
    }

    public Collection<PlayerHome> getHomes(UUID owner) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);

        if (ownerHomes == null) {
            return java.util.List.of();
        }

        ArrayList<PlayerHome> homes = new ArrayList<>(ownerHomes.values());
        homes.sort(Comparator.comparing(PlayerHome::getDisplayName, String::compareToIgnoreCase));
        return homes;
    }

    public int countHomes(UUID owner) {
        Map<String, PlayerHome> ownerHomes = homesByOwner.get(owner);
        return ownerHomes == null ? 0 : ownerHomes.size();
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

    private void loadSplitHomes() throws IOException {
        for (Path file : JsonStorage.listJsonFiles(playersFolder)) {
            try {
                HomePlayerSaveData data = JsonStorage.read(GSON, file, HomePlayerSaveData.class);

                if (data == null || data.homes == null) {
                    continue;
                }

                UUID owner = data.player == null || data.player.isBlank()
                        ? UUID.fromString(StoragePaths.fileBaseName(file))
                        : UUID.fromString(data.player);

                loadOwnerHomes(owner, data.homes);
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load player home file. Broken file archived as: {}", archived, e);
            }
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
            }
        } catch (Exception e) {
            Path archived = JsonStorage.archiveBrokenFile(legacySaveFile);
            SimpleServerUtilities.LOGGER.error("Failed to read legacy homes file. Broken file archived as: {}", archived, e);
        }
    }

    private void loadOwnerHomes(UUID owner, Collection<PlayerHome> homes) {
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

    private int countAllHomes() {
        int count = 0;

        for (Map<String, PlayerHome> ownerHomes : homesByOwner.values()) {
            count += ownerHomes.size();
        }

        return count;
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
