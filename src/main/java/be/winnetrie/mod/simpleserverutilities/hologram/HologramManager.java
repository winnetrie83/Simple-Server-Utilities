package be.winnetrie.mod.simpleserverutilities.hologram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.network.HologramSyncPayload;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.neoforged.neoforge.network.PacketDistributor;

/** Persistent, server-authoritative source for floating text, links, scoreboards and image definitions. */
public final class HologramManager {
    public static final int MAX_HOLOGRAMS = 512;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern STAT_VALUE_PATTERN = Pattern.compile("\\{\\{stat:([a-zA-Z0-9._-]{1,64})}}", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAT_RANK_PATTERN = Pattern.compile("\\{\\{rank:([a-zA-Z0-9._-]{1,64})}}", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINE_TOKEN_PATTERN = Pattern.compile("\\{mine:([a-zA-Z0-9._-]{1,64}):(name|remaining|mined|reset|blocks|resets)}", Pattern.CASE_INSENSITIVE);

    private final Map<String, HologramDefinition> holograms = new LinkedHashMap<>();
    private final Map<String, Map<Long, Set<String>>> spatialIndex = new HashMap<>();
    private final DirtyJsonRecordStore recordStore = new DirtyJsonRecordStore();
    private MinecraftServer server;
    private Path saveFile;
    private long nextProximitySync;
    private final Map<String, Long> nextScoreboardRefresh = new HashMap<>();
    private boolean moduleEnabledLastTick;
    private final Map<UUID, List<HologramSyncPayload.Entry>> lastPlayerSnapshots = new HashMap<>();

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        this.saveFile = StoragePaths.holograms(StoragePaths.root(server)).resolve("holograms.json");
        this.nextProximitySync = 0L;
        nextScoreboardRefresh.clear();
        this.moduleEnabledLastTick = Config.ENABLE_HOLOGRAMS.get();
        lastPlayerSnapshots.clear();
        holograms.clear();
        spatialIndex.clear();
        recordStore.reset();

        try {
            Files.createDirectories(saveFile.getParent());
            if (!Files.exists(saveFile)) {
                save();
                return;
            }
            recordStore.discoverFile(saveFile);
            SaveData data = JsonStorage.read(GSON, saveFile, SaveData.class);
            boolean migrated = data != null && data.schemaVersion < 4;
            if (data != null && data.holograms != null) {
                for (HologramDefinition value : data.holograms) {
                    if (value == null) continue;
                    migrated |= value.schemaVersion < 4;
                    value.normalize();
                    holograms.put(value.id, value);
                }
            }
            rebuildSpatialIndex();
            if (migrated) save();
            SimpleServerUtilities.LOGGER.info("Loaded {} SSU hologram definitions.", holograms.size());
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(saveFile);
            SimpleServerUtilities.LOGGER.error("Failed to load holograms. Broken file archived as {}", archived, exception);
        }
    }

    public synchronized void save() {
        if (saveFile == null) return;
        SaveData data = new SaveData();
        data.holograms = new ArrayList<>(holograms.values());
        data.holograms.sort(Comparator.comparing(value -> value.id));
        recordStore.queueJson(GSON, saveFile, data);
    }


    /** Immediately removes all hologram render state from connected clients. */
    public synchronized void clearClients(MinecraftServer activeServer) {
        if (activeServer == null) return;
        HologramSyncPayload empty = new HologramSyncPayload(List.of());
        for (ServerPlayer player : activeServer.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, empty);
        }
        lastPlayerSnapshots.clear();
    }

    public synchronized void clear() {
        holograms.clear();
        spatialIndex.clear();
        recordStore.reset();
        server = null;
        saveFile = null;
        nextProximitySync = 0L;
        nextScoreboardRefresh.clear();
        moduleEnabledLastTick = false;
        lastPlayerSnapshots.clear();
    }

