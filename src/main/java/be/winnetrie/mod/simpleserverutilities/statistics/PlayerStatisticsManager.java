package be.winnetrie.mod.simpleserverutilities.statistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventBus;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative custom-statistic definitions with lazy player storage and Content Core event updates. */
public final class PlayerStatisticsManager {
    public static final int MAX_DEFINITIONS = 128;
    public static final int MAX_LEADERBOARD_LINES = 64;
    private static final int DEFINITION_FILE_SCHEMA = 2;
    private static final long PLAYER_CACHE_TTL_TICKS = 20L * 60L * 10L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DecimalFormat DECIMAL = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final Map<String, PlayerStatisticDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, PlayerStatisticValues> players = new HashMap<>();
    private final Map<UUID, Path> knownPlayerFiles = new HashMap<>();
    private final Map<UUID, Long> lastAccessTick = new HashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();
    private final Set<UUID> futureSchemaPlayers = new HashSet<>();
    private final EnumMap<StatisticEventType, EventIndex> indexes = new EnumMap<>(StatisticEventType.class);
    private final DirtyJsonRecordStore definitionStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore playerStore = new DirtyJsonRecordStore();
    private MinecraftServer server;
    private Path definitionsFile;
    private Path playersFolder;
    private long nextFlushTick;
    private long nextEvictTick;
    private long eventChecks;
    private long appliedUpdates;
    private ContentEventBus.Subscription eventSubscription;

    public synchronized void load(MinecraftServer server) {
        closeSubscription();
        this.server = server;
        Path root = StoragePaths.statistics(StoragePaths.root(server));
        definitionsFile = root.resolve("definitions.json");
        playersFolder = root.resolve("players");
        definitions.clear();
        players.clear();
        knownPlayerFiles.clear();
        lastAccessTick.clear();
        dirtyPlayers.clear();
        futureSchemaPlayers.clear();
        indexes.clear();
        definitionStore.reset();
        playerStore.reset();
        nextFlushTick = server.getTickCount() + 100L;
        nextEvictTick = server.getTickCount() + 1200L;
        eventChecks = 0L;
        appliedUpdates = 0L;
        try {
            Files.createDirectories(playersFolder);
            loadDefinitions();
            for (Path file : JsonStorage.listJsonFiles(playersFolder)) {
                try {
                    UUID id = UUID.fromString(StoragePaths.fileBaseName(file));
                    knownPlayerFiles.put(id, file);
                } catch (RuntimeException exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.warn("Archived invalid statistic player filename as {}", archived);
                }
            }
            rebuildIndexes();
            eventSubscription = SimpleServerUtilities.CONTENT_EVENTS.subscribe(ContentEventBus.WILDCARD, this::onContentEvent);
            SimpleServerUtilities.LOGGER.info("Loaded {} custom statistic definitions and indexed {} player statistic records (lazy loaded).",
                    definitions.size(), knownPlayerFiles.size());
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load custom player statistics.", exception);
        }
    }

    private void loadDefinitions() {
        if (!Files.exists(definitionsFile)) {
            saveDefinitions();
            return;
        }
        definitionStore.discoverFile(definitionsFile);
        try {
            DefinitionSaveData data = JsonStorage.read(GSON, definitionsFile, DefinitionSaveData.class);
            if (data != null && data.schemaVersion > DEFINITION_FILE_SCHEMA) {
                throw new IllegalStateException("Statistic definition file schema " + data.schemaVersion
                        + " is newer than supported schema " + DEFINITION_FILE_SCHEMA + ".");
            }
            if (data != null && data.definitions != null) {
                for (PlayerStatisticDefinition definition : data.definitions) {
                    if (definition == null || definitions.size() >= MAX_DEFINITIONS) continue;
                    try {
                        definition.normalize();
                        if (definitions.putIfAbsent(definition.id, definition) != null) {
                            SimpleServerUtilities.LOGGER.warn("Ignoring duplicate custom statistic definition ID while loading: {}", definition.id);
                        }
                    } catch (RuntimeException exception) {
                        SimpleServerUtilities.LOGGER.warn("Ignoring invalid statistic definition while loading: {}",
                                definition.id, exception);
                    }
                }
            }
        } catch (Exception exception) {
            // Never overwrite a future-schema file: that would make a downgrade destructive.
            if (exception instanceof IllegalStateException && exception.getMessage() != null
                    && exception.getMessage().contains("newer than supported")) {
                SimpleServerUtilities.LOGGER.error("Refusing to load or overwrite future custom-statistics schema: {}", definitionsFile, exception);
                return;
            }
            Path archived = JsonStorage.archiveBrokenFile(definitionsFile);
            definitionStore.reset();
            definitions.clear();
            SimpleServerUtilities.LOGGER.error("Failed to load custom statistic definitions. Broken file archived as {}", archived, exception);
            saveDefinitions();
        }
    }

