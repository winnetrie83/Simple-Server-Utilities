package be.winnetrie.mod.simpleserverutilities.dungeon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentActionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.content.ContentFeature;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.network.DungeonLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Independent region-based dungeon queue, party and run lifecycle.
 * Quests, NPCs and minigames integrate only through shared events and optional bridges.
 */
public final class DungeonManager {
    public static final int DEFINITION_SCHEMA_VERSION = 1;
    public static final int RECOVERY_SCHEMA_VERSION = 1;
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_SERIALIZED_CHARACTERS = 65_535;
    public static final int MAX_QUEUE_SIZE = 2_048;
    private static final double CHECKPOINT_RADIUS_SQUARED = 9.0D;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, DungeonDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<UUID, Long>> queues = new LinkedHashMap<>();
    private final Map<UUID, DungeonRun> runs = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerRuns = new HashMap<>();
    private final Map<UUID, String> playerQueues = new HashMap<>();
    private final Map<String, UUID> arenaReservations = new HashMap<>();
    private final Set<String> resettingArenas = new LinkedHashSet<>();
    private final Set<String> blockedArenas = new LinkedHashSet<>();
    private final Set<String> unsafeArenas = new LinkedHashSet<>();
    private final Map<UUID, DungeonRecoveryData.Entry> recoveries = new LinkedHashMap<>();
    private final Map<UUID, Long> lastRequests = new HashMap<>();
    private final DirtyJsonRecordStore definitionStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore recoveryStore = new DirtyJsonRecordStore();

    private MinecraftServer server;
    private Path definitionFolder;
    private Path recoveryFile;
    private long serverTicks;

