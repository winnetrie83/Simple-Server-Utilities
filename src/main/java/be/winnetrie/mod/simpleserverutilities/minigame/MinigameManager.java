package be.winnetrie.mod.simpleserverutilities.minigame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

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
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import be.winnetrie.mod.simpleserverutilities.mail.MailManager;
import be.winnetrie.mod.simpleserverutilities.mail.MailOperationResult;
import be.winnetrie.mod.simpleserverutilities.mail.MailSource;
import be.winnetrie.mod.simpleserverutilities.mixin.BlockEntityComponentInvoker;
import be.winnetrie.mod.simpleserverutilities.network.MinigameHudPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCastBarPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameDominationVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCtfVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameScoreActionPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Independent queue, arena and match lifecycle for every SSU minigame type.
 * It deliberately contains no NPC, quest or dungeon dependency.
 */
public final class MinigameManager {
    public static final int DEFINITION_SCHEMA_VERSION = 10;
    public static final int RECOVERY_SCHEMA_VERSION = 2;
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_SERIALIZED_CHARACTERS = 65_535;
    public static final int MAX_QUEUE_SIZE = 2_048;
    private static final Duration CRITICAL_RECOVERY_FLUSH_TIMEOUT = Duration.ofSeconds(5);
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
    /** Session fail-safe: no new live player state may be replaced while recovery persistence is uncertain. */
    private boolean recoverySafetyHalted;

    public synchronized void load(MinecraftServer server) {
        clearRuntime(false);
        this.server = server;
        removeOrphanCtfBackFlags();
        Path root = StoragePaths.minigames(StoragePaths.root(server));
        definitionFolder = StoragePaths.minigameDefinitions(StoragePaths.root(server));
        recoveryFile = StoragePaths.minigameRecovery(StoragePaths.root(server));
        definitionStore.reset();
        recoveryStore.reset();
        recoverySafetyHalted = false;
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
                ensureArenaRegionsUnique(definition, "");
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
        recoveryStore.queueJson(GSON, recoveryFile, recoverySnapshot());
    }

    private synchronized MinigameRecoveryData recoverySnapshot() {
        MinigameRecoveryData data = new MinigameRecoveryData();
        data.players.addAll(recoveries.values());
        data.unsafeArenas.addAll(unsafeArenas);
        data.normalize();
        return data;
    }

    /**
     * Persists and verifies the exact recovery snapshot before SSU replaces any
     * live player inventory/gamemode state. This deliberately blocks only on
     * the recovery file, not on unrelated queued SSU storage writes.
     */
    private synchronized boolean saveRecoveryDurably(String operation) {
        if (recoveryFile == null) {
            SimpleServerUtilities.LOGGER.error(
                    "Refused critical minigame operation '{}' because the recovery path is unavailable.", operation);
            recoverySafetyHalted = true;
            return false;
        }
        MinigameRecoveryData snapshot = recoverySnapshot();
        String expected = GSON.toJson(snapshot);
        recoveryStore.queueJson(GSON, recoveryFile, snapshot);
        if (!SimpleServerUtilities.STORAGE.flushPath(recoveryFile, CRITICAL_RECOVERY_FLUSH_TIMEOUT)) {
            SimpleServerUtilities.LOGGER.error(
                    "Paused new minigame state changes because recovery storage could not be flushed during '{}'.",
                    operation);
            recoverySafetyHalted = true;
            return false;
        }
        try {
            String stored = Files.readString(recoveryFile, StandardCharsets.UTF_8);
            if (!expected.equals(stored)) {
                recoveryStore.forget(recoveryFile);
                SimpleServerUtilities.LOGGER.error(
                        "Paused new minigame state changes because recovery verification differed after '{}'.",
                        operation);
                recoverySafetyHalted = true;
                return false;
            }
            recoverySafetyHalted = false;
            return true;
        } catch (IOException exception) {
            recoveryStore.forget(recoveryFile);
            SimpleServerUtilities.LOGGER.error(
                    "Paused new minigame state changes because recovery verification failed after '{}'.",
                    operation,
                    exception);
            recoverySafetyHalted = true;
            return false;
        }
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
        MinigameDefinition definition = fromDraftJson(json);
        validateDefinition(definition, true);
        return definition;
    }