    public synchronized void tick(MinecraftServer server) {
        if (this.server == null) return;
        long tick = server.getTickCount();
        if (tick >= nextFlushTick) {
            saveDirtyPlayers();
            nextFlushTick = tick + 100L;
        }
        if (tick >= nextEvictTick) {
            evictInactivePlayers(tick);
            nextEvictTick = tick + 1200L;
        }
    }

    public synchronized void clear() {
        closeSubscription();
        definitions.clear();
        players.clear();
        knownPlayerFiles.clear();
        lastAccessTick.clear();
        dirtyPlayers.clear();
        futureSchemaPlayers.clear();
        indexes.clear();
        definitionStore.reset();
        playerStore.reset();
        server = null;
        definitionsFile = null;
        playersFolder = null;
        nextFlushTick = 0L;
        nextEvictTick = 0L;
    }

    public synchronized Collection<PlayerStatisticDefinition> definitions() {
        return definitions.values().stream().sorted(Comparator.comparing(value -> value.id)).toList();
    }

    public synchronized PlayerStatisticDefinition get(String rawId) {
        return definitions.get(PlayerStatisticDefinition.sanitizeId(rawId));
    }

    public synchronized boolean put(String originalId, PlayerStatisticDefinition rawDefinition) {
        if (rawDefinition == null) return false;
        PlayerStatisticDefinition definition = rawDefinition.normalize();
        String oldId = originalId == null || originalId.isBlank() ? "" : PlayerStatisticDefinition.sanitizeId(originalId);
        PlayerStatisticDefinition previous;
        if (oldId.isBlank()) {
            if (definitions.containsKey(definition.id) || definitions.size() >= MAX_DEFINITIONS) return false;
            previous = null;
        } else {
            previous = definitions.get(oldId);
            if (previous == null) return false;
            if (!oldId.equals(definition.id) && definitions.containsKey(definition.id)) return false;
        }
        if (previous != null) definition.createdAtEpochMilli = previous.createdAtEpochMilli;
        definition.updatedAtEpochMilli = System.currentTimeMillis();
        if (!oldId.isBlank() && !oldId.equals(definition.id)) {
            loadAllPlayers();
            definitions.remove(oldId);
            for (Map.Entry<UUID, PlayerStatisticValues> entry : players.entrySet()) {
                Long value = entry.getValue().values.remove(oldId);
                if (value != null) {
                    entry.getValue().values.put(definition.id, value);
                    entry.getValue().updatedAtEpochMilli = definition.updatedAtEpochMilli;
                    dirtyPlayers.add(entry.getKey());
                }
            }
        }
        definitions.put(definition.id, definition);
        rebuildIndexes();
        saveDefinitions();
        saveDirtyPlayers();
        return true;
    }

    public synchronized boolean setEnabled(String rawId, boolean enabled) {
        PlayerStatisticDefinition definition = get(rawId);
        if (definition == null) return false;
        definition.enabled = enabled;
        definition.updatedAtEpochMilli = System.currentTimeMillis();
        rebuildIndexes();
        saveDefinitions();
        return true;
    }