    public synchronized boolean put(HologramDefinition definition) {
        if (definition == null) return false;
        definition.normalize();
        if (definition.id.isBlank()) return false;
        if (!holograms.containsKey(definition.id) && holograms.size() >= MAX_HOLOGRAMS) return false;
        holograms.put(definition.id, definition);
        rebuildSpatialIndex();
        scheduleNextScoreboardRefresh(definition);
        save();
        syncAll();
        return true;
    }

    public synchronized boolean replace(String rawOriginalId, HologramDefinition definition) {
        if (definition == null) return false;
        String originalId = HologramDefinition.sanitizeId(rawOriginalId);
        if (!holograms.containsKey(originalId)) return false;
        definition.normalize();
        if (definition.id.isBlank()) return false;
        if (!originalId.equals(definition.id) && holograms.containsKey(definition.id)) return false;
        holograms.remove(originalId);
        nextScoreboardRefresh.remove(originalId);
        holograms.put(definition.id, definition);
        rebuildSpatialIndex();
        scheduleNextScoreboardRefresh(definition);
        save();
        syncAll();
        return true;
    }

    public synchronized boolean delete(String rawId) {
        HologramDefinition removed = holograms.remove(HologramDefinition.sanitizeId(rawId));
        if (removed == null) return false;
        nextScoreboardRefresh.remove(removed.id);
        rebuildSpatialIndex();
        save();
        syncAll();
        return true;
    }

    public synchronized HologramDefinition get(String rawId) {
        return holograms.get(HologramDefinition.sanitizeId(rawId));
    }

    public synchronized Collection<HologramDefinition> all() {
        List<HologramDefinition> values = new ArrayList<>(holograms.values());
        values.sort(Comparator.comparing(value -> value.id));
        return List.copyOf(values);
    }

    public synchronized SpatialStatistics spatialStatistics() {
        int cells = 0;
        int references = 0;
        int maximumBucket = 0;
        for (Map<Long, Set<String>> dimension : spatialIndex.values()) {
            cells += dimension.size();
            for (Set<String> bucket : dimension.values()) {
                references += bucket.size();
                maximumBucket = Math.max(maximumBucket, bucket.size());
            }
        }
        return new SpatialStatistics(holograms.size(), spatialIndex.size(), cells, references, maximumBucket);
    }

    public record SpatialStatistics(
            int holograms,
            int dimensions,
            int cells,
            int references,
            int maximumBucketSize
    ) {
    }

