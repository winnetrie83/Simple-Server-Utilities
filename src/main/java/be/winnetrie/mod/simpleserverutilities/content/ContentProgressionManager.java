package be.winnetrie.mod.simpleserverutilities.content;

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

/**
 * Server-authoritative persistence for generic content progress. This class intentionally
 * contains no quest, NPC, minigame or dungeon definitions.
 */
public final class ContentProgressionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long FLUSH_INTERVAL_TICKS = 100L;

    private final Map<UUID, PlayerProgressionData> players = new HashMap<>();
    private final Map<UUID, Path> knownPlayerFiles = new HashMap<>();
    private final Map<UUID, Long> lastAccessTick = new HashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();
    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private ServerProgressionData serverData = new ServerProgressionData();
    private MinecraftServer server;
    private Path playersFolder;
    private Path serverFile;
    private boolean serverDirty;
    private long nextFlushTick;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        Path progressionRoot = StoragePaths.contentProgression(StoragePaths.root(server));
        playersFolder = StoragePaths.contentProgressionPlayers(StoragePaths.root(server));
        serverFile = progressionRoot.resolve("server.json");
        players.clear();
        knownPlayerFiles.clear();
        lastAccessTick.clear();
        dirtyPlayers.clear();
        recordStore.reset();
        serverData = new ServerProgressionData();
        serverDirty = false;
        nextFlushTick = server.getTickCount() + FLUSH_INTERVAL_TICKS;

        try {
            Files.createDirectories(playersFolder);
            loadServerData();
            for (Path file : JsonStorage.listJsonFiles(playersFolder)) indexPlayerFile(file);
            SimpleServerUtilities.LOGGER.info(
                    "Indexed Content & Progression Core records for {} player(s); records load on demand.",
                    knownPlayerFiles.size());
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load Content & Progression Core data.", exception);
        }
    }

    public synchronized void clear() {
        saveAll();
        players.clear();
        knownPlayerFiles.clear();
        lastAccessTick.clear();
        dirtyPlayers.clear();
        recordStore.reset();
        serverData = new ServerProgressionData();
        server = null;
        playersFolder = null;
        serverFile = null;
        serverDirty = false;
        nextFlushTick = 0L;
    }

    public synchronized void tick(MinecraftServer server) {
        if (this.server == null || server != this.server) return;
        long tick = server.getTickCount();
        if (tick + FLUSH_INTERVAL_TICKS < nextFlushTick) nextFlushTick = tick;
        if (tick < nextFlushTick) return;
        saveDirty();
        evictInactivePlayers(tick);
        nextFlushTick = tick + FLUSH_INTERVAL_TICKS;
    }

    public synchronized PlayerProgressionData ensurePlayer(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("Player is required.");
        UUID playerId = player.getUUID();
        PlayerProgressionData data = ensureLoaded(playerId);
        if (data == null) {
            data = new PlayerProgressionData(playerId, player.getName().getString());
            players.put(playerId, data);
            touch(data, playerId);
            return data;
        }
        String currentName = player.getName().getString();
        if (!currentName.equals(data.lastKnownName)) {
            data.lastKnownName = currentName;
            touch(data, playerId);
        }
        markAccess(playerId);
        return data;
    }

    public synchronized boolean hasPlayerFlag(UUID playerId, String rawKey) {
        PlayerProgressionData data = ensureLoaded(playerId);
        return data != null && data.flags.contains(ContentId.normalize(rawKey));
    }

    public synchronized void setPlayerFlag(ServerPlayer player, String rawKey, boolean enabled) {
        PlayerProgressionData data = ensurePlayer(player);
        String key = ContentId.require(rawKey, "Player flag");
        boolean changed = enabled ? data.flags.add(key) : data.flags.remove(key);
        if (changed) touch(data, player.getUUID());
    }

    public synchronized long playerCounter(UUID playerId, String rawKey) {
        PlayerProgressionData data = ensureLoaded(playerId);
        return data == null ? 0L : Math.max(0L, data.counters.getOrDefault(ContentId.normalize(rawKey), 0L));
    }

    public synchronized long setPlayerCounter(ServerPlayer player, String rawKey, long value) {
        PlayerProgressionData data = ensurePlayer(player);
        String key = ContentId.require(rawKey, "Player counter");
        long normalized = Math.max(0L, value);
        long previous = data.counters.getOrDefault(key, 0L);
        if (normalized == 0L) data.counters.remove(key); else data.counters.put(key, normalized);
        if (previous != normalized) touch(data, player.getUUID());
        return normalized;
    }

    public synchronized long addPlayerCounter(ServerPlayer player, String rawKey, long delta) {
        return setPlayerCounter(player, rawKey, saturatingNonNegativeAdd(playerCounter(player.getUUID(), rawKey), delta));
    }

    public synchronized boolean isPlayerUnlocked(UUID playerId, String rawKey) {
        PlayerProgressionData data = ensureLoaded(playerId);
        return data != null && data.unlocks.contains(ContentId.normalize(rawKey));
    }

    public synchronized void setPlayerUnlocked(ServerPlayer player, String rawKey, boolean unlocked) {
        PlayerProgressionData data = ensurePlayer(player);
        String key = ContentId.require(rawKey, "Player unlock");
        boolean changed = unlocked ? data.unlocks.add(key) : data.unlocks.remove(key);
        if (changed) touch(data, player.getUUID());
    }

    public synchronized int reputation(UUID playerId, String rawFaction) {
        PlayerProgressionData data = ensureLoaded(playerId);
        return data == null ? 0 : data.reputation.getOrDefault(ContentId.normalize(rawFaction), 0);
    }

    public synchronized int setReputation(ServerPlayer player, String rawFaction, long value) {
        PlayerProgressionData data = ensurePlayer(player);
        String faction = ContentId.require(rawFaction, "Faction");
        int normalized = PlayerProgressionData.clampReputation(value);
        int previous = data.reputation.getOrDefault(faction, 0);
        if (normalized == 0) data.reputation.remove(faction); else data.reputation.put(faction, normalized);
        if (previous != normalized) touch(data, player.getUUID());
        return normalized;
    }

    public synchronized int addReputation(ServerPlayer player, String rawFaction, long delta) {
        return setReputation(player, rawFaction, (long) reputation(player.getUUID(), rawFaction) + delta);
    }

    public synchronized boolean hasServerFlag(String rawKey) {
        return serverData.flags.contains(ContentId.normalize(rawKey));
    }

    public synchronized void setServerFlag(String rawKey, boolean enabled) {
        String key = ContentId.require(rawKey, "Server flag");
        boolean changed = enabled ? serverData.flags.add(key) : serverData.flags.remove(key);
        if (changed) touchServer();
    }

    public synchronized long serverCounter(String rawKey) {
        return Math.max(0L, serverData.counters.getOrDefault(ContentId.normalize(rawKey), 0L));
    }

    public synchronized long setServerCounter(String rawKey, long value) {
        String key = ContentId.require(rawKey, "Server counter");
        long normalized = Math.max(0L, value);
        long previous = serverData.counters.getOrDefault(key, 0L);
        if (normalized == 0L) serverData.counters.remove(key); else serverData.counters.put(key, normalized);
        if (previous != normalized) touchServer();
        return normalized;
    }

    public synchronized long addServerCounter(String rawKey, long delta) {
        return setServerCounter(rawKey, saturatingNonNegativeAdd(serverCounter(rawKey), delta));
    }

    public synchronized boolean isServerUnlocked(String rawKey) {
        return serverData.unlocks.contains(ContentId.normalize(rawKey));
    }

    public synchronized void setServerUnlocked(String rawKey, boolean unlocked) {
        String key = ContentId.require(rawKey, "Server unlock");
        boolean changed = unlocked ? serverData.unlocks.add(key) : serverData.unlocks.remove(key);
        if (changed) touchServer();
    }

    public synchronized void savePlayer(UUID playerId) {
        if (playerId == null || !players.containsKey(playerId)) return;
        savePlayerInternal(playerId);
    }

    public synchronized void saveDirty() {
        for (UUID playerId : Set.copyOf(dirtyPlayers)) savePlayerInternal(playerId);
        if (serverDirty) saveServerInternal();
    }

    public synchronized void saveAll() {
        dirtyPlayers.addAll(players.keySet());
        serverDirty = true;
        saveDirty();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(Math.max(knownPlayerFiles.size(), players.size()), totalPlayerFlags(), totalPlayerCounters(), totalPlayerUnlocks(),
                totalReputationEntries(), serverData.flags.size(), serverData.counters.size(), serverData.unlocks.size(),
                dirtyPlayers.size() + (serverDirty ? 1 : 0));
    }

    private void loadServerData() {
        if (serverFile == null) return;
        if (!Files.exists(serverFile)) {
            serverData = new ServerProgressionData();
            serverDirty = true;
            saveServerInternal();
            return;
        }
        try {
            recordStore.discoverFile(serverFile);
            ServerProgressionData loaded = JsonStorage.read(GSON, serverFile, ServerProgressionData.class);
            serverData = loaded == null ? new ServerProgressionData() : loaded;
            serverData.normalize();
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(serverFile);
            serverData = new ServerProgressionData();
            serverDirty = true;
            SimpleServerUtilities.LOGGER.error(
                    "Failed to load server progression data. Broken file archived as {}.", archived, exception);
            saveServerInternal();
        }
    }

    private void indexPlayerFile(Path file) {
        try {
            UUID playerId = UUID.fromString(StoragePaths.fileBaseName(file));
            knownPlayerFiles.put(playerId, file);
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(file);
            SimpleServerUtilities.LOGGER.error(
                    "Invalid player progression filename. Broken file archived as {}.", archived, exception);
        }
    }

    private PlayerProgressionData ensureLoaded(UUID playerId) {
        if (playerId == null) return null;
        PlayerProgressionData existing = players.get(playerId);
        if (existing != null) {
            markAccess(playerId);
            return existing;
        }
        Path file = knownPlayerFiles.get(playerId);
        if (file == null || !Files.exists(file)) return null;
        try {
            recordStore.discoverFile(file);
            PlayerProgressionData data = JsonStorage.read(GSON, file, PlayerProgressionData.class);
            if (data == null) throw new IllegalArgumentException("Empty player progression file.");
            data.normalize(playerId);
            players.put(playerId, data);
            markAccess(playerId);
            return data;
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(file);
            knownPlayerFiles.remove(playerId);
            SimpleServerUtilities.LOGGER.error(
                    "Failed to load player progression data on demand. Broken file archived as {}.",
                    archived, exception);
            return null;
        }
    }

    private void markAccess(UUID playerId) {
        if (playerId == null || server == null) return;
        lastAccessTick.put(playerId, (long) server.getTickCount());
    }

    private void evictInactivePlayers(long currentTick) {
        if (server == null) return;
        long idleLimit = 20L * 60L * 10L;
        for (UUID playerId : Set.copyOf(players.keySet())) {
            if (dirtyPlayers.contains(playerId)) continue;
            if (server.getPlayerList().getPlayer(playerId) != null) continue;
            Path file = knownPlayerFiles.get(playerId);
            if (file != null && (SimpleServerUtilities.STORAGE.hasPending(file)
                    || SimpleServerUtilities.STORAGE.requiresRetry(file))) continue;
            long last = lastAccessTick.getOrDefault(playerId, currentTick);
            if (currentTick - last < idleLimit) continue;
            players.remove(playerId);
            lastAccessTick.remove(playerId);
        }
    }

    private void savePlayerInternal(UUID playerId) {
        if (playersFolder == null) return;
        PlayerProgressionData data = players.get(playerId);
        if (data == null) {
            dirtyPlayers.remove(playerId);
            return;
        }
        Path file = StoragePaths.jsonFile(playersFolder, playerId.toString());
        recordStore.queueJson(GSON, file, data);
        knownPlayerFiles.put(playerId, file);
        dirtyPlayers.remove(playerId);
    }

    private void saveServerInternal() {
        if (serverFile == null) return;
        recordStore.queueJson(GSON, serverFile, serverData);
        serverDirty = false;
    }

    private void touch(PlayerProgressionData data, UUID playerId) {
        data.updatedAtEpochMilli = System.currentTimeMillis();
        dirtyPlayers.add(playerId);
        markAccess(playerId);
    }

    private void touchServer() {
        serverData.updatedAtEpochMilli = System.currentTimeMillis();
        serverDirty = true;
    }

    private int totalPlayerFlags() { return players.values().stream().mapToInt(data -> data.flags.size()).sum(); }
    private int totalPlayerCounters() { return players.values().stream().mapToInt(data -> data.counters.size()).sum(); }
    private int totalPlayerUnlocks() { return players.values().stream().mapToInt(data -> data.unlocks.size()).sum(); }
    private int totalReputationEntries() { return players.values().stream().mapToInt(data -> data.reputation.size()).sum(); }

    private static long saturatingNonNegativeAdd(long current, long delta) {
        current = Math.max(0L, current);
        if (delta >= 0L) {
            return Long.MAX_VALUE - current < delta ? Long.MAX_VALUE : current + delta;
        }
        if (delta == Long.MIN_VALUE) return 0L;
        long magnitude = -delta;
        return magnitude >= current ? 0L : current - magnitude;
    }

    public record Snapshot(
            int players,
            int playerFlags,
            int playerCounters,
            int playerUnlocks,
            int reputationEntries,
            int serverFlags,
            int serverCounters,
            int serverUnlocks,
            int pendingWrites
    ) {
    }
}