    public synchronized void load(MinecraftServer server) {
        clearRuntime(false);
        this.server = server;
        Path root = StoragePaths.dungeons(StoragePaths.root(server));
        definitionFolder = StoragePaths.dungeonDefinitions(StoragePaths.root(server));
        recoveryFile = StoragePaths.dungeonRecovery(StoragePaths.root(server));
        definitionStore.reset(); recoveryStore.reset(); definitions.clear(); recoveries.clear(); unsafeArenas.clear();
        try {
            Files.createDirectories(root); Files.createDirectories(definitionFolder);
            definitionStore.discover(definitionFolder); recoveryStore.discoverFile(recoveryFile);
            loadDefinitions(); loadRecovery(); saveAll();
            SimpleServerUtilities.LOGGER.info("Loaded {} SSU dungeons and {} pending dungeon recoveries.", definitions.size(), recoveries.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU Dungeon Framework.", exception);
        }
    }

    private void loadDefinitions() throws IOException {
        for (Path file : JsonStorage.listJsonFiles(definitionFolder)) {
            try {
                DungeonDefinition definition = JsonStorage.read(GSON, file, DungeonDefinition.class);
                if (definition == null) continue;
                definition.normalize(); validateDefinition(definition, false);
                if (definitions.putIfAbsent(definition.id, definition) != null) {
                    throw new IllegalArgumentException("Duplicate dungeon ID across files: " + definition.id);
                }
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load dungeon definition; archived as {}.", archived, exception);
            }
        }
    }

    private void loadRecovery() {
        if (recoveryFile == null || !Files.exists(recoveryFile)) return;
        try {
            DungeonRecoveryData data = JsonStorage.read(GSON, recoveryFile, DungeonRecoveryData.class);
            if (data == null) return;
            data.normalize(); unsafeArenas.addAll(data.unsafeArenas);
            for (DungeonRecoveryData.Entry entry : data.players) recoveries.put(entry.playerId, entry);
            blockedArenas.addAll(unsafeArenas);
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(recoveryFile);
            recoveryStore.forget(recoveryFile);
            SimpleServerUtilities.LOGGER.error("Failed to load dungeon recovery data; archived as {}.", archived, exception);
        }
    }

    public synchronized void saveAll() {
        if (definitionFolder == null) return;
        Set<Path> kept = new LinkedHashSet<>();
        for (DungeonDefinition definition : definitions.values()) {
            Path file = StoragePaths.jsonFile(definitionFolder, definition.id);
            definitionStore.queueJson(GSON, file, definition);
            kept.add(file);
        }
        definitionStore.queueDeleteMissing(kept);
        saveRecovery();
    }

    private synchronized void saveRecovery() {
        if (recoveryFile == null) return;
        DungeonRecoveryData data = new DungeonRecoveryData();
        data.players.addAll(recoveries.values());
        data.unsafeArenas.addAll(unsafeArenas);
        data.normalize();
        recoveryStore.queueJson(GSON, recoveryFile, data);
    }

    public synchronized Collection<DungeonDefinition> definitions() {
        return definitions.values().stream().map(this::copy).toList();
    }

    public synchronized DungeonDefinition definition(String rawId) {
        DungeonDefinition definition = definitions.get(ContentId.normalize(rawId));
        return definition == null ? null : copy(definition);
    }

    public String toJson(DungeonDefinition definition) { return GSON.toJson(definition); }

    public DungeonDefinition fromJson(String json) {
        if (json == null || json.length() > MAX_SERIALIZED_CHARACTERS) throw new IllegalArgumentException("Dungeon definition is too large.");
        DungeonDefinition definition = GSON.fromJson(json, DungeonDefinition.class);
        if (definition == null) throw new IllegalArgumentException("Dungeon definition is empty.");
        definition.normalize(); validateDefinition(definition, true); return definition;
    }

    public DungeonDefinition copy(DungeonDefinition definition) {
        if (definition == null) return null;
        DungeonDefinition copy = GSON.fromJson(GSON.toJson(definition), DungeonDefinition.class); copy.normalize(); return copy;
    }

    public synchronized boolean saveDefinition(String rawOriginalId, DungeonDefinition definition) {
        String original = ContentId.normalize(rawOriginalId);
        validateDefinition(definition, true);
        if (hasRuntimeFor(original) || (!original.equals(definition.id) && hasRuntimeFor(definition.id))) {
            throw new IllegalArgumentException("Active dungeon runtime prevents editing or renaming this definition.");
        }
        if (!original.equals(definition.id) && definitions.containsKey(definition.id)) return false;
        if (!definitions.containsKey(definition.id) && definitions.size() >= MAX_DEFINITIONS) return false;
        if (!original.isBlank() && !original.equals(definition.id)) definitions.remove(original);
        definitions.put(definition.id, copy(definition)); saveAll(); return true;
    }

    public synchronized boolean deleteDefinition(String rawId) {
        String id = ContentId.normalize(rawId);
        if (id.isBlank() || hasRuntimeFor(id)) return false;
        boolean removed = definitions.remove(id) != null; queues.remove(id); if (removed) saveAll(); return removed;
    }

    public synchronized void validateDefinition(DungeonDefinition definition, boolean referencesMustExist) {
        if (definition == null) throw new IllegalArgumentException("Dungeon definition is missing.");
        definition.normalize();
        if (GSON.toJson(definition).length() > MAX_SERIALIZED_CHARACTERS) throw new IllegalArgumentException("Dungeon exceeds the serialized size limit.");
        if (definition.stages.isEmpty()) throw new IllegalArgumentException("A dungeon needs at least one stage.");
        if (definition.arenas.isEmpty()) throw new IllegalArgumentException("A dungeon needs at least one arena.");
        LinkedHashSet<String> stageIds = new LinkedHashSet<>();
        for (DungeonStageDefinition stage : definition.stages) {
            if (!stageIds.add(stage.id)) throw new IllegalArgumentException("Duplicate dungeon stage ID: " + stage.id);
        }
        LinkedHashSet<String> arenaIds = new LinkedHashSet<>();
        for (DungeonArenaDefinition arena : definition.arenas) {
            if (!arenaIds.add(arena.id)) throw new IllegalArgumentException("Duplicate dungeon arena ID: " + arena.id);
            validateLocation(arena.lobby, "Dungeon lobby"); validateLocation(arena.start, "Dungeon start"); validateLocation(arena.spectator, "Dungeon spectator");
            LinkedHashSet<String> checkpointIds = new LinkedHashSet<>();
            for (DungeonCheckpointDefinition checkpoint : arena.checkpoints) {
                if (!checkpointIds.add(checkpoint.id)) throw new IllegalArgumentException("Duplicate checkpoint ID in arena '" + arena.id + "': " + checkpoint.id);
                validateLocation(checkpoint.location, "Dungeon checkpoint");
            }
            for (DungeonStageDefinition stage : definition.stages) {
                if ("reach_checkpoint".equals(stage.type) && arena.checkpoint(stage.checkpointId) == null) {
                    throw new IllegalArgumentException("Arena '" + arena.id + "' has no checkpoint '" + stage.checkpointId + "' required by stage '" + stage.id + "'.");
                }
            }
            if (arena.regionId.isBlank()) throw new IllegalArgumentException("Dungeon arena '" + arena.id + "' needs an SSU region ID.");
            if (referencesMustExist) {
                if (!Config.ENABLE_ADMIN_REGIONS.get()) throw new IllegalArgumentException("Customized dungeons require the Regions module.");
                Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
                if (region == null) throw new IllegalArgumentException("Unknown dungeon region: " + arena.regionId);
                if (arena.resetRegionAfterRun && !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
                    throw new IllegalArgumentException("No saved snapshot exists for dungeon region '" + region.getName() + "'.");
                }
            }
        }
        validateCondition(definition.prerequisites);
        validateActions(definition.participationRewards); validateActions(definition.completionRewards); validateActions(definition.failureRewards);
    }

    private static void validateCondition(be.winnetrie.mod.simpleserverutilities.content.ContentCondition condition) {
        if (condition == null) throw new IllegalArgumentException("Dungeon prerequisite condition is missing.");
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered(condition.type())) throw new IllegalArgumentException("Unknown dungeon prerequisite condition: " + condition.type());
        for (var child : condition.children()) validateCondition(child);
    }

    private static void validateActions(List<ContentAction> actions) {
        for (ContentAction action : actions) if (!SimpleServerUtilities.CONTENT_ACTIONS.isRegistered(action.type())) throw new IllegalArgumentException("Unknown dungeon reward action: " + action.type());
    }

    private static void validateLocation(DungeonLocation location, String label) {
        if (location == null) throw new IllegalArgumentException(label + " is missing.");
        location.normalize();
        try { ResourceLocation.parse(location.dimension); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(label + " has an invalid dimension: " + location.dimension); }
    }

