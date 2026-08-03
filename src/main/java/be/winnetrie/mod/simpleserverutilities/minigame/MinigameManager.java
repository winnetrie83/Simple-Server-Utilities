package be.winnetrie.mod.simpleserverutilities.minigame;

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
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameScoreActionPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Independent queue, arena and match lifecycle for every SSU minigame type.
 * It deliberately contains no NPC, quest or dungeon dependency.
 */
public final class MinigameManager {
    public static final int DEFINITION_SCHEMA_VERSION = 1;
    public static final int RECOVERY_SCHEMA_VERSION = 1;
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_SERIALIZED_CHARACTERS = 65_535;
    public static final int MAX_QUEUE_SIZE = 2_048;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, MinigameDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<UUID, Long>> queues = new LinkedHashMap<>();
    private final Map<UUID, MinigameMatch> matches = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerMatches = new HashMap<>();
    private final Map<UUID, String> playerQueues = new HashMap<>();
    private final Map<String, UUID> arenaReservations = new HashMap<>();
    private final Set<String> resettingArenas = new LinkedHashSet<>();
    private final Set<String> blockedArenas = new LinkedHashSet<>();
    /** Persisted safety markers for reset-enabled arenas interrupted before a clean reset. */
    private final Set<String> unsafeArenas = new LinkedHashSet<>();
    private final Map<UUID, MinigameRecoveryData.Entry> recoveries = new LinkedHashMap<>();
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
        Path root = StoragePaths.minigames(StoragePaths.root(server));
        definitionFolder = StoragePaths.minigameDefinitions(StoragePaths.root(server));
        recoveryFile = StoragePaths.minigameRecovery(StoragePaths.root(server));
        definitionStore.reset();
        recoveryStore.reset();
        definitions.clear();
        recoveries.clear();
        unsafeArenas.clear();
        try {
            Files.createDirectories(root);
            Files.createDirectories(definitionFolder);
            definitionStore.discover(definitionFolder);
            recoveryStore.discoverFile(recoveryFile);
            loadDefinitions();
            loadRecovery();
            saveAll();
            SimpleServerUtilities.LOGGER.info("Loaded {} SSU minigames and {} pending player recoveries.",
                    definitions.size(), recoveries.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU Minigame Framework.", exception);
        }
    }

    private void loadDefinitions() throws IOException {
        for (Path file : JsonStorage.listJsonFiles(definitionFolder)) {
            try {
                MinigameDefinition definition = JsonStorage.read(GSON, file, MinigameDefinition.class);
                if (definition == null) continue;
                definition.normalize();
                validateDefinition(definition, false);
                if (definitions.putIfAbsent(definition.id, definition) != null) {
                    throw new IllegalArgumentException("Duplicate minigame ID across files: " + definition.id);
                }
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load minigame definition; archived as {}.", archived, exception);
            }
        }
    }

    private void loadRecovery() {
        if (recoveryFile == null || !Files.exists(recoveryFile)) return;
        try {
            MinigameRecoveryData data = JsonStorage.read(GSON, recoveryFile, MinigameRecoveryData.class);
            if (data == null) return;
            data.normalize();
            for (MinigameRecoveryData.Entry entry : data.players) recoveries.put(entry.playerId, entry);
            unsafeArenas.addAll(data.unsafeArenas);
            blockedArenas.addAll(data.unsafeArenas);
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(recoveryFile);
            recoveryStore.forget(recoveryFile);
            SimpleServerUtilities.LOGGER.error("Failed to load minigame recovery data; archived as {}.", archived, exception);
        }
    }

    public synchronized void saveAll() {
        if (definitionFolder == null) return;
        Set<Path> kept = new LinkedHashSet<>();
        for (MinigameDefinition definition : definitions.values()) {
            Path file = StoragePaths.jsonFile(definitionFolder, definition.id);
            definitionStore.queueJson(GSON, file, definition);
            kept.add(file);
        }
        definitionStore.queueDeleteMissing(kept);
        saveRecovery();
    }

    private void saveRecovery() {
        if (recoveryFile == null) return;
        MinigameRecoveryData data = new MinigameRecoveryData();
        data.players.addAll(recoveries.values());
        data.unsafeArenas.addAll(unsafeArenas);
        data.normalize();
        recoveryStore.queueJson(GSON, recoveryFile, data);
    }

