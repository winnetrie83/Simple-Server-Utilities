package be.winnetrie.mod.simpleserverutilities.visualization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;

public final class BorderVisualizationSettingsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, PlayerBorderPreferences> playerPreferences = new HashMap<>();
    private BorderVisualizationSettings settings = new BorderVisualizationSettings();
    private Path settingsFile;
    private Path playersFolder;
    private long revision;

    public void load(MinecraftServer server) {
        Path folder = StoragePaths.root(server).resolve("visualization");
        settingsFile = folder.resolve("settings.json");
        playersFolder = folder.resolve("players");
        playerPreferences.clear();
        settings = new BorderVisualizationSettings();

        try {
            Files.createDirectories(playersFolder);
            if (Files.exists(settingsFile)) {
                BorderVisualizationSettings loaded = JsonStorage.read(GSON, settingsFile, BorderVisualizationSettings.class);
                if (loaded != null) {
                    settings = loaded;
                }
            }
            settings.ensureDefaults();

            for (Path file : JsonStorage.listJsonFiles(playersFolder)) {
                try {
                    PlayerBorderPreferences preference = JsonStorage.read(GSON, file, PlayerBorderPreferences.class);
                    if (preference != null && preference.getPlayer() != null) {
                        preference.ensureDefaults();
                        playerPreferences.put(preference.getPlayer(), preference);
                    }
                } catch (Exception e) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error(
                            "Failed to load border preference file. Broken file archived as: {}",
                            archived,
                            e
                    );
                }
            }

            queueSettingsSave();
            revision++;
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load border visualization settings.", e);
        }
    }

    public BorderVisualizationSettings settings() {
        return settings;
    }

    public PlayerBorderPreferences preferences(UUID player) {
        return playerPreferences.computeIfAbsent(player, PlayerBorderPreferences::new);
    }

    public void setClaimsVisible(UUID player, boolean visible) {
        PlayerBorderPreferences preference = preferences(player);
        preference.setClaimBordersVisible(visible);
        queuePreferenceSave(preference);
        revision++;
    }

    public void setRegionsVisible(UUID player, boolean visible) {
        PlayerBorderPreferences preference = preferences(player);
        preference.setRegionBordersVisible(visible);
        queuePreferenceSave(preference);
        revision++;
    }


    public boolean pinRegion(UUID player, String regionName) {
        PlayerBorderPreferences preference = preferences(player);
        boolean changed = preference.pinRegion(regionName);
        if (changed) {
            queuePreferenceSave(preference);
            revision++;
        }
        return changed;
    }

    public boolean unpinRegion(UUID player, String regionName) {
        PlayerBorderPreferences preference = preferences(player);
        boolean changed = preference.unpinRegion(regionName);
        if (changed) {
            queuePreferenceSave(preference);
            revision++;
        }
        return changed;
    }

    public boolean clearPinnedRegions(UUID player) {
        PlayerBorderPreferences preference = preferences(player);
        boolean changed = preference.clearPinnedRegions();
        if (changed) {
            queuePreferenceSave(preference);
            revision++;
        }
        return changed;
    }

    public void setColor(BorderCategory category, int rgb) {
        settings.setRgb(category, rgb);
        queueSettingsSave();
        revision++;
    }

    public void resetColor(BorderCategory category) {
        settings.reset(category);
        queueSettingsSave();
        revision++;
    }

    public void resetColors() {
        settings.resetAll();
        queueSettingsSave();
        revision++;
    }

    public long revision() {
        return revision;
    }

    private void queueSettingsSave() {
        if (settingsFile != null) {
            SimpleServerUtilities.STORAGE.queueJson(GSON, settingsFile, settings);
        }
    }

    private void queuePreferenceSave(PlayerBorderPreferences preference) {
        if (playersFolder != null && preference.getPlayer() != null) {
            Path file = StoragePaths.jsonFile(playersFolder, preference.getPlayer().toString());
            SimpleServerUtilities.STORAGE.queueJson(GSON, file, preference);
        }
    }
}
