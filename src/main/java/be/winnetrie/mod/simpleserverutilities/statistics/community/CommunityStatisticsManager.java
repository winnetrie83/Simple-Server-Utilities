package be.winnetrie.mod.simpleserverutilities.statistics.community;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
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

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventBus;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Always-on curated activity history for community pages, leaderboards and future community goals.
 * It consumes Content Core events instead of installing a second set of gameplay hooks.
 */
public final class CommunityStatisticsManager {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long FLUSH_INTERVAL_TICKS = 100L;
    private static final long WEB_SNAPSHOT_INTERVAL_TICKS = 100L;

    private final Map<UUID, CommunityStatisticsRecord> players = new HashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();
    private final Set<UUID> futureSchemaPlayers = new HashSet<>();
    private final DirtyJsonRecordStore playerStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore serverStore = new DirtyJsonRecordStore();

    private MinecraftServer server;
    private Path rootFolder;
    private Path playersFolder;
    private Path serverFile;
    private CommunityStatisticsRecord serverStats = new CommunityStatisticsRecord();
    private ContentEventBus.Subscription subscription;
    private boolean serverDirty;
    private boolean futureSchemaServer;
    private long nextFlushTick;
    private long nextWebSnapshotTick;
    private long nextPeriodRefreshTick;
    private CommunityPeriodKeys currentKeys;
    private long trackedEvents;
    private volatile WebSnapshot webSnapshot = WebSnapshot.disabled();

    public synchronized void load(MinecraftServer server) {
        clear();
        this.server = server;
        rootFolder = StoragePaths.statistics(StoragePaths.root(server)).resolve("community");
        playersFolder = rootFolder.resolve("players");
        serverFile = rootFolder.resolve("server.json");
        int retention = Config.COMMUNITY_STATS_HISTORY_DAYS.get();
        try {
            Files.createDirectories(playersFolder);
            loadServer(retention);
            for (Path file : JsonStorage.listJsonFiles(playersFolder)) loadPlayerFile(file, retention);
            CommunityPeriodKeys keys = periodKeys();
            currentKeys = keys;
            rollAll(keys, retention);
            subscription = SimpleServerUtilities.CONTENT_EVENTS.subscribe(ContentEventBus.WILDCARD, this::onContentEvent);
            nextFlushTick = server.getTickCount() + FLUSH_INTERVAL_TICKS;
            nextWebSnapshotTick = 0L;
            nextPeriodRefreshTick = server.getTickCount() + 20L;
            rebuildWebSnapshot(keys);
            SimpleServerUtilities.LOGGER.info(
                    "Loaded SSU community statistics: {} player record(s), season '{}', {} day history retention.",
                    players.size(), keys.season(), retention);
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU community statistics.", exception);
        }
    }

    public synchronized void tick(MinecraftServer server) {
        if (this.server == null || server == null) return;
        long tick = server.getTickCount();
        int retention = Config.COMMUNITY_STATS_HISTORY_DAYS.get();
        if (currentKeys == null || tick >= nextPeriodRefreshTick) {
            CommunityPeriodKeys refreshed = periodKeys();
            if (currentKeys == null || !refreshed.equals(currentKeys)) {
                currentKeys = refreshed;
                rollAll(currentKeys, retention);
            }
            nextPeriodRefreshTick = tick + 20L;
        }
        if (tick >= nextFlushTick) {
            flushDirty();
            nextFlushTick = tick + FLUSH_INTERVAL_TICKS;
        }
        if (tick >= nextWebSnapshotTick) {
            rebuildWebSnapshot(currentKeys);
            nextWebSnapshotTick = tick + WEB_SNAPSHOT_INTERVAL_TICKS;
        }
    }

    public synchronized void saveAll() {
        if (server == null) return;
        dirtyPlayers.addAll(players.keySet());
        serverDirty = true;
        flushDirty();
    }