    public synchronized Collection<MinigameDefinition> definitions() {
        ArrayList<MinigameDefinition> values = new ArrayList<>(definitions.values());
        values.sort(Comparator.comparing(value -> value.displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(values);
    }

    public synchronized MinigameDefinition definition(String rawId) {
        return definitions.get(ContentId.normalize(rawId));
    }

    public String toJson(MinigameDefinition definition) {
        return GSON.toJson(definition);
    }

    public MinigameDefinition fromJson(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Minigame editor data is missing or too large.");
        }
        MinigameDefinition definition = GSON.fromJson(json, MinigameDefinition.class);
        if (definition == null) throw new IllegalArgumentException("Minigame editor data is invalid.");
        definition.normalize();
        validateDefinition(definition, true);
        return definition;
    }

    public MinigameDefinition copy(MinigameDefinition definition) {
        return definition == null ? null : GSON.fromJson(GSON.toJson(definition), MinigameDefinition.class);
    }

    public synchronized boolean saveDefinition(String rawOriginalId, MinigameDefinition definition) {
        if (definition == null) return false;
        definition.normalize();
        validateDefinition(definition, true);
        String original = ContentId.normalize(rawOriginalId);
        if (!original.isBlank() && hasRuntimeFor(original)) {
            throw new IllegalArgumentException("Stop the queue and active matches before editing this minigame.");
        }
        if (!original.equals(definition.id) && definitions.containsKey(definition.id)) return false;
        if (!definitions.containsKey(original) && !definitions.containsKey(definition.id)
                && definitions.size() >= MAX_DEFINITIONS) return false;
        if (!original.isBlank() && !original.equals(definition.id)) definitions.remove(original);
        definitions.put(definition.id, definition);
        saveAll();
        return true;
    }

    public synchronized boolean deleteDefinition(String rawId) {
        String id = ContentId.normalize(rawId);
        if (id.isBlank() || hasRuntimeFor(id)) return false;
        boolean removed = definitions.remove(id) != null;
        queues.remove(id);
        if (removed) saveAll();
        return removed;
    }

    public synchronized void validateDefinition(MinigameDefinition definition, boolean referencesMustExist) {
        if (definition == null) throw new IllegalArgumentException("Minigame definition is missing.");
        definition.normalize();
        if (GSON.toJson(definition).length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Minigame exceeds the serialized size limit.");
        }
        if (definition.arenas.isEmpty()) throw new IllegalArgumentException("A minigame needs at least one arena.");
        LinkedHashSet<String> arenaIds = new LinkedHashSet<>();
        for (MinigameArenaDefinition arena : definition.arenas) {
            if (!arenaIds.add(arena.id)) throw new IllegalArgumentException("Duplicate arena ID: " + arena.id);
            validateLocation(arena.lobby, "Arena lobby");
            validateLocation(arena.spectator, "Arena spectator spawn");
            if (arena.teamSpawns.isEmpty()) throw new IllegalArgumentException("Arena '" + arena.id + "' has no team spawns.");
            for (int team = 1; team <= definition.teamCount; team++) {
                final int requestedTeam = team;
                if (arena.teamSpawns.stream().noneMatch(spawn -> spawn.team == requestedTeam)) {
                    throw new IllegalArgumentException("Arena '" + arena.id + "' has no spawn for team " + team + ".");
                }
            }
            for (MinigameSpawnPoint spawn : arena.teamSpawns) validateLocation(spawn.location, "Team spawn");
            if (arena.resetRegionAfterMatch && arena.regionId.isBlank()) {
                throw new IllegalArgumentException("Arena '" + arena.id + "' enables region reset without a region ID.");
            }
            if (referencesMustExist && arena.resetRegionAfterMatch) {
                if (!Config.ENABLE_ADMIN_REGIONS.get()) {
                    throw new IllegalArgumentException("Region reset requires the Regions module.");
                }
                Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
                if (region == null) throw new IllegalArgumentException("Unknown reset region: " + arena.regionId);
                if (!SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
                    throw new IllegalArgumentException("No saved snapshot exists for region '" + region.getName() + "'.");
                }
            }
        }
        validateCondition(definition.prerequisites);
        validateActions(definition.participationRewards);
        validateActions(definition.winnerRewards);
    }

    private static void validateCondition(be.winnetrie.mod.simpleserverutilities.content.ContentCondition condition) {
        if (condition == null) throw new IllegalArgumentException("Minigame prerequisite condition is missing.");
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered(condition.type())) {
            throw new IllegalArgumentException("Unknown minigame prerequisite condition: " + condition.type());
        }
        for (var child : condition.children()) validateCondition(child);
    }

    private static void validateActions(List<ContentAction> actions) {
        for (ContentAction action : actions) {
            if (!SimpleServerUtilities.CONTENT_ACTIONS.isRegistered(action.type())) {
                throw new IllegalArgumentException("Unknown reward action type: " + action.type());
            }
        }
    }

    private static void validateLocation(MinigameLocation location, String label) {
        if (location == null) throw new IllegalArgumentException(label + " is missing.");
        location.normalize();
        try { Identifier.parse(location.dimension); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(label + " has an invalid dimension: " + location.dimension); }
    }

