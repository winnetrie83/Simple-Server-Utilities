package be.winnetrie.mod.simpleserverutilities.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Lazy, bounded cache for persistent per-player dashboard and HUD preferences. */
public final class PlayerUiPreferencesManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_LOADED_RECORDS = 512;

    private final Map<UUID, PlayerUiPreferences> preferences = new HashMap<>();
    private final Map<UUID, Path> knownFiles = new HashMap<>();
    private final Map<UUID, Long> accessOrder = new HashMap<>();
    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private long accessSequence;
    private Path folder;
    private MinecraftServer server;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        folder = StoragePaths.playerSettings(StoragePaths.root(server));
        preferences.clear();
        knownFiles.clear();
        accessOrder.clear();
        accessSequence = 0L;
        recordStore.reset();
        try {
            Files.createDirectories(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    UUID playerId = UUID.fromString(StoragePaths.fileBaseName(file));
                    knownFiles.put(playerId, file);
                } catch (Exception exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error(
                            "Invalid player UI settings filename. Archived: {}", archived, exception);
                }
            }
            SimpleServerUtilities.LOGGER.info(
                    "Indexed {} player UI preference record(s); records load on demand.", knownFiles.size());
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to index player UI settings.", exception);
        }
    }

    public synchronized PlayerUiPreferences preferences(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("Player UUID is required.");
        PlayerUiPreferences value = loadIfPresent(playerId);
        if (value == null) {
            value = new PlayerUiPreferences(playerId, "");
            preferences.put(playerId, value);
        }
        touch(playerId);
        trimCache();
        return value;
    }

    public synchronized PlayerUiPreferences ensurePlayer(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("Player is required.");
        UUID playerId = player.getUUID();
        PlayerUiPreferences value = loadIfPresent(playerId);
        boolean changed = false;

        if (value == null) {
            value = new PlayerUiPreferences(playerId, player.getName().getString());
            preferences.put(playerId, value);
            changed = true;
        } else {
            String currentName = player.getName().getString();
            if (!currentName.equals(value.getLastKnownName())) {
                value.setLastKnownName(currentName);
                changed = true;
            }
            value.setUuid(playerId);
        }

        value.normalize();
        touch(playerId);
        if (changed) saveRecord(playerId, value);
        trimCache();
        return value;
    }

    /** Persists only records that have actually been loaded in this server session. */
    public synchronized void save() {
        if (folder == null || preferences.isEmpty()) return;
        try {
            Files.createDirectories(folder);
            for (Map.Entry<UUID, PlayerUiPreferences> entry : preferences.entrySet()) {
                saveRecord(entry.getKey(), entry.getValue());
            }
            trimCache();
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to save player UI settings.", exception);
        }
    }

    private PlayerUiPreferences loadIfPresent(UUID playerId) {
        PlayerUiPreferences existing = preferences.get(playerId);
        if (existing != null) {
            touch(playerId);
            return existing;
        }
        Path file = knownFiles.get(playerId);
        if (file == null || !Files.exists(file)) return null;
        try {
            recordStore.discoverFile(file);
            PlayerUiPreferences value = JsonStorage.read(GSON, file, PlayerUiPreferences.class);
            if (value == null) throw new IllegalArgumentException("Empty player UI settings file.");
            value.setUuid(playerId);
            value.normalize();
            preferences.put(playerId, value);
            touch(playerId);
            return value;
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(file);
            knownFiles.remove(playerId);
            recordStore.forget(file);
            SimpleServerUtilities.LOGGER.error(
                    "Failed to load player UI settings on demand. Archived: {}", archived, exception);
            return null;
        }
    }

    private boolean saveRecord(UUID playerId, PlayerUiPreferences value) {
        if (folder == null || playerId == null || value == null) return false;
        value.setUuid(playerId);
        value.normalize();
        Path file = StoragePaths.jsonFile(folder, playerId.toString());
        boolean queued = recordStore.queueJson(GSON, file, value);
        knownFiles.put(playerId, file);
        return queued;
    }

    private void touch(UUID playerId) {
        if (playerId != null) accessOrder.put(playerId, ++accessSequence);
    }

    private void trimCache() {
        while (preferences.size() > MAX_LOADED_RECORDS) {
            UUID oldest = null;
            long oldestAccess = Long.MAX_VALUE;
            for (UUID playerId : Set.copyOf(preferences.keySet())) {
                if (server != null && server.getPlayerList().getPlayer(playerId) != null) continue;
                Path file = knownFiles.getOrDefault(playerId,
                        folder == null ? null : StoragePaths.jsonFile(folder, playerId.toString()));
                if (file != null && (SimpleServerUtilities.STORAGE.hasPending(file)
                        || SimpleServerUtilities.STORAGE.requiresRetry(file))) continue;
                long accessed = accessOrder.getOrDefault(playerId, Long.MIN_VALUE);
                if (oldest == null || accessed < oldestAccess) {
                    oldest = playerId;
                    oldestAccess = accessed;
                }
            }
            if (oldest == null) return;
            PlayerUiPreferences value = preferences.get(oldest);
            if (saveRecord(oldest, value)) continue;
            preferences.remove(oldest);
            accessOrder.remove(oldest);
        }
    }
}
