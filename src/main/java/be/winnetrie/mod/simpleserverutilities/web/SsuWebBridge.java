package be.winnetrie.mod.simpleserverutilities.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcManager;
import be.winnetrie.mod.simpleserverutilities.statistics.community.CommunityMetric;
import be.winnetrie.mod.simpleserverutilities.statistics.community.CommunityStatisticsManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Opt-in read-only HTTP bridge for website integrations.
 * Minecraft state is copied on the server thread; HTTP worker threads only read immutable snapshots.
 */
public final class SsuWebBridge {
    public static final int API_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.offline());
    private final AtomicReference<CommunityStatisticsManager.WebSnapshot> communityStats =
            new AtomicReference<>(CommunityStatisticsManager.WebSnapshot.disabled());
    private HttpServer http;
    private ExecutorService executor;
    private long nextSnapshotTick;

    public synchronized void start(MinecraftServer server) {
        stop();
        nextSnapshotTick = 0L;
        refreshSnapshot(server, true);
        if (!Config.ENABLE_WEB_API.get()) return;
        String envToken = System.getenv("SSU_WEB_API_TOKEN");
        String token = envToken != null && !envToken.isBlank() ? envToken.trim() : Config.WEB_API_TOKEN.get().trim();
        if (token.length() < 16) {
            SimpleServerUtilities.LOGGER.error("SSU Web API is enabled but webApiToken has fewer than 16 characters; API will not start.");
            return;
        }
        String bind = Config.WEB_API_BIND_ADDRESS.get().trim();
        int port = Config.WEB_API_PORT.get();
        String allowedOrigin = Config.WEB_API_ALLOWED_ORIGIN.get().trim();
        try {
            http = HttpServer.create(new InetSocketAddress(bind, port), 0);
            http.createContext("/api/v1/", exchange -> handle(exchange, token, allowedOrigin));
            executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "SSU-WebApi"); thread.setDaemon(true); return thread;
            });
            http.setExecutor(executor);
            http.start();
            SimpleServerUtilities.LOGGER.info("SSU read-only Web API listening on {}:{} (API v{}).", bind, port, API_VERSION);
            if (!isLoopback(bind)) SimpleServerUtilities.LOGGER.warn(
                    "SSU Web API is bound to '{}'. Use a firewall/reverse proxy and never expose the Minecraft server port as an HTTP security boundary.", bind);
        } catch (IOException | RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error("Could not start SSU Web API on {}:{}.", bind, port, exception);
            stop();
        }
    }

    public void tick(MinecraftServer server) {
        if (server == null) return;
        long tick = server.getTickCount();
        if (tick < nextSnapshotTick) return;
        nextSnapshotTick = tick + 20L;
        refreshSnapshot(server, false);
    }

    public synchronized void stop() {
        if (http != null) { http.stop(0); http = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
        nextSnapshotTick = 0L;
        snapshot.set(Snapshot.offline());
        communityStats.set(CommunityStatisticsManager.WebSnapshot.disabled());
    }

    private void refreshSnapshot(MinecraftServer server, boolean force) {
        if (server == null) {
            snapshot.set(Snapshot.offline());
            communityStats.set(CommunityStatisticsManager.WebSnapshot.disabled());
            return;
        }
        if (!force && !Config.ENABLE_WEB_API.get()) return;
        List<PlayerRow> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            players.add(new PlayerRow(player.getUUID().toString(), player.getName().getString(),
                    player.level().dimension().identifier().toString()));
        }
        boolean statsEnabled = SimpleServerUtilities.CORE.modules().isActive("community_statistics");
        CommunityStatisticsManager.WebSnapshot stats = statsEnabled
                ? SimpleServerUtilities.COMMUNITY_STATISTICS.webSnapshot()
                : CommunityStatisticsManager.WebSnapshot.disabled();
        communityStats.set(stats);
        NpcManager.RuntimeStatistics npc = SimpleServerUtilities.NPCS.runtimeStatistics();
        snapshot.set(new Snapshot(API_VERSION, true, System.currentTimeMillis(), server.getTickCount(), players.size(),
                List.copyOf(players), new NpcRow(npc.definitions(), npc.placements(), npc.scheduledPlacements()),
                new Capabilities(true, true, stats.enabled(), false)));
    }

    private void handle(HttpExchange exchange, String expectedToken, String allowedOrigin) throws IOException {
        try {
            addBaseHeaders(exchange, allowedOrigin);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handlePreflight(exchange, allowedOrigin); return; }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { send(exchange, 405, Map.of("error", "method_not_allowed")); return; }
            if (!authorized(exchange, expectedToken)) { send(exchange, 401, Map.of("error", "unauthorized")); return; }
            String path = exchange.getRequestURI().getPath();
            Snapshot current = snapshot.get();
            CommunityStatisticsManager.WebSnapshot stats = communityStats.get();
            switch (path) {
                case "/api/v1/health" -> send(exchange, 200, Map.of("ok", current.running(), "apiVersion", API_VERSION));
                case "/api/v1/status" -> send(exchange, 200, current);
                case "/api/v1/players" -> send(exchange, 200, Map.of("online", current.onlinePlayers(), "players", current.players()));
                case "/api/v1/capabilities" -> send(exchange, 200, current.capabilities());
                case "/api/v1/stats/catalog" -> handleStatsCatalog(exchange, stats);
                case "/api/v1/stats/server" -> handleStatsServer(exchange, stats);
                case "/api/v1/stats/players" -> handleStatsPlayers(exchange, stats);
                case "/api/v1/stats/leaderboard" -> handleLeaderboard(exchange, stats);
                case "/api/v1/stats/history" -> handleHistory(exchange, stats);
                default -> {
                    if (path.startsWith("/api/v1/stats/player/")) handlePlayerStats(exchange, stats, path);
                    else send(exchange, 404, Map.of("error", "not_found"));
                }
            }
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.warn("SSU Web API request failed: {}", exchange.getRequestURI(), exception);
            if (exchange.getResponseCode() < 0) send(exchange, 500, Map.of("error", "internal_error"));
        } finally { exchange.close(); }
    }

    private static void handleStatsCatalog(HttpExchange exchange, CommunityStatisticsManager.WebSnapshot stats) throws IOException {
        if (!stats.enabled()) { send(exchange, 503, Map.of("error", "community_statistics_disabled")); return; }
        send(exchange, 200, Map.of(
                "schemaVersion", stats.schemaVersion(),
                "periods", List.of("lifetime", "day", "week", "month", "season"),
                "periodKeys", Map.of("day", stats.day(), "week", stats.week(), "month", stats.month(), "season", stats.season()),
                "metrics", stats.catalog()));
    }

    private static void handleStatsServer(HttpExchange exchange, CommunityStatisticsManager.WebSnapshot stats) throws IOException {
        if (!stats.enabled()) { send(exchange, 503, Map.of("error", "community_statistics_disabled")); return; }
        send(exchange, 200, Map.of(
                "generatedAtEpochMillis", stats.generatedAtEpochMillis(),
                "trackedEventsSinceStartup", stats.trackedEvents(),
                "periodKeys", Map.of("day", stats.day(), "week", stats.week(), "month", stats.month(), "season", stats.season()),
                "server", stats.server()));
    }

    private static void handleStatsPlayers(HttpExchange exchange, CommunityStatisticsManager.WebSnapshot stats) throws IOException {
        if (!stats.enabled()) { send(exchange, 503, Map.of("error", "community_statistics_disabled")); return; }
        Map<String, String> query = query(exchange);
        String period = period(query.get("period"));
        int limit = intQuery(query.get("limit"), 100, 1, 500);
        List<PlayerPeriodRow> rows = stats.players().stream()
                .limit(limit)
                .map(player -> new PlayerPeriodRow(player.uuid(), player.name(), period,
                        player.periods().getOrDefault(period, Map.of())))
                .toList();
        send(exchange, 200, Map.of("period", period, "count", rows.size(), "players", rows));
    }

    private static void handlePlayerStats(HttpExchange exchange, CommunityStatisticsManager.WebSnapshot stats, String path) throws IOException {
        if (!stats.enabled()) { send(exchange, 503, Map.of("error", "community_statistics_disabled")); return; }
        String raw = path.substring("/api/v1/stats/player/".length()).trim();
        if (raw.isBlank()) { send(exchange, 400, Map.of("error", "player_required")); return; }
        CommunityStatisticsManager.PlayerView player = stats.players().stream()
                .filter(value -> value.uuid().equalsIgnoreCase(raw) || value.name().equalsIgnoreCase(raw))
                .findFirst().orElse(null);
        if (player == null) { send(exchange, 404, Map.of("error", "player_not_found")); return; }
        send(exchange, 200, player);
    }

    private static void handleLeaderboard(HttpExchange exchange, CommunityStatisticsManager.WebSnapshot stats) throws IOException {
        if (!stats.enabled()) { send(exchange, 503, Map.of("error", "community_statistics_disabled")); return; }
        Map<String, String> query = query(exchange);
        String metricId = normalizeMetric(query.getOrDefault("metric", "play_time_seconds"));
        CommunityMetric.Descriptor descriptor = stats.catalog().get(metricId);
        if (descriptor == null) { send(exchange, 400, Map.of("error", "unknown_metric", "metric", metricId)); return; }
        String period = period(query.get("period"));
        int limit = intQuery(query.get("limit"), 10, 1, 100);
        List<LeaderboardSeed> seeds = new ArrayList<>();
        for (CommunityStatisticsManager.PlayerView player : stats.players()) {
            long value = player.periods().getOrDefault(period, Map.of()).getOrDefault(metricId, 0L);
            if (value > 0L) seeds.add(new LeaderboardSeed(player.uuid(), player.name(), value));
        }
        seeds.sort(Comparator.comparingLong(LeaderboardSeed::value).reversed()
                .thenComparing(LeaderboardSeed::name, String.CASE_INSENSITIVE_ORDER));
        List<LeaderboardRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, seeds.size()); i++) {
            LeaderboardSeed seed = seeds.get(i);
            rows.add(new LeaderboardRow(i + 1, seed.uuid(), seed.name(), seed.value()));
        }
        send(exchange, 200, Map.of("metric", descriptor, "period", period, "leaderboardSafe", descriptor.leaderboardSafe(), "entries", rows));
    }

    private static void handleHistory(HttpExchange exchange, CommunityStatisticsManager.WebSnapshot stats) throws IOException {
        if (!stats.enabled()) { send(exchange, 503, Map.of("error", "community_statistics_disabled")); return; }
        Map<String, String> query = query(exchange);
        String metricId = normalizeMetric(query.getOrDefault("metric", "play_time_seconds"));
        CommunityMetric.Descriptor descriptor = stats.catalog().get(metricId);
        if (descriptor == null) { send(exchange, 400, Map.of("error", "unknown_metric", "metric", metricId)); return; }
        String period = query.getOrDefault("period", "day").trim().toLowerCase(Locale.ROOT);
        Map<String, Map<String, Long>> history = switch (period) {
            case "day" -> stats.server().dailyHistory();
            case "week" -> stats.server().weeklyHistory();
            case "month" -> stats.server().monthlyHistory();
            case "season" -> stats.server().seasonHistory();
            default -> null;
        };
        if (history == null) { send(exchange, 400, Map.of("error", "invalid_history_period")); return; }
        List<HistoryPoint> points = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : history.entrySet()) {
            points.add(new HistoryPoint(entry.getKey(), entry.getValue().getOrDefault(metricId, 0L)));
        }
        send(exchange, 200, Map.of("metric", descriptor, "period", period, "points", points));
    }

    private static boolean authorized(HttpExchange exchange, String expectedToken) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
        byte[] actual = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private static void addBaseHeaders(HttpExchange exchange, String allowedOrigin) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (!allowedOrigin.isBlank() && origin != null && allowedOrigin.equals(origin)) {
            headers.set("Access-Control-Allow-Origin", allowedOrigin);
            headers.set("Vary", "Origin");
        }
    }

    private static void handlePreflight(HttpExchange exchange, String allowedOrigin) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (allowedOrigin.isBlank() || origin == null || !allowedOrigin.equals(origin)) { exchange.sendResponseHeaders(403, -1); return; }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        headers.set("Access-Control-Max-Age", "600");
        exchange.sendResponseHeaders(204, -1);
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) return Map.of();
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) continue;
            int equals = pair.indexOf('=');
            String key = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = decode(equals < 0 ? "" : pair.substring(equals + 1));
            if (!key.isBlank()) values.put(key, value);
        }
        return Map.copyOf(values);
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException exception) { return value; }
    }

    private static String period(String raw) {
        String value = raw == null || raw.isBlank() ? "week" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "lifetime", "day", "week", "month", "season" -> value;
            default -> "week";
        };
    }

    private static String normalizeMetric(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static int intQuery(String raw, int fallback, int minimum, int maximum) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Math.max(minimum, Math.min(maximum, Integer.parseInt(raw.trim()))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean isLoopback(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("127.0.0.1") || value.equals("localhost") || value.equals("::1");
    }

    public record Snapshot(int apiVersion, boolean running, long generatedAtEpochMillis, long serverTick,
            int onlinePlayers, List<PlayerRow> players, NpcRow npcs, Capabilities capabilities) {
        static Snapshot offline() { return new Snapshot(API_VERSION, false, System.currentTimeMillis(), 0L, 0,
                List.of(), new NpcRow(0,0,0), new Capabilities(true,true,false,false)); }
    }
    public record PlayerRow(String uuid, String name, String dimension) { }
    public record NpcRow(int definitions, int placements, int scheduledPlacements) { }
    public record Capabilities(boolean status, boolean players, boolean statistics, boolean remoteActions) { }
    public record PlayerPeriodRow(String uuid, String name, String period, Map<String, Long> values) { }
    private record LeaderboardSeed(String uuid, String name, long value) { }
    public record LeaderboardRow(int rank, String uuid, String name, long value) { }
    public record HistoryPoint(String key, long value) { }
}
