package be.winnetrie.mod.simpleserverutilities.spawn;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Persistent owner for the single server-wide spawn destination. */
public final class ServerSpawnManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private Path saveFile;
    private ServerSpawn spawn;

    public void load(MinecraftServer server) {
        saveFile = StoragePaths.serverSpawn(StoragePaths.root(server));
        spawn = null;
        recordStore.reset();

        try {
            Files.createDirectories(saveFile.getParent());
            if (!Files.exists(saveFile)) {
                return;
            }

            recordStore.discoverFile(saveFile);
            ServerSpawn loaded = JsonStorage.read(GSON, saveFile, ServerSpawn.class);
            if (loaded == null || loaded.getDimension() == null || loaded.getDimension().isBlank()) {
                return;
            }
            spawn = loaded;
            SimpleServerUtilities.LOGGER.info("Loaded server spawn in {}.", spawn.getDimension());
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(saveFile);
            SimpleServerUtilities.LOGGER.error("Failed to load server spawn. Broken file archived as: {}", archived, exception);
        }
    }

    public void save() {
        if (saveFile == null) {
            return;
        }
        if (spawn == null) {
            SimpleServerUtilities.STORAGE.queueDelete(saveFile);
            recordStore.forget(saveFile);
            return;
        }
        recordStore.queueJson(GSON, saveFile, spawn);
    }

    public boolean isSet() {
        return spawn != null;
    }

    public ServerSpawn get() {
        return spawn;
    }

    public void set(ServerPlayer player) {
        long now = System.currentTimeMillis();
        if (spawn == null) {
            spawn = new ServerSpawn();
        }
        spawn.update(
                player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.getUUID(), player.getName().getString(), now
        );
        save();
    }

    public boolean clear() {
        if (spawn == null) {
            return false;
        }
        spawn = null;
        save();
        return true;
    }
}