    public synchronized boolean delete(String rawId) {
        String id = PlayerStatisticDefinition.sanitizeId(rawId);
        if (definitions.remove(id) == null) return false;
        loadAllPlayers();
        for (Map.Entry<UUID, PlayerStatisticValues> entry : players.entrySet()) {
            if (entry.getValue().values.remove(id) != null) dirtyPlayers.add(entry.getKey());
        }
        rebuildIndexes();
        saveDefinitions();
        saveDirtyPlayers();
        return true;
    }

    public synchronized boolean reset(String rawId) {
        String id = PlayerStatisticDefinition.sanitizeId(rawId);
        if (!definitions.containsKey(id)) return false;
        loadAllPlayers();
        for (Map.Entry<UUID, PlayerStatisticValues> entry : players.entrySet()) {
            if (entry.getValue().values.remove(id) != null) dirtyPlayers.add(entry.getKey());
        }
        saveDirtyPlayers();
        return true;
    }

    /** Compatibility entry point; new gameplay code should publish a ContentEvent instead. */
    public synchronized void increment(ServerPlayer player, StatisticEventType type, String rawTarget, long amount) {
        if (player == null) return;
        increment(player.getUUID(), player.getName().getString(), type, rawTarget, amount);
    }

    private synchronized void onContentEvent(ContentEvent event) {
        if (event == null || event.playerId() == null || server == null) return;
        StatisticEventType type = StatisticEventType.fromContentEvent(event.type());
        if (type == null || indexes.get(type) == null) return;
        String name = "";
        ServerPlayer online = server.getPlayerList().getPlayer(event.playerId());
        if (online != null) name = online.getName().getString();
        boolean durable = "true".equalsIgnoreCase(event.metadata().getOrDefault("durable_event", "false"));
        PlayerStatisticValues durableValues = null;
        String durableKey = event.eventId().toString();
        if (durable) {
            durableValues = ensurePlayer(event.playerId(), name);
            if (durableValues == null) return;
            if (durableValues.processedDurableEvents.contains(durableKey)) return;
        }
        increment(event.playerId(), name, type, event.subject(), Math.max(0L, event.amount()));
        if (durable) {
            durableValues.processedDurableEvents.add(durableKey);
            durableValues.updatedAtEpochMilli = System.currentTimeMillis();
            dirtyPlayers.add(event.playerId());
            saveDirtyPlayers(Set.of(event.playerId()));
        }
    }

    private void increment(UUID playerId, String playerName, StatisticEventType type, String rawTarget, long amount) {
        if (playerId == null || type == null || amount <= 0L) return;
        EventIndex index = indexes.get(type);
        if (index == null) return;
        String target = rawTarget == null || rawTarget.isBlank() ? "*" : rawTarget.toLowerCase(Locale.ROOT);
        eventChecks++;
        List<String> ids = index.match(target);
        if (ids.isEmpty()) return;
        PlayerStatisticValues values = ensurePlayer(playerId, playerName);
        if (values == null) return;
        for (String id : ids) {
            values.values.merge(id, amount, PlayerStatisticsManager::saturatingAdd);
            appliedUpdates++;
        }
        values.updatedAtEpochMilli = System.currentTimeMillis();
        dirtyPlayers.add(playerId);
    }

    public synchronized long value(UUID playerId, String rawId) {
        PlayerStatisticValues values = loadPlayer(playerId);
        if (values == null) return 0L;
        touch(playerId);
        return Math.max(0L, values.values.getOrDefault(PlayerStatisticDefinition.sanitizeId(rawId), 0L));
    }

    public synchronized String formattedValue(UUID playerId, String rawId) {
        PlayerStatisticDefinition definition = get(rawId);
        if (definition == null) return "-";
        return format(definition, value(playerId, definition.id));
    }

    public synchronized int rank(UUID playerId, String rawId) {
        String id = PlayerStatisticDefinition.sanitizeId(rawId);
        if (!definitions.containsKey(id)) return 0;
        loadAllPlayers();
        long own = value(playerId, id);
        if (own <= 0L) return 0;
        int rank = 1;
        for (Map.Entry<UUID, PlayerStatisticValues> entry : players.entrySet()) {
            if (!entry.getKey().equals(playerId) && entry.getValue().values.getOrDefault(id, 0L) > own) rank++;
        }
        return rank;
    }

