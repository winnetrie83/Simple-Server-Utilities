package be.winnetrie.mod.simpleserverutilities.mapmarker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

/** Persistent personal marker storage, isolated per player. */
public final class MapMarkerManager {
    public static final int MAX_MARKERS_PER_PLAYER = 256;
    private static final int CURRENT_SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, MarkerFile> files = new HashMap<>();
    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private Path folder;

    public void load(MinecraftServer server) {
        folder = StoragePaths.mapMarkerPlayers(StoragePaths.root(server));
        files.clear();
        recordStore.reset();
        recordStore.discover(folder);
        try {
            Files.createDirectories(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    MarkerFile value = JsonStorage.read(GSON, file, MarkerFile.class);
                    if (value == null || value.uuid == null || value.uuid.isBlank()) continue;
                    UUID playerId = UUID.fromString(value.uuid);
                    value.normalize(playerId);
                    files.put(playerId, value);
                } catch (Exception e) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load map markers. Archived: {}", archived, e);
                }
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to load map markers.", e);
        }
    }

    public void ensurePlayer(ServerPlayer player) {
        MarkerFile file = files.computeIfAbsent(player.getUUID(), MarkerFile::new);
        if (!player.getName().getString().equals(file.lastKnownName)) {
            file.lastKnownName = player.getName().getString();
            save();
        }
    }

    public List<MapMarker> markers(UUID playerId) {
        MarkerFile file = files.computeIfAbsent(playerId, MarkerFile::new);
        return file.markers.stream()
                .sorted(Comparator.comparing(MapMarker::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(marker -> marker.id().toString()))
                .toList();
    }

    public MapMarker marker(UUID playerId, UUID markerId) {
        if (markerId == null) return null;
        MarkerFile file = files.computeIfAbsent(playerId, MarkerFile::new);
        for (MapMarker marker : file.markers) {
            if (marker.id().equals(markerId)) return marker;
        }
        return null;
    }

    public Result create(UUID playerId, String name, String dimension, int x, int y, int z, int colorArgb) {
        MarkerFile file = files.computeIfAbsent(playerId, MarkerFile::new);
        if (file.markers.size() >= MAX_MARKERS_PER_PLAYER) {
            return Result.failure("You reached the limit of " + MAX_MARKERS_PER_PLAYER + " map markers.");
        }
        MapMarker marker = new MapMarker(UUID.randomUUID(), name, dimension, x, y, z, colorArgb);
        file.markers.add(marker);
        return Result.success(marker, "Marker created.");
    }

    public Result update(UUID playerId, UUID markerId, String name, int x, int y, int z, int colorArgb) {
        MapMarker marker = marker(playerId, markerId);
        if (marker == null) return Result.failure("Marker not found.");
        marker.update(name, x, y, z, colorArgb);
        return Result.success(marker, "Marker updated.");
    }

    public Result delete(UUID playerId, UUID markerId) {
        MarkerFile file = files.computeIfAbsent(playerId, MarkerFile::new);
        boolean removed = file.markers.removeIf(marker -> marker.id().equals(markerId));
        return removed ? Result.success(null, "Marker deleted.") : Result.failure("Marker not found.");
    }

    public void save() {
        if (folder == null) return;
        try {
            Files.createDirectories(folder);
            Set<Path> kept = new HashSet<>();
            for (Map.Entry<UUID, MarkerFile> entry : files.entrySet()) {
                entry.getValue().normalize(entry.getKey());
                Path file = StoragePaths.jsonFile(folder, entry.getKey().toString());
                recordStore.queueJson(GSON, file, entry.getValue());
                kept.add(file);
            }
            recordStore.queueDeleteMissing(kept);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save map markers.", e);
        }
    }

    public void clear() {
        files.clear();
        recordStore.reset();
        folder = null;
    }

    public record Result(boolean success, MapMarker marker, String message) {
        static Result success(MapMarker marker, String message) { return new Result(true, marker, message); }
        static Result failure(String message) { return new Result(false, null, message); }
    }

    private static final class MarkerFile {
        private int schema = CURRENT_SCHEMA;
        private String uuid = "";
        private String lastKnownName = "";
        private List<MapMarker> markers = new ArrayList<>();

        private MarkerFile() {
            // Gson.
        }

        private MarkerFile(UUID playerId) {
            uuid = playerId.toString();
        }

        private void normalize(UUID playerId) {
            schema = CURRENT_SCHEMA;
            uuid = playerId.toString();
            if (lastKnownName == null) lastKnownName = "";
            if (markers == null) markers = new ArrayList<>();
            List<MapMarker> normalized = new ArrayList<>(Math.min(MAX_MARKERS_PER_PLAYER, markers.size()));
            Set<UUID> ids = new HashSet<>();
            for (MapMarker marker : markers) {
                if (marker == null || normalized.size() >= MAX_MARKERS_PER_PLAYER) continue;
                marker.normalize();
                if (ids.add(marker.id())) normalized.add(marker);
            }
            markers = normalized;
        }
    }
}