    public synchronized void clear() {
        if (subscription != null) {
            try { subscription.close(); } catch (RuntimeException ignored) { }
            subscription = null;
        }
        players.clear();
        dirtyPlayers.clear();
        futureSchemaPlayers.clear();
        playerStore.reset();
        serverStore.reset();
        serverStats = new CommunityStatisticsRecord();
        server = null;
        rootFolder = null;
        playersFolder = null;
        serverFile = null;
        serverDirty = false;
        futureSchemaServer = false;
        nextFlushTick = 0L;
        nextWebSnapshotTick = 0L;
        nextPeriodRefreshTick = 0L;
        currentKeys = null;
        trackedEvents = 0L;
        webSnapshot = WebSnapshot.disabled();
    }

    public WebSnapshot webSnapshot() {
        return webSnapshot;
    }

    private synchronized void onContentEvent(ContentEvent event) {
        if (event == null || event.playerId() == null || server == null) return;
        CommunityMetric metric = CommunityMetric.fromEvent(event.type());
        boolean derivedOnly = isDerivedActivityEvent(event.type());
        if (metric == null && !derivedOnly) return;
        // Countdown-join is an admission event; the same participant receives the canonical
        // MINIGAME_STARTED event again when the match actually enters RUNNING. Count once.
        if (ContentEventTypes.MINIGAME_STARTED.equals(event.type())
                && "countdown_join".equalsIgnoreCase(event.metadata().getOrDefault("phase", ""))) return;

        UUID playerId = event.playerId();
        CommunityStatisticsRecord player = ensurePlayer(playerId, playerName(playerId, event));
        if (player == null) return;

        boolean durable = "true".equalsIgnoreCase(event.metadata().getOrDefault("durable_event", "false"));
        if (durable && !player.rememberDurableEvent(event.eventId().toString())) return;

        CommunityPeriodKeys keys = currentKeys == null ? periodKeys() : currentKeys;
        int retention = Config.COMMUNITY_STATS_HISTORY_DAYS.get();
        player.rollTo(keys, retention);
        serverStats.rollTo(keys, retention);

        if (metric != null && event.amount() > 0L) {
            applyMetric(player, metric, event);
            applyMetric(serverStats, metric, event);
        }
        boolean onlineActivity = server.getPlayerList().getPlayer(playerId) != null;
        boolean firstActiveDay = applyDerivedPlayer(player, event, keys, onlineActivity);
        applyDerivedServer(serverStats, event, keys, firstActiveDay, onlineActivity);

        long now = System.currentTimeMillis();
        player.updatedAtEpochMilli = now;
        serverStats.updatedAtEpochMilli = now;
        dirtyPlayers.add(playerId);
        serverDirty = true;
        trackedEvents++;
    }

    private static boolean isDerivedActivityEvent(String type) {
        return ContentEventTypes.PLAYER_LOGIN.equals(type)
                || ContentEventTypes.PLAY_TIME.equals(type)
                || ContentEventTypes.BIOME_VISITED.equals(type)
                || ContentEventTypes.DIMENSION_VISITED.equals(type);
    }

    private static void applyMetric(CommunityStatisticsRecord record, CommunityMetric metric, ContentEvent event) {
        applyMetric(record.lifetime, metric, event);
        applyMetric(record.day.stats, metric, event);
        applyMetric(record.week.stats, metric, event);
        applyMetric(record.month.stats, metric, event);
        applyMetric(record.season.stats, metric, event);
    }