    public void handleScoreAction(MinigameScoreActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> processScoreAction(player, payload));
    }

    private void processScoreAction(ServerPlayer actor, MinigameScoreActionPayload payload) {
        if (!acceptRequest(actor.getUUID(), payload.requestId())) return;
        String notice;
        boolean error = false;
        try {
            if (!active()) throw new IllegalArgumentException("The Minigame Framework is disabled.");
            requireAdmin(actor);
            ServerPlayer target = actor.level().getServer().getPlayerList().getPlayerByName(payload.playerName());
            if (target == null) throw new IllegalArgumentException("That player is not online.");
            switch (payload.mode().trim().toLowerCase(Locale.ROOT)) {
                case "add" -> addScore(target.getUUID(), payload.amount());
                case "set" -> setScore(target.getUUID(), payload.amount());
                default -> throw new IllegalArgumentException("Unknown score adjustment mode.");
            }
            notice = ("set".equalsIgnoreCase(payload.mode()) ? "Set" : "Changed")
                    + " minigame score for " + target.getName().getString() + ".";
        } catch (RuntimeException exception) {
            error = true;
            notice = exception.getMessage() == null ? "The score adjustment failed safely." : exception.getMessage();
        }
        sendLobby(actor, notice, error, payload.requestId());
    }

    public void handleRequest(MinigameLobbyRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> processRequest(player, payload));
    }

    private void processRequest(ServerPlayer player, MinigameLobbyRequestPayload payload) {
        String action = payload.action().trim().toLowerCase(Locale.ROOT);
        // Opening is side-effect free and begins a fresh lobby request sequence. This
        // prevents a newly opened GUI from inheriting a higher sequence number from
        // a previously closed lobby while all mutating follow-up actions remain ordered.
        if ("open".equals(action)) resetRequestSequence(player.getUUID(), payload.requestId());
        else if (!acceptRequest(player.getUUID(), payload.requestId())) return;
        if (!active() || !canAccess(player)) {
            PacketDistributor.sendToPlayer(player, new MinigameLobbyDataPayload(
                    "You do not have permission to use the Minigame Framework.", true, canAdmin(player),
                    payload.requestId(), "", "", List.of()));
            return;
        }
        String notice = "";
        boolean error = false;
        try {
            switch (action) {
                case "open", "refresh" -> { }
                case "join" -> notice = joinQueue(player, payload.minigameId());
                case "leave" -> notice = leave(player, true);
                case "force_start" -> {
                    requireAdmin(player);
                    notice = forceStart(payload.minigameId());
                }
                case "finish" -> {
                    requireAdmin(player);
                    notice = finishFirstMatch(payload.minigameId(), "Finished by an administrator.");
                }
                case "release_arena" -> {
                    requireAdmin(player);
                    notice = releaseBlockedArena(payload.minigameId());
                }
                case "delete" -> {
                    requireAdmin(player);
                    if (!deleteDefinition(payload.minigameId())) throw new IllegalArgumentException("The minigame is active or could not be deleted.");
                    notice = "Minigame deleted.";
                }
                default -> throw new IllegalArgumentException("Unknown minigame action.");
            }
        } catch (RuntimeException exception) {
            notice = exception.getMessage() == null ? "The minigame action failed safely." : exception.getMessage();
            error = true;
        }
        sendLobby(player, notice, error, payload.requestId());
    }

    private synchronized void resetRequestSequence(UUID playerId, long requestId) {
        lastRequests.put(playerId, Math.max(0L, requestId));
    }

    private synchronized boolean acceptRequest(UUID playerId, long requestId) {
        long previous = lastRequests.getOrDefault(playerId, -1L);
        if (requestId <= previous) return false;
        lastRequests.put(playerId, requestId);
        return true;
    }

    public void open(ServerPlayer player) {
        synchronized (this) { lastRequests.remove(player.getUUID()); }
        if (!active() || !canAccess(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to use the Minigame Framework."));
            return;
        }
        sendLobby(player, "", false, 0L);
    }

    public String joinQueue(ServerPlayer player, String rawId) {
        if (!active()) throw new IllegalArgumentException("The Minigame Framework is disabled.");
        if (!ContentAccessPolicy.canJoinMinigameQueue(player)) {
            throw new IllegalArgumentException("You do not have permission to join minigame queues.");
        }
        String id = ContentId.require(rawId, "Minigame ID");
        synchronized (this) {
            if (playerQueues.containsKey(player.getUUID())) throw new IllegalArgumentException("You are already queued for a minigame.");
            if (playerMatches.containsKey(player.getUUID())) throw new IllegalArgumentException("You are already in a minigame match.");
            MinigameDefinition definition = definitions.get(id);
            if (definition == null || !definition.enabled) throw new IllegalArgumentException("That minigame is unavailable.");
            var condition = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(definition.prerequisites,
                    new ContentConditionContext(server, player, "minigames", definition.id,
                            Map.of("minigame", definition.id)));
            if (!condition.matched()) throw new IllegalArgumentException(condition.reason());
            if (definition.allowLateJoin && tryLateJoin(player, definition)) {
                return "Joined the running match for " + definition.displayName + ".";
            }
            LinkedHashMap<UUID, Long> queue = queues.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
            if (queue.size() >= MAX_QUEUE_SIZE) throw new IllegalArgumentException("This minigame queue is full.");
            queue.put(player.getUUID(), System.currentTimeMillis());
            playerQueues.put(player.getUUID(), id);
        }
        publish(player, ContentEventTypes.MINIGAME_QUEUE_JOINED, id, 1L, Map.of());
        return "Joined the queue for " + displayName(id) + ".";
    }

    private boolean tryLateJoin(ServerPlayer player, MinigameDefinition definition) {
        MinigameMatch target = null;
        MinigameArenaDefinition arena = null;
        for (MinigameMatch candidate : matches.values()) {
            if (!candidate.minigameId.equals(definition.id) || candidate.state != MinigameMatchState.RUNNING
                    || candidate.teams.size() >= definition.maxPlayers) continue;
            MinigameArenaDefinition candidateArena = arena(definition, candidate.arenaId);
            if (candidateArena == null || !locationsResolvable(definition, candidateArena)) continue;
            target = candidate;
            arena = candidateArena;
            break;
        }
        if (target == null || arena == null) return false;
        int selectedTeam = leastPopulatedTeam(target, definition.teamCount);
        UUID playerId = player.getUUID();
        target.teams.put(playerId, selectedTeam);
        target.scores.put(playerId, 0L);
        target.returnLocations.put(playerId, MinigameLocation.of(player));
        recoveries.put(playerId, new MinigameRecoveryData.Entry(playerId, definition.id,
                target.id.toString(), target.returnLocations.get(playerId).copy()));
        playerMatches.put(playerId, target.id);
        saveRecovery();
        teleport(player, arena.spawnForTeam(selectedTeam));
        player.sendSystemMessage(Component.literal("Joined " + definition.displayName + " on team " + selectedTeam + "."));
        publish(player, ContentEventTypes.MINIGAME_STARTED, definition.id, 1L,
                Map.of("match", target.id.toString(), "arena", target.arenaId,
                        "team", Integer.toString(selectedTeam), "phase", "late_join"));
        return true;
    }

    private static int leastPopulatedTeam(MinigameMatch match, int teamCount) {
        int selected = 1;
        int selectedSize = Integer.MAX_VALUE;
        for (int team = 1; team <= teamCount; team++) {
            int requested = team;
            int size = (int) match.teams.values().stream().filter(value -> value == requested).count();
            if (size < selectedSize) { selected = team; selectedSize = size; }
        }
        return selected;
    }

    public String leave(ServerPlayer player, boolean voluntary) {
        UUID playerId = player.getUUID();
        String queued;
        UUID matchId;
        synchronized (this) {
            queued = playerQueues.remove(playerId);
            if (queued != null) {
                LinkedHashMap<UUID, Long> queue = queues.get(queued);
                if (queue != null) queue.remove(playerId);
            }
            matchId = playerMatches.get(playerId);
        }
        if (queued != null) {
            publish(player, ContentEventTypes.MINIGAME_QUEUE_LEFT, queued, 1L,
                    Map.of("reason", voluntary ? "voluntary" : "disconnect"));
            return "Left the queue for " + displayName(queued) + ".";
        }
        if (matchId != null) {
            if (voluntary) withdrawFromMatch(playerId, "Player left the match.");
            else eliminate(playerId, "Player disconnected.");
            return "You left the active minigame match.";
        }
        return "You are not in a minigame queue or match.";
    }


    /**
     * Removes a voluntary leaver from the live roster immediately. This frees the
     * queue/match slot and prevents participation or winner rewards from being
     * granted after abandoning a match.
     */
    private void withdrawFromMatch(UUID playerId, String reason) {
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameLocation destination;
        synchronized (this) {
            match = matchFor(playerId);
            if (match == null) return;
            definition = definitions.get(match.minigameId);
            destination = match.returnLocations.remove(playerId);
            match.teams.remove(playerId);
            match.scores.remove(playerId);
            match.eliminated.remove(playerId);
            playerMatches.remove(playerId);
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        boolean returned = player != null && destination != null && teleport(player, destination);
        if (returned) {
            synchronized (this) {
                recoveries.remove(playerId);
                saveRecovery();
            }
        }
        if (player != null) player.sendSystemMessage(Component.literal(reason));
        if (match.teams.isEmpty()) {
            finish(match, "All players left the match.");
        } else if (definition != null && "last_team_standing".equals(definition.victoryMode)) {
            Set<Integer> alive = activeTeams(match);
            if (alive.size() == 1) {
                match.winningTeams = Set.copyOf(alive);
                finish(match, "Last team standing.");
            }
        }
    }

    public synchronized String forceStart(String rawId) {
        String id = ContentId.require(rawId, "Minigame ID");
        MinigameDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown minigame: " + id);
        MinigameMatch match = tryStart(definition, true);
        if (match == null) throw new IllegalArgumentException("Not enough queued players or no usable arena is available.");
        return "Started match " + match.id + " for " + definition.displayName + ".";
    }

    public synchronized void tick(MinecraftServer activeServer) {
        if (!active() || server == null || server != activeServer) return;
        serverTicks++;
        if (serverTicks % 20L != 0L) return;
        removeOfflineQueuedPlayers();
        for (MinigameDefinition definition : definitions.values()) {
            if (definition.enabled && definition.automaticStart) {
                while (queueSize(definition.id) >= definition.minPlayers && tryStart(definition, false) != null) {
                    // Start as many complete matches as there are free arenas and queued players.
                }
            }
        }
        for (MinigameMatch match : List.copyOf(matches.values())) advance(match);
    }

    private void removeOfflineQueuedPlayers() {
        for (Map.Entry<UUID, String> entry : List.copyOf(playerQueues.entrySet())) {
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) continue;
            LinkedHashMap<UUID, Long> queue = queues.get(entry.getValue());
            if (queue != null) queue.remove(entry.getKey());
            playerQueues.remove(entry.getKey());
        }
    }

    private synchronized MinigameMatch tryStart(MinigameDefinition definition, boolean forced) {
        LinkedHashMap<UUID, Long> queue = queues.computeIfAbsent(definition.id, ignored -> new LinkedHashMap<>());
        ArrayList<ServerPlayer> candidates = new ArrayList<>();
        for (UUID playerId : List.copyOf(queue.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) { queue.remove(playerId); playerQueues.remove(playerId); continue; }
            candidates.add(player);
            if (candidates.size() >= definition.maxPlayers) break;
        }
        int required = forced ? 1 : definition.minPlayers;
        if (candidates.size() < required) return null;
        MinigameArenaDefinition arena = freeArena(definition);
        if (arena == null) return null;
        if (!locationsResolvable(definition, arena)) return null;

        UUID matchId = UUID.randomUUID();
        MinigameMatch match = new MinigameMatch(matchId, definition.id, arena.id, serverTicks);
        int team = 1;
        for (ServerPlayer player : candidates) {
            queue.remove(player.getUUID());
            playerQueues.remove(player.getUUID());
            match.teams.put(player.getUUID(), team);
            match.scores.put(player.getUUID(), 0L);
            match.returnLocations.put(player.getUUID(), MinigameLocation.of(player));
            recoveries.put(player.getUUID(), new MinigameRecoveryData.Entry(player.getUUID(), definition.id,
                    match.id.toString(), match.returnLocations.get(player.getUUID()).copy()));
            playerMatches.put(player.getUUID(), match.id);
            team = team % definition.teamCount + 1;
        }
        matches.put(match.id, match);
        String reservedArenaKey = arenaKey(definition.id, arena.id);
        arenaReservations.put(reservedArenaKey, match.id);
        if (arena.resetRegionAfterMatch) unsafeArenas.add(reservedArenaKey);
        saveRecovery();
        for (ServerPlayer player : candidates) {
            teleport(player, arena.spawnForTeam(match.team(player.getUUID())));
            player.sendSystemMessage(Component.literal("Joined " + definition.displayName + " on team "
                    + match.team(player.getUUID()) + ". Match begins in " + definition.countdownSeconds + " seconds."));
        }
        return match;
    }

    private boolean locationsResolvable(MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (resolveLevel(arena.lobby.dimension) == null || resolveLevel(arena.spectator.dimension) == null) return false;
        for (int team = 1; team <= definition.teamCount; team++) {
            if (resolveLevel(arena.spawnForTeam(team).dimension) == null) return false;
        }
        return true;
    }

    private void advance(MinigameMatch match) {
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            definition = definitions.get(match.minigameId);
            arena = definition == null ? null : arena(definition, match.arenaId);
        }
        if (definition == null || arena == null) {
            finish(match, "Definition or arena was removed.");
            cleanup(match, null, null);
            return;
        }
        long elapsedSeconds = Math.max(0L, (serverTicks - match.stateStartedTick) / 20L);
        switch (match.state) {
            case COUNTDOWN -> {
                long remaining = Math.max(0L, definition.countdownSeconds - elapsedSeconds);
                if (remaining != match.lastAnnouncementSecond && (remaining <= 5L || remaining == 10L || remaining % 30L == 0L)) {
                    match.lastAnnouncementSecond = remaining;
                    announce(match, remaining == 0L ? "Go!" : "Match starts in " + remaining + "…");
                }
                if (elapsedSeconds >= definition.countdownSeconds) {
                    match.state = MinigameMatchState.RUNNING;
                    match.stateStartedTick = serverTicks;
                    match.lastAnnouncementSecond = Long.MIN_VALUE;
                    announce(match, definition.displayName + " has started!");
                    publishMatch(match, ContentEventTypes.MINIGAME_STARTED, "started");
                }
            }
            case RUNNING -> {
                if ("last_team_standing".equals(definition.victoryMode)) {
                    Set<Integer> alive = activeTeams(match);
                    if (alive.size() <= 1 && !alive.isEmpty()) {
                        match.winningTeams = Set.copyOf(alive);
                        finish(match, "Last team standing.");
                        return;
                    }
                }
                if (definition.matchDurationSeconds > 0 && elapsedSeconds >= definition.matchDurationSeconds) {
                    finish(match, "Time limit reached.");
                }
            }
            case POST_GAME -> {
                if (elapsedSeconds >= definition.postGameSeconds) cleanup(match, definition, arena);
            }
            case RESETTING, FINISHED -> { }
        }
    }

    /** Immutable runtime view for concrete minigame rule handlers. */
    public synchronized MatchView matchView(UUID playerId) {
        MinigameMatch match = matchFor(playerId);
        if (match == null) return null;
        return new MatchView(match.id, match.minigameId, match.arenaId, match.state,
                match.team(playerId), match.score(playerId), match.eliminated.contains(playerId));
    }

    /** Completes one exact live match and optionally supplies its winning teams. */
    public void finishMatch(UUID matchId, Set<Integer> winningTeams, String reason) {
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matches.get(matchId);
            if (match == null) throw new IllegalArgumentException("Unknown active minigame match.");
            definition = definitions.get(match.minigameId);
            if (definition == null) throw new IllegalArgumentException("The minigame definition is unavailable.");
            LinkedHashSet<Integer> validated = new LinkedHashSet<>();
            if (winningTeams != null) {
                for (Integer team : winningTeams) {
                    if (team == null || team < 1 || team > definition.teamCount) {
                        throw new IllegalArgumentException("Winning team is outside the configured team range.");
                    }
                    validated.add(team);
                }
            }
            match.winningTeams = Set.copyOf(validated);
        }
        finish(match, reason);
    }

    public synchronized void addScore(UUID playerId, long amount) {
        MinigameMatch match = matchFor(playerId);
        if (match == null || match.state != MinigameMatchState.RUNNING) {
            throw new IllegalArgumentException("The player is not in a running minigame.");
        }
        long before = match.scores.getOrDefault(playerId, 0L);
        long next;
        try { next = Math.addExact(before, amount); }
        catch (ArithmeticException ignored) { next = amount > 0 ? Long.MAX_VALUE : Long.MIN_VALUE; }
        match.scores.put(playerId, next);
    }

    public synchronized void setScore(UUID playerId, long value) {
        MinigameMatch match = matchFor(playerId);
        if (match == null || match.state != MinigameMatchState.RUNNING) {
            throw new IllegalArgumentException("The player is not in a running minigame.");
        }
        match.scores.put(playerId, value);
    }

    public void eliminate(UUID playerId, String reason) {
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(playerId);
            if (match == null || match.eliminated.contains(playerId)) return;
            match.eliminated.add(playerId);
            definition = definitions.get(match.minigameId);
            arena = definition == null ? null : arena(definition, match.arenaId);
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            if (arena != null) teleport(player, arena.spectator);
            player.sendSystemMessage(Component.literal(reason == null || reason.isBlank() ? "You were eliminated." : reason));
        }
        if (definition != null && "last_team_standing".equals(definition.victoryMode)) {
            Set<Integer> alive = activeTeams(match);
            if (alive.size() <= 1 && !alive.isEmpty()) {
                match.winningTeams = Set.copyOf(alive);
                finish(match, "Last team standing.");
            }
        }
    }

    public void onPlayerDeath(ServerPlayer player) {
        if (player == null) return;
        eliminate(player.getUUID(), "You were eliminated from the minigame.");
    }

    public void onPlayerRespawn(ServerPlayer player) {
        if (player == null) return;
        MinigameMatch match;
        MinigameArenaDefinition arena = null;
        synchronized (this) {
            match = matchFor(player.getUUID());
            if (match != null && match.eliminated.contains(player.getUUID())) {
                MinigameDefinition definition = definitions.get(match.minigameId);
                arena = definition == null ? null : arena(definition, match.arenaId);
            }
        }
        if (arena != null) teleport(player, arena.spectator);
    }

    public void onLogin(ServerPlayer player) {
        MinigameRecoveryData.Entry recovery;
        MinigameArenaDefinition activeArena = null;
        synchronized (this) {
            MinigameMatch activeMatch = matchFor(player.getUUID());
            if (activeMatch != null) {
                MinigameDefinition definition = definitions.get(activeMatch.minigameId);
                activeArena = definition == null ? null : arena(definition, activeMatch.arenaId);
                recovery = null;
            } else {
                recovery = recoveries.get(player.getUUID());
            }
        }
        if (activeArena != null) {
            teleport(player, activeArena.spectator);
            player.sendSystemMessage(Component.literal("You rejoined an active minigame as a spectator."));
            return;
        }
        if (recovery == null) return;
        if (teleport(player, recovery.returnLocation)) {
            synchronized (this) {
                recoveries.remove(player.getUUID());
                saveRecovery();
            }
            player.sendSystemMessage(Component.literal("You were returned from an interrupted minigame session."));
        }
    }

    public void onLogout(ServerPlayer player) {
        if (player == null) return;
        leave(player, false);
        synchronized (this) { lastRequests.remove(player.getUUID()); }
    }

    private void finish(MinigameMatch match, String reason) {
        MinigameDefinition definition;
        synchronized (this) {
            if (match.state == MinigameMatchState.POST_GAME || match.state == MinigameMatchState.RESETTING
                    || match.state == MinigameMatchState.FINISHED) return;
            definition = definitions.get(match.minigameId);
            if (definition == null) return;
            if (match.winningTeams.isEmpty()) match.winningTeams = determineWinners(match);
            match.state = MinigameMatchState.POST_GAME;
            match.stateStartedTick = serverTicks;
            match.finishReason = reason == null ? "" : reason;
        }
        deliverRewardsAndEvents(match, definition);
        String winners = match.winningTeams.isEmpty() ? "No winner" : "Winning team(s): " + match.winningTeams;
        announce(match, definition.displayName + " finished. " + winners + ". " + match.finishReason);
    }

    private Set<Integer> determineWinners(MinigameMatch match) {
        Map<Integer, Long> teamScores = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            long score = match.scores.getOrDefault(entry.getKey(), 0L);
            teamScores.merge(entry.getValue(), score, MinigameManager::safeAdd);
        }
        if (teamScores.isEmpty()) return Set.of();
        long best = teamScores.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        LinkedHashSet<Integer> winners = new LinkedHashSet<>();
        for (Map.Entry<Integer, Long> entry : teamScores.entrySet()) if (entry.getValue() == best) winners.add(entry.getKey());
        return Set.copyOf(winners);
    }

    private void deliverRewardsAndEvents(MinigameMatch match, MinigameDefinition definition) {
        for (Map.Entry<UUID, Integer> participant : match.teams.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.getKey());
            if (player == null) continue;
            boolean won = match.winningTeams.contains(participant.getValue());
            executeRewards(player, definition.participationRewards, match, "participation");
            if (won) executeRewards(player, definition.winnerRewards, match, "winner");
            Map<String, String> metadata = Map.of(
                    "match", match.id.toString(),
                    "arena", match.arenaId,
                    "team", Integer.toString(participant.getValue()),
                    "score", Long.toString(match.score(participant.getKey())),
                    "result", won ? "win" : "loss");
            publish(player, ContentEventTypes.MINIGAME_COMPLETED, definition.id, 1L, metadata);
            if (won) publish(player, ContentEventTypes.MINIGAME_WON, definition.id, 1L, metadata);
        }
    }

    private void executeRewards(ServerPlayer player, List<ContentAction> actions, MinigameMatch match, String kind) {
        if (actions == null || actions.isEmpty()) return;
        var result = SimpleServerUtilities.CONTENT_ACTIONS.execute(actions,
                new ContentActionContext(server, player, "minigames", match.minigameId,
                        match.id + ":" + player.getUUID() + ":" + kind,
                        Map.of("minigame", match.minigameId, "match", match.id.toString(), "reward", kind)));
        if (!result.successful()) {
            SimpleServerUtilities.LOGGER.error("Failed to deliver {} minigame rewards to {}: {}",
                    kind, player.getName().getString(), result.error());
            player.sendSystemMessage(Component.literal("Some minigame rewards could not be delivered: " + result.error()));
        }
    }

    private void cleanup(MinigameMatch match, MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (definition == null) definition = definition(match.minigameId);
        if (definition != null && arena == null) arena = arena(definition, match.arenaId);
        for (UUID playerId : List.copyOf(match.teams.keySet())) returnParticipant(match, playerId);
        synchronized (this) {
            matches.remove(match.id);
            match.state = MinigameMatchState.FINISHED;
        }
        if (definition != null && arena != null && arena.resetRegionAfterMatch) {
            scheduleArenaReset(match, definition, arena);
        } else {
            synchronized (this) { unsafeArenas.remove(arenaKey(match.minigameId, match.arenaId)); }
            releaseArena(match.minigameId, match.arenaId);
            saveRecovery();
        }
    }

    private void returnParticipant(MinigameMatch match, UUID playerId) {
        MinigameLocation destination;
        synchronized (this) {
            playerMatches.remove(playerId);
            destination = match.returnLocations.get(playerId);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null && destination != null && teleport(player, destination)) {
            synchronized (this) {
                recoveries.remove(playerId);
                saveRecovery();
            }
        }
    }

    private void scheduleArenaReset(MinigameMatch match, MinigameDefinition definition, MinigameArenaDefinition arena) {
        String key = arenaKey(definition.id, arena.id);
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null || !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
            synchronized (this) { blockedArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.LOGGER.error("Blocked minigame arena '{}' because reset region '{}' is unavailable.", key, arena.regionId);
            return;
        }
        ServerLevel level = server.getLevel(region.getDimension());
        if (level == null) {
            synchronized (this) { blockedArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.LOGGER.error("Blocked minigame arena '{}' because its reset dimension is unavailable.", key);
            return;
        }
        try {
            var job = SimpleServerUtilities.REGION_SNAPSHOTS.createResetJob(level, region);
            synchronized (this) {
                resettingArenas.add(key);
                unsafeArenas.add(key);
                arenaReservations.remove(key);
                saveRecovery();
            }
            SimpleServerUtilities.JOBS.submit(job, result -> {
                synchronized (MinigameManager.this) {
                    resettingArenas.remove(key);
                    if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                        blockedArenas.remove(key);
                        unsafeArenas.remove(key);
                    } else {
                        blockedArenas.add(key);
                        unsafeArenas.add(key);
                    }
                    saveRecovery();
                }
                if (result.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    SimpleServerUtilities.LOGGER.error("Minigame arena reset failed for '{}': {}", key, result.error());
                }
            });
        } catch (Exception exception) {
            synchronized (this) { blockedArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.LOGGER.error("Failed to schedule reset for minigame arena '{}'.", key, exception);
        }
    }

    public synchronized String releaseBlockedArena(String raw) {
        String target = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        boolean removed = blockedArenas.removeIf(key -> key.equals(target) || key.startsWith(target + ":"));
        boolean unsafeRemoved = unsafeArenas.removeIf(key -> key.equals(target) || key.startsWith(target + ":"));
        if (removed || unsafeRemoved) saveRecovery();
        return removed || unsafeRemoved ? "Released blocked arena state." : "No blocked arena matched that minigame or arena key.";
    }

    public String finishFirstMatch(String rawMinigameId, String reason) {
        String id = ContentId.require(rawMinigameId, "Minigame ID");
        MinigameMatch match;
        synchronized (this) {
            match = matches.values().stream().filter(value -> value.minigameId.equals(id)
                    && value.state != MinigameMatchState.POST_GAME && value.state != MinigameMatchState.FINISHED)
                    .findFirst().orElse(null);
        }
        if (match == null) throw new IllegalArgumentException("That minigame has no active match.");
        finish(match, reason);
        return "Match " + match.id + " moved to post-game state.";
    }

    public String finishPlayerMatch(ServerPlayer actor, String reason) {
        MinigameMatch match;
        synchronized (this) { match = matchFor(actor.getUUID()); }
        if (match == null) throw new IllegalArgumentException("You are not inside an active match. Use the command with a player in that match.");
        finish(match, reason);
        return "Match moved to post-game state.";
    }

    private synchronized MinigameMatch matchFor(UUID playerId) {
        UUID matchId = playerMatches.get(playerId);
        return matchId == null ? null : matches.get(matchId);
    }

    private synchronized boolean hasRuntimeFor(String id) {
        String prefix = id + ":";
        return queueSize(id) > 0
                || matches.values().stream().anyMatch(match -> match.minigameId.equals(id))
                || unsafeArenas.stream().anyMatch(key -> key.startsWith(prefix))
                || resettingArenas.stream().anyMatch(key -> key.startsWith(prefix));
    }

    private synchronized int queueSize(String id) {
        LinkedHashMap<UUID, Long> queue = queues.get(id);
        return queue == null ? 0 : queue.size();
    }

    private synchronized MinigameArenaDefinition freeArena(MinigameDefinition definition) {
        for (MinigameArenaDefinition arena : definition.arenas) {
            if (!arena.enabled) continue;
            String key = arenaKey(definition.id, arena.id);
            if (!arenaReservations.containsKey(key) && !resettingArenas.contains(key) && !blockedArenas.contains(key)) return arena;
        }
        return null;
    }

    private static MinigameArenaDefinition arena(MinigameDefinition definition, String id) {
        if (definition == null) return null;
        for (MinigameArenaDefinition arena : definition.arenas) if (arena.id.equals(id)) return arena;
        return null;
    }

    private synchronized void releaseArena(String minigameId, String arenaId) {
        arenaReservations.remove(arenaKey(minigameId, arenaId));
    }

    private synchronized Set<Integer> activeTeams(MinigameMatch match) {
        LinkedHashSet<Integer> teams = new LinkedHashSet<>();
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            if (!match.eliminated.contains(entry.getKey())) teams.add(entry.getValue());
        }
        return Set.copyOf(teams);
    }

    private void announce(MinigameMatch match, String message) {
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(Component.literal(message), true);
        }
    }

    private void publishMatch(MinigameMatch match, String type, String phase) {
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) publish(player, type, match.minigameId, 1L,
                    Map.of("match", match.id.toString(), "arena", match.arenaId,
                            "team", Integer.toString(entry.getValue()), "phase", phase));
        }
    }

    private void publish(ServerPlayer player, String type, String subject, long amount, Map<String, String> metadata) {
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(),
                ContentEvent.player(type, player.getUUID(), "minigames", subject, subject, amount, metadata));
    }

    private void sendLobby(ServerPlayer player, String notice, boolean error, long requestId) {
        ArrayList<MinigameLobbyDataPayload.GameEntry> entries = new ArrayList<>();
        String queued;
        MinigameMatch ownMatch;
        synchronized (this) {
            queued = playerQueues.getOrDefault(player.getUUID(), "");
            ownMatch = matchFor(player.getUUID());
            for (MinigameDefinition definition : definitions.values()) {
                if (!definition.enabled && !canAdmin(player)) continue;
                var availability = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(definition.prerequisites,
                        new ContentConditionContext(server, player, "minigames", definition.id,
                                Map.of("minigame", definition.id)));
                int free = 0, blocked = 0;
                for (MinigameArenaDefinition arena : definition.arenas) {
                    String key = arenaKey(definition.id, arena.id);
                    if (!arena.enabled) continue;
                    if (blockedArenas.contains(key)) blocked++;
                    else if (!arenaReservations.containsKey(key) && !resettingArenas.contains(key)) free++;
                }
                int running = (int) matches.values().stream().filter(match -> match.minigameId.equals(definition.id)).count();
                boolean inThisQueue = definition.id.equals(queued);
                boolean inThisMatch = ownMatch != null && definition.id.equals(ownMatch.minigameId);
                entries.add(new MinigameLobbyDataPayload.GameEntry(definition.id, definition.displayName,
                        definition.description, definition.iconItem, definition.enabled, definition.minPlayers,
                        definition.maxPlayers, definition.teamCount, queueSize(definition.id), running, free, blocked,
                        definition.victoryMode, availability.matched(), availability.reason(), inThisQueue, inThisMatch,
                        inThisMatch ? ownMatch.state.name().toLowerCase(Locale.ROOT) : "",
                        inThisMatch ? ownMatch.team(player.getUUID()) : 0,
                        inThisMatch ? ownMatch.score(player.getUUID()) : 0L));
            }
        }
        entries.sort(Comparator.comparing(MinigameLobbyDataPayload.GameEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        PacketDistributor.sendToPlayer(player, new MinigameLobbyDataPayload(notice, error, canAdmin(player), requestId,
                queued, ownMatch == null ? "" : ownMatch.id.toString(), entries));
    }

    public synchronized boolean isQueued(UUID playerId, String rawMinigameId) {
        String queued = playerQueues.get(playerId);
        String requested = ContentId.normalize(rawMinigameId);
        return queued != null && (requested.isBlank() || queued.equals(requested));
    }

    public synchronized boolean isInMatch(UUID playerId, String rawMinigameId) {
        MinigameMatch match = matchFor(playerId);
        String requested = ContentId.normalize(rawMinigameId);
        return match != null && (requested.isBlank() || match.minigameId.equals(requested));
    }

    public synchronized String queuedMinigame(UUID playerId) {
        return playerQueues.getOrDefault(playerId, "");
    }

    public synchronized String activeMinigame(UUID playerId) {
        MinigameMatch match = matchFor(playerId);
        return match == null ? "" : match.minigameId;
    }

    public synchronized Snapshot snapshot() {
        int queued = queues.values().stream().mapToInt(Map::size).sum();
        return new Snapshot(definitions.size(), queued, matches.size(), arenaReservations.size(),
                resettingArenas.size(), blockedArenas.size(), recoveries.size());
    }

    public synchronized void shutdownRuntime(boolean returnOnlinePlayers) {
        if (returnOnlinePlayers && server != null) {
            for (MinigameMatch match : List.copyOf(matches.values())) {
                for (UUID playerId : List.copyOf(match.teams.keySet())) returnParticipant(match, playerId);
            }
        }
        clearRuntime(false);
        saveRecovery();
    }

    private synchronized void clearRuntime(boolean keepServer) {
        queues.clear();
        matches.clear();
        playerMatches.clear();
        playerQueues.clear();
        arenaReservations.clear();
        resettingArenas.clear();
        blockedArenas.clear();
        lastRequests.clear();
        serverTicks = 0L;
        if (!keepServer) server = null;
    }

    public synchronized void clear() {
        clearRuntime(false);
        definitions.clear();
        recoveries.clear();
        unsafeArenas.clear();
        definitionStore.reset();
        recoveryStore.reset();
        definitionFolder = null;
        recoveryFile = null;
    }

    private boolean teleport(ServerPlayer player, MinigameLocation location) {
        if (player == null || location == null) return false;
        ServerLevel level = resolveLevel(location.dimension);
        if (level == null) return false;
        player.teleportTo(level, location.x, location.y, location.z, Set.of(), location.yaw, location.pitch, true);
        return true;
    }

    private ServerLevel resolveLevel(String rawDimension) {
        if (server == null) return null;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(rawDimension));
            return server.getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static long safeAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException ignored) { return right >= 0 ? Long.MAX_VALUE : Long.MIN_VALUE; }
    }

    private static String arenaKey(String minigame, String arena) { return minigame + ":" + arena; }
    private String displayName(String id) { MinigameDefinition value = definition(id); return value == null ? id : value.displayName; }
    private static boolean active() { return Config.ENABLE_MINIGAMES.get() && SimpleServerUtilities.CORE.modules().isActive("minigames"); }
    private static boolean canAccess(ServerPlayer player) {
        return canAdmin(player) || ContentAccessPolicy.canUse(player, ContentFeature.MINIGAMES);
    }
    private static boolean canAdmin(ServerPlayer player) { return PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false); }
    private static void requireAdmin(ServerPlayer player) { if (!canAdmin(player)) throw new IllegalArgumentException("Minigame administrator permission is required."); }

    public record MatchView(UUID matchId, String minigameId, String arenaId, MinigameMatchState state,
                            int team, long score, boolean eliminated) { }

    public record Snapshot(int definitions, int queuedPlayers, int matches, int reservedArenas,
                           int resettingArenas, int blockedArenas, int pendingRecoveries) { }
}