    public synchronized List<LeaderboardEntry> leaderboard(String rawId, int limit) {
        PlayerStatisticDefinition definition = get(rawId);
        if (definition == null) return List.of();
        loadAllPlayers();
        String id = definition.id;
        return players.entrySet().stream()
                .map(entry -> new LeaderboardEntry(entry.getKey(), displayName(entry.getKey(), entry.getValue()),
                        Math.max(0L, entry.getValue().values.getOrDefault(id, 0L))))
                .filter(entry -> entry.value() > 0L)
                .sorted(Comparator.comparingLong(LeaderboardEntry::value).reversed()
                        .thenComparing(LeaderboardEntry::name, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, Math.min(MAX_LEADERBOARD_LINES, limit))).toList();
    }

    public synchronized int playerCount(String rawId) {
        loadAllPlayers();
        String id = PlayerStatisticDefinition.sanitizeId(rawId);
        return (int) players.values().stream().filter(value -> value.values.getOrDefault(id, 0L) > 0L).count();
    }

    public synchronized long total(String rawId) {
        loadAllPlayers();
        String id = PlayerStatisticDefinition.sanitizeId(rawId);
        long total = 0L;
        for (PlayerStatisticValues value : players.values()) total = saturatingAdd(total, value.values.getOrDefault(id, 0L));
        return total;
    }

    public synchronized String format(PlayerStatisticDefinition definition, long rawValue) {
        String number = definition.eventType.decimal() ? DECIMAL.format(rawValue / 100.0D) : Long.toString(rawValue);
        return definition.unit == null || definition.unit.isBlank() ? number : number + " " + definition.unit;
    }

    public synchronized StatisticsSnapshot snapshot() {
        return new StatisticsSnapshot(definitions.size(), Math.max(knownPlayerFiles.size(), players.size()),
                eventChecks, appliedUpdates, dirtyPlayers.size());
    }

    public synchronized void saveAll() {
        saveDefinitions();
        dirtyPlayers.addAll(players.keySet());
        saveDirtyPlayers();
    }

    public synchronized void savePlayer(UUID playerId) {
        if (playerId == null || !players.containsKey(playerId)) return;
        dirtyPlayers.add(playerId);
        saveDirtyPlayers(Set.of(playerId));
    }

    private PlayerStatisticValues ensurePlayer(UUID id, String name) {
        if (id == null || futureSchemaPlayers.contains(id)) return null;
        PlayerStatisticValues value = loadPlayer(id);
        if (value == null) {
            if (futureSchemaPlayers.contains(id)) return null;
            value = new PlayerStatisticValues(id, name);
        }
        players.put(id, value);
        value.uuid = id.toString();
        if (name != null && !name.isBlank()) value.lastKnownName = name;
        touch(id);
        return value;
    }

