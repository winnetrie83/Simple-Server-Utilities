package be.winnetrie.mod.simpleserverutilities.spawn;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Persistent owner for the single server-wide respawn and first-join lobby destinations. */
public final class ServerSpawnManager {

    public static final int SCHEMA_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private Path saveFile;
    private ServerSpawn spawn;
    private ServerSpawn lobbySpawn;

    public void load(MinecraftServer server) {
        saveFile = StoragePaths.serverSpawn(StoragePaths.root(server));
        spawn = null;
        lobbySpawn = null;
        recordStore.reset();

        try {
            Files.createDirectories(saveFile.getParent());
            if (!Files.exists(saveFile)) return;
            recordStore.discoverFile(saveFile);
            JsonElement root = JsonParser.parseString(Files.readString(saveFile));
            if (root != null && root.isJsonObject() && root.getAsJsonObject().has("serverSpawn")) {
                Data loaded = GSON.fromJson(root, Data.class);
                if (loaded != null) {
                    spawn = valid(loaded.serverSpawn) ? loaded.serverSpawn : null;
                    lobbySpawn = valid(loaded.lobbySpawn) ? loaded.lobbySpawn : null;
                }
            } else {
                // Schema 1 migration: the file itself was the one server-spawn object.
                ServerSpawn legacy = GSON.fromJson(root, ServerSpawn.class);
                if (valid(legacy)) spawn = legacy;
                save();
            }
            SimpleServerUtilities.LOGGER.info("Loaded SSU locations: server spawn={}, lobby spawn={}.",
                    spawn == null ? "unset" : spawn.getDimension(),
                    lobbySpawn == null ? "unset" : lobbySpawn.getDimension());
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(saveFile);
            SimpleServerUtilities.LOGGER.error("Failed to load SSU spawn locations. Broken file archived as: {}", archived, exception);
        }
    }

    public void save() {
        if (saveFile == null) return;
        if (spawn == null && lobbySpawn == null) {
            SimpleServerUtilities.STORAGE.queueDelete(saveFile);
            recordStore.forget(saveFile);
            return;
        }
        recordStore.queueJson(GSON, saveFile, new Data(SCHEMA_VERSION, spawn, lobbySpawn));
    }

    public boolean isSet() { return spawn != null; }
    public ServerSpawn get() { return spawn; }
    public boolean isLobbySet() { return lobbySpawn != null; }
    public ServerSpawn getLobby() { return lobbySpawn; }

    public void set(ServerPlayer player) {
        spawn = updated(spawn, player);
        save();
    }

    public void setLobby(ServerPlayer player) {
        lobbySpawn = updated(lobbySpawn, player);
        save();
    }

    public boolean clear() {
        if (spawn == null) return false;
        spawn = null;
        save();
        return true;
    }

    public boolean clearLobby() {
        if (lobbySpawn == null) return false;
        lobbySpawn = null;
        save();
        return true;
    }

    private static ServerSpawn updated(ServerSpawn existing, ServerPlayer player) {
        ServerSpawn value = existing == null ? new ServerSpawn() : existing;
        value.update(player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.getUUID(), player.getName().getString(), System.currentTimeMillis());
        return value;
    }

    private static boolean valid(ServerSpawn value) {
        return value != null && value.getDimension() != null && !value.getDimension().isBlank();
    }

    private record Data(int schemaVersion, ServerSpawn serverSpawn, ServerSpawn lobbySpawn) {
    }
}