    public void handleRequest(DungeonLobbyRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("dungeons")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> processRequest(player, payload));
    }

    private void processRequest(ServerPlayer player, DungeonLobbyRequestPayload payload) {
        String action = payload.action().trim().toLowerCase(Locale.ROOT);
        if ("open".equals(action)) resetRequestSequence(player.getUUID(), payload.requestId());
        else if (!acceptRequest(player.getUUID(), payload.requestId())) return;
        if (!active() || !canAccess(player)) {
            PacketDistributor.sendToPlayer(player, new DungeonLobbyDataPayload("You do not have permission to use customized dungeons.", true, canAdmin(player), payload.requestId(), "", "", List.of()));
            return;
        }
        String notice = ""; boolean error = false;
        try {
            switch (action) {
                case "open", "refresh" -> { }
                case "join" -> notice = joinQueue(player, payload.dungeonId());
                case "leave" -> notice = leave(player, true);
                case "force_start" -> { requireAdmin(player); notice = forceStart(payload.dungeonId()); }
                case "complete" -> { requireAdmin(player); notice = completeFirstRun(payload.dungeonId(), "Completed by an administrator."); }
                case "fail" -> { requireAdmin(player); notice = failFirstRun(payload.dungeonId(), "Failed by an administrator."); }
                case "advance_stage" -> { requireAdmin(player); notice = advancePlayerRun(player, "Advanced by an administrator."); }
                case "release_arena" -> { requireAdmin(player); notice = releaseBlockedArena(payload.dungeonId()); }
                case "delete" -> { requireAdmin(player); if (!deleteDefinition(payload.dungeonId())) throw new IllegalArgumentException("The dungeon is active or could not be deleted."); notice = "Dungeon deleted."; }
                default -> throw new IllegalArgumentException("Unknown dungeon action.");
            }
        } catch (RuntimeException exception) { error = true; notice = exception.getMessage() == null ? "The dungeon action failed safely." : exception.getMessage(); }
        sendLobby(player, notice, error, payload.requestId());
    }

    private synchronized void resetRequestSequence(UUID playerId, long requestId) { lastRequests.put(playerId, Math.max(0L, requestId)); }
    private synchronized boolean acceptRequest(UUID playerId, long requestId) { long previous = lastRequests.getOrDefault(playerId, -1L); if (requestId <= previous) return false; lastRequests.put(playerId, requestId); return true; }

    public void open(ServerPlayer player) {
        synchronized (this) { lastRequests.remove(player.getUUID()); }
        if (!active() || !canAccess(player)) { player.sendSystemMessage(Component.literal("You do not have permission to use customized dungeons.")); return; }
        sendLobby(player, "", false, 0L);
    }

    public String joinQueue(ServerPlayer player, String rawId) {
        if (!active()) throw new IllegalArgumentException("The Dungeon Framework is disabled.");
        if (!ContentAccessPolicy.canJoinDungeonQueue(player)) throw new IllegalArgumentException("You do not have permission to join dungeon queues.");
        String id = ContentId.require(rawId, "Dungeon ID");
        synchronized (this) {
            if (playerQueues.containsKey(player.getUUID())) throw new IllegalArgumentException("You are already queued for a dungeon.");
            if (playerRuns.containsKey(player.getUUID())) throw new IllegalArgumentException("You are already in a dungeon run.");
            DungeonDefinition definition = definitions.get(id);
            if (definition == null || !definition.enabled) throw new IllegalArgumentException("That dungeon is unavailable.");
            var condition = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(definition.prerequisites,
                    new ContentConditionContext(server, player, "dungeons", definition.id, Map.of("dungeon", definition.id)));
            if (!condition.matched()) throw new IllegalArgumentException(condition.reason());
            if (definition.allowLateJoin && tryLateJoin(player, definition)) return "Joined the active dungeon run for " + definition.displayName + ".";
            LinkedHashMap<UUID, Long> queue = queues.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
            if (queue.size() >= MAX_QUEUE_SIZE) throw new IllegalArgumentException("This dungeon queue is full.");
            queue.put(player.getUUID(), System.currentTimeMillis()); playerQueues.put(player.getUUID(), id);
        }
        publish(player, ContentEventTypes.DUNGEON_QUEUE_JOINED, id, 1L, Map.of());
        return "Joined the queue for " + displayName(id) + ".";
    }

    private boolean tryLateJoin(ServerPlayer player, DungeonDefinition definition) {
        DungeonRun target = null; DungeonArenaDefinition arena = null;
        for (DungeonRun candidate : runs.values()) {
            if (!candidate.dungeonId.equals(definition.id) || candidate.state != DungeonRunState.RUNNING || candidate.participants.size() >= definition.maxPlayers) continue;
            DungeonArenaDefinition candidateArena = arena(definition, candidate.arenaId);
            if (candidateArena == null || !locationsResolvable(candidateArena)) continue;
            target = candidate; arena = candidateArena; break;
        }
        if (target == null || arena == null) return false;
        UUID playerId = player.getUUID();
        target.participants.add(playerId); target.remainingLives.put(playerId, definition.livesPerPlayer);
        target.returnLocations.put(playerId, DungeonLocation.of(player));
        recoveries.put(playerId, new DungeonRecoveryData.Entry(playerId, definition.id, target.id.toString(), target.returnLocations.get(playerId).copy()));
        playerRuns.put(playerId, target.id); saveRecovery();
        teleport(player, respawnLocation(target, definition, arena));
        publish(player, ContentEventTypes.DUNGEON_STARTED, definition.id, 1L, Map.of("run", target.id.toString(), "arena", target.arenaId, "phase", "late_join"));
        return true;
    }

    public String leave(ServerPlayer player, boolean voluntary) {
        UUID playerId = player.getUUID(); String queued; UUID runId;
        synchronized (this) {
            queued = playerQueues.remove(playerId);
            if (queued != null) { LinkedHashMap<UUID, Long> queue = queues.get(queued); if (queue != null) queue.remove(playerId); }
            runId = playerRuns.get(playerId);
        }
        if (queued != null) {
            publish(player, ContentEventTypes.DUNGEON_QUEUE_LEFT, queued, 1L, Map.of("reason", voluntary ? "voluntary" : "disconnect"));
            return "Left the queue for " + displayName(queued) + ".";
        }
        if (runId != null) {
            if (voluntary) withdrawFromRun(playerId, "You left the dungeon run.");
            else eliminate(playerId, "You disconnected from the dungeon run.");
            return "You left the active dungeon run.";
        }
        return "You are not in a dungeon queue or run.";
    }

    private void withdrawFromRun(UUID playerId, String reason) {
        DungeonRun run; DungeonLocation destination;
        synchronized (this) {
            run = runFor(playerId); if (run == null) return;
            destination = run.returnLocations.remove(playerId); run.participants.remove(playerId); run.remainingLives.remove(playerId); run.eliminated.remove(playerId); playerRuns.remove(playerId);
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        boolean returned = player != null && destination != null && teleport(player, destination);
        if (returned) synchronized (this) { recoveries.remove(playerId); saveRecovery(); }
        if (player != null) player.sendSystemMessage(Component.literal(reason));
        if (run.participants.isEmpty()) fail(run, "All players left the dungeon.");
    }

    public synchronized String forceStart(String rawId) {
        String id = ContentId.require(rawId, "Dungeon ID"); DungeonDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown dungeon: " + id);
        DungeonRun run = tryStart(definition, true);
        if (run == null) throw new IllegalArgumentException("Not enough queued players or no usable dungeon arena is available.");
        return "Started dungeon run " + run.id + " for " + definition.displayName + ".";
    }

    public synchronized void tick(MinecraftServer activeServer) {
        if (!active() || server == null || server != activeServer) return;
        serverTicks++; if (serverTicks % 20L != 0L) return;
        removeOfflineQueuedPlayers();
        for (DungeonDefinition definition : definitions.values()) if (definition.enabled && definition.automaticStart) {
            while (queueSize(definition.id) >= definition.minPlayers && tryStart(definition, false) != null) { }
        }
        for (DungeonRun run : List.copyOf(runs.values())) advance(run);
    }

    private void removeOfflineQueuedPlayers() {
        for (Map.Entry<UUID, String> entry : List.copyOf(playerQueues.entrySet())) {
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) continue;
            LinkedHashMap<UUID, Long> queue = queues.get(entry.getValue()); if (queue != null) queue.remove(entry.getKey()); playerQueues.remove(entry.getKey());
        }
    }

    private synchronized DungeonRun tryStart(DungeonDefinition definition, boolean forced) {
        LinkedHashMap<UUID, Long> queue = queues.computeIfAbsent(definition.id, ignored -> new LinkedHashMap<>());
        ArrayList<ServerPlayer> candidates = new ArrayList<>();
        for (UUID playerId : List.copyOf(queue.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) { queue.remove(playerId); playerQueues.remove(playerId); continue; }
            candidates.add(player); if (candidates.size() >= definition.maxPlayers) break;
        }
        int required = forced ? 1 : definition.minPlayers; if (candidates.size() < required) return null;
        DungeonArenaDefinition arena = freeArena(definition); if (arena == null || !locationsResolvable(arena)) return null;
        DungeonRun run = new DungeonRun(UUID.randomUUID(), definition.id, arena.id, serverTicks);
        for (ServerPlayer player : candidates) {
            UUID playerId = player.getUUID(); queue.remove(playerId); playerQueues.remove(playerId);
            run.participants.add(playerId); run.remainingLives.put(playerId, definition.livesPerPlayer); run.returnLocations.put(playerId, DungeonLocation.of(player));
            recoveries.put(playerId, new DungeonRecoveryData.Entry(playerId, definition.id, run.id.toString(), run.returnLocations.get(playerId).copy()));
            playerRuns.put(playerId, run.id);
        }
        runs.put(run.id, run); String key = arenaKey(definition.id, arena.id); arenaReservations.put(key, run.id);
        if (arena.resetRegionAfterRun) unsafeArenas.add(key); saveRecovery();
        for (ServerPlayer player : candidates) {
            teleport(player, arena.start);
            player.sendSystemMessage(Component.literal("Joined " + definition.displayName + ". The run begins in " + definition.countdownSeconds + " seconds."));
        }
        return run;
    }

    private boolean locationsResolvable(DungeonArenaDefinition arena) {
        if (resolveLevel(arena.lobby.dimension) == null || resolveLevel(arena.start.dimension) == null || resolveLevel(arena.spectator.dimension) == null) return false;
        for (DungeonCheckpointDefinition checkpoint : arena.checkpoints) if (resolveLevel(checkpoint.location.dimension) == null) return false;
        return true;
    }

    private void advance(DungeonRun run) {
        DungeonDefinition definition; DungeonArenaDefinition arena;
        synchronized (this) { definition = definitions.get(run.dungeonId); arena = definition == null ? null : arena(definition, run.arenaId); }
        if (definition == null || arena == null) { fail(run, "Definition or arena was removed."); cleanup(run, null, null); return; }
        long elapsedSeconds = Math.max(0L, (serverTicks - run.stateStartedTick) / 20L);
        switch (run.state) {
            case COUNTDOWN -> {
                long remaining = Math.max(0L, definition.countdownSeconds - elapsedSeconds);
                if (remaining != run.lastAnnouncementSecond && (remaining <= 5L || remaining == 10L || remaining % 30L == 0L)) {
                    run.lastAnnouncementSecond = remaining; announce(run, remaining == 0L ? "Enter the dungeon!" : "Dungeon begins in " + remaining + "…");
                }
                if (elapsedSeconds >= definition.countdownSeconds) {
                    run.state = DungeonRunState.RUNNING; run.stateStartedTick = serverTicks; run.stageStartedTick = serverTicks; run.lastAnnouncementSecond = Long.MIN_VALUE;
                    announce(run, definition.displayName + " has started!"); publishRun(run, ContentEventTypes.DUNGEON_STARTED, "started"); announceCurrentStage(run, definition);
                }
            }
            case RUNNING -> {
                if (definition.timeLimitSeconds > 0 && elapsedSeconds >= definition.timeLimitSeconds) { fail(run, "Dungeon time limit reached."); return; }
                if (allEliminated(run)) { fail(run, "The entire party was defeated."); return; }
                processCurrentStage(run, definition, arena);
            }
            case POST_RUN -> { if (elapsedSeconds >= definition.postRunSeconds) cleanup(run, definition, arena); }
            case RESETTING, FINISHED -> { }
        }
    }

    private void processCurrentStage(DungeonRun run, DungeonDefinition definition, DungeonArenaDefinition arena) {
        DungeonStageDefinition stage = currentStage(run, definition);
        if (stage == null) { complete(run, "All dungeon stages completed."); return; }
        if ("survive_seconds".equals(stage.type)) {
            long elapsed = Math.max(0L, (serverTicks - run.stageStartedTick) / 20L); run.stageProgress = Math.min(stage.durationSeconds, elapsed);
            if (elapsed >= stage.durationSeconds) completeStage(run, definition, "Survival timer completed.");
        } else if ("reach_checkpoint".equals(stage.type)) {
            DungeonCheckpointDefinition checkpoint = arena.checkpoint(stage.checkpointId);
            if (checkpoint == null) { fail(run, "Required checkpoint is unavailable."); return; }
            for (UUID playerId : run.participants) {
                if (run.eliminated.contains(playerId)) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && atLocation(player, checkpoint.location)) {
                    run.activeCheckpointId = checkpoint.id; run.stageProgress = 1L;
                    completeStage(run, definition, "Checkpoint reached."); break;
                }
            }
        }
    }

    private static DungeonStageDefinition currentStage(DungeonRun run, DungeonDefinition definition) {
        return run.stageIndex >= 0 && run.stageIndex < definition.stages.size() ? definition.stages.get(run.stageIndex) : null;
    }

    public void onEntityKilled(ServerPlayer killer, Entity defeated, String entityType) {
        if (killer == null || defeated == null) return;
        DungeonRun run; DungeonDefinition definition; DungeonStageDefinition stage; DungeonArenaDefinition arena;
        synchronized (this) {
            run = runFor(killer.getUUID()); if (run == null || run.state != DungeonRunState.RUNNING || run.eliminated.contains(killer.getUUID())) return;
            definition = definitions.get(run.dungeonId); arena = definition == null ? null : arena(definition, run.arenaId);
            stage = definition == null ? null : currentStage(run, definition);
            if (stage == null || arena == null || !"kill_count".equals(stage.type) || !insideArena(defeated, arena)) return;
            String subject = entityType == null ? "" : entityType.toLowerCase(Locale.ROOT);
            if (!"*".equals(stage.subject) && !stage.subject.equals(subject)) return;
            run.stageProgress = Math.min(stage.requiredAmount, safeAdd(run.stageProgress, 1L));
        }
        if (run.stageProgress >= stage.requiredAmount) completeStage(run, definition, "Required enemies defeated.");
    }

    private static boolean insideArena(Entity entity, DungeonArenaDefinition arena) {
        if (entity == null || arena == null || arena.regionId.isBlank()) return false;
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        return region != null && region.contains(entity.level().dimension(), entity.blockPosition());
    }

    public String advancePlayerRun(ServerPlayer player, String reason) {
        DungeonRun run; DungeonDefinition definition;
        synchronized (this) { run = runFor(player.getUUID()); definition = run == null ? null : definitions.get(run.dungeonId); }
        if (run == null || definition == null || run.state != DungeonRunState.RUNNING) throw new IllegalArgumentException("You are not inside a running dungeon.");
        completeStage(run, definition, reason); return "Dungeon stage advanced.";
    }

    private void completeStage(DungeonRun run, DungeonDefinition definition, String reason) {
        DungeonStageDefinition completed;
        synchronized (this) {
            if (run.state != DungeonRunState.RUNNING) return;
            completed = currentStage(run, definition); if (completed == null) { complete(run, reason); return; }
            run.stageIndex++; run.stageProgress = 0L; run.stageStartedTick = serverTicks;
        }
        publishRun(run, ContentEventTypes.DUNGEON_STAGE_COMPLETED, completed.id);
        announce(run, "Stage completed: " + completed.displayName + ". " + (reason == null ? "" : reason));
        if (run.stageIndex >= definition.stages.size()) complete(run, "All dungeon stages completed."); else announceCurrentStage(run, definition);
    }

    private void announceCurrentStage(DungeonRun run, DungeonDefinition definition) {
        DungeonStageDefinition stage = currentStage(run, definition);
        if (stage != null) announce(run, "Current stage: " + stage.displayName + " — " + stage.description);
    }

    public void onPlayerDeath(ServerPlayer player) {
        if (player == null) return;
        DungeonRun run; DungeonDefinition definition;
        synchronized (this) {
            run = runFor(player.getUUID()); if (run == null || run.state != DungeonRunState.RUNNING || run.eliminated.contains(player.getUUID())) return;
            definition = definitions.get(run.dungeonId); if (definition == null) return;
            int lives = run.remainingLives.getOrDefault(player.getUUID(), definition.livesPerPlayer);
            if (definition.livesPerPlayer <= 0) lives = Integer.MAX_VALUE;
            else lives = Math.max(0, lives - 1);
            run.remainingLives.put(player.getUUID(), lives);
        }
        if (definition.livesPerPlayer > 0 && run.lives(player.getUUID()) <= 0) eliminate(player.getUUID(), "You have no dungeon lives remaining.");
        else player.sendSystemMessage(Component.literal("Dungeon death. Lives remaining: " + (definition.livesPerPlayer <= 0 ? "unlimited" : run.lives(player.getUUID()))));
    }

    public void onPlayerRespawn(ServerPlayer player) {
        if (player == null) return;
        DungeonRun run; DungeonDefinition definition; DungeonArenaDefinition arena;
        synchronized (this) { run = runFor(player.getUUID()); definition = run == null ? null : definitions.get(run.dungeonId); arena = definition == null ? null : arena(definition, run.arenaId); }
        if (run == null || arena == null) return;
        teleport(player, run.eliminated.contains(player.getUUID()) ? arena.spectator : respawnLocation(run, definition, arena));
    }

    public void eliminate(UUID playerId, String reason) {
        DungeonRun run; DungeonDefinition definition; DungeonArenaDefinition arena;
        synchronized (this) {
            run = runFor(playerId); if (run == null || run.eliminated.contains(playerId)) return;
            run.eliminated.add(playerId); definition = definitions.get(run.dungeonId); arena = definition == null ? null : arena(definition, run.arenaId);
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) { if (arena != null) teleport(player, arena.spectator); player.sendSystemMessage(Component.literal(reason == null || reason.isBlank() ? "You were eliminated from the dungeon." : reason)); }
        if (allEliminated(run)) fail(run, "The entire party was defeated.");
    }

    private static boolean allEliminated(DungeonRun run) { return !run.participants.isEmpty() && run.eliminated.containsAll(run.participants); }

    public void onLogin(ServerPlayer player) {
        DungeonRecoveryData.Entry recovery; DungeonRun activeRun; DungeonDefinition definition; DungeonArenaDefinition arena;
        synchronized (this) {
            activeRun = runFor(player.getUUID()); definition = activeRun == null ? null : definitions.get(activeRun.dungeonId); arena = definition == null ? null : arena(definition, activeRun.arenaId); recovery = recoveries.get(player.getUUID());
        }
        if (activeRun != null && arena != null) {
            teleport(player, activeRun.eliminated.contains(player.getUUID()) ? arena.spectator : respawnLocation(activeRun, definition, arena));
            player.sendSystemMessage(Component.literal("Rejoined the active dungeon run.")); return;
        }
        if (recovery != null && teleport(player, recovery.returnLocation)) {
            synchronized (this) { recoveries.remove(player.getUUID()); saveRecovery(); }
            player.sendSystemMessage(Component.literal("You were returned from an interrupted dungeon session."));
        }
    }

    public void onLogout(ServerPlayer player) {
        if (player == null) return;
        leave(player, false); synchronized (this) { lastRequests.remove(player.getUUID()); }
    }

    public void complete(DungeonRun run, String reason) { finish(run, true, reason); }
    public void fail(DungeonRun run, String reason) { finish(run, false, reason); }

    private void finish(DungeonRun run, boolean successful, String reason) {
        DungeonDefinition definition;
        synchronized (this) {
            if (run.state == DungeonRunState.POST_RUN || run.state == DungeonRunState.RESETTING || run.state == DungeonRunState.FINISHED) return;
            definition = definitions.get(run.dungeonId); if (definition == null) return;
            run.state = DungeonRunState.POST_RUN; run.stateStartedTick = serverTicks; run.successful = successful; run.finishReason = reason == null ? "" : reason;
        }
        deliverRewardsAndEvents(run, definition);
        announce(run, definition.displayName + (successful ? " completed! " : " failed. ") + run.finishReason);
    }

    private void deliverRewardsAndEvents(DungeonRun run, DungeonDefinition definition) {
        for (UUID playerId : run.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId); if (player == null) continue;
            executeRewards(player, definition.participationRewards, run, "participation");
            executeRewards(player, run.successful ? definition.completionRewards : definition.failureRewards, run, run.successful ? "completion" : "failure");
            Map<String, String> metadata = Map.of("run", run.id.toString(), "arena", run.arenaId, "result", run.successful ? "completed" : "failed", "stage", Integer.toString(run.stageIndex));
            publish(player, run.successful ? ContentEventTypes.DUNGEON_COMPLETED : ContentEventTypes.DUNGEON_FAILED, definition.id, 1L, metadata);
        }
    }

    private void executeRewards(ServerPlayer player, List<ContentAction> actions, DungeonRun run, String kind) {
        if (actions == null || actions.isEmpty()) return;
        var result = SimpleServerUtilities.CONTENT_ACTIONS.execute(actions,
                new ContentActionContext(server, player, "dungeons", run.dungeonId, run.id + ":" + player.getUUID() + ":" + kind,
                        Map.of("dungeon", run.dungeonId, "run", run.id.toString(), "reward", kind)));
        if (!result.successful()) {
            SimpleServerUtilities.LOGGER.error("Failed to deliver {} dungeon rewards to {}: {}", kind, player.getName().getString(), result.error());
            player.sendSystemMessage(Component.literal("Some dungeon rewards could not be delivered: " + result.error()));
        }
    }

    private void cleanup(DungeonRun run, DungeonDefinition definition, DungeonArenaDefinition arena) {
        if (definition == null) definition = definitionInternal(run.dungeonId);
        if (definition != null && arena == null) arena = arena(definition, run.arenaId);
        for (UUID playerId : List.copyOf(run.participants)) returnParticipant(run, playerId);
        synchronized (this) { runs.remove(run.id); run.state = DungeonRunState.FINISHED; }
        if (definition != null && arena != null && arena.resetRegionAfterRun) scheduleArenaReset(run, definition, arena);
        else {
            synchronized (this) { unsafeArenas.remove(arenaKey(run.dungeonId, run.arenaId)); }
            releaseArena(run.dungeonId, run.arenaId); saveRecovery();
        }
    }

    private void returnParticipant(DungeonRun run, UUID playerId) {
        DungeonLocation destination;
        synchronized (this) { playerRuns.remove(playerId); destination = run.returnLocations.get(playerId); }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null && destination != null && teleport(player, destination)) synchronized (this) { recoveries.remove(playerId); saveRecovery(); }
    }

    private void scheduleArenaReset(DungeonRun run, DungeonDefinition definition, DungeonArenaDefinition arena) {
        String key = arenaKey(definition.id, arena.id); Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null || !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
            synchronized (this) { blockedArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.LOGGER.error("Blocked dungeon arena '{}' because reset region '{}' is unavailable.", key, arena.regionId); return;
        }
        ServerLevel level = server.getLevel(region.getDimension());
        if (level == null) {
            synchronized (this) { blockedArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.LOGGER.error("Blocked dungeon arena '{}' because its reset dimension is unavailable.", key); return;
        }
        try {
            var job = SimpleServerUtilities.REGION_SNAPSHOTS.createResetJob(level, region);
            synchronized (this) { resettingArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.JOBS.submit(job, result -> {
                synchronized (DungeonManager.this) {
                    resettingArenas.remove(key);
                    if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) { blockedArenas.remove(key); unsafeArenas.remove(key); }
                    else { blockedArenas.add(key); unsafeArenas.add(key); }
                    saveRecovery();
                }
                if (result.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) SimpleServerUtilities.LOGGER.error("Dungeon arena reset failed for '{}': {}", key, result.error());
            });
        } catch (Exception exception) {
            synchronized (this) { blockedArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.LOGGER.error("Failed to schedule reset for dungeon arena '{}'.", key, exception);
        }
    }

    public synchronized String releaseBlockedArena(String raw) {
        String target = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        boolean removed = blockedArenas.removeIf(key -> key.equals(target) || key.startsWith(target + ":"));
        boolean unsafeRemoved = unsafeArenas.removeIf(key -> key.equals(target) || key.startsWith(target + ":"));
        if (removed || unsafeRemoved) saveRecovery();
        return removed || unsafeRemoved ? "Released blocked dungeon arena state." : "No blocked dungeon arena matched that ID or arena key.";
    }

    public String completeFirstRun(String rawDungeonId, String reason) { return finishFirst(rawDungeonId, true, reason); }
    public String failFirstRun(String rawDungeonId, String reason) { return finishFirst(rawDungeonId, false, reason); }

    private String finishFirst(String rawDungeonId, boolean success, String reason) {
        String id = ContentId.require(rawDungeonId, "Dungeon ID"); DungeonRun run;
        synchronized (this) { run = runs.values().stream().filter(value -> value.dungeonId.equals(id) && value.state != DungeonRunState.POST_RUN && value.state != DungeonRunState.FINISHED).findFirst().orElse(null); }
        if (run == null) throw new IllegalArgumentException("That dungeon has no active run.");
        finish(run, success, reason); return "Dungeon run " + run.id + " moved to post-run state.";
    }

    private synchronized DungeonRun runFor(UUID playerId) { UUID runId = playerRuns.get(playerId); return runId == null ? null : runs.get(runId); }
    private synchronized DungeonDefinition definitionInternal(String id) { return definitions.get(id); }
    private synchronized boolean hasRuntimeFor(String id) {
        if (id == null || id.isBlank()) return false; String prefix = id + ":";
        return queueSize(id) > 0 || runs.values().stream().anyMatch(run -> run.dungeonId.equals(id)) || unsafeArenas.stream().anyMatch(key -> key.startsWith(prefix)) || resettingArenas.stream().anyMatch(key -> key.startsWith(prefix));
    }
    private synchronized int queueSize(String id) { LinkedHashMap<UUID, Long> queue = queues.get(id); return queue == null ? 0 : queue.size(); }
    private synchronized DungeonArenaDefinition freeArena(DungeonDefinition definition) {
        for (DungeonArenaDefinition arena : definition.arenas) {
            if (!arena.enabled) continue; String key = arenaKey(definition.id, arena.id);
            if (!arenaReservations.containsKey(key) && !resettingArenas.contains(key) && !blockedArenas.contains(key)) return arena;
        }
        return null;
    }
    private static DungeonArenaDefinition arena(DungeonDefinition definition, String id) { if (definition == null) return null; for (DungeonArenaDefinition arena : definition.arenas) if (arena.id.equals(id)) return arena; return null; }
    private synchronized void releaseArena(String dungeonId, String arenaId) { arenaReservations.remove(arenaKey(dungeonId, arenaId)); }

    private void announce(DungeonRun run, String message) {
        for (UUID playerId : run.participants) { ServerPlayer player = server.getPlayerList().getPlayer(playerId); if (player != null) player.sendSystemMessage(Component.literal(message), true); }
    }
    private void publishRun(DungeonRun run, String type, String phase) {
        for (UUID playerId : run.participants) { ServerPlayer player = server.getPlayerList().getPlayer(playerId); if (player != null) publish(player, type, run.dungeonId, 1L, Map.of("run", run.id.toString(), "arena", run.arenaId, "phase", phase)); }
    }
    private void publish(ServerPlayer player, String type, String subject, long amount, Map<String, String> metadata) {
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(type, player.getUUID(), "dungeons", subject, subject, amount, metadata));
    }

    private void sendLobby(ServerPlayer player, String notice, boolean error, long requestId) {
        ArrayList<DungeonLobbyDataPayload.DungeonEntry> entries = new ArrayList<>(); String queued; DungeonRun ownRun;
        synchronized (this) {
            queued = playerQueues.getOrDefault(player.getUUID(), ""); ownRun = runFor(player.getUUID());
            for (DungeonDefinition definition : definitions.values()) {
                if (!definition.enabled && !canAdmin(player)) continue;
                var availability = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(definition.prerequisites,
                        new ContentConditionContext(server, player, "dungeons", definition.id, Map.of("dungeon", definition.id)));
                int free = 0, blocked = 0;
                for (DungeonArenaDefinition arena : definition.arenas) {
                    String key = arenaKey(definition.id, arena.id); if (!arena.enabled) continue;
                    if (blockedArenas.contains(key)) blocked++; else if (!arenaReservations.containsKey(key) && !resettingArenas.contains(key)) free++;
                }
                int running = (int) runs.values().stream().filter(run -> run.dungeonId.equals(definition.id)).count();
                boolean inQueue = definition.id.equals(queued); boolean inRun = ownRun != null && definition.id.equals(ownRun.dungeonId);
                DungeonStageDefinition stage = inRun ? currentStage(ownRun, definition) : null;
                entries.add(new DungeonLobbyDataPayload.DungeonEntry(definition.id, definition.displayName, definition.description, definition.iconItem,
                        definition.enabled, definition.minPlayers, definition.maxPlayers, definition.livesPerPlayer, definition.stages.size(), queueSize(definition.id), running,
                        free, blocked, availability.matched(), availability.reason(), inQueue, inRun,
                        inRun ? ownRun.state.name().toLowerCase(Locale.ROOT) : "", inRun ? Math.min(definition.stages.size(), ownRun.stageIndex + 1) : 0,
                        stage == null ? "" : stage.displayName, inRun ? ownRun.stageProgress : 0L,
                        stage == null ? 0L : ("survive_seconds".equals(stage.type) ? stage.durationSeconds : stage.requiredAmount),
                        inRun ? ownRun.lives(player.getUUID()) : 0));
            }
        }
        entries.sort(Comparator.comparing(DungeonLobbyDataPayload.DungeonEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        PacketDistributor.sendToPlayer(player, new DungeonLobbyDataPayload(notice, error, canAdmin(player), requestId, queued, ownRun == null ? "" : ownRun.id.toString(), entries));
    }

    public synchronized boolean isQueued(UUID playerId, String rawDungeonId) { String queued = playerQueues.get(playerId); String requested = ContentId.normalize(rawDungeonId); return queued != null && (requested.isBlank() || queued.equals(requested)); }
    public synchronized boolean isInRun(UUID playerId, String rawDungeonId) { DungeonRun run = runFor(playerId); String requested = ContentId.normalize(rawDungeonId); return run != null && (requested.isBlank() || run.dungeonId.equals(requested)); }
    public synchronized String queuedDungeon(UUID playerId) { return playerQueues.getOrDefault(playerId, ""); }
    public synchronized String activeDungeon(UUID playerId) { DungeonRun run = runFor(playerId); return run == null ? "" : run.dungeonId; }
    public synchronized Snapshot snapshot() {
        int queued = queues.values().stream().mapToInt(Map::size).sum();
        return new Snapshot(definitions.size(), queued, runs.size(), arenaReservations.size(), resettingArenas.size(), blockedArenas.size(), recoveries.size());
    }

    public synchronized void shutdownRuntime(boolean returnOnlinePlayers) {
        if (returnOnlinePlayers && server != null) for (DungeonRun run : List.copyOf(runs.values())) for (UUID playerId : List.copyOf(run.participants)) returnParticipant(run, playerId);
        clearRuntime(false); saveRecovery();
    }
    private synchronized void clearRuntime(boolean keepServer) {
        queues.clear(); runs.clear(); playerRuns.clear(); playerQueues.clear(); arenaReservations.clear(); resettingArenas.clear(); blockedArenas.clear(); lastRequests.clear(); serverTicks = 0L; if (!keepServer) server = null;
    }
    public synchronized void clear() {
        clearRuntime(false); definitions.clear(); recoveries.clear(); unsafeArenas.clear(); definitionStore.reset(); recoveryStore.reset(); definitionFolder = null; recoveryFile = null;
    }

    private DungeonLocation respawnLocation(DungeonRun run, DungeonDefinition definition, DungeonArenaDefinition arena) {
        DungeonCheckpointDefinition checkpoint = arena.checkpoint(run.activeCheckpointId); return checkpoint == null ? arena.start : checkpoint.location;
    }
    private boolean atLocation(ServerPlayer player, DungeonLocation location) {
        if (player == null || location == null || !player.level().dimension().location().toString().equals(location.dimension)) return false;
        double dx = player.getX() - location.x, dy = player.getY() - location.y, dz = player.getZ() - location.z;
        return dx * dx + dy * dy + dz * dz <= CHECKPOINT_RADIUS_SQUARED;
    }
    private boolean teleport(ServerPlayer player, DungeonLocation location) {
        if (player == null || location == null) return false; ServerLevel level = resolveLevel(location.dimension); if (level == null) return false;
        player.teleportTo(level, location.x, location.y, location.z, Set.of(), location.yaw, location.pitch); return true;
    }
    private ServerLevel resolveLevel(String rawDimension) {
        if (server == null) return null;
        try { ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(rawDimension)); return server.getLevel(key); }
        catch (RuntimeException exception) { return null; }
    }
    private static long safeAdd(long left, long right) { try { return Math.addExact(left, right); } catch (ArithmeticException ignored) { return right >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE; } }
    private static String arenaKey(String dungeon, String arena) { return dungeon + ":" + arena; }
    private String displayName(String id) { DungeonDefinition value = definitionInternal(id); return value == null ? id : value.displayName; }
    private static boolean active() { return Config.ENABLE_DUNGEONS.get() && SimpleServerUtilities.CORE.modules().isActive("dungeons"); }
    private static boolean canAccess(ServerPlayer player) { return canAdmin(player) || ContentAccessPolicy.canUse(player, ContentFeature.DUNGEONS); }
    private static boolean canAdmin(ServerPlayer player) { return PermissionService.getBoolean(player, PermissionKeys.DUNGEONS_ADMIN, false); }
    private static void requireAdmin(ServerPlayer player) { if (!canAdmin(player)) throw new IllegalArgumentException("Dungeon administrator permission is required."); }

    public record Snapshot(int definitions, int queuedPlayers, int runs, int reservedArenas, int resettingArenas, int blockedArenas, int pendingRecoveries) { }
}
