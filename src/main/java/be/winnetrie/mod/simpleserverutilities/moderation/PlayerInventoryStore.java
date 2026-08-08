package be.winnetrie.mod.simpleserverutilities.moderation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Lazy-ish durable SSU inventory mirror used for offline editing and jail recovery. */
public final class PlayerInventoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final DirtyJsonRecordStore records = new DirtyJsonRecordStore();
    private final Map<UUID, PlayerInventorySnapshot> snapshots = new HashMap<>();
    private Path folder;

    public synchronized void load(MinecraftServer server) {
        folder = StoragePaths.playerInventorySnapshots(StoragePaths.root(server));
        records.reset(); snapshots.clear();
        try {
            Files.createDirectories(folder); records.discover(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    UUID id = UUID.fromString(StoragePaths.fileBaseName(file));
                    PlayerInventorySnapshot snapshot = JsonStorage.read(GSON, file, PlayerInventorySnapshot.class);
                    if (snapshot != null) { snapshot.normalize(); snapshots.put(id, snapshot); }
                } catch (Exception exception) { JsonStorage.archiveBrokenFile(file); }
            }
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load player inventory snapshots.", exception);
        }
    }

    public synchronized void saveAll() { for (var e : snapshots.entrySet()) save(e.getKey(), e.getValue()); }
    public synchronized PlayerInventorySnapshot get(UUID id) { return snapshots.get(id); }
    public synchronized PlayerInventorySnapshot getOrCreate(UUID id, String name) {
        PlayerInventorySnapshot value = snapshots.computeIfAbsent(id, ignored -> new PlayerInventorySnapshot());
        if (name != null && !name.isBlank()) value.lastKnownName = name;
        value.normalize(); return value;
    }
    public synchronized PlayerInventorySnapshot capture(ServerPlayer player) {
        PlayerInventorySnapshot snapshot = PlayerInventorySnapshot.capture(player);
        snapshots.put(player.getUUID(), snapshot); save(player.getUUID(), snapshot); return snapshot;
    }
    public synchronized void applyPending(ServerPlayer player) {
        PlayerInventorySnapshot snapshot = snapshots.get(player.getUUID());
        if (snapshot != null && snapshot.pendingApply) { snapshot.apply(player); save(player.getUUID(), snapshot); }
        else capture(player);
    }
    public synchronized void markModified(UUID id, PlayerInventorySnapshot snapshot, boolean offline) {
        snapshot.normalize(); snapshot.pendingApply = offline; snapshot.updatedAt = System.currentTimeMillis(); snapshots.put(id, snapshot); save(id, snapshot);
    }
    private void save(UUID id, PlayerInventorySnapshot snapshot) { if (folder != null) records.queueJson(GSON, StoragePaths.jsonFile(folder,id.toString()),snapshot); }
}