    /** Parses editor state without requiring every live-world reference to be complete yet. */
    public MinigameDefinition fromDraftJson(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Minigame editor data is missing or too large.");
        }
        MinigameDefinition definition = GSON.fromJson(json, MinigameDefinition.class);
        if (definition == null) throw new IllegalArgumentException("Minigame editor data is invalid.");
        definition.normalize();
        return definition;
    }

    public MinigameDefinition copy(MinigameDefinition definition) {
        return definition == null ? null : GSON.fromJson(GSON.toJson(definition), MinigameDefinition.class);
    }

    public synchronized boolean saveDefinition(String rawOriginalId, MinigameDefinition definition) {
        return saveDefinitionInternal(rawOriginalId, definition, false);
    }

    /** Package-private trusted path used only by the server-side selection wizard. */
    synchronized boolean saveManagedDefinition(String rawOriginalId, MinigameDefinition definition) {
        return saveDefinitionInternal(rawOriginalId, definition, true);
    }

    private boolean saveDefinitionInternal(String rawOriginalId, MinigameDefinition definition,
                                           boolean trustManagedArenaMetadata) {
        if (definition == null) return false;
        definition.normalize();
        String original = ContentId.normalize(rawOriginalId);
        MinigameDefinition existing = original.isBlank() ? null : definitions.get(original);
        reconcileManagedArenaOwnership(existing, definition, trustManagedArenaMetadata);
        validateDefinition(definition, true);
        ensureArenaRegionsUnique(definition, original);
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

    private static void reconcileManagedArenaOwnership(MinigameDefinition existing,
                                                        MinigameDefinition submitted,
                                                        boolean trusted) {
        if (trusted) {
            for (MinigameArenaDefinition arena : submitted.arenas) {
                if (arena.managedRegion && !isManagedRegionName(arena.regionId)) {
                    throw new IllegalArgumentException("Managed minigame regions must use the reserved ssu_mg_ name prefix.");
                }
            }
            return;
        }
        LinkedHashSet<String> ownedRegions = new LinkedHashSet<>();
        if (existing != null) {
            for (MinigameArenaDefinition arena : existing.arenas) {
                if (arena.managedRegion && isManagedRegionName(arena.regionId)) {
                    ownedRegions.add(arena.regionId.toLowerCase(Locale.ROOT));
                }
            }
        }
        LinkedHashSet<String> retained = new LinkedHashSet<>();
        for (MinigameArenaDefinition arena : submitted.arenas) {
            String region = arena.regionId == null ? "" : arena.regionId.trim().toLowerCase(Locale.ROOT);
            boolean ownsRegion = ownedRegions.contains(region);
            arena.managedRegion = ownsRegion;
            if (ownsRegion) retained.add(region);
        }
        if (!retained.containsAll(ownedRegions)) {
            throw new IllegalArgumentException("A selection-created arena region cannot be removed or changed in the editor. Delete the minigame to remove its managed arena safely.");
        }
    }

    private static boolean isManagedRegionName(String raw) {
        return raw != null && raw.trim().toLowerCase(Locale.ROOT).startsWith("ssu_mg_");
    }

    private void ensureArenaRegionsUnique(MinigameDefinition submitted, String originalId) {
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (MinigameArenaDefinition arena : submitted.arenas) {
            String region = arena.regionId == null ? "" : arena.regionId.trim().toLowerCase(Locale.ROOT);
            if (!region.isBlank()) requested.add(region);
        }
        if (requested.isEmpty()) return;
        for (Map.Entry<String, MinigameDefinition> entry : definitions.entrySet()) {
            if (!originalId.isBlank() && entry.getKey().equals(originalId)) continue;
            for (MinigameArenaDefinition arena : entry.getValue().arenas) {
                String region = arena.regionId == null ? "" : arena.regionId.trim().toLowerCase(Locale.ROOT);
                if (requested.contains(region)) {
                    throw new IllegalArgumentException("Arena region '" + arena.regionId
                            + "' is already owned by minigame '" + entry.getKey() + "'.");
                }
            }
        }
    }

    public synchronized boolean deleteDefinition(String rawId) {
        String id = ContentId.normalize(rawId);
        if (id.isBlank() || hasRuntimeFor(id)) return false;
        MinigameDefinition removedDefinition = definitions.remove(id);
        queues.remove(id);
        if (removedDefinition == null) return false;
        saveAll();
        // Only regions explicitly created and owned by the selection wizard are cleaned up.
        // Existing administrator regions referenced manually remain untouched.
        for (MinigameArenaDefinition arena : removedDefinition.arenas) {
            if (!arena.managedRegion || !isManagedRegionName(arena.regionId)) continue;
            boolean referencedElsewhere = definitions.values().stream()
                    .flatMap(value -> value.arenas.stream())
                    .anyMatch(value -> arena.regionId.equalsIgnoreCase(value.regionId));
            if (referencedElsewhere) {
                SimpleServerUtilities.LOGGER.error("Refused to delete managed minigame region '{}' because another definition still references it.", arena.regionId);
                continue;
            }
            try {
                SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(arena.regionId, "minigame-definition-delete");
                SimpleServerUtilities.REGIONS.delete(arena.regionId);
            } catch (Exception exception) {
                SimpleServerUtilities.LOGGER.error("Failed to clean managed minigame region '{}'.", arena.regionId, exception);
            }
        }
        return true;
    }

    public synchronized void validateDefinition(MinigameDefinition definition, boolean referencesMustExist) {
        if (definition == null) throw new IllegalArgumentException("Minigame definition is missing.");
        definition.normalize();
        MinigameGameType gameType = MinigameGameType.parse(definition.gameType);
        if (!gameType.implemented()) {
            throw new IllegalArgumentException(gameType.label() + " is planned but not implemented yet.");
        }
        if (GSON.toJson(definition).length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Minigame exceeds the serialized size limit.");
        }
        if (gameType == MinigameGameType.SPLEEF) {
            Identifier toolId = Identifier.parse(definition.spleef.toolItem);
            if (BuiltInRegistries.ITEM.getOptional(toolId).isEmpty()) {
                throw new IllegalArgumentException("Unknown Spleef tool item: " + definition.spleef.toolItem);
            }
            for (String blockId : definition.spleef.breakableBlocks) {
                Identifier parsed = Identifier.parse(blockId);
                if (BuiltInRegistries.BLOCK.getOptional(parsed).isEmpty()) {
                    throw new IllegalArgumentException("Unknown Spleef breakable block: " + blockId);
                }
            }
        }
        if (gameType == MinigameGameType.CAPTURE_THE_FLAG) {
            Identifier weapon = Identifier.parse(definition.captureTheFlag.weaponItem);
            if (BuiltInRegistries.ITEM.getOptional(weapon).isEmpty()) {
                throw new IllegalArgumentException("Unknown Capture the Flag weapon item: " + definition.captureTheFlag.weaponItem);
            }
            for (int team = 1; team <= 2; team++) {
                String blockId = definition.captureTheFlag.flagBlock(team);
                Identifier parsed = Identifier.parse(blockId);
                if (BuiltInRegistries.BLOCK.getOptional(parsed).isEmpty() || !blockId.endsWith("_banner")) {
                    throw new IllegalArgumentException("Capture the Flag team " + team + " needs a standing banner block ID.");
                }
            }
        }
        if (gameType == MinigameGameType.DOMINATION) {
            Identifier weapon = Identifier.parse(definition.domination.weaponItem);
            if (BuiltInRegistries.ITEM.getOptional(weapon).isEmpty()) {
                throw new IllegalArgumentException("Unknown Domination weapon item: " + definition.domination.weaponItem);
            }
            for (String blockId : List.of(definition.domination.neutralBannerBlock,
                    definition.domination.team1BannerBlock, definition.domination.team2BannerBlock)) {
                Identifier parsed = Identifier.parse(blockId);
                if (BuiltInRegistries.BLOCK.getOptional(parsed).isEmpty() || !blockId.endsWith("_banner")) {
                    throw new IllegalArgumentException("Domination node markers must use standing banner block IDs.");
                }
            }
        }
        if (definition.arenas.isEmpty()) throw new IllegalArgumentException("A minigame needs at least one arena.");
        LinkedHashSet<String> arenaIds = new LinkedHashSet<>();
        LinkedHashSet<String> arenaRegions = new LinkedHashSet<>();
        for (MinigameArenaDefinition arena : definition.arenas) {
            if (!arenaIds.add(arena.id)) throw new IllegalArgumentException("Duplicate arena ID: " + arena.id);
            if (!arena.regionId.isBlank() && !arenaRegions.add(arena.regionId.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Multiple arenas in one minigame cannot share region '" + arena.regionId + "'.");
            }
            validateLocation(arena.lobby, "Arena lobby");
            validateLocation(arena.spectator, "Arena spectator spawn");
            if (arena.spectatorBounds != null && arena.spectatorBounds.configured()) {
                try { Identifier.parse(arena.spectatorBounds.dimension); }
                catch (RuntimeException exception) { throw new IllegalArgumentException("Arena spectator bounds use an invalid dimension."); }
            }
            if (arena.playFloor != null && arena.playFloor.configured()) {
                if (gameType != MinigameGameType.SPLEEF) throw new IllegalArgumentException("Only Spleef arenas may define a playfloor.");
                try { Identifier.parse(arena.playFloor.dimension); }
                catch (RuntimeException exception) { throw new IllegalArgumentException("Spleef playfloor uses an invalid dimension."); }
            }
            if (arena.teamSpawns.isEmpty()) throw new IllegalArgumentException("Arena '" + arena.id + "' has no team spawns.");
            if (gameType == MinigameGameType.SPLEEF && arena.teamSpawns.size() < definition.maxPlayers) {
                throw new IllegalArgumentException("Spleef arena '" + arena.id + "' needs at least one spawn per maximum player.");
            }
            if (gameType == MinigameGameType.CAPTURE_THE_FLAG) {
                if (arena.flagForTeam(1) == null || arena.flagForTeam(2) == null || arena.flagPoints.size() != 2) {
                    throw new IllegalArgumentException("Capture the Flag arena '" + arena.id + "' needs exactly one flag base for each team.");
                }
                validateLocation(arena.flagForTeam(1).location, "Red flag base");
                validateLocation(arena.flagForTeam(2).location, "Blue flag base");
                BlockPos red = blockPos(arena.flagForTeam(1).location);
                BlockPos blue = blockPos(arena.flagForTeam(2).location);
                if (red.equals(blue) && arena.flagForTeam(1).location.dimension.equals(arena.flagForTeam(2).location.dimension)) {
                    throw new IllegalArgumentException("Capture the Flag bases must use different blocks.");
                }
            }
            if (gameType == MinigameGameType.DOMINATION) {
                if (arena.controlPoints.size() < 3 || arena.controlPoints.size() > 9) {
                    throw new IllegalArgumentException("Domination arena '" + arena.id + "' needs between 3 and 9 capture nodes.");
                }
                LinkedHashSet<String> nodeIds = new LinkedHashSet<>();
                LinkedHashSet<String> nodeBlocks = new LinkedHashSet<>();
                for (MinigameControlPoint point : arena.controlPoints) {
                    if (!nodeIds.add(point.id)) throw new IllegalArgumentException("Duplicate Domination node ID: " + point.id);
                    validateLocation(point.location, "Domination node " + point.displayName);
                    validateLocation(point.respawn, "Domination node respawn " + point.displayName);
                    String cell = point.location.dimension + ":" + blockPos(point.location).asLong();
                    if (!nodeBlocks.add(cell)) throw new IllegalArgumentException("Domination nodes must occupy different blocks.");
                }
            }
            for (int team = 1; team <= definition.teamCount; team++) {
                final int requestedTeam = team;
                if (arena.teamSpawns.stream().noneMatch(spawn -> spawn.team == requestedTeam)) {
                    throw new IllegalArgumentException("Arena '" + arena.id + "' has no spawn for team " + team + ".");
                }
            }
            LinkedHashSet<String> occupiedSpawnBlocks = new LinkedHashSet<>();
            for (MinigameSpawnPoint spawn : arena.teamSpawns) {
                validateLocation(spawn.location, "Team spawn");
                if (gameType == MinigameGameType.CAPTURE_THE_FLAG) {
                    for (MinigameFlagPoint flag : arena.flagPoints) {
                        if (spawn.location.dimension.equals(flag.location.dimension)
                                && blockPos(spawn.location).equals(blockPos(flag.location))) {
                            throw new IllegalArgumentException("Capture the Flag team spawns cannot occupy a flag block.");
                        }
                    }
                }
                if (gameType == MinigameGameType.DOMINATION) {
                    for (MinigameControlPoint point : arena.controlPoints) {
                        if (spawn.location.dimension.equals(point.location.dimension)
                                && blockPos(spawn.location).equals(blockPos(point.location))) {
                            throw new IllegalArgumentException("Domination team spawns cannot occupy a capture-node block.");
                        }
                    }
                }
                if (gameType == MinigameGameType.SPLEEF) {
                    MinigameLocation location = spawn.location;
                    String cell = location.dimension + ":" + (int) Math.floor(location.x) + ":"
                            + (int) Math.floor(location.y) + ":" + (int) Math.floor(location.z);
                    if (!occupiedSpawnBlocks.add(cell)) {
                        throw new IllegalArgumentException("Spleef player spawns must occupy different blocks.");
                    }
                }
            }
            if (gameType == MinigameGameType.SPLEEF && definition.enabled && !arena.resetRegionAfterMatch) {
                throw new IllegalArgumentException("Enabled Spleef arenas require a verified region snapshot reset.");
            }
            if (gameType == MinigameGameType.CAPTURE_THE_FLAG && definition.enabled && !arena.resetRegionAfterMatch) {
                throw new IllegalArgumentException("Enabled Capture the Flag arenas require a verified region snapshot reset.");
            }
            if (gameType == MinigameGameType.DOMINATION && definition.enabled && !arena.resetRegionAfterMatch) {
                throw new IllegalArgumentException("Enabled Domination arenas require a verified region snapshot reset.");
            }
            if (arena.resetRegionAfterMatch && arena.regionId.isBlank()) {
                throw new IllegalArgumentException("Arena '" + arena.id + "' enables region reset without a region ID.");
            }
            if (referencesMustExist && arena.resetRegionAfterMatch) {
                if (!Config.ENABLE_ADMIN_REGIONS.get()) {
                    throw new IllegalArgumentException("Region reset requires the Regions module.");
                }
                Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
                if (region == null) throw new IllegalArgumentException("Unknown reset region: " + arena.regionId);
                if (arena.spectatorBounds != null && arena.spectatorBounds.configured()
                        && !areaInsideRegion(arena.spectatorBounds, region, 32)) {
                    throw new IllegalArgumentException("Spectator bounds for arena '" + arena.id + "' must stay close to the arena region.");
                }
                if (arena.playFloor != null && arena.playFloor.configured()
                        && !areaInsideRegion(arena.playFloor, region, 0)) {
                    throw new IllegalArgumentException("Spleef playfloor for arena '" + arena.id + "' must be inside the arena region.");
                }
                if (!SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
                    throw new IllegalArgumentException("No saved snapshot exists for region '" + region.getName() + "'.");
                }
                if (gameType == MinigameGameType.SPLEEF) {
                    String regionDimension = region.getDimension().identifier().toString();
                    if (!locationNearRegion(arena.spectator, region, 24.0D, 32.0D)) {
                        throw new IllegalArgumentException("The spectator point for arena '" + arena.id
                                + "' must stay close to the arena region.");
                    }
                    for (MinigameSpawnPoint spawn : arena.teamSpawns) {
                        MinigameLocation location = spawn.location;
                        boolean insideHorizontal = location.x >= region.getMinX() && location.x < region.getMaxX() + 1.0D
                                && location.z >= region.getMinZ() && location.z < region.getMaxZ() + 1.0D;
                        boolean safeHeight = location.y >= region.getMinY() - 2.0D && location.y <= region.getMaxY() + 8.0D;
                        if (!regionDimension.equals(location.dimension) || !insideHorizontal || !safeHeight) {
                            throw new IllegalArgumentException("Spleef spawn for player slot " + spawn.team
                                    + " must be inside the arena footprint and close to its selected height.");
                        }
                    }
                }
                if (gameType == MinigameGameType.CAPTURE_THE_FLAG) {
                    String regionDimension = region.getDimension().identifier().toString();
                    if (!locationNearRegion(arena.spectator, region, 24.0D, 32.0D)) {
                        throw new IllegalArgumentException("The spectator point for arena '" + arena.id
                                + "' must stay close to the arena region.");
                    }
                    for (MinigameSpawnPoint spawn : arena.teamSpawns) {
                        if (!locationInsideRegion(spawn.location, region, 8.0D)) {
                            throw new IllegalArgumentException("Capture the Flag team spawns must be inside the arena footprint.");
                        }
                    }
                    for (MinigameFlagPoint point : arena.flagPoints) {
                        validateLocation(point.location, "Flag base");
                        if (!regionDimension.equals(point.location.dimension) || !locationInsideRegion(point.location, region, 2.0D)) {
                            throw new IllegalArgumentException("Capture the Flag base for team " + point.team
                                    + " must be inside the arena region.");
                        }
                    }
                }
                if (gameType == MinigameGameType.DOMINATION) {
                    String regionDimension = region.getDimension().identifier().toString();
                    if (!locationNearRegion(arena.spectator, region, 24.0D, 32.0D)) {
                        throw new IllegalArgumentException("The spectator point for arena '" + arena.id
                                + "' must stay close to the arena region.");
                    }
                    for (MinigameSpawnPoint spawn : arena.teamSpawns) {
                        if (!locationInsideRegion(spawn.location, region, 8.0D)) {
                            throw new IllegalArgumentException("Domination team spawns must be inside the arena footprint.");
                        }
                    }
                    for (MinigameControlPoint point : arena.controlPoints) {
                        if (!regionDimension.equals(point.location.dimension) || !locationInsideRegion(point.location, region, 2.0D)) {
                            throw new IllegalArgumentException("Domination node '" + point.displayName
                                    + "' must be inside the arena region.");
                        }
                        if (!regionDimension.equals(point.respawn.dimension) || !locationInsideRegion(point.respawn, region, 8.0D)) {
                            throw new IllegalArgumentException("Linked respawn for Domination node '" + point.displayName
                                    + "' must be inside the arena footprint.");
                        }
                        if (blockPos(point.respawn).equals(blockPos(point.location))) {
                            throw new IllegalArgumentException("Linked respawn for Domination node '" + point.displayName
                                    + "' cannot occupy the banner block.");
                        }
                    }
                }
            }
        }
        validateCondition(definition.prerequisites);
        validateRewardSet(definition.participationReward, "Participation reward");
        validateRewardSet(definition.winnerReward, "Winner reward");
    }

    private static void validateCondition(be.winnetrie.mod.simpleserverutilities.content.ContentCondition condition) {
        if (condition == null) throw new IllegalArgumentException("Minigame prerequisite condition is missing.");
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered(condition.type())) {
            throw new IllegalArgumentException("Unknown minigame prerequisite condition: " + condition.type());
        }
        for (var child : condition.children()) validateCondition(child);
    }

    private void validateRewardSet(MinigameRewardSet reward, String label) {
        if (reward == null) throw new IllegalArgumentException(label + " configuration is missing.");
        reward.normalize();
        if (reward.itemStacks.size() > MinigameRewardSet.MAX_ITEM_STACKS) {
            throw new IllegalArgumentException(label + " may contain at most "
                    + MinigameRewardSet.MAX_ITEM_STACKS + " item stacks.");
        }
        int mailStacks = reward.itemCount();
        if (server != null) {
            for (JsonElement encoded : reward.itemStacks) {
                if (encoded == null || encoded.isJsonNull()) continue;
                ItemStack stack = MailItemCodec.decode(server.registryAccess(), encoded);
                if (stack.isEmpty()) throw new IllegalArgumentException(label + " contains an invalid item stack.");
            }
            for (ContentAction action : reward.directActions) {
                if ("give_item".equals(action.type())) {
                    String rawItem = action.parameter("item");
                    ItemStack template;
                    try {
                        template = BuiltInRegistries.ITEM.getOptional(Identifier.parse(rawItem))
                                .map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException(label + " contains an invalid legacy item: " + rawItem);
                    }
                    if (template.isEmpty()) throw new IllegalArgumentException(label + " contains an unknown legacy item: " + rawItem);
                    long count = positiveLong(action.parameter("count"), "count");
                    mailStacks += (int) ((count + Math.max(1, template.getMaxStackSize()) - 1L)
                            / Math.max(1, template.getMaxStackSize()));
                } else if ("give_money".equals(action.type())) {
                    positiveLong(action.parameter("amount_minor"), "amount_minor");
                }
            }
        }
        if (mailStacks > MailManager.HARD_ATTACHMENT_CAP) {
            throw new IllegalArgumentException(label + " would create more than "
                    + MailManager.HARD_ATTACHMENT_CAP + " mail item stacks.");
        }
        validateActions(reward.directActions);
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
        sendLobby(actor, notice, error, payload.requestId(), true);
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
        boolean adminView = "open_admin".equals(action) || "refresh_admin".equals(action)
                || "force_start".equals(action) || "finish".equals(action)
                || "release_arena".equals(action) || "delete".equals(action);
        if ("open".equals(action) || "open_admin".equals(action)) resetRequestSequence(player.getUUID(), payload.requestId());
        else if (!acceptRequest(player.getUUID(), payload.requestId())) return;
        if (!active() || (adminView ? !canAdmin(player) : !canAccess(player))) {
            PacketDistributor.sendToPlayer(player, new MinigameLobbyDataPayload(
                    adminView ? "Minigame administrator permission is required."
                            : "You do not have permission to use the Minigame Framework.",
                    true, canAdmin(player), adminView, payload.requestId(), "", "", List.of()));
            return;
        }
        String notice = "";
        boolean error = false;
        try {
            switch (action) {
                case "open", "refresh" -> { }
                case "open_admin", "refresh_admin" -> requireAdmin(player);
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
                    notice = restoreBlockedArena(payload.minigameId());
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
        sendLobby(player, notice, error, payload.requestId(), adminView);
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
        sendLobby(player, "", false, 0L, false);
    }

    public String joinQueue(ServerPlayer player, String rawId) {
        if (!active()) throw new IllegalArgumentException("The Minigame Framework is disabled.");
        if (!ContentAccessPolicy.canJoinMinigameQueue(player)) {
            throw new IllegalArgumentException("You do not have permission to join minigame queues.");
        }
        String id = ContentId.require(rawId, "Minigame ID");
        synchronized (this) {
            if (recoverySafetyHalted) {
                throw new IllegalArgumentException(
                        "Minigame starts are paused because recovery storage is unavailable. No player state was changed.");
            }
            if (recoveries.containsKey(player.getUUID()) && !playerMatches.containsKey(player.getUUID())) {
                throw new IllegalArgumentException(
                        "You still have a pending minigame recovery. Reconnect or contact an administrator before joining again.");
            }
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
        if (player == null || player.isDeadOrDying()) return false;
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
        player.closeContainer();
        // Capture before mutating the live match. An unserializable inventory must
        // reject the late join without leaving a partial participant record behind.
        MinigamePlayerState state = MinigamePlayerState.capture(player);
        MinigameLocation returnLocation = MinigameLocation.of(player);
        target.teams.put(playerId, selectedTeam);
        target.scores.put(playerId, 0L);
        target.joinOrder.add(playerId);
        target.playerStates.put(playerId, state);
        target.returnLocations.put(playerId, returnLocation);
        MinigameRecoveryData.Entry recovery = new MinigameRecoveryData.Entry(playerId, definition.id,
                target.id.toString(), returnLocation.copy(), state);
        recoveries.put(playerId, recovery);
        playerMatches.put(playerId, target.id);
        if (!saveRecoveryDurably("late join " + target.id + " for " + playerId)) {
            target.teams.remove(playerId);
            target.scores.remove(playerId);
            target.joinOrder.remove(playerId);
            target.playerStates.remove(playerId);
            target.returnLocations.remove(playerId);
            target.eliminated.remove(playerId);
            playerMatches.remove(playerId);
            // Keep the no-op recovery entry in memory. The durable write result is
            // uncertain, so a later login may safely restore the untouched original
            // state rather than silently discarding a possibly persisted record.
            throw new IllegalArgumentException(
                    "The minigame could not safely store your recovery data. No inventory or gamemode changes were made.");
        }
        beginParticipant(player, definition, arena, target);
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
        MinigameMatch activeMatch;
        synchronized (this) {
            matchId = playerMatches.get(playerId);
            activeMatch = matchId == null ? null : matches.get(matchId);
            // During POST_GAME the real inventory may already contain committed
            // rewards while the durable recovery snapshot is still being refreshed.
            // Never route this phase through withdrawFromMatch(), because that would
            // restore the pre-match snapshot and erase legitimate rewards. Logout is
            // also handled by the persisted recovery record instead of mutating state.
            if (activeMatch != null && activeMatch.state == MinigameMatchState.POST_GAME) {
                return activeMatch.postRewardRecoveryDurable
                        ? "The match is ending. You will be returned automatically."
                        : "Your match return is waiting for a safe recovery save. No state was discarded.";
            }
            queued = playerQueues.remove(playerId);
            if (queued != null) {
                LinkedHashMap<UUID, Long> queue = queues.get(queued);
                if (queue != null) queue.remove(playerId);
            }
        }
        if (queued != null) {
            publish(player, ContentEventTypes.MINIGAME_QUEUE_LEFT, queued, 1L,
                    Map.of("reason", voluntary ? "voluntary" : "disconnect"));
            return "Left the queue for " + displayName(queued) + ".";
        }
        if (matchId != null) {
            MinigameDefinition activeDefinition;
            MinigameArenaDefinition activeArena;
            synchronized (this) {
                activeDefinition = activeMatch == null ? null : definitions.get(activeMatch.minigameId);
                activeArena = activeDefinition == null || activeMatch == null ? null : arena(activeDefinition, activeMatch.arenaId);
            }
            MinigameGameType activeType = activeDefinition == null ? MinigameGameType.GENERIC
                    : MinigameGameType.parse(activeDefinition.gameType);
            boolean ctf = activeType == MinigameGameType.CAPTURE_THE_FLAG;
            boolean respawnTeamMode = ctf || activeType == MinigameGameType.DOMINATION;
            if (ctf && activeArena != null) {
                interruptCtfCast(activeMatch, playerId, "Flag capture interrupted because you left the match.");
                returnFlagsCarriedBy(activeMatch, activeDefinition, activeArena, playerId,
                        "A departing carrier's flag returned to base.");
            }
            if (voluntary || respawnTeamMode || activeMatch != null && activeMatch.state == MinigameMatchState.COUNTDOWN) {
                withdrawFromMatch(playerId, voluntary ? "Player left the match."
                        : respawnTeamMode ? "Player disconnected from " + activeType.label() + "."
                        : "Player disconnected during countdown.");
            } else {
                eliminate(playerId, "Player disconnected.");
            }
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
        MinigamePlayerState playerState;
        synchronized (this) {
            match = matchFor(playerId);
            if (match == null) return;
            definition = definitions.get(match.minigameId);
            destination = match.returnLocations.remove(playerId);
            playerState = match.playerStates.remove(playerId);
            match.joinOrder.remove(playerId);
            match.teams.remove(playerId);
            match.scores.remove(playerId);
            match.eliminated.remove(playerId);
            playerMatches.remove(playerId);
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        boolean stateRestored = player == null;
        if (player != null) {
            try {
                if (playerState != null) playerState.restore(player);
                stateRestored = true;
            } catch (RuntimeException exception) {
                SimpleServerUtilities.LOGGER.error("Failed to restore minigame state for {} while leaving match {}.",
                        player.getName().getString(), match.id, exception);
                player.sendSystemMessage(Component.literal(
                        "Your minigame state could not be restored safely. Reconnect after an administrator checks the server log."));
            }
        }
        boolean returned = stateRestored && player != null && destination != null && teleport(player, destination);
        if (player != null) {
            clearHud(player);
            PacketDistributor.sendToPlayer(player, MinigameCtfVisualPayload.clear());
            PacketDistributor.sendToPlayer(player, MinigameDominationVisualPayload.clear());
        }
        if (returned) {
            synchronized (this) {
                recoveries.remove(playerId);
                saveRecovery();
            }
        }
        if (player != null) player.sendSystemMessage(Component.literal(reason));
        if (match.state == MinigameMatchState.COUNTDOWN) {
            if (definition == null || match.teams.size() < definition.minPlayers) {
                cancelCountdown(match, definition, "Countdown cancelled because too few players remain.");
            } else {
                announce(match, "A player left. " + match.teams.size() + " player(s) remain in the countdown.");
            }
        } else if (match.teams.isEmpty()) {
            finish(match, "All players left the match.");
        } else if (definition != null && "last_team_standing".equals(definition.victoryMode)) {
            Set<Integer> alive = activeTeams(match);
            if (alive.size() == 1) {
                match.winningTeams = Set.copyOf(alive);
                finish(match, "Last team standing.");
            }
        } else if (definition != null) {
            MinigameGameType teamMode = MinigameGameType.parse(definition.gameType);
            if (teamMode != MinigameGameType.CAPTURE_THE_FLAG && teamMode != MinigameGameType.DOMINATION) return;
            LinkedHashSet<Integer> occupiedTeams = new LinkedHashSet<>(match.teams.values());
            if (occupiedTeams.size() == 1) {
                match.winningTeams = Set.copyOf(occupiedTeams);
                finish(match, "The opposing team left the match.");
            }
        }
    }

    private void cancelCountdown(MinigameMatch match, MinigameDefinition definition, String reason) {
        if (match == null || match.state != MinigameMatchState.COUNTDOWN) return;
        ArrayList<UUID> requeue = new ArrayList<>(match.teams.keySet());
        match.rewardsDelivered = true;
        match.rewardsEnabled = false;
        announce(match, reason);
        MinigameArenaDefinition arena = definition == null ? null : arena(definition, match.arenaId);
        if (!cleanup(match, definition, arena)) {
            announce(match, "Return is paused because SSU could not durably store player recovery data.");
            return;
        }
        if (definition == null || !definition.enabled) return;
        for (UUID playerId : requeue) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.isDeadOrDying()) continue;
            try {
                joinQueue(player, definition.id);
                player.sendSystemMessage(Component.literal("You were returned to the queue for " + definition.displayName + "."));
            } catch (RuntimeException exception) {
                player.sendSystemMessage(Component.literal("The cancelled match could not return you to the queue: " + exception.getMessage()));
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
        tickRealtimeMinigames();
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

    private void tickRealtimeMinigames() {
        for (MinigameMatch match : List.copyOf(matches.values())) {
            if (match.state != MinigameMatchState.RUNNING) continue;
            MinigameDefinition definition = definitions.get(match.minigameId);
            if (definition == null) continue;
            MinigameArenaDefinition arena = arena(definition, match.arenaId);
            if (arena == null) continue;
            MinigameGameType type = MinigameGameType.parse(definition.gameType);
            if (type == MinigameGameType.DOMINATION) tickDominationRealtime(match, definition, arena);
            else if (type == MinigameGameType.CAPTURE_THE_FLAG) tickCtfRealtime(match, definition, arena);
        }
    }

    private void tickDominationRealtime(MinigameMatch match, MinigameDefinition definition,
                                        MinigameArenaDefinition arena) {
        boolean visualsChanged = false;
        for (Map.Entry<UUID, MinigameMatch.DominationCast> entry : List.copyOf(match.dominationCasts.entrySet())) {
            UUID playerId = entry.getKey();
            MinigameMatch.DominationCast cast = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            MinigameControlPoint point = controlPoint(arena, cast.pointId());
            if (player == null || point == null || !match.active(playerId)) {
                interruptDominationCast(match, playerId, "Capture interrupted.");
                continue;
            }
            double dx = player.getX() - cast.startX();
            double dy = player.getY() - cast.startY();
            double dz = player.getZ() - cast.startZ();
            boolean moved = dx * dx + dy * dy + dz * dz > 0.01D;
            boolean wrongDimension = !point.location.dimension.equals(player.level().dimension().identifier().toString());
            boolean tooFar = player.distanceToSqr(point.location.x, point.location.y, point.location.z) > 16.0D;
            if (moved || wrongDimension || tooFar) {
                interruptDominationCast(match, playerId, moved ? "Capture interrupted because you moved."
                        : "Capture interrupted because you left the flag.");
                continue;
            }
            long total = Math.max(1L, cast.completesTick() - cast.startedTick());
            float progress = (float) Math.max(0.0D, Math.min(1.0D,
                    (serverTicks - cast.startedTick()) / (double) total));
            sendDominationCastBar(player, point.displayName, definition.domination.teamName(cast.team()),
                    definition.domination.color(cast.team()), progress);
            if (serverTicks < cast.completesTick()) continue;

            match.dominationCasts.remove(playerId);
            clearCastBar(player);
            int previousOwner = match.dominationOwners.getOrDefault(point.id, 0);
            if (previousOwner == cast.team()) {
                player.sendSystemMessage(Component.literal("Your team already controls " + point.displayName + "."), true);
                continue;
            }
            long delayTicks = Math.max(20L, definition.domination.captureDelaySeconds * 20L);
            match.dominationOwners.put(point.id, 0); // no team scores while the base is being claimed
            match.dominationClaims.put(point.id, new MinigameMatch.DominationClaim(
                    previousOwner, cast.team(), serverTicks, serverTicks + delayTicks));
            placeDominationClaimMarker(definition, point, previousOwner, cast.team());
            announce(match, definition.domination.teamName(cast.team()) + " is claiming "
                    + point.displayName + "! Capture completes in "
                    + definition.domination.captureDelaySeconds + " seconds.");
            playDominationHorns(match, definition, cast.team());
            visualsChanged = true;
        }

        for (Map.Entry<String, MinigameMatch.DominationClaim> entry : List.copyOf(match.dominationClaims.entrySet())) {
            if (serverTicks < entry.getValue().completesTick()) continue;
            MinigameControlPoint point = controlPoint(arena, entry.getKey());
            MinigameMatch.DominationClaim claim = entry.getValue();
            match.dominationClaims.remove(entry.getKey());
            match.dominationOwners.put(entry.getKey(), claim.claimingTeam());
            if (point != null) {
                placeDominationMarker(definition, point, claim.claimingTeam());
                announce(match, definition.domination.teamName(claim.claimingTeam()) + " captured "
                        + point.displayName + "!");
                playDominationCaptureCompleteSounds(match, claim.claimingTeam(), claim.previousOwner());
            }
            visualsChanged = true;
        }
        if (visualsChanged || serverTicks % 20L == 0L) publishDominationVisuals(match, definition, arena);
    }

    private void tickCtfRealtime(MinigameMatch match, MinigameDefinition definition,
                                 MinigameArenaDefinition arena) {
        for (Map.Entry<UUID, MinigameMatch.CtfCast> entry : List.copyOf(match.ctfCasts.entrySet())) {
            UUID playerId = entry.getKey();
            MinigameMatch.CtfCast cast = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            MinigameFlagPoint point = arena.flagForTeam(cast.flagTeam());
            if (player == null || point == null || !match.active(playerId)) {
                interruptCtfCast(match, playerId, "Flag capture interrupted.");
                continue;
            }
            double dx = player.getX() - cast.startX();
            double dy = player.getY() - cast.startY();
            double dz = player.getZ() - cast.startZ();
            boolean moved = dx * dx + dy * dy + dz * dz > 0.01D;
            boolean wrongDimension = !point.location.dimension.equals(player.level().dimension().identifier().toString());
            boolean tooFar = player.distanceToSqr(point.location.x, point.location.y, point.location.z) > 16.0D;
            if (moved || wrongDimension || tooFar) {
                interruptCtfCast(match, playerId, moved ? "Flag capture interrupted because you moved."
                        : "Flag capture interrupted because you left the flag.");
                continue;
            }
            long total = Math.max(1L, cast.completesTick() - cast.startedTick());
            float progress = (float) Math.max(0.0D, Math.min(1.0D,
                    (serverTicks - cast.startedTick()) / (double) total));
            sendCtfCastBar(player, definition.captureTheFlag.teamName(cast.flagTeam()),
                    definition.captureTheFlag.color(cast.carrierTeam()), progress);
            if (serverTicks < cast.completesTick()) continue;

            synchronized (this) {
                match.ctfCasts.remove(playerId);
                if (match.flagCarriers.containsKey(cast.flagTeam())
                        || match.ctfDroppedFlags.containsKey(cast.flagTeam())
                        || match.flagCarriers.containsValue(playerId)) {
                    clearCastBar(player);
                    continue;
                }
                match.flagCarriers.put(cast.flagTeam(), playerId);
            }
            clearCastBar(player);
            removeCtfFlagFromBase(arena, cast.flagTeam());
            attachCtfCarrierVisual(match, definition, player, cast.flagTeam());
            announce(match, player.getName().getString() + " took the "
                    + definition.captureTheFlag.teamName(cast.flagTeam()) + " flag!");
            player.sendSystemMessage(Component.literal("Return the enemy flag to your own base to score."), true);
            playCtfHorns(match, cast.carrierTeam());
            publishCtfVisuals(match, definition);
        }
        for (Map.Entry<Integer, UUID> carried : List.copyOf(match.flagCarriers.entrySet())) {
            UUID carrierId = carried.getValue();
            ServerPlayer carrier = server.getPlayerList().getPlayer(carrierId);
            if (carrier == null) continue;
            if (!carrier.isShiftKeyDown()) {
                match.ctfCarrierSneakLatch.remove(carrierId);
                continue;
            }
            if (match.ctfCarrierSneakLatch.add(carrierId)) {
                dropFlagsCarriedBy(match, definition, arena, carrierId, MinigameLocation.of(carrier),
                        carrier.getName().getString() + " dropped the "
                                + definition.captureTheFlag.teamName(carried.getKey()) + " flag.");
                carrier.sendSystemMessage(Component.literal("You dropped the carried flag."), true);
            }
        }
        syncCtfCarrierVisuals(match, definition);
    }

    private static MinigameControlPoint controlPoint(MinigameArenaDefinition arena, String id) {
        if (arena == null || id == null) return null;
        for (MinigameControlPoint point : arena.controlPoints) if (id.equals(point.id)) return point;
        return null;
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
        if (recoverySafetyHalted) return null;
        LinkedHashMap<UUID, Long> queue = queues.computeIfAbsent(definition.id, ignored -> new LinkedHashMap<>());
        ArrayList<ServerPlayer> online = new ArrayList<>();
        for (UUID playerId : List.copyOf(queue.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                queue.remove(playerId);
                playerQueues.remove(playerId);
                continue;
            }
            online.add(player);
        }
        int required = forced ? 1 : definition.minPlayers;
        if (online.size() < required) return null;
        MinigameArenaDefinition arena = freeArena(definition);
        if (arena == null || !locationsResolvable(definition, arena)) return null;

        ArrayList<ServerPlayer> candidates = new ArrayList<>();
        LinkedHashMap<UUID, MinigamePlayerState> capturedStates = new LinkedHashMap<>();
        LinkedHashMap<UUID, MinigameLocation> capturedReturns = new LinkedHashMap<>();
        for (ServerPlayer player : online) {
            if (candidates.size() >= definition.maxPlayers) break;
            if (player.isDeadOrDying()) continue;
            player.closeContainer();
            try {
                UUID playerId = player.getUUID();
                capturedStates.put(playerId, MinigamePlayerState.capture(player));
                capturedReturns.put(playerId, MinigameLocation.of(player));
                candidates.add(player);
            } catch (RuntimeException exception) {
                queue.remove(player.getUUID());
                playerQueues.remove(player.getUUID());
                player.sendSystemMessage(Component.literal(
                        "You were removed from the queue because SSU could not safely preserve your player inventory."));
                SimpleServerUtilities.LOGGER.error("Refused unsafe minigame start for {}.",
                        player.getName().getString(), exception);
            }
        }
        if (candidates.size() < required) return null;
        MinigameSetupToolService.removePhysicalSetupMarkers(server, definition, arena);

        UUID matchId = UUID.randomUUID();
        MinigameMatch match = new MinigameMatch(matchId, definition.id, arena.id, serverTicks);
        match.rewardsEnabled = !forced;
        int team = 1;
        for (ServerPlayer player : candidates) {
            UUID playerId = player.getUUID();
            match.teams.put(playerId, team);
            match.scores.put(playerId, 0L);
            match.joinOrder.add(playerId);
            MinigamePlayerState state = capturedStates.get(playerId);
            MinigameLocation returnLocation = capturedReturns.get(playerId);
            match.playerStates.put(playerId, state);
            match.returnLocations.put(playerId, returnLocation);
            recoveries.put(playerId, new MinigameRecoveryData.Entry(playerId, definition.id,
                    match.id.toString(), returnLocation.copy(), state));
            playerMatches.put(playerId, match.id);
            team = team % definition.teamCount + 1;
        }
        MinigameGameType startingType = MinigameGameType.parse(definition.gameType);
        if (startingType == MinigameGameType.CAPTURE_THE_FLAG) {
            match.ctfScores.put(1, 0);
            match.ctfScores.put(2, 0);
        } else if (startingType == MinigameGameType.DOMINATION) {
            match.dominationScores.put(1, 0);
            match.dominationScores.put(2, 0);
        }
        matches.put(match.id, match);
        String reservedArenaKey = arenaKey(definition.id, arena.id);
        arenaReservations.put(reservedArenaKey, match.id);
        if (arena.resetRegionAfterMatch) unsafeArenas.add(reservedArenaKey);
        if (!saveRecoveryDurably("match start " + match.id)) {
            matches.remove(match.id);
            arenaReservations.remove(reservedArenaKey);
            unsafeArenas.remove(reservedArenaKey);
            for (ServerPlayer player : candidates) {
                UUID playerId = player.getUUID();
                playerMatches.remove(playerId);
                // As with late joins, retain the no-op recovery entries because the
                // disk outcome is uncertain. Players themselves remain untouched.
            }
            MinigameSetupToolService.restorePhysicalSetupMarkers(server, definition, arena);
            return null;
        }
        for (ServerPlayer player : candidates) {
            queue.remove(player.getUUID());
            playerQueues.remove(player.getUUID());
        }
        if (MinigameGameType.parse(definition.gameType) == MinigameGameType.SPLEEF) {
            clearArenaItemEntities(arena);
        }
        for (ServerPlayer player : candidates) {
            prepareCountdownPlayer(player, definition, arena, match);
            player.sendSystemMessage(Component.literal("Joined " + definition.displayName
                    + ". Match begins in " + definition.countdownSeconds + " seconds."));
        }
        return match;
    }

    private boolean locationsResolvable(MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (resolveLevel(arena.lobby.dimension) == null || resolveLevel(arena.spectator.dimension) == null) return false;
        for (int team = 1; team <= definition.teamCount; team++) {
            if (resolveLevel(arena.spawnForTeam(team).dimension) == null) return false;
        }
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.SPLEEF || type == MinigameGameType.CAPTURE_THE_FLAG
                || type == MinigameGameType.DOMINATION) {
            if (!Config.ENABLE_ADMIN_REGIONS.get() || !SimpleServerUtilities.CORE.modules().isActive("regions")) return false;
            Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
            if (region == null || server.getLevel(region.getDimension()) == null) return false;
            if (!arena.resetRegionAfterMatch || !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) return false;
            if (type == MinigameGameType.CAPTURE_THE_FLAG) {
                for (int team = 1; team <= 2; team++) {
                    MinigameFlagPoint point = arena.flagForTeam(team);
                    if (point == null || resolveLevel(point.location.dimension) == null) return false;
                }
            }
            if (type == MinigameGameType.DOMINATION) {
                if (arena.controlPoints.size() < 3) return false;
                for (MinigameControlPoint point : arena.controlPoints) {
                    if (point == null || point.respawn == null
                            || resolveLevel(point.location.dimension) == null
                            || resolveLevel(point.respawn.dimension) == null) return false;
                }
            }
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
        updateHud(match, definition, elapsedSeconds);
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
                    MinigameGameType startingType = MinigameGameType.parse(definition.gameType);
                    if (startingType == MinigameGameType.CAPTURE_THE_FLAG
                            && !initializeCaptureTheFlag(match, definition, arena)) {
                        finish(match, "Capture the Flag bases could not be initialized.");
                        return;
                    }
                    if (startingType == MinigameGameType.DOMINATION
                            && !initializeDomination(match, definition, arena)) {
                        finish(match, "Domination capture nodes could not be initialized.");
                        return;
                    }
                    for (UUID playerId : List.copyOf(match.teams.keySet())) {
                        ServerPlayer participant = server.getPlayerList().getPlayer(playerId);
                        if (participant != null) beginParticipant(participant, definition, arena, match);
                    }
                    announce(match, definition.displayName + " has started!");
                    publishMatch(match, ContentEventTypes.MINIGAME_STARTED, "started");
                }
            }
            case RUNNING -> {
                MinigameGameType runningType = MinigameGameType.parse(definition.gameType);
                if (runningType == MinigameGameType.SPLEEF) {
                    tickSpleef(match, definition, arena);
                    if (match.state != MinigameMatchState.RUNNING) return;
                } else if (runningType == MinigameGameType.CAPTURE_THE_FLAG) {
                    tickCaptureTheFlag(match, definition, arena);
                    if (match.state != MinigameMatchState.RUNNING) return;
                } else if (runningType == MinigameGameType.DOMINATION) {
                    tickDomination(match, definition, arena);
                    if (match.state != MinigameMatchState.RUNNING) return;
                }
                enforceSpectatorsNearArena(match, arena);
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
            if (match == null || match.state != MinigameMatchState.RUNNING || match.eliminated.contains(playerId)) return;
            match.eliminated.add(playerId);
            definition = definitions.get(match.minigameId);
            arena = definition == null ? null : arena(definition, match.arenaId);
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.setGameMode(GameType.SPECTATOR);
            if (arena != null) teleport(player, arena.spectator);
            player.sendSystemMessage(Component.literal(reason == null || reason.isBlank() ? "You were eliminated." : reason));
        }
        // Winner evaluation happens on the next lifecycle tick. Deferring it batches
        // simultaneous deaths/falls so two players eliminated in the same second can
        // correctly produce a draw instead of whichever event happened to fire first.
    }

    public void onPlayerDeath(ServerPlayer player) {
        if (player == null) return;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        if (match != null && match.state == MinigameMatchState.COUNTDOWN) {
            cancelCountdown(match, definition, "Countdown cancelled because a participant died.");
        } else {
            eliminate(player.getUUID(), "You were eliminated from the minigame.");
        }
    }

    public void onPlayerRespawn(ServerPlayer player) {
        if (player == null) return;
        MinigameMatch match;
        MinigameArenaDefinition arena = null;
        MinigameRecoveryData.Entry recovery = null;
        synchronized (this) {
            match = matchFor(player.getUUID());
            if (match != null && match.eliminated.contains(player.getUUID())) {
                MinigameDefinition definition = definitions.get(match.minigameId);
                arena = definition == null ? null : arena(definition, match.arenaId);
            } else if (match == null) {
                recovery = recoveries.get(player.getUUID());
            }
        }
        if (arena != null) {
            player.setGameMode(GameType.SPECTATOR);
            teleport(player, arena.spectator);
        } else if (recovery != null) {
            restoreRecovery(player, recovery, "You were restored after an interrupted minigame death.");
        }
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
            player.setGameMode(GameType.SPECTATOR);
            teleport(player, activeArena.spectator);
            player.sendSystemMessage(Component.literal("You rejoined an active minigame as a spectator."));
            return;
        }
        if (recovery == null) return;
        restoreRecovery(player, recovery, "You were returned from an interrupted minigame session.");
    }

    private void restoreRecovery(ServerPlayer player, MinigameRecoveryData.Entry recovery, String message) {
        if (player == null || recovery == null || player.isDeadOrDying()) return;
        try {
            if (recovery.stateCaptured && recovery.playerState != null) recovery.playerState.restore(player);
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to decode minigame recovery state for {}.",
                    player.getName().getString(), exception);
            player.sendSystemMessage(Component.literal(
                    "SSU found minigame recovery data but could not restore it safely. Your current inventory was left untouched; contact an administrator."));
            return;
        }
        if (teleport(player, recovery.returnLocation)) {
            synchronized (this) {
                recoveries.remove(player.getUUID());
                saveRecovery();
            }
            clearHud(player);
            player.sendSystemMessage(Component.literal(message));
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
        MinigameArenaDefinition arena = arena(definition, match.arenaId);
        MinigameGameType finishingType = MinigameGameType.parse(definition.gameType);
        if (finishingType == MinigameGameType.CAPTURE_THE_FLAG) {
            restoreAllCtfFlags(match, definition, arena);
        } else if (finishingType == MinigameGameType.DOMINATION) {
            clearDominationCastBars(match);
            clearDominationVisuals(match);
            match.dominationClaims.clear();
        }
        celebrateWinners(match, definition);
        String winners = winnerAnnouncement(match, definition);
        announce(match, definition.displayName + " finished. " + winners + ". " + match.finishReason);
    }

    private String winnerAnnouncement(MinigameMatch match, MinigameDefinition definition) {
        if (match.winningTeams.isEmpty()) return "No winner";
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.CAPTURE_THE_FLAG && match.winningTeams.size() == 1) {
            return definition.captureTheFlag.teamName(match.winningTeams.iterator().next()) + " won";
        }
        if (type == MinigameGameType.DOMINATION && match.winningTeams.size() == 1) {
            return definition.domination.teamName(match.winningTeams.iterator().next()) + " won";
        }
        if (type == MinigameGameType.SPLEEF && match.winningTeams.size() == 1) {
            int winningTeam = match.winningTeams.iterator().next();
            for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
                if (entry.getValue() != winningTeam) continue;
                ServerPlayer winner = server.getPlayerList().getPlayer(entry.getKey());
                return (winner == null ? "Player " + winningTeam : winner.getName().getString()) + " won";
            }
        }
        return "Winning team(s): " + match.winningTeams;
    }

    private void celebrateWinners(MinigameMatch match, MinigameDefinition definition) {
        String title;
        String subtitle;
        if (match.winningTeams.isEmpty()) {
            title = "Draw";
            subtitle = definition.displayName;
        } else if (MinigameGameType.parse(definition.gameType) == MinigameGameType.CAPTURE_THE_FLAG
                && match.winningTeams.size() == 1) {
            int team = match.winningTeams.iterator().next();
            title = definition.captureTheFlag.teamName(team) + " wins!";
            subtitle = definition.displayName;
        } else if (MinigameGameType.parse(definition.gameType) == MinigameGameType.DOMINATION
                && match.winningTeams.size() == 1) {
            int team = match.winningTeams.iterator().next();
            title = definition.domination.teamName(team) + " wins!";
            subtitle = definition.displayName;
        } else if (MinigameGameType.parse(definition.gameType) == MinigameGameType.SPLEEF
                && match.winningTeams.size() == 1) {
            int team = match.winningTeams.iterator().next();
            String winnerName = "Player " + team;
            for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
                if (entry.getValue() == team) {
                    ServerPlayer winner = server.getPlayerList().getPlayer(entry.getKey());
                    if (winner != null) winnerName = winner.getName().getString();
                    break;
                }
            }
            title = winnerName + " wins!";
            subtitle = definition.displayName;
        } else {
            title = "Winner!";
            subtitle = winnerAnnouncement(match, definition);
        }
        for (UUID participantId : match.teams.keySet()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(participantId);
            if (participant == null) continue;
            participant.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            participant.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
            participant.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        }
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            if (!match.winningTeams.contains(entry.getValue())) continue;
            ServerPlayer winner = server.getPlayerList().getPlayer(entry.getKey());
            if (winner == null || !(winner.level() instanceof ServerLevel level)) continue;
            MinigameGameType winnerType = MinigameGameType.parse(definition.gameType);
            int color = winnerType == MinigameGameType.CAPTURE_THE_FLAG
                    ? definition.captureTheFlag.color(entry.getValue())
                    : winnerType == MinigameGameType.DOMINATION
                    ? definition.domination.color(entry.getValue()) : 0xFFD700;
            launchWinnerFirework(level, winner, color);
            launchWinnerFirework(level, winner, color);
        }
    }

    private static void launchWinnerFirework(ServerLevel level, ServerPlayer winner, int color) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.STAR,
                IntList.of(color), IntList.of(0xFFFFFF), true, true);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
        FireworkRocketEntity entity = new FireworkRocketEntity(level, winner.getX(), winner.getY() + 0.5D,
                winner.getZ(), rocket);
        level.addFreshEntity(entity);
    }

    private Set<Integer> determineWinners(MinigameMatch match) {
        MinigameDefinition definition = definitions.get(match.minigameId);
        if (definition != null && MinigameGameType.parse(definition.gameType) == MinigameGameType.CAPTURE_THE_FLAG) {
            int red = match.ctfScores.getOrDefault(1, 0);
            int blue = match.ctfScores.getOrDefault(2, 0);
            if (red == blue) return Set.of();
            return Set.of(red > blue ? 1 : 2);
        }
        if (definition != null && MinigameGameType.parse(definition.gameType) == MinigameGameType.DOMINATION) {
            int team1 = match.dominationScores.getOrDefault(1, 0);
            int team2 = match.dominationScores.getOrDefault(2, 0);
            if (team1 == team2) return Set.of();
            return Set.of(team1 > team2 ? 1 : 2);
        }
        if (definition != null && "last_team_standing".equals(definition.victoryMode)) {
            // A time-limit draw belongs only to players who are still alive. Eliminated
            // players must never become winners merely because every score is still zero.
            return activeTeams(match);
        }
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

    private boolean deliverRewardsAndEvents(MinigameMatch match, MinigameDefinition definition) {
        ArrayList<RewardedParticipant> rewarded = new ArrayList<>();
        for (Map.Entry<UUID, Integer> participant : match.teams.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.getKey());
            if (player == null || !match.restoredStates.contains(participant.getKey())) continue;
            boolean won = match.winningTeams.contains(participant.getValue());
            if (!executeRewardSet(player, definition, definition.participationReward, match, "participation")) return false;
            if (won && !executeRewardSet(player, definition, definition.winnerReward, match, "winner")) return false;
            rewarded.add(new RewardedParticipant(player, participant.getValue(), won));
        }
        // Publish progression events only after every reward package was committed. Mail and
        // direct actions are independently idempotent, while event counters are not retried.
        for (RewardedParticipant participant : rewarded) {
            ServerPlayer player = participant.player();
            Map<String, String> metadata = Map.of(
                    "match", match.id.toString(),
                    "arena", match.arenaId,
                    "team", Integer.toString(participant.team()),
                    "score", Long.toString(match.score(player.getUUID())),
                    "result", participant.won() ? "win" : "loss");
            publish(player, ContentEventTypes.MINIGAME_COMPLETED, definition.id, 1L, metadata);
            if (participant.won()) publish(player, ContentEventTypes.MINIGAME_WON, definition.id, 1L, metadata);
        }
        return true;
    }

    private boolean executeRewardSet(ServerPlayer player, MinigameDefinition definition,
                                     MinigameRewardSet configured, MinigameMatch match, String kind) {
        if (configured == null) return true;
        configured.normalize();
        if (configured.empty()) return true;

        ArrayList<ItemStack> mailItems = new ArrayList<>();
        long mailMoney = configured.moneyMinor;
        ArrayList<ContentAction> immediate = new ArrayList<>();
        try {
            for (JsonElement encoded : configured.itemStacks) {
                if (encoded == null || encoded.isJsonNull()) continue;
                ItemStack stack = MailItemCodec.decode(server.registryAccess(), encoded);
                if (stack.isEmpty()) throw new IllegalArgumentException("A configured reward item is no longer valid.");
                mailItems.add(stack.copy());
            }
            // dev1 definitions may contain give_item/give_money actions. Route those old
            // entries through Mail as well so every physical/economy reward follows the
            // same safe claim flow after migration.
            for (ContentAction action : configured.directActions) {
                if ("give_item".equals(action.type())) {
                    appendLegacyMailItem(mailItems, action);
                } else if ("give_money".equals(action.type())) {
                    mailMoney = Math.addExact(mailMoney, positiveLong(action.parameter("amount_minor"), "amount_minor"));
                } else {
                    immediate.add(action);
                }
            }
        } catch (RuntimeException exception) {
            rewardFailure(player, kind, exception.getMessage());
            return false;
        }

        if (!immediate.isEmpty()) {
            var result = SimpleServerUtilities.CONTENT_ACTIONS.execute(immediate,
                    new ContentActionContext(server, player, "minigames", match.minigameId,
                            match.id + ":" + player.getUUID() + ":" + kind + ":direct",
                            Map.of("minigame", match.minigameId, "match", match.id.toString(), "reward", kind)));
            if (!result.successful()) {
                rewardFailure(player, kind, result.error());
                return false;
            }
        }

        String reason = "winner".equals(kind)
                ? "You received this because you won " + definition.displayName + "."
                : "You received this for participating in " + definition.displayName + ".";
        StringBuilder body = new StringBuilder(reason)
                .append("\nArena: ").append(match.arenaId)
                .append("\nMatch: ").append(match.id);
        if (!mailItems.isEmpty() || mailMoney > 0L) {
            body.append("\n\nClaim the attached rewards from this mail.");
            if (!mailItems.isEmpty()) body.append("\nItem stacks: ").append(mailItems.size());
            if (mailMoney > 0L) body.append("\nMoney: ")
                    .append(MoneyFormat.format(mailMoney, SimpleServerUtilities.ECONOMY.settings()));
        }
        if (!immediate.isEmpty()) {
            body.append("\n\nApplied immediately to your player account:");
            for (ContentAction action : immediate) body.append("\n- ").append(describeAction(action));
        }
        String subject = definition.displayName + ("winner".equals(kind) ? " · Winner reward" : " · Participation reward");
        String correlation = "minigame:" + match.id + ":" + player.getUUID() + ":" + kind;
        MailOperationResult delivered = SimpleServerUtilities.MAIL.deliverSystemMail(
                player.getUUID(), player.getName().getString(), subject, body.toString(), mailItems, mailMoney,
                MailSource.MINIGAME, correlation);
        if (!delivered.successful()) {
            rewardFailure(player, kind, delivered.message());
            return false;
        }
        return true;
    }

    private static void appendLegacyMailItem(List<ItemStack> target, ContentAction action) {
        String rawItem = action.parameter("item");
        int count = (int) Math.min(64_000L, positiveLong(action.parameter("count"), "count"));
        ItemStack template;
        try {
            template = BuiltInRegistries.ITEM.getOptional(Identifier.parse(rawItem))
                    .map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid legacy minigame reward item: " + rawItem);
        }
        if (template.isEmpty()) throw new IllegalArgumentException("Unknown legacy minigame reward item: " + rawItem);
        int remaining = count;
        while (remaining > 0) {
            int move = Math.min(remaining, Math.max(1, template.getMaxStackSize()));
            target.add(template.copyWithCount(move));
            remaining -= move;
            if (target.size() > MailManager.HARD_ATTACHMENT_CAP) {
                throw new IllegalArgumentException("A minigame mail may contain at most "
                        + MailManager.HARD_ATTACHMENT_CAP + " item stacks.");
            }
        }
    }

    private static long positiveLong(String raw, String label) {
        try {
            long value = Long.parseLong(raw == null ? "" : raw.trim());
            if (value <= 0L) throw new IllegalArgumentException(label + " must be positive.");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private static String describeAction(ContentAction action) {
        return switch (action.type()) {
            case "grant_permission" -> "Permission granted: " + action.parameter("permission");
            case "set_permission" -> "Permission updated: " + action.parameter("permission");
            case "set_player_unlock" -> "Unlock updated: " + action.parameter("key");
            case "set_player_flag" -> "Player flag updated: " + action.parameter("key");
            case "add_player_counter" -> "Player counter changed: " + action.parameter("key");
            case "add_reputation", "set_reputation" -> "Reputation updated: " + action.parameter("faction");
            case "add_claim_chunks" -> "Personal claim capacity increased by " + action.parameter("amount") + " chunks";
            default -> action.type().replace('_', ' ');
        };
    }

    private void rewardFailure(ServerPlayer player, String kind, String error) {
        String message = error == null || error.isBlank() ? "Unknown reward delivery error." : error;
        SimpleServerUtilities.LOGGER.error("Failed to deliver {} minigame rewards to {}: {}",
                kind, player.getName().getString(), message);
        player.sendSystemMessage(Component.literal("Your minigame reward is waiting for a safe retry: " + message));
    }

    private record RewardedParticipant(ServerPlayer player, int team, boolean won) { }

    private boolean cleanup(MinigameMatch match, MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (definition == null) definition = definition(match.minigameId);
        if (definition != null && arena == null) arena = arena(definition, match.arenaId);

        // Restore the real inventory/state before rewards are granted. This prevents item
        // rewards from being written into a temporary minigame inventory and then lost.
        // An online/dead participant that cannot yet be restored pauses cleanup; rewards
        // must never be marked delivered while an online player still has temporary state.
        for (UUID playerId : List.copyOf(match.teams.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && !restoreParticipantState(match, playerId)) return false;
        }
        if (definition != null && arena != null
                && MinigameGameType.parse(definition.gameType) == MinigameGameType.SPLEEF) {
            // Temporary tools and arena drops must never outlive the match or become
            // obtainable by players outside the isolated minigame inventory.
            clearArenaItemEntities(arena);
        }
        if (definition != null && !match.rewardsDelivered) {
            if (match.rewardsEnabled && !deliverRewardsAndEvents(match, definition)) return false;
            match.rewardsDelivered = true;
        }
        if (!match.postRewardRecoveryDurable) {
            // Persist the restored state including any item rewards before the final
            // return teleport. If storage is unavailable, cleanup pauses and retries;
            // participants keep their real restored inventory and no reward action is
            // executed twice because rewardsDelivered is already set.
            synchronized (this) {
                boolean captureFailed = false;
                for (UUID playerId : match.teams.keySet()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    MinigameRecoveryData.Entry recovery = recoveries.get(playerId);
                    if (player != null && recovery != null && match.restoredStates.contains(playerId)) {
                        try {
                            recovery.playerState = MinigamePlayerState.capture(player);
                            recovery.stateCaptured = true;
                        } catch (RuntimeException exception) {
                            // Keep the prior recovery and pause cleanup. Continuing would
                            // make a crash restore the pre-reward inventory while durable
                            // reward transactions correctly refuse to execute a second time.
                            captureFailed = true;
                            SimpleServerUtilities.LOGGER.error("Could not refresh post-reward recovery for {} in match {}.",
                                    player.getName().getString(), match.id, exception);
                            player.sendSystemMessage(Component.literal(
                                    "Your minigame return is paused because SSU could not safely store the rewarded inventory."));
                        }
                    }
                }
                if (captureFailed || !saveRecoveryDurably("post-reward recovery " + match.id)) {
                    return false;
                }
                match.postRewardRecoveryDurable = true;
            }
        }
        for (UUID playerId : List.copyOf(match.teams.keySet())) returnParticipant(match, playerId);
        synchronized (this) {
            matches.remove(match.id);
            match.state = MinigameMatchState.FINISHED;
        }
        if (definition != null && arena != null && arena.resetRegionAfterMatch) {
            scheduleArenaReset(definition, arena);
        } else {
            synchronized (this) { unsafeArenas.remove(arenaKey(match.minigameId, match.arenaId)); }
            releaseArena(match.minigameId, match.arenaId);
            saveRecovery();
        }
        return true;
    }

    private boolean restoreParticipantState(MinigameMatch match, UUID playerId) {
        MinigamePlayerState playerState;
        synchronized (this) {
            if (match.restoredStates.contains(playerId)) return true;
            playerState = match.playerStates.get(playerId);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || player.isDeadOrDying()) return false;
        try {
            if (playerState != null) playerState.restore(player);
            synchronized (this) { match.restoredStates.add(playerId); }
            return true;
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to restore minigame player state for {} in match {}.",
                    player.getName().getString(), match.id, exception);
            player.sendSystemMessage(Component.literal(
                    "Your minigame state could not be restored safely. SSU kept the recovery record and did not overwrite your current inventory."));
            return false;
        }
    }

    private void returnParticipant(MinigameMatch match, UUID playerId) {
        MinigameLocation destination;
        synchronized (this) {
            playerMatches.remove(playerId);
            destination = match.returnLocations.get(playerId);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            clearHud(player);
            PacketDistributor.sendToPlayer(player, MinigameCtfVisualPayload.clear());
        }
        if (player == null || player.isDeadOrDying()) return;
        if (!restoreParticipantState(match, playerId)) {
            player.setGameMode(GameType.SPECTATOR);
            return;
        }
        if (destination != null && teleport(player, destination)) {
            synchronized (this) {
                recoveries.remove(playerId);
                saveRecovery();
            }
        }
    }

    private void scheduleArenaReset(MinigameDefinition definition, MinigameArenaDefinition arena) {
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
                if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    restorePhysicalModeMarkers(definition, arena);
                } else {
                    SimpleServerUtilities.LOGGER.error("Minigame arena reset failed for '{}': {}", key, result.error());
                }
            });
        } catch (Exception exception) {
            synchronized (this) { blockedArenas.add(key); unsafeArenas.add(key); arenaReservations.remove(key); saveRecovery(); }
            SimpleServerUtilities.LOGGER.error("Failed to schedule reset for minigame arena '{}'.", key, exception);
        }
    }

    private void restorePhysicalModeMarkers(MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (definition == null || arena == null) return;
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            placeCtfFlagAtBase(definition, arena, 1);
            placeCtfFlagAtBase(definition, arena, 2);
        } else if (type == MinigameGameType.DOMINATION) {
            for (MinigameControlPoint point : arena.controlPoints) placeDominationMarker(definition, point, 0);
        }
    }

    public String restoreBlockedArena(String raw) {
        String target = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        ArrayList<MinigameArenaDefinition> arenas = new ArrayList<>();
        ArrayList<MinigameDefinition> owners = new ArrayList<>();
        synchronized (this) {
            for (String key : List.copyOf(blockedArenas)) {
                if (!key.equals(target) && !key.startsWith(target + ":")) continue;
                int split = key.indexOf(':');
                if (split <= 0 || split >= key.length() - 1) continue;
                MinigameDefinition definition = definitions.get(key.substring(0, split));
                MinigameArenaDefinition arena = definition == null ? null : arena(definition, key.substring(split + 1));
                if (definition != null && arena != null && !resettingArenas.contains(key)) {
                    owners.add(definition);
                    arenas.add(arena);
                }
            }
        }
        if (arenas.isEmpty()) return "No blocked arena matched that minigame or arena key.";
        for (int i = 0; i < arenas.size(); i++) scheduleArenaReset(owners.get(i), arenas.get(i));
        return "Scheduled safe snapshot restoration for " + arenas.size() + " blocked arena(s).";
    }

    /** Emergency-only recovery escape hatch retained for console/legacy tooling. */
    public synchronized String releaseBlockedArena(String raw) {
        String target = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        boolean removed = blockedArenas.removeIf(key -> key.equals(target) || key.startsWith(target + ":"));
        boolean unsafeRemoved = unsafeArenas.removeIf(key -> key.equals(target) || key.startsWith(target + ":"));
        if (removed || unsafeRemoved) saveRecovery();
        return removed || unsafeRemoved ? "Force-released blocked arena state without restoration." : "No blocked arena matched that minigame or arena key.";
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

    private void prepareCountdownPlayer(ServerPlayer player, MinigameDefinition definition,
                                        MinigameArenaDefinition arena, MinigameMatch match) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.SPLEEF || type == MinigameGameType.CAPTURE_THE_FLAG
                || type == MinigameGameType.DOMINATION) {
            player.setGameMode(GameType.ADVENTURE);
            clearMatchInventory(player);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
        }
        teleport(player, arena.lobby);
        updateHud(match, definition, 0L);
    }

    private void beginParticipant(ServerPlayer player, MinigameDefinition definition,
                                  MinigameArenaDefinition arena, MinigameMatch match) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.SPLEEF || type == MinigameGameType.CAPTURE_THE_FLAG
                || type == MinigameGameType.DOMINATION) {
            clearMatchInventory(player);
            player.setGameMode(GameType.SURVIVAL);
            String itemId = type == MinigameGameType.SPLEEF
                    ? definition.spleef.toolItem
                    : type == MinigameGameType.DOMINATION
                    ? definition.domination.weaponItem : definition.captureTheFlag.weaponItem;
            ItemStack tool = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId))
                    .map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
            if (!tool.isEmpty()) player.getInventory().add(tool);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
        }
        int team = match.team(player.getUUID());
        teleport(player, matchSpawn(definition, arena, match, player.getUUID(), team));
    }

    private static int teamSpawnOrdinal(MinigameMatch match, UUID playerId, int team) {
        int ordinal = 0;
        for (UUID joined : match.joinOrder) {
            if (joined.equals(playerId)) return ordinal;
            if (match.team(joined) == team) ordinal++;
        }
        return ordinal;
    }

    private MinigameLocation matchSpawn(MinigameDefinition definition, MinigameArenaDefinition arena,
                                        MinigameMatch match, UUID playerId, int team) {
        return MinigameGameType.parse(definition.gameType) == MinigameGameType.SPLEEF
                ? arena.spawnForTeam(team, teamSpawnOrdinal(match, playerId, team))
                : randomTeamSpawn(arena, match, team);
    }

    private MinigameLocation randomTeamSpawn(MinigameArenaDefinition arena, MinigameMatch match,
                                             int team) {
        ArrayList<MinigameLocation> choices = new ArrayList<>();
        for (MinigameSpawnPoint spawn : arena.teamSpawns) {
            if (spawn != null && spawn.team == team && spawn.location != null) choices.add(spawn.location);
        }
        if (choices.isEmpty()) return arena.lobby;
        if (choices.size() == 1) return choices.getFirst();
        long seed = match.id.getMostSignificantBits() ^ match.id.getLeastSignificantBits()
                ^ ((long) team << 32);
        int offset = Math.floorMod(Long.hashCode(seed), choices.size());
        int step = 1 + Math.floorMod(Long.hashCode(seed >>> 17), choices.size() - 1);
        while (greatestCommonDivisor(step, choices.size()) != 1) step = step % (choices.size() - 1) + 1;
        int cursor;
        synchronized (this) {
            cursor = match.teamSpawnCursors.getOrDefault(team, 0);
            match.teamSpawnCursors.put(team, cursor + 1);
        }
        return choices.get(Math.floorMod(offset + cursor * step, choices.size()));
    }

    private static int greatestCommonDivisor(int first, int second) {
        int a = Math.abs(first);
        int b = Math.abs(second);
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return Math.max(1, a);
    }

    private MinigameLocation dominationRespawn(MinigameMatch match, MinigameArenaDefinition arena,
                                                int team, MinigameLocation deathLocation) {
        ArrayList<MinigameLocation> choices = new ArrayList<>();
        for (MinigameSpawnPoint spawn : arena.teamSpawns) {
            if (spawn != null && spawn.team == team && spawn.location != null) choices.add(spawn.location);
        }
        for (MinigameControlPoint point : arena.controlPoints) {
            if (point == null || point.respawn == null) continue;
            if (match.dominationOwners.getOrDefault(point.id, 0) == team) choices.add(point.respawn);
        }
        if (choices.isEmpty()) return arena.lobby;
        MinigameLocation nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (MinigameLocation choice : choices) {
            if (deathLocation != null && !choice.dimension.equals(deathLocation.dimension)) continue;
            double dx = choice.x - deathLocation.x;
            double dy = choice.y - deathLocation.y;
            double dz = choice.z - deathLocation.z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = choice;
            }
        }
        return nearest == null ? randomTeamSpawn(arena, match, team) : nearest;
    }

    private static void clearMatchInventory(ServerPlayer player) {
        player.closeContainer();
        player.getInventory().clearContent();
        // Experience is part of the persisted player snapshot. The temporary match
        // state uses zero XP so a death cannot drop the player's real experience and
        // duplicate it when the original state is restored afterwards.
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.experienceProgress = 0.0F;
        player.removeAllEffects();
        player.setAbsorptionAmount(0.0F);
        player.setAirSupply(player.getMaxAirSupply());
        player.setRemainingFireTicks(0);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private void enforceSpectatorsNearArena(MinigameMatch match, MinigameArenaDefinition arena) {
        if (arena == null || arena.regionId == null || arena.regionId.isBlank()) return;
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) return;
        for (UUID playerId : List.copyOf(match.eliminated)) {
            ServerPlayer spectator = server.getPlayerList().getPlayer(playerId);
            if (spectator == null) continue;
            MinigameLocation current = MinigameLocation.of(spectator);
            boolean allowed = arena.spectatorBounds != null && arena.spectatorBounds.configured()
                    ? arena.spectatorBounds.contains(current)
                    : locationNearRegion(current, region, 24.0D, 32.0D);
            if (!allowed) teleport(spectator, arena.spectator);
        }
    }

    private static boolean areaInsideRegion(MinigameAreaBounds bounds, Region region, int margin) {
        if (bounds == null || !bounds.configured() || region == null) return true;
        return bounds.dimension.equals(region.getDimension().identifier().toString())
                && bounds.minX >= region.getMinX() - margin && bounds.maxX <= region.getMaxX() + margin
                && bounds.minY >= region.getMinY() - margin && bounds.maxY <= region.getMaxY() + margin
                && bounds.minZ >= region.getMinZ() - margin && bounds.maxZ <= region.getMaxZ() + margin;
    }

    private static boolean locationNearRegion(MinigameLocation location, Region region,
                                              double horizontalMargin, double verticalMargin) {
        if (location == null || region == null) return false;
        if (!region.getDimension().identifier().toString().equals(location.dimension)) return false;
        return location.x >= region.getMinX() - horizontalMargin
                && location.x < region.getMaxX() + 1.0D + horizontalMargin
                && location.z >= region.getMinZ() - horizontalMargin
                && location.z < region.getMaxZ() + 1.0D + horizontalMargin
                && location.y >= region.getMinY() - verticalMargin
                && location.y <= region.getMaxY() + verticalMargin;
    }

    private static boolean locationInsideRegion(MinigameLocation location, Region region, double verticalMargin) {
        if (location == null || region == null) return false;
        if (!region.getDimension().identifier().toString().equals(location.dimension)) return false;
        return location.x >= region.getMinX() && location.x < region.getMaxX() + 1.0D
                && location.z >= region.getMinZ() && location.z < region.getMaxZ() + 1.0D
                && location.y >= region.getMinY() - verticalMargin
                && location.y <= region.getMaxY() + verticalMargin;
    }

    private static BlockPos blockPos(MinigameLocation location) {
        return BlockPos.containing(location.x, location.y, location.z);
    }

    private boolean initializeDomination(MinigameMatch match, MinigameDefinition definition,
                                         MinigameArenaDefinition arena) {
        synchronized (this) {
            match.dominationOwners.clear();
            match.dominationProgress.clear();
            match.dominationCasts.clear();
            match.dominationClaims.clear();
            match.dominationScores.put(1, 0);
            match.dominationScores.put(2, 0);
            match.dominationLastScoreTick = serverTicks;
            match.dominationInitialized = true;
            for (MinigameControlPoint point : arena.controlPoints) {
                match.dominationOwners.put(point.id, 0);
                match.dominationProgress.put(point.id, 0);
            }
        }
        boolean placed = true;
        for (MinigameControlPoint point : arena.controlPoints) {
            placed &= placeDominationMarker(definition, point, 0);
        }
        if (placed) publishDominationVisuals(match, definition, arena);
        return placed;
    }

    private boolean placeDominationMarker(MinigameDefinition definition, MinigameControlPoint point, int owner) {
        if (definition == null || point == null) return false;
        ServerLevel level = resolveLevel(point.location.dimension);
        if (level == null) return false;
        Block block = dominationBannerBlock(definition, owner);
        if (block == null) return false;
        BlockPos pos = blockPos(point.location);
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        clearBannerPatterns(level, pos, block);
        return true;
    }

    /**
     * Shows an in-progress assault on the physical banner itself. The banner's base
     * color remains the previous owner (or neutral white), while the vanilla
     * half-horizontal banner pattern paints the claimant color across the top half.
     */
    private boolean placeDominationClaimMarker(MinigameDefinition definition, MinigameControlPoint point,
                                                int previousOwner, int claimingTeam) {
        if (definition == null || point == null) return false;
        ServerLevel level = resolveLevel(point.location.dimension);
        if (level == null) return false;
        Block baseBlock = dominationBannerBlock(definition, previousOwner);
        Block claimantBlock = dominationBannerBlock(definition, claimingTeam);
        if (!(baseBlock instanceof AbstractBannerBlock)
                || !(claimantBlock instanceof AbstractBannerBlock claimantBanner)) return false;

        BlockPos pos = blockPos(point.location);
        level.setBlockAndUpdate(pos, baseBlock.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof BannerBlockEntity bannerEntity)) return false;

        Holder<BannerPattern> topHalf = level.registryAccess()
                .lookupOrThrow(Registries.BANNER_PATTERN)
                .getOrThrow(BannerPatterns.HALF_HORIZONTAL);
        BannerPatternLayers patterns = new BannerPatternLayers.Builder()
                .add(topHalf, claimantBanner.getColor())
                .build();
        ItemStack bannerStack = new ItemStack(baseBlock.asItem());
        bannerStack.set(DataComponents.BANNER_PATTERNS, patterns);
        ((BlockEntityComponentInvoker) (Object) bannerEntity).ssu$applyComponentsFromItemStack(bannerStack);
        bannerEntity.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    private static void clearBannerPatterns(ServerLevel level, BlockPos pos, Block block) {
        if (!(block instanceof AbstractBannerBlock)
                || !(level.getBlockEntity(pos) instanceof BannerBlockEntity bannerEntity)) return;
        ItemStack bannerStack = new ItemStack(block.asItem());
        bannerStack.set(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        ((BlockEntityComponentInvoker) (Object) bannerEntity).ssu$applyComponentsFromItemStack(bannerStack);
        bannerEntity.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private static Block dominationBannerBlock(MinigameDefinition definition, int owner) {
        String blockId = definition.domination.bannerBlock(owner);
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId)).orElse(null);
    }

    private void tickDomination(MinigameMatch match, MinigameDefinition definition,
                                MinigameArenaDefinition arena) {
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) {
            finish(match, "Arena region became unavailable.");
            return;
        }
        for (UUID playerId : List.copyOf(match.teams.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            if (!locationInsideRegion(MinigameLocation.of(player), region, 12.0D)) {
                interruptDominationCast(match, playerId, "Capture interrupted because you left the arena.");
                int team = match.team(playerId);
                teleport(player, randomTeamSpawn(arena, match, team));
                player.setHealth(player.getMaxHealth());
                player.removeAllEffects();
                player.setAbsorptionAmount(0.0F);
                player.setRemainingFireTicks(0);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
                player.sendSystemMessage(Component.literal("You were returned to your team spawn."), true);
            }
        }

        long intervalTicks = Math.max(20L, definition.domination.scoreIntervalSeconds * 20L);
        if (serverTicks - match.dominationLastScoreTick < intervalTicks) return;
        match.dominationLastScoreTick = serverTicks;
        int owned1 = 0;
        int owned2 = 0;
        for (int owner : match.dominationOwners.values()) {
            if (owner == 1) owned1++;
            else if (owner == 2) owned2++;
        }
        int score1 = Math.min(definition.domination.scoreToWin,
                match.dominationScores.getOrDefault(1, 0) + owned1 * definition.domination.pointsPerNode);
        int score2 = Math.min(definition.domination.scoreToWin,
                match.dominationScores.getOrDefault(2, 0) + owned2 * definition.domination.pointsPerNode);
        match.dominationScores.put(1, score1);
        match.dominationScores.put(2, score2);
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            match.scores.put(entry.getKey(), (long) match.dominationScores.getOrDefault(entry.getValue(), 0));
        }
        if (score1 >= definition.domination.scoreToWin || score2 >= definition.domination.scoreToWin) {
            if (score1 == score2) match.winningTeams = Set.of();
            else match.winningTeams = Set.of(score1 > score2 ? 1 : 2);
            finish(match, "The resource score limit was reached.");
        }
    }

    private boolean initializeCaptureTheFlag(MinigameMatch match, MinigameDefinition definition,
                                             MinigameArenaDefinition arena) {
        clearCtfCastBars(match);
        removeAllCtfCarrierVisuals(match);
        clearDroppedCtfFlags(match, definition);
        synchronized (this) {
            match.flagCarriers.clear();
            match.ctfCarrierSneakLatch.clear();
            match.ctfScores.putIfAbsent(1, 0);
            match.ctfScores.putIfAbsent(2, 0);
            match.ctfInitialized = true;
        }
        boolean placed = placeCtfFlagAtBase(definition, arena, 1)
                && placeCtfFlagAtBase(definition, arena, 2);
        if (placed) publishCtfVisuals(match, definition);
        return placed;
    }

    private boolean placeCtfFlagAtBase(MinigameDefinition definition, MinigameArenaDefinition arena, int team) {
        MinigameFlagPoint point = arena.flagForTeam(team);
        if (point == null) return false;
        ServerLevel level = resolveLevel(point.location.dimension);
        if (level == null) return false;
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(definition.captureTheFlag.flagBlock(team)))
                .orElse(null);
        if (block == null) return false;
        level.setBlockAndUpdate(blockPos(point.location), block.defaultBlockState());
        return true;
    }

    private void removeCtfFlagFromBase(MinigameArenaDefinition arena, int team) {
        MinigameFlagPoint point = arena.flagForTeam(team);
        if (point == null) return;
        ServerLevel level = resolveLevel(point.location.dimension);
        if (level != null) level.setBlockAndUpdate(blockPos(point.location), Blocks.AIR.defaultBlockState());
    }

    public boolean handleRightClickBlock(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            arena = definition == null || match == null ? null : arena(definition, match.arenaId);
        }
        if (match == null || definition == null || arena == null
                || match.state != MinigameMatchState.RUNNING || !match.active(player.getUUID())) return false;
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            return handleCtfRightClick(player, pos, match, definition, arena);
        }
        if (type == MinigameGameType.DOMINATION) {
            return handleDominationRightClick(player, pos, match, definition, arena);
        }
        return false;
    }

    private boolean handleCtfRightClick(ServerPlayer player, BlockPos pos, MinigameMatch match,
                                        MinigameDefinition definition, MinigameArenaDefinition arena) {
        int droppedFlagTeam = 0;
        synchronized (this) {
            for (Map.Entry<Integer, MinigameLocation> entry : match.ctfDroppedFlags.entrySet()) {
                MinigameLocation location = entry.getValue();
                if (location != null && location.dimension.equals(player.level().dimension().identifier().toString())
                        && blockPos(location).equals(pos)) {
                    droppedFlagTeam = entry.getKey();
                    break;
                }
            }
        }
        if (droppedFlagTeam != 0) {
            return handleDroppedCtfFlag(player, match, definition, arena, droppedFlagTeam);
        }
        int flagTeam = 0;
        for (int team = 1; team <= 2; team++) {
            MinigameFlagPoint point = arena.flagForTeam(team);
            if (point != null && point.location.dimension.equals(player.level().dimension().identifier().toString())
                    && blockPos(point.location).equals(pos)) {
                flagTeam = team;
                break;
            }
        }
        if (flagTeam == 0) return false;
        int playerTeam = match.team(player.getUUID());
        synchronized (this) {
            if (flagTeam == playerTeam) {
                player.sendSystemMessage(Component.literal("That is your own team flag."), true);
                return true;
            }
            if (match.flagCarriers.containsKey(flagTeam)) {
                player.sendSystemMessage(Component.literal("That flag is already being carried."), true);
                return true;
            }
            if (match.ctfDroppedFlags.containsKey(flagTeam)) {
                player.sendSystemMessage(Component.literal("That flag is currently dropped in the arena."), true);
                return true;
            }
            if (match.flagCarriers.containsValue(player.getUUID())) {
                player.sendSystemMessage(Component.literal("You are already carrying a flag."), true);
                return true;
            }
            if (match.ctfCasts.containsKey(player.getUUID())) {
                player.sendSystemMessage(Component.literal("You are already taking a flag."), true);
                return true;
            }
            for (MinigameMatch.CtfCast activeCast : match.ctfCasts.values()) {
                if (activeCast.flagTeam() == flagTeam) {
                    player.sendSystemMessage(Component.literal("Another player is already taking that flag."), true);
                    return true;
                }
            }
            long durationTicks = Math.max(20L, definition.captureTheFlag.flagTakeCastSeconds * 20L);
            match.ctfCasts.put(player.getUUID(), new MinigameMatch.CtfCast(
                    flagTeam, playerTeam, serverTicks, serverTicks + durationTicks,
                    player.getX(), player.getY(), player.getZ()));
        }
        String flagName = definition.captureTheFlag.teamName(flagTeam);
        player.sendSystemMessage(Component.literal("Taking the " + flagName
                + " flag. Do not move and do not take damage."), true);
        sendCtfCastBar(player, flagName, definition.captureTheFlag.color(playerTeam), 0.0F);
        return true;
    }

    private boolean handleDroppedCtfFlag(ServerPlayer player, MinigameMatch match,
                                         MinigameDefinition definition, MinigameArenaDefinition arena,
                                         int flagTeam) {
        int playerTeam = match.team(player.getUUID());
        MinigameLocation dropped;
        boolean returned;
        synchronized (this) {
            dropped = match.ctfDroppedFlags.get(flagTeam);
            if (dropped == null) return true;
            returned = playerTeam == flagTeam;
            if (!returned) {
                if (match.flagCarriers.containsKey(flagTeam)) {
                    player.sendSystemMessage(Component.literal("That flag is already being carried."), true);
                    return true;
                }
                if (match.flagCarriers.containsValue(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("You are already carrying a flag."), true);
                    return true;
                }
            }
            match.ctfDroppedFlags.remove(flagTeam);
            if (!returned) match.flagCarriers.put(flagTeam, player.getUUID());
        }
        interruptCtfCast(match, player.getUUID(), "");
        removeDroppedCtfFlagBlock(definition, flagTeam, dropped);
        if (returned) {
            placeCtfFlagAtBase(definition, arena, flagTeam);
            announce(match, player.getName().getString() + " returned the "
                    + definition.captureTheFlag.teamName(flagTeam) + " flag to base.");
            player.sendSystemMessage(Component.literal("Your team flag returned to base."), true);
        } else {
            attachCtfCarrierVisual(match, definition, player, flagTeam);
            announce(match, player.getName().getString() + " picked up the dropped "
                    + definition.captureTheFlag.teamName(flagTeam) + " flag!");
            player.sendSystemMessage(Component.literal("Return the enemy flag to your own base to score."), true);
        }
        publishCtfVisuals(match, definition);
        return true;
    }

    private boolean handleDominationRightClick(ServerPlayer player, BlockPos pos, MinigameMatch match,
                                                MinigameDefinition definition, MinigameArenaDefinition arena) {
        MinigameControlPoint clicked = null;
        for (MinigameControlPoint point : arena.controlPoints) {
            if (point.location.dimension.equals(player.level().dimension().identifier().toString())
                    && blockPos(point.location).equals(pos)) {
                clicked = point;
                break;
            }
        }
        if (clicked == null) return false;
        final MinigameControlPoint point = clicked;
        int team = match.team(player.getUUID());
        MinigameMatch.DominationClaim claim;
        int owner;
        synchronized (this) {
            claim = match.dominationClaims.get(point.id);
            owner = match.dominationOwners.getOrDefault(point.id, 0);
            if (claim != null) {
                if (claim.previousOwner() != 0 && team == claim.previousOwner()) {
                    match.dominationClaims.remove(point.id);
                    match.dominationOwners.put(point.id, claim.previousOwner());
                    cancelCastsForPoint(match, point.id, "The defending team secured this base.");
                    placeDominationMarker(definition, point, claim.previousOwner());
                    announce(match, definition.domination.teamName(team) + " defended " + point.displayName + "!");
                    playDominationHorns(match, definition, team);
                    publishDominationVisuals(match, definition, arena);
                    return true;
                }
                if (team == claim.claimingTeam()) {
                    player.sendSystemMessage(Component.literal(point.displayName + " is already being claimed by your team."), true);
                    return true;
                }
                // A neutral point can be counter-claimed, but the challenger must complete a fresh cast.
                match.dominationClaims.remove(point.id);
                match.dominationOwners.put(point.id, claim.previousOwner());
                placeDominationMarker(definition, point, claim.previousOwner());
                announce(match, definition.domination.teamName(team) + " interrupted the claim on " + point.displayName + ".");
            }
            if (owner == team) {
                player.sendSystemMessage(Component.literal("Your team already controls " + point.displayName + "."), true);
                return true;
            }
            if (match.dominationCasts.containsKey(player.getUUID())) {
                player.sendSystemMessage(Component.literal("You are already claiming a base."), true);
                return true;
            }
            for (MinigameMatch.DominationCast activeCast : match.dominationCasts.values()) {
                if (activeCast.pointId().equals(point.id)) {
                    player.sendSystemMessage(Component.literal("Another player is already claiming " + point.displayName + "."), true);
                    return true;
                }
            }
            long durationTicks = Math.max(20L, definition.domination.claimCastSeconds * 20L);
            match.dominationCasts.put(player.getUUID(), new MinigameMatch.DominationCast(
                    point.id, team, serverTicks, serverTicks + durationTicks,
                    player.getX(), player.getY(), player.getZ()));
        }
        player.sendSystemMessage(Component.literal("Claiming " + point.displayName
                + ". Do not move and do not take damage."), true);
        sendDominationCastBar(player, point.displayName, definition.domination.teamName(team),
                definition.domination.color(team), 0.0F);
        return true;
    }

    private void tickCaptureTheFlag(MinigameMatch match, MinigameDefinition definition,
                                    MinigameArenaDefinition arena) {
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) {
            finish(match, "Arena region became unavailable.");
            return;
        }
        for (UUID playerId : List.copyOf(match.teams.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            if (!locationInsideRegion(MinigameLocation.of(player), region, 12.0D)) {
                interruptCtfCast(match, playerId, "Flag capture interrupted because you left the arena.");
                returnFlagsCarriedBy(match, definition, arena, playerId, "The flag returned because its carrier left the arena.");
                int team = match.team(playerId);
                teleport(player, randomTeamSpawn(arena, match, team));
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
                player.sendSystemMessage(Component.literal("You were returned to your team spawn."), true);
            }
        }

        for (Map.Entry<Integer, UUID> carried : List.copyOf(match.flagCarriers.entrySet())) {
            int enemyFlagTeam = carried.getKey();
            UUID carrierId = carried.getValue();
            ServerPlayer carrier = server.getPlayerList().getPlayer(carrierId);
            if (carrier == null) {
                returnFlagsCarriedBy(match, definition, arena, carrierId, "A disconnected carrier's flag returned.");
                continue;
            }
            int carrierTeam = match.team(carrierId);
            MinigameFlagPoint ownBase = arena.flagForTeam(carrierTeam);
            boolean ownFlagAtBase = !match.flagCarriers.containsKey(carrierTeam)
                    && !match.ctfDroppedFlags.containsKey(carrierTeam);
            if (ownBase == null || !ownFlagAtBase || !ownBase.location.dimension.equals(
                    carrier.level().dimension().identifier().toString())) continue;
            double radius = definition.captureTheFlag.captureRadius;
            double dx = carrier.getX() - ownBase.location.x;
            double dy = carrier.getY() - ownBase.location.y;
            double dz = carrier.getZ() - ownBase.location.z;
            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;

            synchronized (this) {
                match.flagCarriers.remove(enemyFlagTeam);
                int score = match.ctfScores.getOrDefault(carrierTeam, 0) + 1;
                match.ctfScores.put(carrierTeam, score);
                for (Map.Entry<UUID, Integer> teammate : match.teams.entrySet()) {
                    if (teammate.getValue() == carrierTeam) match.scores.put(teammate.getKey(), (long) score);
                }
            }
            removeCtfCarrierVisual(match, carrierId);
            placeCtfFlagAtBase(definition, arena, enemyFlagTeam);
            int score = match.ctfScores.getOrDefault(carrierTeam, 0);
            announce(match, carrier.getName().getString() + " captured the "
                    + definition.captureTheFlag.teamName(enemyFlagTeam) + " flag! "
                    + definition.captureTheFlag.teamName(carrierTeam) + " " + score + "–"
                    + match.ctfScores.getOrDefault(carrierTeam == 1 ? 2 : 1, 0));
            publishCtfVisuals(match, definition);
            if (score >= definition.captureTheFlag.scoreToWin) {
                match.winningTeams = Set.of(carrierTeam);
                finish(match, definition.captureTheFlag.teamName(carrierTeam) + " reached the capture limit.");
                return;
            }
        }
        if (serverTicks % 40L == 0L) publishCtfVisuals(match, definition);
    }

    private void dropFlagsCarriedBy(MinigameMatch match, MinigameDefinition definition,
                                     MinigameArenaDefinition arena, UUID carrierId,
                                     MinigameLocation requestedLocation, String reason) {
        ArrayList<Integer> droppedTeams = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<Integer, UUID> entry : List.copyOf(match.flagCarriers.entrySet())) {
                if (entry.getValue().equals(carrierId)) {
                    match.flagCarriers.remove(entry.getKey());
                    droppedTeams.add(entry.getKey());
                }
            }
            match.ctfCarrierSneakLatch.remove(carrierId);
        }
        if (droppedTeams.isEmpty()) return;
        removeCtfCarrierVisual(match, carrierId);
        for (int flagTeam : droppedTeams) {
            MinigameLocation placed = placeDroppedCtfFlag(definition, flagTeam, requestedLocation);
            if (placed == null) {
                placeCtfFlagAtBase(definition, arena, flagTeam);
            } else {
                synchronized (this) { match.ctfDroppedFlags.put(flagTeam, placed); }
            }
        }
        announce(match, reason);
        publishCtfVisuals(match, definition);
    }

    private MinigameLocation placeDroppedCtfFlag(MinigameDefinition definition, int flagTeam,
                                                  MinigameLocation requestedLocation) {
        if (definition == null || requestedLocation == null) return null;
        ServerLevel level = resolveLevel(requestedLocation.dimension);
        if (level == null) return null;
        Block block = BuiltInRegistries.BLOCK.getOptional(
                Identifier.parse(definition.captureTheFlag.flagBlock(flagTeam))).orElse(null);
        if (block == null) return null;
        BlockPos origin = blockPos(requestedLocation);
        BlockPos selected = null;
        int[] offsets = {0, 1, -1, 2, -2, 3, -3, 4, -4};
        for (int offset : offsets) {
            BlockPos candidate = origin.offset(0, offset, 0);
            if (level.getBlockState(candidate).isAir()
                    && !level.getBlockState(candidate.below()).isAir()) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) return null;
        level.setBlockAndUpdate(selected, block.defaultBlockState());
        return new MinigameLocation(requestedLocation.dimension,
                selected.getX() + 0.5D, selected.getY(), selected.getZ() + 0.5D,
                requestedLocation.yaw, 0.0F);
    }

    private void removeDroppedCtfFlagBlock(MinigameDefinition definition, int flagTeam,
                                           MinigameLocation location) {
        if (definition == null || location == null) return;
        ServerLevel level = resolveLevel(location.dimension);
        if (level == null) return;
        Block expected = BuiltInRegistries.BLOCK.getOptional(
                Identifier.parse(definition.captureTheFlag.flagBlock(flagTeam))).orElse(null);
        BlockPos pos = blockPos(location);
        if (expected != null && level.getBlockState(pos).getBlock() == expected) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private void clearDroppedCtfFlags(MinigameMatch match, MinigameDefinition definition) {
        if (match == null || definition == null) return;
        Map<Integer, MinigameLocation> dropped;
        synchronized (this) {
            dropped = new LinkedHashMap<>(match.ctfDroppedFlags);
            match.ctfDroppedFlags.clear();
            match.ctfCarrierSneakLatch.clear();
        }
        for (Map.Entry<Integer, MinigameLocation> entry : dropped.entrySet()) {
            removeDroppedCtfFlagBlock(definition, entry.getKey(), entry.getValue());
        }
    }

    private void returnFlagsCarriedBy(MinigameMatch match, MinigameDefinition definition,
                                      MinigameArenaDefinition arena, UUID carrierId, String reason) {
        ArrayList<Integer> returned = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<Integer, UUID> entry : List.copyOf(match.flagCarriers.entrySet())) {
                if (entry.getValue().equals(carrierId)) {
                    match.flagCarriers.remove(entry.getKey());
                    returned.add(entry.getKey());
                }
            }
        }
        if (!returned.isEmpty()) removeCtfCarrierVisual(match, carrierId);
        for (int team : returned) placeCtfFlagAtBase(definition, arena, team);
        if (!returned.isEmpty()) {
            announce(match, reason);
            publishCtfVisuals(match, definition);
        }
    }

    private void restoreAllCtfFlags(MinigameMatch match, MinigameDefinition definition,
                                    MinigameArenaDefinition arena) {
        if (match == null || definition == null || arena == null
                || MinigameGameType.parse(definition.gameType) != MinigameGameType.CAPTURE_THE_FLAG) return;
        clearCtfCastBars(match);
        removeAllCtfCarrierVisuals(match);
        clearDroppedCtfFlags(match, definition);
        synchronized (this) {
            match.flagCarriers.clear();
            match.ctfCarrierSneakLatch.clear();
        }
        placeCtfFlagAtBase(definition, arena, 1);
        placeCtfFlagAtBase(definition, arena, 2);
        clearCtfVisuals(match);
    }

    public void onPlayerDamaged(ServerPlayer player) {
        if (player == null) return;
        MinigameMatch match;
        synchronized (this) { match = matchFor(player.getUUID()); }
        if (match != null) {
            interruptDominationCast(match, player.getUUID(), "Capture interrupted because you were attacked.");
            interruptCtfCast(match, player.getUUID(), "Flag capture interrupted because you were attacked.");
        }
    }

    private void interruptDominationCast(MinigameMatch match, UUID playerId, String reason) {
        if (match == null || playerId == null) return;
        MinigameMatch.DominationCast removed;
        synchronized (this) { removed = match.dominationCasts.remove(playerId); }
        if (removed == null) return;
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            clearCastBar(player);
            if (reason != null && !reason.isBlank()) player.sendSystemMessage(Component.literal(reason), true);
        }
    }

    private void cancelCastsForPoint(MinigameMatch match, String pointId, String reason) {
        if (match == null || pointId == null) return;
        for (Map.Entry<UUID, MinigameMatch.DominationCast> entry : List.copyOf(match.dominationCasts.entrySet())) {
            if (pointId.equals(entry.getValue().pointId())) interruptDominationCast(match, entry.getKey(), reason);
        }
    }

    private static void sendDominationCastBar(ServerPlayer player, String pointName, String teamName,
                                              int color, float progress) {
        if (player == null) return;
        int percent = Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * 100.0F);
        PacketDistributor.sendToPlayer(player, new MinigameCastBarPayload(true,
                "Claiming " + pointName + " for " + teamName + " · " + percent + "%", progress, color));
    }

    private static void clearCastBar(ServerPlayer player) {
        if (player != null) PacketDistributor.sendToPlayer(player, MinigameCastBarPayload.clear());
    }

    private void clearDominationCastBars(MinigameMatch match) {
        if (match == null) return;
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) clearCastBar(player);
        }
        match.dominationCasts.clear();
    }

    private static void sendCtfCastBar(ServerPlayer player, String flagName, int color, float progress) {
        if (player == null) return;
        int percent = Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * 100.0F);
        PacketDistributor.sendToPlayer(player, new MinigameCastBarPayload(true,
                "Taking " + flagName + " flag · " + percent + "%", progress, color));
    }

    private void interruptCtfCast(MinigameMatch match, UUID playerId, String reason) {
        if (match == null || playerId == null) return;
        MinigameMatch.CtfCast removed;
        synchronized (this) { removed = match.ctfCasts.remove(playerId); }
        if (removed == null) return;
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            clearCastBar(player);
            if (reason != null && !reason.isBlank()) player.sendSystemMessage(Component.literal(reason), true);
        }
    }

    private void clearCtfCastBars(MinigameMatch match) {
        if (match == null) return;
        for (UUID playerId : List.copyOf(match.ctfCasts.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) clearCastBar(player);
        }
        match.ctfCasts.clear();
    }

    private void playCtfHorns(MinigameMatch match, int carrierTeam) {
        SoundEvent sing = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:item.goat_horn.sound.1")).orElse(null);
        SoundEvent seek = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:item.goat_horn.sound.2")).orElse(null);
        if (sing == null || seek == null) return;
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            SoundEvent sound = entry.getValue() == carrierTeam ? sing : seek;
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.MASTER,
                    player.getX(), player.getY(), player.getZ(),
                    16.0F, 1.0F,
                    serverTicks ^ player.getUUID().getMostSignificantBits()));
        }
    }

    private void removeOrphanCtfBackFlags() {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                Component customName = entity.getCustomName();
                if (customName != null && "ssu_ctf_back_flag".equals(customName.getString())) entity.discard();
            }
        }
    }

    private void attachCtfCarrierVisual(MinigameMatch match, MinigameDefinition definition,
                                        ServerPlayer carrier, int flagTeam) {
        if (match == null || definition == null || carrier == null) return;
        removeCtfCarrierVisual(match, carrier.getUUID());
        Block banner = BuiltInRegistries.BLOCK.getOptional(
                Identifier.parse(definition.captureTheFlag.flagBlock(flagTeam))).orElse(null);
        if (banner == null || banner.asItem() == Items.AIR) return;

        // Vanilla pillager captains render their ominous banner by equipping the
        // banner item in the head slot. Doing the same here makes the flag part of
        // the player model instead of a separately interpolated follower entity.
        UUID carrierId = carrier.getUUID();
        match.ctfPreviousHeadItems.put(carrierId, carrier.getItemBySlot(EquipmentSlot.HEAD).copy());
        carrier.setItemSlot(EquipmentSlot.HEAD, new ItemStack(banner.asItem()));
        carrier.getInventory().setChanged();
        carrier.containerMenu.broadcastChanges();

        enableCtfCarrierGlow(match, carrier, definition.captureTheFlag.color(flagTeam), flagTeam);
        if (carrier.isShiftKeyDown()) match.ctfCarrierSneakLatch.add(carrierId);
        else match.ctfCarrierSneakLatch.remove(carrierId);
    }

    private void syncCtfCarrierVisuals(MinigameMatch match, MinigameDefinition definition) {
        if (match == null || definition == null) return;
        LinkedHashSet<UUID> activeCarriers = new LinkedHashSet<>(match.flagCarriers.values());
        for (UUID carrierId : activeCarriers) {
            ServerPlayer carrier = server.getPlayerList().getPlayer(carrierId);
            if (carrier == null) continue;
            int flagTeam = 0;
            for (Map.Entry<Integer, UUID> entry : match.flagCarriers.entrySet()) {
                if (carrierId.equals(entry.getValue())) { flagTeam = entry.getKey(); break; }
            }
            Block banner = BuiltInRegistries.BLOCK.getOptional(
                    Identifier.parse(definition.captureTheFlag.flagBlock(flagTeam))).orElse(null);
            if (banner == null || banner.asItem() == Items.AIR) continue;
            if (!match.ctfPreviousHeadItems.containsKey(carrierId)) {
                attachCtfCarrierVisual(match, definition, carrier, flagTeam);
                continue;
            }
            if (!carrier.getItemBySlot(EquipmentSlot.HEAD).is(banner.asItem())) {
                carrier.setItemSlot(EquipmentSlot.HEAD, new ItemStack(banner.asItem()));
                carrier.getInventory().setChanged();
                carrier.containerMenu.broadcastChanges();
            }
            if (!carrier.hasGlowingTag()) carrier.setGlowingTag(true);
        }
        for (UUID stale : List.copyOf(match.ctfPreviousHeadItems.keySet())) {
            if (!activeCarriers.contains(stale)) removeCtfCarrierVisual(match, stale);
        }
    }

    private void removeCtfCarrierVisual(MinigameMatch match, UUID carrierId) {
        if (match == null || carrierId == null) return;
        ItemStack previousHead = match.ctfPreviousHeadItems.remove(carrierId);
        ServerPlayer carrier = server.getPlayerList().getPlayer(carrierId);
        if (carrier != null && previousHead != null) {
            carrier.setItemSlot(EquipmentSlot.HEAD, previousHead.copy());
            carrier.getInventory().setChanged();
            carrier.containerMenu.broadcastChanges();
        }
        match.ctfCarrierSneakLatch.remove(carrierId);
        disableCtfCarrierGlow(match, carrierId);
    }

    private void removeAllCtfCarrierVisuals(MinigameMatch match) {
        if (match == null) return;
        LinkedHashSet<UUID> carriers = new LinkedHashSet<>(match.flagCarriers.values());
        carriers.addAll(match.ctfPreviousHeadItems.keySet());
        carriers.addAll(match.ctfPreviousGlowing.keySet());
        carriers.addAll(match.ctfPreviousScoreboardTeams.keySet());
        carriers.addAll(match.ctfGlowTeams.keySet());
        for (UUID carrierId : carriers) removeCtfCarrierVisual(match, carrierId);
    }

    private void enableCtfCarrierGlow(MinigameMatch match, ServerPlayer player, int rgb, int flagTeam) {
        if (match == null || player == null) return;
        UUID playerId = player.getUUID();
        match.ctfPreviousGlowing.putIfAbsent(playerId, player.hasGlowingTag());
        Scoreboard scoreboard = server.getScoreboard();
        String playerName = player.getScoreboardName();
        PlayerTeam previous = scoreboard.getPlayersTeam(playerName);
        match.ctfPreviousScoreboardTeams.putIfAbsent(playerId, previous == null ? "" : previous.getName());
        String teamName = "ssu" + match.id.toString().replace("-", "").substring(0, 8)
                + (flagTeam == 1 ? "r" : "b");
        match.ctfGlowTeams.put(playerId, teamName);
        PlayerTeam glowTeam = scoreboard.getPlayerTeam(teamName);
        if (glowTeam == null) glowTeam = scoreboard.addPlayerTeam(teamName);
        TeamColor fallback = flagTeam == 1 ? TeamColor.RED : TeamColor.BLUE;
        glowTeam.setColor(Optional.of(nearestTeamColor(rgb, fallback)));
        scoreboard.addPlayerToTeam(playerName, glowTeam);
        player.setGlowingTag(true);
    }

    private static TeamColor nearestTeamColor(int rgb, TeamColor fallback) {
        TeamColor nearest = fallback;
        long nearestDistance = Long.MAX_VALUE;
        int targetR = rgb >> 16 & 0xFF;
        int targetG = rgb >> 8 & 0xFF;
        int targetB = rgb & 0xFF;
        for (TeamColor teamColor : TeamColor.values()) {
            int color = teamColor.rgb();
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;
            long dr = targetR - r, dg = targetG - g, db = targetB - b;
            long distance = dr * dr + dg * dg + db * db;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = teamColor;
            }
        }
        return nearest;
    }

    private void disableCtfCarrierGlow(MinigameMatch match, UUID playerId) {
        if (match == null || playerId == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        String previousTeam = match.ctfPreviousScoreboardTeams.remove(playerId);
        Boolean previousGlow = match.ctfPreviousGlowing.remove(playerId);
        String glowTeamName = match.ctfGlowTeams.remove(playerId);
        if (player == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        String playerName = player.getScoreboardName();
        PlayerTeam current = scoreboard.getPlayersTeam(playerName);
        if (current != null && glowTeamName != null && glowTeamName.equals(current.getName())) {
            scoreboard.removePlayerFromTeam(playerName, current);
            if (current.getPlayers().isEmpty()) scoreboard.removePlayerTeam(current);
        }
        if (previousTeam != null && !previousTeam.isBlank()) {
            PlayerTeam restore = scoreboard.getPlayerTeam(previousTeam);
            if (restore != null) scoreboard.addPlayerToTeam(playerName, restore);
        }
        player.setGlowingTag(previousGlow != null && previousGlow);
    }

    private void playDominationHorns(MinigameMatch match, MinigameDefinition definition, int claimingTeam) {
        SoundEvent sing = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:item.goat_horn.sound.1")).orElse(null);
        SoundEvent seek = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:item.goat_horn.sound.2")).orElse(null);
        if (sing == null || seek == null) return;
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            SoundEvent sound = entry.getValue() == claimingTeam ? sing : seek;
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.MASTER,
                    player.getX(), player.getY(), player.getZ(),
                    16.0F, 1.0F,
                    serverTicks ^ player.getUUID().getMostSignificantBits()));
        }
    }

    private void playDominationCaptureCompleteSounds(MinigameMatch match, int capturingTeam, int previousOwner) {
        SoundEvent captured = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:block.beacon.activate")).orElse(null);
        SoundEvent lost = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:block.beacon.deactivate")).orElse(null);
        if (captured == null || lost == null) return;
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            SoundEvent sound = entry.getValue() == capturingTeam ? captured
                    : previousOwner != 0 && entry.getValue() == previousOwner ? lost : null;
            if (sound == null) continue;
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.MASTER,
                    player.getX(), player.getY(), player.getZ(),
                    1.5F, 1.0F,
                    serverTicks ^ player.getUUID().getLeastSignificantBits()));
        }
    }

    private void publishDominationVisuals(MinigameMatch match, MinigameDefinition definition,
                                          MinigameArenaDefinition arena) {
        if (match == null || definition == null || arena == null) return;
        ArrayList<MinigameDominationVisualPayload.Entry> entries = new ArrayList<>();
        for (MinigameControlPoint point : arena.controlPoints) {
            int owner = match.dominationOwners.getOrDefault(point.id, 0);
            MinigameMatch.DominationClaim claim = match.dominationClaims.get(point.id);
            int baseColor = claim != null && claim.previousOwner() != 0
                    ? definition.domination.color(claim.previousOwner())
                    : owner == 0 ? 0xFFFFFF : definition.domination.color(owner);
            int topColor = claim != null ? definition.domination.color(claim.claimingTeam()) : baseColor;
            String label;
            if (claim != null) {
                long remaining = Math.max(0L, (claim.completesTick() - serverTicks + 19L) / 20L);
                label = point.displayName + " · " + definition.domination.teamName(claim.claimingTeam())
                        + " claiming " + formatSeconds(remaining);
            } else {
                label = point.displayName + " · " + (owner == 0 ? "Neutral" : definition.domination.teamName(owner));
            }
            entries.add(new MinigameDominationVisualPayload.Entry(point.location.dimension,
                    point.location.x, point.location.y, point.location.z, label,
                    baseColor, topColor, claim != null));
        }
        MinigameDominationVisualPayload payload = new MinigameDominationVisualPayload(true, entries);
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private void clearDominationVisuals(MinigameMatch match) {
        if (match == null) return;
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) PacketDistributor.sendToPlayer(player, MinigameDominationVisualPayload.clear());
        }
    }

    private void publishCtfVisuals(MinigameMatch match, MinigameDefinition definition) {
        ArrayList<MinigameCtfVisualPayload.Entry> carriers = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<Integer, UUID> entry : match.flagCarriers.entrySet()) {
                ServerPlayer carrier = server.getPlayerList().getPlayer(entry.getValue());
                if (carrier != null) carriers.add(new MinigameCtfVisualPayload.Entry(
                        carrier.getId(), entry.getKey(), definition.captureTheFlag.color(entry.getKey())));
            }
        }
        MinigameCtfVisualPayload payload = new MinigameCtfVisualPayload(true, carriers);
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private void clearCtfVisuals(MinigameMatch match) {
        if (match == null) return;
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) PacketDistributor.sendToPlayer(player, MinigameCtfVisualPayload.clear());
        }
    }

    public boolean handlePlayerDeath(ServerPlayer player) {
        if (player == null) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            arena = definition == null || match == null ? null : arena(definition, match.arenaId);
        }
        MinigameGameType type = definition == null ? MinigameGameType.GENERIC
                : MinigameGameType.parse(definition.gameType);
        if (match == null || definition == null || arena == null
                || (type != MinigameGameType.CAPTURE_THE_FLAG && type != MinigameGameType.DOMINATION)
                || match.state != MinigameMatchState.RUNNING) return false;
        MinigameLocation deathLocation = MinigameLocation.of(player);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            interruptCtfCast(match, player.getUUID(), "Flag capture interrupted because you were defeated.");
            dropFlagsCarriedBy(match, definition, arena, player.getUUID(), deathLocation,
                    player.getName().getString() + " dropped the carried flag.");
        }
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.setAbsorptionAmount(0.0F);
        player.setRemainingFireTicks(0);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        int team = match.team(player.getUUID());
        if (type == MinigameGameType.DOMINATION) {
            teleport(player, dominationRespawn(match, arena, team, deathLocation));
            player.sendSystemMessage(Component.literal("You were defeated and respawned at the nearest controlled location."), true);
        } else {
            teleport(player, randomTeamSpawn(arena, match, team));
            player.sendSystemMessage(Component.literal("You were defeated and returned to a team spawn."), true);
        }
        return true;
    }

    private void tickSpleef(MinigameMatch match, MinigameDefinition definition, MinigameArenaDefinition arena) {
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) {
            finish(match, "Arena region became unavailable.");
            return;
        }
        int floorY = arena.playFloor != null && arena.playFloor.configured()
                ? arena.playFloor.minY : region.getMinY();
        int eliminationY = floorY - definition.spleef.eliminationDepth;
        for (UUID playerId : List.copyOf(match.teams.keySet())) {
            if (!match.active(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            boolean wrongDimension = !player.level().dimension().equals(region.getDimension());
            boolean outsideHorizontal = player.getX() < region.getMinX() || player.getX() >= region.getMaxX() + 1.0D
                    || player.getZ() < region.getMinZ() || player.getZ() >= region.getMaxZ() + 1.0D;
            if (wrongDimension || outsideHorizontal || player.getY() < eliminationY) {
                eliminate(playerId, "You fell out of the Spleef arena.");
            }
        }
        if (match.state == MinigameMatchState.RUNNING && activeTeams(match).isEmpty()) {
            match.winningTeams = Set.of();
            finish(match, "All players were eliminated.");
            return;
        }
        if (definition.spleef.removeBlockDrops) clearArenaItemEntities(arena);
    }

    private void clearArenaItemEntities(MinigameArenaDefinition arena) {
        if (server == null || arena == null || arena.regionId == null || arena.regionId.isBlank()) return;
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) return;
        ServerLevel level = server.getLevel(region.getDimension());
        if (level == null) return;
        AABB box = new AABB(region.getMinX(), region.getMinY() - 8.0D, region.getMinZ(),
                region.getMaxX() + 1.0D, region.getMaxY() + 8.0D, region.getMaxZ() + 1.0D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) item.discard();
    }

    private MinigameMatch activeMatchAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (MinigameMatch candidate : matches.values()) {
            if (candidate.state == MinigameMatchState.RESETTING || candidate.state == MinigameMatchState.FINISHED) continue;
            MinigameDefinition definition = definitions.get(candidate.minigameId);
            MinigameArenaDefinition arena = definition == null ? null : arena(definition, candidate.arenaId);
            if (arena == null || arena.regionId.isBlank()) continue;
            Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
            if (region != null && region.contains(dimension, pos)) return candidate;
        }
        return null;
    }

    public BlockBreakDecision blockBreakDecision(ServerPlayer player, BlockPos pos, BlockState state) {
        if (player == null || pos == null || state == null) return BlockBreakDecision.PASS;
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(player.getUUID());
            if (match == null) {
                return activeMatchAt(player.level().dimension(), pos) == null
                        ? BlockBreakDecision.PASS : BlockBreakDecision.DENY;
            }
            definition = definitions.get(match.minigameId);
            arena = definition == null ? null : arena(definition, match.arenaId);
        }
        if (definition == null || arena == null) return BlockBreakDecision.DENY;
        if (MinigameGameType.parse(definition.gameType) != MinigameGameType.SPLEEF) return BlockBreakDecision.DENY;
        if (match.state != MinigameMatchState.RUNNING || !match.active(player.getUUID())) return BlockBreakDecision.DENY;
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null || !region.contains(player.level().dimension(), pos)) return BlockBreakDecision.DENY;
        if (arena.playFloor != null && arena.playFloor.configured()
                && !arena.playFloor.contains(player.level().dimension(), pos)) return BlockBreakDecision.DENY;
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!definition.spleef.canBreak(blockId)) return BlockBreakDecision.DENY;
        if (definition.spleef.requireConfiguredTool) {
            String held = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
            if (!held.equals(definition.spleef.toolItem)) return BlockBreakDecision.DENY;
        }
        return definition.spleef.removeBlockDrops ? BlockBreakDecision.ALLOW_NO_DROPS : BlockBreakDecision.ALLOW;
    }

    /** Removes a permitted Spleef floor block without ever creating an ItemEntity. */
    public void breakSpleefBlockWithoutDrops(ServerPlayer player, BlockPos pos, BlockState expectedState) {
        if (player == null || pos == null || expectedState == null || !(player.level() instanceof ServerLevel level)) return;
        if (blockBreakDecision(player, pos, expectedState) != BlockBreakDecision.ALLOW_NO_DROPS) return;
        BlockState current = level.getBlockState(pos);
        if (!current.equals(expectedState)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) held.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
    }

    public boolean shouldCancelItemPickup(ServerPlayer player, BlockPos pos) {
        if (player == null) return false;
        synchronized (this) {
            if (matchFor(player.getUUID()) != null) return true;
            return pos != null && activeMatchAt(player.level().dimension(), pos) != null;
        }
    }

    public boolean shouldCancelBlockPlace(ServerPlayer player, BlockPos pos) {
        if (player == null) return false;
        synchronized (this) {
            if (matchFor(player.getUUID()) != null) return true;
            return pos != null && activeMatchAt(player.level().dimension(), pos) != null;
        }
    }

    /** Narrow bypass for the normal region PvP flag. The damage event still verifies
     * that both players belong to the same live match before damage can land. */
    public boolean canBypassRegionPvp(ServerPlayer attacker, BlockPos targetPos) {
        if (attacker == null || targetPos == null) return false;
        synchronized (this) {
            MinigameMatch match = matchFor(attacker.getUUID());
            if (match == null || match.state != MinigameMatchState.RUNNING || !match.active(attacker.getUUID())) return false;
            MinigameDefinition definition = definitions.get(match.minigameId);
            if (definition == null) return false;
            MinigameGameType type = MinigameGameType.parse(definition.gameType);
            if (type == MinigameGameType.SPLEEF && !definition.spleef.allowPvp) return false;
            if (type != MinigameGameType.SPLEEF && type != MinigameGameType.CAPTURE_THE_FLAG
                    && type != MinigameGameType.DOMINATION) return false;
            MinigameArenaDefinition arena = arena(definition, match.arenaId);
            Region region = arena == null ? null : SimpleServerUtilities.REGIONS.get(arena.regionId);
            return region != null && region.contains(attacker.level().dimension(), targetPos);
        }
    }

    public boolean shouldCancelDamage(ServerPlayer victim, ServerPlayer attacker) {
        if (victim == null) return false;
        MinigameMatch victimMatch;
        MinigameMatch attackerMatch;
        MinigameDefinition definition;
        synchronized (this) {
            victimMatch = matchFor(victim.getUUID());
            attackerMatch = attacker == null ? null : matchFor(attacker.getUUID());
            if (victimMatch == null) return attackerMatch != null;
            if (victimMatch.state != MinigameMatchState.RUNNING || victimMatch.eliminated.contains(victim.getUUID())) return true;
            if (attacker == null) return false;
            if (attackerMatch != victimMatch) return true;
            definition = definitions.get(victimMatch.minigameId);
        }
        if (definition == null) return true;
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.SPLEEF) return !definition.spleef.allowPvp;
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            return !definition.captureTheFlag.allowFriendlyFire
                    && victimMatch.team(victim.getUUID()) == victimMatch.team(attacker.getUUID());
        }
        if (type == MinigameGameType.DOMINATION) {
            return !definition.domination.allowFriendlyFire
                    && victimMatch.team(victim.getUUID()) == victimMatch.team(attacker.getUUID());
        }
        return false;
    }

    private void updateHud(MinigameMatch match, MinigameDefinition definition, long elapsedSeconds) {
        int alive = 0;
        for (UUID playerId : match.teams.keySet()) if (match.active(playerId)) alive++;
        long remaining = switch (match.state) {
            case COUNTDOWN -> Math.max(0L, definition.countdownSeconds - elapsedSeconds);
            case RUNNING -> definition.matchDurationSeconds <= 0 ? -1L : Math.max(0L, definition.matchDurationSeconds - elapsedSeconds);
            case POST_GAME -> Math.max(0L, definition.postGameSeconds - elapsedSeconds);
            default -> 0L;
        };
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            ArrayList<String> lines = new ArrayList<>();
            lines.add("Mode: " + type.label());
            lines.add("State: " + match.state.name().toLowerCase(Locale.ROOT).replace('_', ' '));
            if (remaining >= 0L) lines.add((match.state == MinigameMatchState.COUNTDOWN ? "Starts in: " : "Time: ") + formatSeconds(remaining));
            if (type == MinigameGameType.CAPTURE_THE_FLAG) {
                int team = match.team(playerId);
                lines.add(definition.captureTheFlag.teamName(1) + ": " + match.ctfScores.getOrDefault(1, 0)
                        + " · " + definition.captureTheFlag.teamName(2) + ": " + match.ctfScores.getOrDefault(2, 0));
                lines.add("Your team: " + definition.captureTheFlag.teamName(team));
                String flag1State = match.flagCarriers.containsKey(1) ? "carried"
                        : match.ctfDroppedFlags.containsKey(1) ? "dropped" : "base";
                String flag2State = match.flagCarriers.containsKey(2) ? "carried"
                        : match.ctfDroppedFlags.containsKey(2) ? "dropped" : "base";
                lines.add("Flags: " + definition.captureTheFlag.teamName(1) + " " + flag1State
                        + " · " + definition.captureTheFlag.teamName(2) + " " + flag2State);
                int carriedFlag = 0;
                for (Map.Entry<Integer, UUID> entry : match.flagCarriers.entrySet()) {
                    if (entry.getValue().equals(playerId)) { carriedFlag = entry.getKey(); break; }
                }
                lines.add(carriedFlag == 0 ? "Defend your flag and capture the enemy flag"
                        : "Carrying: " + definition.captureTheFlag.teamName(carriedFlag) + " flag");
            } else if (type == MinigameGameType.DOMINATION) {
                int team = match.team(playerId);
                lines.add(definition.domination.teamName(1) + ": " + match.dominationScores.getOrDefault(1, 0)
                        + " · " + definition.domination.teamName(2) + ": " + match.dominationScores.getOrDefault(2, 0));
                lines.add("Your team: " + definition.domination.teamName(team));
                StringBuilder nodes = new StringBuilder("Nodes: ");
                MinigameArenaDefinition dominationArena = arena(definition, match.arenaId);
                if (dominationArena != null) {
                    for (MinigameControlPoint point : dominationArena.controlPoints) {
                        if (nodes.length() > 7) nodes.append(" · ");
                        int owner = match.dominationOwners.getOrDefault(point.id, 0);
                        MinigameMatch.DominationClaim claim = match.dominationClaims.get(point.id);
                        String marker;
                        if (claim != null) {
                            long seconds = Math.max(0L, (claim.completesTick() - serverTicks + 19L) / 20L);
                            marker = definition.domination.teamName(claim.claimingTeam()) + " " + seconds + "s";
                        } else marker = owner == 0 ? "N" : definition.domination.teamName(owner);
                        nodes.append(point.displayName).append(':').append(marker);
                    }
                }
                lines.add(nodes.toString());
                int claimLines = 0;
                for (Map.Entry<String, MinigameMatch.DominationClaim> entry : match.dominationClaims.entrySet()) {
                    if (claimLines++ >= 3) break;
                    MinigameControlPoint point = dominationArena == null ? null : controlPoint(dominationArena, entry.getKey());
                    long seconds = Math.max(0L, (entry.getValue().completesTick() - serverTicks + 19L) / 20L);
                    lines.add("Claiming: " + (point == null ? entry.getKey() : point.displayName) + " → "
                            + definition.domination.teamName(entry.getValue().claimingTeam()) + " ("
                            + formatSeconds(seconds) + ")");
                }
            } else {
                lines.add("Alive: " + alive + " / " + match.teams.size());
                lines.add(match.eliminated.contains(playerId) ? "You are spectating" : "Position: active");
            }
            PacketDistributor.sendToPlayer(player, new MinigameHudPayload(true, definition.displayName, lines));
        }
    }

    private static String formatSeconds(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(Locale.ROOT, "%d:%02d", safe / 60L, safe % 60L);
    }

    private static void clearHud(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, MinigameHudPayload.clear());
        PacketDistributor.sendToPlayer(player, MinigameCastBarPayload.clear());
    }

    public enum BlockBreakDecision { PASS, ALLOW, ALLOW_NO_DROPS, DENY }

    private synchronized MinigameMatch matchFor(UUID playerId) {
        UUID matchId = playerMatches.get(playerId);
        return matchId == null ? null : matches.get(matchId);
    }

    public synchronized boolean hasActiveRuntimeFor(String rawId) {
        String id = ContentId.normalize(rawId);
        return !id.isBlank() && hasRuntimeFor(id);
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

    private void sendLobby(ServerPlayer player, String notice, boolean error, long requestId, boolean adminView) {
        ArrayList<MinigameLobbyDataPayload.GameEntry> entries = new ArrayList<>();
        String queued;
        MinigameMatch ownMatch;
        synchronized (this) {
            queued = playerQueues.getOrDefault(player.getUUID(), "");
            ownMatch = matchFor(player.getUUID());
            for (MinigameDefinition definition : definitions.values()) {
                MinigameGameType listedType = MinigameGameType.parse(definition.gameType);
                if (!adminView && (!definition.enabled || listedType == MinigameGameType.GENERIC
                        || !listedType.implemented())) continue;
                var availability = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(definition.prerequisites,
                        new ContentConditionContext(server, player, "minigames", definition.id,
                                Map.of("minigame", definition.id)));
                int free = 0, blocked = 0;
                for (MinigameArenaDefinition arena : definition.arenas) {
                    String key = arenaKey(definition.id, arena.id);
                    if (!arena.enabled) continue;
                    if (blockedArenas.contains(key)) blocked++;
                    else if (!arenaReservations.containsKey(key) && !resettingArenas.contains(key)
                            && locationsResolvable(definition, arena)) free++;
                }
                int running = (int) matches.values().stream().filter(match -> match.minigameId.equals(definition.id)).count();
                boolean inThisQueue = definition.id.equals(queued);
                boolean inThisMatch = ownMatch != null && definition.id.equals(ownMatch.minigameId);
                entries.add(new MinigameLobbyDataPayload.GameEntry(definition.id, definition.displayName,
                        definition.description, definition.iconItem, definition.gameType, definition.enabled, definition.minPlayers,
                        definition.maxPlayers, definition.teamCount, queueSize(definition.id), running, free, blocked,
                        definition.victoryMode, availability.matched(), availability.reason(), inThisQueue, inThisMatch,
                        inThisMatch ? ownMatch.state.name().toLowerCase(Locale.ROOT) : "",
                        inThisMatch ? ownMatch.team(player.getUUID()) : 0,
                        inThisMatch ? ownMatch.score(player.getUUID()) : 0L));
            }
        }
        entries.sort(Comparator.comparing(MinigameLobbyDataPayload.GameEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        PacketDistributor.sendToPlayer(player, new MinigameLobbyDataPayload(notice, error, canAdmin(player), adminView, requestId,
                queued, ownMatch == null ? "" : ownMatch.id.toString(), entries));
    }

    public synchronized boolean isManagedArenaRegion(String regionId) {
        if (regionId == null || regionId.isBlank()) return false;
        for (MinigameDefinition definition : definitions.values()) {
            for (MinigameArenaDefinition arena : definition.arenas) {
                if (arena.managedRegion && regionId.equalsIgnoreCase(arena.regionId)) return true;
            }
        }
        return false;
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
                MinigameDefinition definition = definitions.get(match.minigameId);
                for (UUID playerId : List.copyOf(match.teams.keySet())) returnParticipant(match, playerId);
            }
        }
        clearRuntime(false);
        saveRecovery();
    }

    private synchronized void clearRuntime(boolean keepServer) {
        if (server != null) {
            for (MinigameMatch match : List.copyOf(matches.values())) {
                MinigameDefinition definition = definitions.get(match.minigameId);
                MinigameArenaDefinition arena = definition == null ? null : arena(definition, match.arenaId);
                if (definition != null && arena != null
                        && MinigameGameType.parse(definition.gameType) == MinigameGameType.CAPTURE_THE_FLAG) {
                    restoreAllCtfFlags(match, definition, arena);
                } else {
                    clearCtfCastBars(match);
                    removeAllCtfCarrierVisuals(match);
                }
            }
        }
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
