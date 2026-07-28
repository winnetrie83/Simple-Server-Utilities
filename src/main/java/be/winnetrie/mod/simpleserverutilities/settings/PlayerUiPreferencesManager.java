package be.winnetrie.mod.simpleserverutilities.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
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

public final class PlayerUiPreferencesManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, PlayerUiPreferences> preferences = new HashMap<>();
    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private Path folder;

    public void load(MinecraftServer server) {
        folder = StoragePaths.playerSettings(StoragePaths.root(server));
        preferences.clear();
        recordStore.reset();
        recordStore.discover(folder);
        try {
            Files.createDirectories(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    PlayerUiPreferences value = JsonStorage.read(GSON, file, PlayerUiPreferences.class);
                    if (value == null || value.getUuid().isBlank()) {
                        continue;
                    }
                    value.normalize();
                    preferences.put(UUID.fromString(value.getUuid()), value);
                } catch (Exception e) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load player UI settings. Archived: {}", archived, e);
                }
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to load player UI settings.", e);
        }
    }

    public PlayerUiPreferences preferences(UUID playerId) {
        return preferences.computeIfAbsent(playerId, id -> new PlayerUiPreferences(id, ""));
    }

    public PlayerUiPreferences ensurePlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerUiPreferences value = preferences.get(playerId);
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
        if (changed) {
            save();
        }
        return value;
    }

    public void save() {
        if (folder == null) {
            return;
        }
        try {
            Files.createDirectories(folder);
            Set<Path> kept = new HashSet<>();
            for (Map.Entry<UUID, PlayerUiPreferences> entry : preferences.entrySet()) {
                PlayerUiPreferences value = entry.getValue();
                value.setUuid(entry.getKey());
                value.normalize();
                Path file = StoragePaths.jsonFile(folder, entry.getKey().toString());
                recordStore.queueJson(GSON, file, value);
                kept.add(file);
            }
            recordStore.queueDeleteMissing(kept);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save player UI settings.", e);
        }
    }
}