    public void tick(MinecraftServer server) {
        boolean enabled = Config.ENABLE_HOLOGRAMS.get();
        if (!enabled) {
            if (moduleEnabledLastTick) {
                moduleEnabledLastTick = false;
                syncAll(true);
            }
            return;
        }
        if (!moduleEnabledLastTick) {
            moduleEnabledLastTick = true;
            nextProximitySync = 0L;
            synchronized (this) {
                nextScoreboardRefresh.clear();
            }
        }
        long tick = server.getTickCount();
        boolean proximityDue = tick >= nextProximitySync;
        Set<String> dueScoreboards = dueScoreboards(tick);
        if (!proximityDue && dueScoreboards.isEmpty()) return;
        if (proximityDue) nextProximitySync = tick + 20L;
        syncAll(false, dueScoreboards);

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());
        synchronized (this) {
            lastPlayerSnapshots.keySet().removeIf(uuid -> !online.contains(uuid));
        }
    }

    public synchronized void syncAll() {
        syncAll(true, null);
    }

    private synchronized void syncAll(boolean force) {
        syncAll(force, null);
    }

    /** A null set refreshes every scoreboard; an empty set reuses every cached scoreboard. */
    private synchronized void syncAll(boolean force, Set<String> refreshScoreboardIds) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player, force, refreshScoreboardIds);
        }
    }

    public synchronized void syncPlayer(ServerPlayer player) {
        syncPlayer(player, true, null);
    }

    private void syncPlayer(ServerPlayer player, boolean force, Set<String> refreshScoreboardIds) {
        if (player == null) return;
        List<HologramSyncPayload.Entry> entries = buildNearbySnapshot(player, refreshScoreboardIds);
        List<HologramSyncPayload.Entry> previous = lastPlayerSnapshots.get(player.getUUID());
        if (!force && entries.equals(previous)) return;
        lastPlayerSnapshots.put(player.getUUID(), entries);
        PacketDistributor.sendToPlayer(player, new HologramSyncPayload(entries));
    }

    private List<HologramSyncPayload.Entry> buildNearbySnapshot(
            ServerPlayer player,
            Set<String> refreshScoreboardIds
    ) {
        if (!Config.ENABLE_HOLOGRAMS.get()) return List.of();
        int globalDistance = Config.HOLOGRAM_RENDER_DISTANCE.get();
        Map<String, HologramSyncPayload.Entry> previousById = new HashMap<>();
        List<HologramSyncPayload.Entry> previous = lastPlayerSnapshots.get(player.getUUID());
        if (previous != null) {
            for (HologramSyncPayload.Entry entry : previous) previousById.put(entry.id(), entry);
        }
        String dimension = player.level().dimension().location().toString();
        List<HologramSyncPayload.Entry> entries = new ArrayList<>();
        for (HologramDefinition definition : nearbyDefinitions(
                dimension, player.getX(), player.getZ(), globalDistance)) {
            if (!definition.enabled || !dimension.equals(definition.dimension)) continue;
            if (definition.type == HologramType.IMAGE && isRemoteImage(definition.imageSource)
                    && !Config.ALLOW_REMOTE_HOLOGRAM_IMAGES.get()) continue;
            double effectiveDistance = Math.max(8.0D, Math.min((double) globalDistance, definition.viewDistance));
            double dx = definition.x - player.getX();
            double dy = definition.y - player.getY();
            double dz = definition.z - player.getZ();
            if (dx * dx + dy * dy + dz * dz > (double) effectiveDistance * effectiveDistance) continue;
            List<String> cachedScoreboardLines = null;
            boolean refreshScoreboard = refreshScoreboardIds == null
                    || refreshScoreboardIds.contains(definition.id);
            if (!refreshScoreboard && definition.type == HologramType.SCOREBOARD) {
                HologramSyncPayload.Entry previousEntry = previousById.get(definition.id);
                if (previousEntry != null) cachedScoreboardLines = previousEntry.lines();
            }
            entries.add(toSnapshot(player, definition, effectiveDistance, cachedScoreboardLines));
        }
        return List.copyOf(entries);
    }

    private List<HologramDefinition> nearbyDefinitions(
            String dimension,
            double x,
            double z,
            int range
    ) {
        Map<Long, Set<String>> dimensionIndex = spatialIndex.get(dimension);
        if (dimensionIndex == null || dimensionIndex.isEmpty()) return List.of();
        int centerX = ((int) Math.floor(x)) >> 4;
        int centerZ = ((int) Math.floor(z)) >> 4;
        int radius = Math.max(1, (int) Math.ceil(Math.max(8, range) / 16.0D));
        Set<String> ids = new LinkedHashSet<>();
        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                Set<String> bucket = dimensionIndex.get(chunkKey(chunkX, chunkZ));
                if (bucket != null) ids.addAll(bucket);
            }
        }
        List<HologramDefinition> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            HologramDefinition definition = holograms.get(id);
            if (definition != null) result.add(definition);
        }
        result.sort(Comparator.comparing(value -> value.id));
        return result;
    }

    private void rebuildSpatialIndex() {
        spatialIndex.clear();
        for (HologramDefinition definition : holograms.values()) {
            int chunkX = ((int) Math.floor(definition.x)) >> 4;
            int chunkZ = ((int) Math.floor(definition.z)) >> 4;
            spatialIndex.computeIfAbsent(definition.dimension, ignored -> new HashMap<>())
                    .computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new LinkedHashSet<>())
                    .add(definition.id);
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
    }

    private HologramSyncPayload.Entry toSnapshot(
            ServerPlayer player,
            HologramDefinition definition,
            double effectiveDistance,
            List<String> cachedScoreboardLines
    ) {
        List<String> lines = switch (definition.type) {
            case TEXT, LINK -> HologramRichText.splitLines(resolveStatisticPlaceholders(player, definition.text), 64);
            case SCOREBOARD -> cachedScoreboardLines == null
                    ? scoreboardLines(player, definition)
                    : cachedScoreboardLines;
            case IMAGE -> List.of();
        };
        return new HologramSyncPayload.Entry(
                definition.id,
                definition.type,
                definition.dimension,
                definition.x,
                definition.y,
                definition.z,
                definition.color,
                definition.backgroundColor,
                definition.scale,
                definition.bold,
                definition.italic,
                definition.underlined,
                definition.strikethrough,
                definition.shadow,
                definition.seeThrough,
                effectiveDistance,
                definition.url,
                definition.imageSource,
                definition.imageWidth,
                definition.imageHeight,
                lines
        );
    }

    private List<String> scoreboardLines(ServerPlayer player, HologramDefinition definition) {
        if (definition.objective != null
                && definition.objective.toLowerCase(java.util.Locale.ROOT).startsWith("ssu:")) {
            return customStatisticLines(player, definition);
        }
        if (server == null || definition.objective.isBlank()) {
            List<String> lines = new ArrayList<>(HologramRichText.splitLines(
                    definition.text.isBlank() ? "Scoreboard" : definition.text, definition.maxLines));
            addLineIfRoom(lines, "Objective not configured", definition.maxLines);
            return limitLines(lines, definition.maxLines);
        }
        Objective objective = server.getScoreboard().getObjective(definition.objective);
        String title = resolveStatisticPlaceholders(
                player, definition.text.isBlank() ? definition.objective : definition.text);
        if (objective == null) {
            List<String> lines = new ArrayList<>(HologramRichText.splitLines(title, definition.maxLines));
            addLineIfRoom(lines, "Missing objective: " + definition.objective, definition.maxLines);
            return limitLines(lines, definition.maxLines);
        }

        List<String> lines = new ArrayList<>(HologramRichText.splitLines(title, definition.maxLines));
        int titleLineCount = lines.size();
        if (definition.scoreboardMode == HologramScoreboardMode.SELF) {
            ReadOnlyScoreInfo score = server.getScoreboard().getPlayerScoreInfo(player, objective);
            addLineIfRoom(lines, player.getScoreboardName() + ": " + (score == null ? 0 : score.value()),
                    definition.maxLines);
            return limitLines(lines, definition.maxLines);
        }

        int remaining = Math.max(0, definition.maxLines - lines.size());
        if (remaining > 0) {
            server.getScoreboard().listPlayerScores(objective).stream()
                    .filter(entry -> !entry.isHidden())
                    .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                    .limit(remaining)
                    .forEach(entry -> lines.add(entry.ownerName().getString() + ": " + entry.value()));
        }
        if (remaining > 0 && lines.size() == titleLineCount) {
            addLineIfRoom(lines, "No scores yet", definition.maxLines);
        }
        return limitLines(lines, definition.maxLines);
    }

    private List<String> customStatisticLines(ServerPlayer player, HologramDefinition definition) {
        String id = definition.objective.substring(4).trim();
        var statistic = SimpleServerUtilities.STATISTICS.get(id);
        String titleSource = definition.text.isBlank()
                ? (statistic == null ? id : statistic.displayName) : definition.text;
        List<String> lines = new ArrayList<>(HologramRichText.splitLines(
                resolveStatisticPlaceholders(player, titleSource), definition.maxLines));
        if (!Config.ENABLE_CUSTOM_STATISTICS.get()
                || !SimpleServerUtilities.CORE.modules().isActive("statistics")) {
            addLineIfRoom(lines, "Statistics module disabled", definition.maxLines);
            return limitLines(lines, definition.maxLines);
        }
        if (statistic == null) {
            addLineIfRoom(lines, "Missing statistic: " + id, definition.maxLines);
            return limitLines(lines, definition.maxLines);
        }
        if (definition.scoreboardMode == HologramScoreboardMode.SELF) {
            addLineIfRoom(lines, player.getScoreboardName() + ": "
                    + SimpleServerUtilities.STATISTICS.formattedValue(player.getUUID(), statistic.id),
                    definition.maxLines);
            return limitLines(lines, definition.maxLines);
        }

        int remaining = Math.max(0, definition.maxLines - lines.size());
        List<be.winnetrie.mod.simpleserverutilities.statistics.PlayerStatisticsManager.LeaderboardEntry> leaders =
                remaining == 0 ? List.of()
                        : SimpleServerUtilities.STATISTICS.leaderboard(statistic.id, remaining);
        int rank = 1;
        for (var entry : leaders) {
            lines.add(rank++ + ". " + entry.name() + ": "
                    + SimpleServerUtilities.STATISTICS.format(statistic, entry.value()));
        }
        if (leaders.isEmpty()) addLineIfRoom(lines, "No values yet", definition.maxLines);
        return limitLines(lines, definition.maxLines);
    }

    private String resolveStatisticPlaceholders(ServerPlayer player, String raw) {
        String value = raw == null ? "" : raw;
        if (!Config.ENABLE_CUSTOM_STATISTICS.get()
                || !SimpleServerUtilities.CORE.modules().isActive("statistics")) {
            value = replaceStatisticPattern(value, STAT_VALUE_PATTERN, ignored -> "-");
            value = replaceStatisticPattern(value, STAT_RANK_PATTERN, ignored -> "-");
        } else {
            value = replaceStatisticPattern(value, STAT_VALUE_PATTERN, id ->
                    SimpleServerUtilities.STATISTICS.formattedValue(player.getUUID(), id));
            value = replaceStatisticPattern(value, STAT_RANK_PATTERN, id -> {
                int rank = SimpleServerUtilities.STATISTICS.rank(player.getUUID(), id);
                return rank <= 0 ? "-" : Integer.toString(rank);
            });
        }
        return replaceMineTokens(value);
    }

    private static String replaceMineTokens(String input) {
        Matcher matcher = MINE_TOKEN_PATTERN.matcher(input == null ? "" : input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = SsuModuleAccess.active("mines")
                    ? SimpleServerUtilities.MINES.statusToken(matcher.group(1), matcher.group(2))
                    : "-";
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceStatisticPattern(String input, Pattern pattern,
            java.util.function.Function<String, String> replacement) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = replacement.apply(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void addLineIfRoom(List<String> lines, String value, int maximum) {
        if (lines.size() < Math.max(1, maximum)) lines.add(value);
    }

    private static boolean isRemoteImage(String source) {
        if (source == null) return false;
        String value = source.trim().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static List<String> limitLines(List<String> lines, int maximum) {
        if (lines.size() <= maximum) return List.copyOf(lines);
        return List.copyOf(lines.subList(0, maximum));
    }

    private synchronized Set<String> dueScoreboards(long currentTick) {
        Set<String> activeIds = new HashSet<>();
        Set<String> due = new HashSet<>();
        for (HologramDefinition definition : holograms.values()) {
            if (!definition.enabled || definition.type != HologramType.SCOREBOARD) continue;
            activeIds.add(definition.id);
            long nextRefresh = nextScoreboardRefresh.getOrDefault(definition.id, 0L);
            if (currentTick >= nextRefresh) {
                due.add(definition.id);
                nextScoreboardRefresh.put(definition.id,
                        currentTick + Math.max(10, definition.updateIntervalTicks));
            }
        }
        nextScoreboardRefresh.keySet().removeIf(id -> !activeIds.contains(id));
        return Set.copyOf(due);
    }

    private synchronized void scheduleNextScoreboardRefresh(HologramDefinition definition) {
        if (definition == null || definition.type != HologramType.SCOREBOARD || !definition.enabled) {
            if (definition != null) nextScoreboardRefresh.remove(definition.id);
            return;
        }
        long currentTick = server == null ? 0L : server.getTickCount();
        nextScoreboardRefresh.put(definition.id,
                currentTick + Math.max(10, definition.updateIntervalTicks));
    }


    private static final class SaveData {
        private int schemaVersion = 4;
        private ArrayList<HologramDefinition> holograms = new ArrayList<>();
    }
}