    private static void applyMetric(CommunityStatBucket bucket, CommunityMetric metric, ContentEvent event) {
        long amount = Math.max(0L, event.amount());
        if (amount <= 0L) return;
        bucket.add(metric.id(), amount);
        if (metric.subjectBreakdown() && meaningful(event.subject())) {
            bucket.addBreakdown(metric.id() + ".by_subject", event.subject(), amount);
        }
        String dimension = event.metadata().get("dimension");
        if (meaningful(dimension) && (!meaningful(event.subject()) || !dimension.equalsIgnoreCase(event.subject()))) {
            bucket.addBreakdown(metric.id() + ".by_dimension", dimension, amount);
        }
        String movement = event.metadata().get("movement");
        if (meaningful(movement)) bucket.addBreakdown(metric.id() + ".by_movement", movement, amount);
        String role = event.metadata().get("role");
        if (meaningful(role)) bucket.addBreakdown(metric.id() + ".by_role", role, amount);
        String team = event.metadata().get("team");
        if (meaningful(team)) bucket.addBreakdown(metric.id() + ".by_team", team, amount);
    }

    private static boolean applyDerivedPlayer(CommunityStatisticsRecord record, ContentEvent event, CommunityPeriodKeys keys, boolean onlineActivity) {
        String dayKey = keys.day();
        boolean firstActiveDay = false;
        if (onlineActivity) {
            firstActiveDay = record.lifetime.addDistinct("active_days", dayKey);
            record.day.stats.addDistinct("active_days", dayKey);
            record.week.stats.addDistinct("active_days", dayKey);
            record.month.stats.addDistinct("active_days", dayKey);
            record.season.stats.addDistinct("active_days", dayKey);
        }
        if (ContentEventTypes.BIOME_VISITED.equals(event.type()) && meaningful(event.subject())) {
            addDistinctEveryPeriod(record, "unique_biomes", event.subject());
        }
        if (ContentEventTypes.DIMENSION_VISITED.equals(event.type()) && meaningful(event.subject())) {
            addDistinctEveryPeriod(record, "unique_dimensions", event.subject());
        }
        return firstActiveDay;
    }

    private static void applyDerivedServer(CommunityStatisticsRecord record, ContentEvent event, CommunityPeriodKeys keys, boolean firstActiveDay, boolean onlineActivity) {
        String player = event.playerId().toString();
        if (onlineActivity) addDistinctEveryPeriod(record, "active_players", player);
        if (firstActiveDay) {
            record.lifetime.add("player_active_days", 1L);
            record.day.stats.add("player_active_days", 1L);
            record.week.stats.add("player_active_days", 1L);
            record.month.stats.add("player_active_days", 1L);
            record.season.stats.add("player_active_days", 1L);
        }
        if (ContentEventTypes.BIOME_VISITED.equals(event.type()) && meaningful(event.subject())) {
            addDistinctEveryPeriod(record, "unique_biomes", event.subject());
        }
        if (ContentEventTypes.DIMENSION_VISITED.equals(event.type()) && meaningful(event.subject())) {
            addDistinctEveryPeriod(record, "unique_dimensions", event.subject());
        }
    }

    private static void addDistinctEveryPeriod(CommunityStatisticsRecord record, String metricId, String key) {
        record.lifetime.addDistinct(metricId, key);
        record.day.stats.addDistinct(metricId, key);
        record.week.stats.addDistinct(metricId, key);
        record.month.stats.addDistinct(metricId, key);
        record.season.stats.addDistinct(metricId, key);
    }

    private CommunityStatisticsRecord ensurePlayer(UUID playerId, String name) {
        if (playerId == null || futureSchemaPlayers.contains(playerId)) return null;
        CommunityStatisticsRecord record = players.computeIfAbsent(playerId, ignored -> {
            CommunityStatisticsRecord created = new CommunityStatisticsRecord();
            created.ownerId = playerId.toString();
            return created;
        });
        if (name != null && !name.isBlank()) record.displayName = name;
        return record;
    }