    private PlayerStatisticValues loadPlayer(UUID id) {
        if (id == null) return null;
        PlayerStatisticValues cached = players.get(id);
        if (cached != null) { touch(id); return cached; }
        Path file = knownPlayerFiles.get(id);
        if (file == null || !Files.exists(file)) return null;
        try {
            playerStore.discoverFile(file);
            PlayerStatisticValues value = JsonStorage.read(GSON, file, PlayerStatisticValues.class);
            if (value == null) return null;
            value.normalize();
            if (!id.toString().equals(value.uuid)) throw new IllegalStateException("Player statistic UUID mismatch.");
            players.put(id, value);
            touch(id);
            return value;
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("newer than supported")) {
                futureSchemaPlayers.add(id);
                SimpleServerUtilities.LOGGER.error("Refusing to load or overwrite future player statistic record {}: {}", file, exception.getMessage());
                return null;
            }
            Path archived = JsonStorage.archiveBrokenFile(file);
            knownPlayerFiles.remove(id); playerStore.forget(file);
            SimpleServerUtilities.LOGGER.error("Failed to load custom player statistics. Archived: {}", archived, exception);
            return null;
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(file);
            knownPlayerFiles.remove(id);
            playerStore.forget(file);
            SimpleServerUtilities.LOGGER.error("Failed to load custom player statistics. Archived: {}", archived, exception);
            return null;
        }
    }

    private void loadAllPlayers() {
        for (UUID id : Set.copyOf(knownPlayerFiles.keySet())) loadPlayer(id);
    }

    private void touch(UUID id) {
        if (id != null && server != null) lastAccessTick.put(id, (long) server.getTickCount());
    }

    private void evictInactivePlayers(long tick) {
        long cutoff = tick - PLAYER_CACHE_TTL_TICKS;
        for (UUID id : Set.copyOf(players.keySet())) {
            if (dirtyPlayers.contains(id)) continue;
            if (server != null && server.getPlayerList().getPlayer(id) != null) continue;
            if (lastAccessTick.getOrDefault(id, 0L) >= cutoff) continue;
            players.remove(id);
            lastAccessTick.remove(id);
        }
    }

    private String displayName(UUID id, PlayerStatisticValues value) {
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return online.getName().getString();
        }
        if (value != null && value.lastKnownName != null && !value.lastKnownName.isBlank()) return value.lastKnownName;
        return id.toString().substring(0, 8);
    }

    private void rebuildIndexes() {
        indexes.clear();
        for (PlayerStatisticDefinition definition : definitions.values()) {
            if (definition.enabled) indexes.computeIfAbsent(definition.eventType, ignored -> new EventIndex()).add(definition);
        }
    }

    private void saveDefinitions() {
        if (definitionsFile == null) return;
        DefinitionSaveData data = new DefinitionSaveData();
        data.schemaVersion = DEFINITION_FILE_SCHEMA;
        data.definitions = new ArrayList<>(definitions.values());
        data.definitions.sort(Comparator.comparing(value -> value.id));
        definitionStore.queueJson(GSON, definitionsFile, data);
    }

    private void saveDirtyPlayers() { saveDirtyPlayers(Set.copyOf(dirtyPlayers)); }

    private void saveDirtyPlayers(Set<UUID> ids) {
        if (playersFolder == null || ids.isEmpty()) return;
        try {
            Files.createDirectories(playersFolder);
            for (UUID id : ids) {
                PlayerStatisticValues value = players.get(id);
                if (value == null) { dirtyPlayers.remove(id); continue; }
                value.normalize();
                Path file = StoragePaths.jsonFile(playersFolder, id.toString());
                playerStore.queueJson(GSON, file, value);
                knownPlayerFiles.put(id, file);
                dirtyPlayers.remove(id);
            }
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to queue custom statistic player storage.", exception);
        }
    }

    private void closeSubscription() {
        if (eventSubscription != null) {
            try { eventSubscription.close(); } catch (RuntimeException exception) {
                SimpleServerUtilities.LOGGER.warn("Failed to close custom-statistics Content Event subscription cleanly.", exception);
            }
            eventSubscription = null;
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return Math.max(0L, left);
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return Math.max(0L, left) + right;
    }

    private static final class EventIndex {
        private final List<String> wildcard = new ArrayList<>();
        private final Map<String, List<String>> exact = new HashMap<>();
        void add(PlayerStatisticDefinition definition) {
            if (!definition.eventType.targetSupported() || "*".equals(definition.target)) wildcard.add(definition.id);
            else exact.computeIfAbsent(definition.target, ignored -> new ArrayList<>()).add(definition.id);
        }
        List<String> match(String target) {
            List<String> exactValues = exact.get(target);
            if (wildcard.isEmpty()) return exactValues == null ? List.of() : exactValues;
            if (exactValues == null || exactValues.isEmpty()) return wildcard;
            ArrayList<String> result = new ArrayList<>(wildcard.size() + exactValues.size());
            result.addAll(wildcard); result.addAll(exactValues); return result;
        }
    }

    private static final class DefinitionSaveData {
        int schemaVersion = DEFINITION_FILE_SCHEMA;
        ArrayList<PlayerStatisticDefinition> definitions = new ArrayList<>();
    }

    public record LeaderboardEntry(UUID playerId, String name, long value) {}
    public record StatisticsSnapshot(int definitions, int playerRecords, long eventChecks, long appliedUpdates, int dirtyPlayers) {}
}