    private String playerName(UUID id, ContentEvent event) {
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return online.getName().getString();
        }
        CommunityStatisticsRecord known = players.get(id);
        if (known != null && known.displayName != null && !known.displayName.isBlank()) return known.displayName;
        if (ContentEventTypes.PLAYER_LOGIN.equals(event.type()) && meaningful(event.subject())) return event.subject();
        return id.toString().substring(0, 8);
    }

    private void loadServer(int retention) {
        serverStats = new CommunityStatisticsRecord();
        serverStats.ownerId = "server";
        serverStats.displayName = "Server";
        if (!Files.exists(serverFile)) return;
        serverStore.discoverFile(serverFile);
        try {
            CommunityStatisticsRecord loaded = JsonStorage.read(GSON, serverFile, CommunityStatisticsRecord.class);
            if (loaded != null) {
                loaded.normalize(retention);
                serverStats = loaded;
                serverStats.ownerId = "server";
                serverStats.displayName = "Server";
            }
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("newer than supported")) {
                futureSchemaServer = true;
                SimpleServerUtilities.LOGGER.error("Refusing to overwrite future community statistics server schema: {}", serverFile, exception);
                return;
            }
            archiveBrokenServer(exception);
        } catch (Exception exception) {
            archiveBrokenServer(exception);
        }
    }

    private void archiveBrokenServer(Exception exception) {
        Path archived = JsonStorage.archiveBrokenFile(serverFile);
        serverStore.reset();
        serverStats = new CommunityStatisticsRecord();
        serverStats.ownerId = "server";
        serverStats.displayName = "Server";
        SimpleServerUtilities.LOGGER.error("Failed to load community server statistics. Archived as {}.", archived, exception);
    }

    private void loadPlayerFile(Path file, int retention) {
        UUID id;
        try { id = UUID.fromString(StoragePaths.fileBaseName(file)); }
        catch (RuntimeException exception) {
            Path archived = JsonStorage.archiveBrokenFile(file);
            SimpleServerUtilities.LOGGER.warn("Archived invalid community-statistics player filename as {}", archived);
            return;
        }
        playerStore.discoverFile(file);
        try {
            CommunityStatisticsRecord record = JsonStorage.read(GSON, file, CommunityStatisticsRecord.class);
            if (record == null) return;
            record.normalize(retention);
            if (!record.ownerId.isBlank() && !id.toString().equals(record.ownerId)) {
                throw new IllegalStateException("Community statistic UUID mismatch.");
            }
            record.ownerId = id.toString();
            players.put(id, record);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("newer than supported")) {
                futureSchemaPlayers.add(id);
                SimpleServerUtilities.LOGGER.error("Refusing to overwrite future community statistics player record {}.", file, exception);
                return;
            }
            archiveBrokenPlayer(id, file, exception);
        } catch (Exception exception) {
            archiveBrokenPlayer(id, file, exception);
        }
    }

    private void archiveBrokenPlayer(UUID id, Path file, Exception exception) {
        Path archived = JsonStorage.archiveBrokenFile(file);
        playerStore.forget(file);
        players.remove(id);
        SimpleServerUtilities.LOGGER.error("Failed to load community player statistics. Archived as {}.", archived, exception);
    }

    private void rollAll(CommunityPeriodKeys keys, int retention) {
        if (serverStats.rollTo(keys, retention)) serverDirty = true;
        for (Map.Entry<UUID, CommunityStatisticsRecord> entry : players.entrySet()) {
            if (entry.getValue().rollTo(keys, retention)) dirtyPlayers.add(entry.getKey());
        }
    }

    private void flushDirty() {
        if (server == null || playersFolder == null || serverFile == null) return;
        if (serverDirty) {
            if (!futureSchemaServer) serverStore.queueJson(GSON, serverFile, serverStats);
            serverDirty = false;
        }
        for (UUID id : Set.copyOf(dirtyPlayers)) {
            CommunityStatisticsRecord record = players.get(id);
            if (record != null && !futureSchemaPlayers.contains(id)) {
                playerStore.queueJson(GSON, playersFolder.resolve(id + ".json"), record);
            }
            dirtyPlayers.remove(id);
        }
    }

    private CommunityPeriodKeys periodKeys() {
        return CommunityPeriodKeys.now(Config.COMMUNITY_STATS_SEASON_ID.get());
    }

    private void rebuildWebSnapshot(CommunityPeriodKeys keys) {
        ServerView serverView = viewServer(serverStats);
        List<PlayerView> playerViews = new ArrayList<>(players.size());
        for (Map.Entry<UUID, CommunityStatisticsRecord> entry : players.entrySet()) {
            CommunityStatisticsRecord record = entry.getValue();
            playerViews.add(new PlayerView(entry.getKey().toString(), displayName(entry.getKey(), record),
                    valuesByPeriod(record)));
        }
        playerViews.sort(Comparator.comparing(PlayerView::name, String.CASE_INSENSITIVE_ORDER));
        webSnapshot = new WebSnapshot(true, SCHEMA_VERSION, System.currentTimeMillis(), keys.day(), keys.week(),
                keys.month(), keys.season(), CommunityMetric.catalog(), serverView, List.copyOf(playerViews), trackedEvents);
    }

    private String displayName(UUID id, CommunityStatisticsRecord record) {
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) return online.getName().getString();
        }
        if (record.displayName != null && !record.displayName.isBlank()) return record.displayName;
        return id.toString().substring(0, 8);
    }

    private static ServerView viewServer(CommunityStatisticsRecord record) {
        LinkedHashMap<String, PeriodView> periods = new LinkedHashMap<>();
        periods.put("lifetime", new PeriodView("lifetime", record.lifetime.view(true)));
        periods.put("day", new PeriodView(record.day.key, record.day.stats.view(true)));
        periods.put("week", new PeriodView(record.week.key, record.week.stats.view(true)));
        periods.put("month", new PeriodView(record.month.key, record.month.stats.view(true)));
        periods.put("season", new PeriodView(record.season.key, record.season.stats.view(true)));
        return new ServerView(Map.copyOf(periods), historyCopy(record.dailyHistory), historyCopy(record.weeklyHistory),
                historyCopy(record.monthlyHistory), historyCopy(record.seasonHistory));
    }

    private static Map<String, Map<String, Long>> valuesByPeriod(CommunityStatisticsRecord record) {
        LinkedHashMap<String, Map<String, Long>> periods = new LinkedHashMap<>();
        periods.put("lifetime", Map.copyOf(record.lifetime.values));
        periods.put("day", Map.copyOf(record.day.stats.values));
        periods.put("week", Map.copyOf(record.week.stats.values));
        periods.put("month", Map.copyOf(record.month.stats.values));
        periods.put("season", Map.copyOf(record.season.stats.values));
        return Map.copyOf(periods);
    }

    private static Map<String, Map<String, Long>> historyCopy(LinkedHashMap<String, Map<String, Long>> history) {
        LinkedHashMap<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : history.entrySet()) result.put(entry.getKey(), Map.copyOf(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    private static boolean meaningful(String value) {
        return value != null && !value.isBlank() && !"*".equals(value.trim()) && !"unknown".equals(value.trim().toLowerCase(Locale.ROOT));
    }

    public record WebSnapshot(boolean enabled, int schemaVersion, long generatedAtEpochMillis,
                              String day, String week, String month, String season,
                              Map<String, CommunityMetric.Descriptor> catalog,
                              ServerView server, List<PlayerView> players, long trackedEvents) {
        public static WebSnapshot disabled() {
            return new WebSnapshot(false, SCHEMA_VERSION, System.currentTimeMillis(), "", "", "", "",
                    CommunityMetric.catalog(), new ServerView(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()), List.of(), 0L);
        }
    }

    public record ServerView(Map<String, PeriodView> periods,
                             Map<String, Map<String, Long>> dailyHistory,
                             Map<String, Map<String, Long>> weeklyHistory,
                             Map<String, Map<String, Long>> monthlyHistory,
                             Map<String, Map<String, Long>> seasonHistory) { }
    public record PeriodView(String key, CommunityStatBucket.View stats) { }
    public record PlayerView(String uuid, String name, Map<String, Map<String, Long>> periods) { }
}
