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
import be.winnetrie.mod.simpleserverutilities.network.MinigameKillFeedPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameResultsPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameProfilePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSpectatorActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCastBarPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameDominationVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameDiagnosticsPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCtfVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameScoreActionPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import be.winnetrie.mod.simpleserverutilities.statistics.StatisticEventType;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderLayer;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderVisualizationSettings;
import be.winnetrie.mod.simpleserverutilities.visualization.PlayerBorderPreferences;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Independent queue, arena and match lifecycle for every SSU minigame type.
 * It deliberately contains no NPC, quest or dungeon dependency.
 */
public final class MinigameManager {
    public static final int DEFINITION_SCHEMA_VERSION = 19;
    public static final int RECOVERY_SCHEMA_VERSION = 4;
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_SERIALIZED_CHARACTERS = 65_535;
    public static final int MAX_QUEUE_SIZE = 2_048;
    private static final Duration CRITICAL_RECOVERY_FLUSH_TIMEOUT = Duration.ofSeconds(5);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BOOST_ENTITY_TAG = "ssu_minigame_boost";
    /** Hunger stays visually full while LivingHealEvent blocks hunger-based healing. */
    private static final int COMBAT_FOOD_LEVEL = 20;
    private static final String ROLE_DPS_SWORD = "DPS Sword";
    private static final String ROLE_DPS_BOW = "DPS Bow";
    private static final String ROLE_DPS_ARROW = "DPS Role Arrow";
    private static final String ROLE_TANK_SWORD = "Tank Sword";
    private static final String ROLE_HEALER_SWORD = "Healer Sword";
    private static final String ROLE_TEAM_HELMET = "Team Helmet";
    private static final String ROLE_TEAM_CHESTPLATE = "Team Chestplate";
    private static final String ROLE_TEAM_LEGGINGS = "Team Leggings";
    private static final String ROLE_TEAM_BOOTS = "Team Boots";
    private static final String ROLE_TANK_FIELD = "Tank Defensive Field";
    private static final String ROLE_TANK_SHIELD = "Team Tank Shield";
    private static final String ROLE_HEAL_SINGLE = "Healer Single Heal";
    private static final String ROLE_HEAL_AOE = "Healer AOE Heal";
    private static final String ROLE_HEAL_SELF = "Healer Self Heal";
    private static final Set<String> ROLE_LOCKED_NAMES = Set.of(
            ROLE_DPS_SWORD, ROLE_DPS_BOW, ROLE_DPS_ARROW,
            ROLE_TANK_SWORD, ROLE_TANK_FIELD, ROLE_TANK_SHIELD,
            ROLE_HEALER_SWORD, ROLE_HEAL_SINGLE, ROLE_HEAL_AOE, ROLE_HEAL_SELF,
            ROLE_TEAM_HELMET, ROLE_TEAM_CHESTPLATE, ROLE_TEAM_LEGGINGS, ROLE_TEAM_BOOTS);
    private static final float MINIGAME_GAME_BORDER_WIDTH = 3.5F;
    private static final float MINIGAME_SPECTATOR_BORDER_WIDTH = 3.5F;

    private final Map<String, MinigameDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, LinkedHashMap<UUID, Long>> queues = new LinkedHashMap<>();
    private final Map<UUID, MinigameMatch> matches = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerMatches = new HashMap<>();
    private final Map<UUID, String> playerQueues = new HashMap<>();
    /** Queue-time preference only; final assignment is composed per team at match start. */
    private final Map<UUID, MinigameRole> playerRolePreferences = new HashMap<>();
    private final Map<String, UUID> arenaReservations = new HashMap<>();
    private final Set<String> resettingArenas = new LinkedHashSet<>();
    private final Set<String> blockedArenas = new LinkedHashSet<>();
    /** Persisted safety markers for reset-enabled arenas interrupted before a clean reset. */
    private final Set<String> unsafeArenas = new LinkedHashSet<>();
    private final Map<UUID, MinigameRecoveryData.Entry> recoveries = new LinkedHashMap<>();
    private final Map<UUID, Long> lastRequests = new HashMap<>();
    /** Last effective runtime-border state sent to each player. */
    private final Map<UUID, RuntimeBorderSyncState> runtimeBorderSyncStates = new HashMap<>();
    private final DirtyJsonRecordStore definitionStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore recoveryStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore experienceStore = new DirtyJsonRecordStore();
    private MinigameProgressionData progression = new MinigameProgressionData();
    private MinigameMatchHistory history = new MinigameMatchHistory();
    /** Current spectator target cursor per participant. */
    private final Map<UUID, Integer> spectatorCursors = new HashMap<>();
    /** Round-robin cursor used by next-arena/rematch voting and ordinary queue starts. */
    private final Map<String, Integer> arenaRotationCursors = new HashMap<>();

    private MinecraftServer server;
    private Path definitionFolder;
    private Path recoveryFile;
    private Path progressionFile;
    private Path historyFile;
    private long serverTicks;
    /** Session fail-safe: no new live player state may be replaced while recovery persistence is uncertain. */
    private boolean recoverySafetyHalted;

    public synchronized void load(MinecraftServer server) {
        clearRuntime(false);
        this.server = server;
        removeOrphanCtfBackFlags();
        removeOrphanMinigameBoosts();
        Path root = StoragePaths.minigames(StoragePaths.root(server));
        definitionFolder = StoragePaths.minigameDefinitions(StoragePaths.root(server));
        recoveryFile = StoragePaths.minigameRecovery(StoragePaths.root(server));
        progressionFile = StoragePaths.minigameProgression(StoragePaths.root(server));
        historyFile = StoragePaths.minigameHistory(StoragePaths.root(server));
        definitionStore.reset();
        recoveryStore.reset();
        experienceStore.reset();
        recoverySafetyHalted = false;
        definitions.clear();
        recoveries.clear();
        unsafeArenas.clear();
        try {
            Files.createDirectories(root);
            Files.createDirectories(definitionFolder);
            definitionStore.discover(definitionFolder);
            recoveryStore.discoverFile(recoveryFile);
            experienceStore.discoverFile(progressionFile);
            experienceStore.discoverFile(historyFile);
            loadDefinitions();
            loadRecovery();
            loadExperienceData();
            saveAll();
            SimpleServerUtilities.LOGGER.info("Loaded {} SSU minigames, {} pending recoveries, {} progression profiles and {} match history entries.",
                    definitions.size(), recoveries.size(), progression.players.size(), history.matches.size());
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

    private void loadExperienceData() {
        progression = new MinigameProgressionData();
        history = new MinigameMatchHistory();
        if (progressionFile != null && Files.exists(progressionFile)) {
            try {
                MinigameProgressionData loaded = JsonStorage.read(GSON, progressionFile, MinigameProgressionData.class);
                if (loaded != null) progression = loaded;
                progression.normalize();
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(progressionFile);
                experienceStore.forget(progressionFile);
                SimpleServerUtilities.LOGGER.error("Failed to load minigame progression; archived as {}.", archived, exception);
            }
        }
        if (historyFile != null && Files.exists(historyFile)) {
            try {
                MinigameMatchHistory loaded = JsonStorage.read(GSON, historyFile, MinigameMatchHistory.class);
                if (loaded != null) history = loaded;
                history.normalize();
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(historyFile);
                experienceStore.forget(historyFile);
                SimpleServerUtilities.LOGGER.error("Failed to load minigame history; archived as {}.", archived, exception);
            }
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
        saveExperienceData();
    }

    private void saveRecovery() {
        if (recoveryFile == null) return;
        recoveryStore.queueJson(GSON, recoveryFile, recoverySnapshot());
    }

    private void saveExperienceData() {
        if (progressionFile != null) {
            progression.normalize();
            experienceStore.queueJson(GSON, progressionFile, progression);
        }
        if (historyFile != null) {
            history.normalize();
            experienceStore.queueJson(GSON, historyFile, history);
        }
    }

    /**
     * Persists progression and history as one logical minigame settlement barrier.
     * Both files are flushed and byte-verified before cleanup may return players.
     */
    private synchronized boolean saveExperienceDataDurably(String operation) {
        if (progressionFile == null || historyFile == null) {
            SimpleServerUtilities.LOGGER.error(
                    "Paused minigame cleanup because experience storage is unavailable during '{}'.", operation);
            return false;
        }
        progression.normalize();
        history.normalize();
        String expectedProgression = GSON.toJson(progression);
        String expectedHistory = GSON.toJson(history);
        experienceStore.queueJson(GSON, progressionFile, progression);
        experienceStore.queueJson(GSON, historyFile, history);
        if (!SimpleServerUtilities.STORAGE.flushPath(progressionFile, CRITICAL_RECOVERY_FLUSH_TIMEOUT)
                || !SimpleServerUtilities.STORAGE.flushPath(historyFile, CRITICAL_RECOVERY_FLUSH_TIMEOUT)) {
            SimpleServerUtilities.LOGGER.error(
                    "Paused minigame cleanup because experience storage could not be flushed during '{}'.", operation);
            return false;
        }
        try {
            String storedProgression = Files.readString(progressionFile, StandardCharsets.UTF_8);
            String storedHistory = Files.readString(historyFile, StandardCharsets.UTF_8);
            if (!expectedProgression.equals(storedProgression) || !expectedHistory.equals(storedHistory)) {
                experienceStore.forget(progressionFile);
                experienceStore.forget(historyFile);
                SimpleServerUtilities.LOGGER.error(
                        "Paused minigame cleanup because experience storage verification differed after '{}'.",
                        operation);
                return false;
            }
            return true;
        } catch (IOException exception) {
            experienceStore.forget(progressionFile);
            experienceStore.forget(historyFile);
            SimpleServerUtilities.LOGGER.error(
                    "Paused minigame cleanup because experience storage verification failed after '{}'.",
                    operation, exception);
            return false;
        }
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

    public MinigameArenaDefinition copyArena(MinigameArenaDefinition arena) {
        return arena == null ? null : GSON.fromJson(GSON.toJson(arena), MinigameArenaDefinition.class);
    }

    public String arenaToJson(MinigameArenaDefinition arena) {
        if (arena == null) throw new IllegalArgumentException("Arena is required.");
        return GSON.toJson(arena);
    }

    public MinigameArenaDefinition arenaFromJson(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Arena import data is missing or too large.");
        }
        MinigameArenaDefinition arena = GSON.fromJson(json, MinigameArenaDefinition.class);
        if (arena == null) throw new IllegalArgumentException("Arena import data is invalid.");
        arena.normalize();
        return arena;
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
        MinigameRoleRules roleRules = roleRules(definition);
        if (roleRules != null && roleRules.enabled) {
            int smallestTeamAtMinimum = definition.minPlayers / 2;
            int largestTeamAtMaximum = (definition.maxPlayers + 1) / 2;
            if (roleRules.minimumTotalPerTeam() > smallestTeamAtMinimum) {
                throw new IllegalArgumentException("Role minima require at least "
                        + (roleRules.minimumTotalPerTeam() * 2) + " players so both teams can be composed.");
            }
            if (roleRules.maximumTotalPerTeam() < largestTeamAtMaximum) {
                throw new IllegalArgumentException("Role maxima cannot hold the configured maximum team size of "
                        + largestTeamAtMaximum + ".");
            }
            Identifier effectId = Identifier.parse(roleRules.dpsArrowEffect);
            if (BuiltInRegistries.MOB_EFFECT.getOptional(effectId).isEmpty()) {
                throw new IllegalArgumentException("Unknown DPS arrow effect: " + roleRules.dpsArrowEffect);
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
            if (gameType == MinigameGameType.CAPTURE_THE_FLAG || gameType == MinigameGameType.DOMINATION) {
                LinkedHashSet<String> boostBlocks = new LinkedHashSet<>();
                for (MinigameLocation boostSpawn : arena.boostSpawns) {
                    validateLocation(boostSpawn, "Boost spawn");
                    String cell = boostSpawn.dimension + ":" + blockPos(boostSpawn).asLong();
                    if (!boostBlocks.add(cell)) {
                        throw new IllegalArgumentException("Boost spawn points must occupy different blocks.");
                    }
                }
                MinigameBoostRules boostRules = gameType == MinigameGameType.CAPTURE_THE_FLAG
                        ? definition.captureTheFlag.boosts : definition.domination.boosts;
                if (boostRules.enabled && boostRules.enabledTypes().isEmpty()) {
                    throw new IllegalArgumentException("At least one boost type must be enabled.");
                }
                if (boostRules.enabled && !boostRules.automatic() && arena.boostSpawns.isEmpty()) {
                    throw new IllegalArgumentException("Manual boost placement requires at least one boost spawn in arena '"
                            + arena.id + "'.");
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
            if (gameType == MinigameGameType.SPLEEF && definition.enabled && arena.enabled && !arena.resetRegionAfterMatch) {
                throw new IllegalArgumentException("Enabled Spleef arenas require a verified region snapshot reset.");
            }
            if (gameType == MinigameGameType.CAPTURE_THE_FLAG && definition.enabled && arena.enabled && !arena.resetRegionAfterMatch) {
                throw new IllegalArgumentException("Enabled Capture the Flag arenas require a verified region snapshot reset.");
            }
            if (gameType == MinigameGameType.DOMINATION && definition.enabled && arena.enabled && !arena.resetRegionAfterMatch) {
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
                if (gameType == MinigameGameType.CAPTURE_THE_FLAG || gameType == MinigameGameType.DOMINATION) {
                    for (MinigameLocation boostSpawn : arena.boostSpawns) {
                        if (!locationInsideRegion(boostSpawn, region, 2.0D)) {
                            throw new IllegalArgumentException("Boost spawn points for arena '" + arena.id
                                    + "' must be inside the arena region.");
                        }
                    }
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

    public void handleSpectatorAction(MinigameSpectatorActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> spectateParticipant(player, payload == null ? "" : payload.action()));
    }

    private void spectateParticipant(ServerPlayer spectator, String action) {
        if (spectator == null) return;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(spectator.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        if (match == null || definition == null || definition.experience == null
                || !definition.experience.spectatorToolsEnabled
                || !spectator.isSpectator()) return;
        ArrayList<ServerPlayer> targets = new ArrayList<>();
        for (UUID playerId : match.joinOrder) {
            if (playerId.equals(spectator.getUUID()) || match.eliminated.contains(playerId)
                    || match.pendingRespawns.containsKey(playerId) || match.disconnected.containsKey(playerId)) continue;
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target != null && !target.isDeadOrDying()) targets.add(target);
        }
        if (targets.isEmpty()) {
            spectator.sendSystemMessage(Component.literal("No living participant is available to spectate."), true);
            return;
        }
        int delta = "previous".equalsIgnoreCase(action) ? -1 : 1;
        int cursor = Math.floorMod(spectatorCursors.getOrDefault(spectator.getUUID(), delta < 0 ? 0 : -1) + delta,
                targets.size());
        spectatorCursors.put(spectator.getUUID(), cursor);
        ServerPlayer target = targets.get(cursor);
        spectator.setCamera(target);
        spectator.sendSystemMessage(Component.literal("Spectating " + target.getName().getString()
                + " • , previous • . next"), true);
    }

    public void handleRequest(MinigameLobbyRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> processRequest(player, payload));
    }

    public void handleMatchOverviewRequest(MinigameMatchOverviewRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> processMatchOverviewRequest(player, payload));
    }

    private void processMatchOverviewRequest(ServerPlayer player, MinigameMatchOverviewRequestPayload payload) {
        if (player == null || payload == null) return;
        String action = payload.action();
        if ("leave".equals(action)) {
            String notice;
            try {
                notice = leave(player, true);
            } catch (RuntimeException exception) {
                notice = exception.getMessage() == null ? "The match could not be left safely." : exception.getMessage();
                sendMatchOverview(player, notice, true, payload.requestId(), false);
                return;
            }
            if (isInMatch(player.getUUID(), "")) {
                sendMatchOverview(player, notice, false, payload.requestId(), false);
            } else {
                PacketDistributor.sendToPlayer(player, MinigameMatchOverviewPayload.inactive(false, notice, false,
                        payload.requestId()));
            }
            return;
        }
        if (!"open".equals(action) && !"refresh".equals(action)) {
            PacketDistributor.sendToPlayer(player, MinigameMatchOverviewPayload.inactive(false,
                    "Unknown match-overview action.", true, payload.requestId()));
            return;
        }
        sendMatchOverview(player, "", false, payload.requestId(), true);
    }

    private void sendMatchOverview(ServerPlayer player, String notice, boolean error, long requestId,
                                   boolean openDashboardFallback) {
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            arena = definition == null || match == null ? null : arena(definition, match.arenaId);
        }
        if (match == null || definition == null) {
            PacketDistributor.sendToPlayer(player, MinigameMatchOverviewPayload.inactive(openDashboardFallback,
                    notice, error, requestId));
            return;
        }

        ArrayList<MinigameMatchOverviewPayload.TeamRow> teamRows = new ArrayList<>();
        ArrayList<MinigameMatchOverviewPayload.PlayerRow> playerRows = new ArrayList<>();
        ArrayList<String> objectiveLines = new ArrayList<>();
        ArrayList<String> statusLines = new ArrayList<>();
        ArrayList<String> ruleLines = new ArrayList<>();
        int ownTeam;
        String ownRole;
        long ownScore;
        boolean spectator;
        boolean overtime;
        String phase;
        long remaining;

        synchronized (this) {
            ownTeam = match.team(player.getUUID());
            ownRole = match.role(player.getUUID()).id();
            ownScore = match.score(player.getUUID());
            spectator = player.isSpectator() || match.eliminated.contains(player.getUUID())
                    || match.pendingRespawns.containsKey(player.getUUID());
            overtime = match.overtime;
            phase = match.state.name().toLowerCase(Locale.ROOT);
            remaining = matchRemainingSeconds(match, definition);

            for (int team = 1; team <= Math.max(1, definition.teamCount); team++) {
                int requestedTeam = team;
                int count = (int) match.teams.values().stream().filter(value -> value == requestedTeam).count();
                long teamScore = teamScore(match, definition, team);
                teamRows.add(new MinigameMatchOverviewPayload.TeamRow(team,
                        matchTeamName(match, definition, team), teamScore, count));
            }
            for (UUID playerId : match.joinOrder) {
                MinigamePerformance performance = match.performance(playerId);
                int team = match.team(playerId);
                playerRows.add(new MinigameMatchOverviewPayload.PlayerRow(playerId.toString(),
                        participantName(playerId), team, matchTeamName(match, definition, team),
                        match.role(playerId).id(), match.score(playerId), performance.kills,
                        performance.deaths, performance.assists, performance.captures,
                        performance.defenses, match.disconnected.containsKey(playerId),
                        match.eliminated.contains(playerId) || match.pendingRespawns.containsKey(playerId),
                        playerId.equals(player.getUUID())));
            }
            appendObjectiveOverview(match, definition, arena, objectiveLines);
            appendStatusOverview(match, definition, player.getUUID(), statusLines);
            appendRuleOverview(definition, ruleLines);
        }
        PacketDistributor.sendToPlayer(player, new MinigameMatchOverviewPayload(true, false,
                match.id.toString(), definition.id, definition.displayName, definition.gameType,
                definition.description, phase, remaining, ownTeam,
                matchTeamName(match, definition, ownTeam), ownRole, ownScore, spectator, overtime,
                teamRows, playerRows, objectiveLines, statusLines, ruleLines, notice, error, requestId));
    }

    private long matchRemainingSeconds(MinigameMatch match, MinigameDefinition definition) {
        long elapsed = Math.max(0L, (serverTicks - match.stateStartedTick) / 20L);
        return switch (match.state) {
            case COUNTDOWN -> Math.max(0L, definition.countdownSeconds - elapsed);
            case RUNNING -> match.overtime
                    ? Math.max(0L, (match.overtimeCompletesTick - serverTicks + 19L) / 20L)
                    : definition.matchDurationSeconds <= 0 ? -1L
                    : Math.max(0L, definition.matchDurationSeconds - elapsed);
            case POST_GAME -> {
                long duration = definition.postGameSeconds;
                if (definition.experience != null && definition.experience.postGameVotingEnabled) {
                    duration = Math.max(duration, definition.experience.postGameVoteSeconds);
                }
                yield Math.max(0L, duration - elapsed);
            }
            case RESETTING, FINISHED -> 0L;
        };
    }

    private static long teamScore(MinigameMatch match, MinigameDefinition definition, int team) {
        return switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> match.ctfScores.getOrDefault(team, 0);
            case DOMINATION -> match.dominationScores.getOrDefault(team, 0);
            default -> {
                long total = 0L;
                for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
                    if (entry.getValue() == team) total = saturatingAdd(total, match.score(entry.getKey()));
                }
                yield total;
            }
        };
    }

    private String matchTeamName(MinigameMatch match, MinigameDefinition definition, int team) {
        if (team <= 0) return "No team";
        return switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> definition.captureTheFlag.teamName(team);
            case DOMINATION -> definition.domination.teamName(team);
            case SPLEEF -> {
                String name = "Player " + team;
                for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
                    if (entry.getValue() == team) {
                        name = participantName(entry.getKey());
                        break;
                    }
                }
                yield name;
            }
            default -> "Team " + team;
        };
    }

    private void appendObjectiveOverview(MinigameMatch match, MinigameDefinition definition,
                                         MinigameArenaDefinition arena, List<String> lines) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        switch (type) {
            case CAPTURE_THE_FLAG -> {
                lines.add(definition.captureTheFlag.teamName(1) + ": " + match.ctfScores.getOrDefault(1, 0)
                        + "/" + definition.captureTheFlag.scoreToWin);
                lines.add(definition.captureTheFlag.teamName(2) + ": " + match.ctfScores.getOrDefault(2, 0)
                        + "/" + definition.captureTheFlag.scoreToWin);
                for (int flagTeam = 1; flagTeam <= 2; flagTeam++) {
                    UUID carrier = match.flagCarriers.get(flagTeam);
                    String state = carrier != null ? "carried by " + participantName(carrier)
                            : match.ctfDroppedFlags.containsKey(flagTeam) ? "dropped" : "at base";
                    lines.add(definition.captureTheFlag.teamName(flagTeam) + " flag: " + state);
                }
            }
            case DOMINATION -> {
                lines.add("First to " + definition.domination.scoreToWin + " points wins.");
                if (arena != null) {
                    for (MinigameControlPoint point : arena.controlPoints) {
                        int owner = match.dominationOwners.getOrDefault(point.id, 0);
                        MinigameMatch.DominationClaim claim = match.dominationClaims.get(point.id);
                        String state = claim != null ? "being claimed by "
                                + definition.domination.teamName(claim.claimingTeam())
                                : owner == 0 ? "Neutral" : definition.domination.teamName(owner);
                        lines.add(point.displayName + ": " + state);
                    }
                }
            }
            case SPLEEF -> {
                int alive = 0;
                for (UUID playerId : match.teams.keySet()) if (!match.eliminated.contains(playerId)) alive++;
                lines.add(alive + " player" + (alive == 1 ? "" : "s") + " still alive.");
                lines.add("Last player standing wins.");
                if (definition.spleef.standardProjectileEnabled) lines.add("Snowball projectile unlocks during the match.");
            }
            default -> {
                lines.add("Victory mode: " + definition.victoryMode.replace('_', ' '));
                lines.add("Highest team score wins.");
            }
        }
    }

    private void appendStatusOverview(MinigameMatch match, MinigameDefinition definition, UUID playerId,
                                      List<String> lines) {
        lines.add("Phase: " + match.state.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        lines.add("Players: " + match.teams.size() + "/" + definition.maxPlayers);
        if (!match.disconnected.isEmpty()) lines.add("Disconnected: " + match.disconnected.size());
        if (!match.activeBoosts.isEmpty()) lines.add("Active boosts: " + match.activeBoosts.size());
        MinigameMatch.PendingRespawn pending = match.pendingRespawns.get(playerId);
        if (pending != null) {
            lines.add("Respawn in " + Math.max(1L, (pending.completesTick - serverTicks + 19L) / 20L) + "s");
        }
        if (match.overtime) lines.add("Objective sudden-death overtime is active.");
        if (match.state == MinigameMatchState.POST_GAME && !match.finishReason.isBlank()) {
            lines.add(match.finishReason);
        }
    }

    private static void appendRuleOverview(MinigameDefinition definition, List<String> lines) {
        switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> {
                lines.add("Take the enemy flag and return it to your own base.");
                lines.add("First team to " + definition.captureTheFlag.scoreToWin + " captures wins.");
                lines.add("Defend your carrier and recover dropped flags.");
            }
            case DOMINATION -> {
                lines.add("Right-click and hold position to claim bases.");
                lines.add("Owned bases generate score for your team.");
                lines.add("First team to " + definition.domination.scoreToWin + " points wins.");
            }
            case SPLEEF -> {
                lines.add("Break the floor beneath opponents.");
                lines.add("Avoid falling below the elimination depth.");
                lines.add("Be the last surviving player.");
            }
            default -> {
                lines.add("Complete the configured objectives.");
                lines.add("Work with your team and earn the highest score.");
            }
        }
    }

    private void processRequest(ServerPlayer player, MinigameLobbyRequestPayload payload) {
        String action = payload.action().trim().toLowerCase(Locale.ROOT);
        // Opening is side-effect free and begins a fresh lobby request sequence. This
        // prevents a newly opened GUI from inheriting a higher sequence number from
        // a previously closed lobby while all mutating follow-up actions remain ordered.
        boolean adminView = "open_admin".equals(action) || "refresh_admin".equals(action)
                || "force_start".equals(action) || "finish".equals(action)
                || "release_arena".equals(action) || "delete".equals(action)
                || "diagnostics".equals(action) || "integrity_check".equals(action)
                || "clean_orphans".equals(action);
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
                case "profile" -> {
                    sendProfile(player, "", false, payload.requestId(),
                            payload.contextMinigameId().isBlank() ? payload.minigameId() : payload.contextMinigameId());
                    return;
                }
                case "select_title" -> {
                    updateProfileSelection(player, true, payload.minigameId());
                    sendProfile(player, "Selected title updated.", false, payload.requestId(), payload.contextMinigameId());
                    return;
                }
                case "select_victory" -> {
                    updateProfileSelection(player, false, payload.minigameId());
                    sendProfile(player, "Selected victory effect updated.", false, payload.requestId(), payload.contextMinigameId());
                    return;
                }
                case "open_admin", "refresh_admin" -> requireAdmin(player);
                case "join" -> notice = joinQueue(player, payload.minigameId(), payload.preferredRole());
                case "leave" -> notice = leave(player, true);
                case "vote_rematch", "vote_next", "vote_leave" -> notice = castPostGameVote(player, action);
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
                case "diagnostics" -> {
                    requireAdmin(player);
                    sendDiagnostics(player, "", false, false, payload.requestId());
                    return;
                }
                case "integrity_check" -> {
                    requireAdmin(player);
                    sendDiagnostics(player, "Integrity check completed.", false, true, payload.requestId());
                    return;
                }
                case "clean_orphans" -> {
                    requireAdmin(player);
                    int cleaned = cleanOrphanedRuntimeData();
                    sendDiagnostics(player, "Cleaned " + cleaned + " orphaned runtime reference(s).", false, true, payload.requestId());
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown minigame action.");
            }
        } catch (RuntimeException exception) {
            notice = exception.getMessage() == null ? "The minigame action failed safely." : exception.getMessage();
            error = true;
            if ("profile".equals(action) || "select_title".equals(action) || "select_victory".equals(action)) {
                sendProfile(player, notice, true, payload.requestId(), payload.contextMinigameId());
                return;
            }
        }
        sendLobby(player, notice, error, payload.requestId(), adminView);
    }

    private void updateProfileSelection(ServerPlayer player, boolean title, String value) {
        if (player == null) throw new IllegalArgumentException("Player is unavailable.");
        synchronized (this) {
            MinigameProgressionData.PlayerProgress progress = progression.getOrCreate(
                    player.getUUID(), player.getName().getString());
            if (title) progress.selectTitle(value);
            else progress.selectVictoryEffect(value);
            progress.updatedAtEpochMilli = System.currentTimeMillis();
            progress.normalize(player.getUUID());
            saveExperienceData();
        }
    }

    /** Shared progression level used by the global title catalogue. */
    public synchronized int progressionLevel(UUID playerId) {
        if (playerId == null) return 1;
        MinigameProgressionData.PlayerProgress value = progression.players.get(playerId.toString());
        if (value == null) return 1;
        value.normalize(playerId);
        return value.level;
    }

    /** Lifetime minigame wins used by the global title catalogue. */
    public synchronized long progressionWins(UUID playerId) {
        if (playerId == null) return 0L;
        MinigameProgressionData.PlayerProgress value = progression.players.get(playerId.toString());
        if (value == null) return 0L;
        value.normalize(playerId);
        return value.matchesWon;
    }

    /** Legacy selected minigame title, used once when migrating to the global profile system. */
    public synchronized String legacySelectedTitle(UUID playerId) {
        if (playerId == null) return "Rookie";
        MinigameProgressionData.PlayerProgress value = progression.players.get(playerId.toString());
        if (value == null) return "Rookie";
        value.normalize(playerId);
        return value.selectedTitle;
    }

    private void sendProfile(ServerPlayer player, String notice, boolean error, long requestId,
                             String requestedMinigameId) {
        MinigameProgressionData.PlayerProgress progress;
        ArrayList<MinigameProfilePayload.Rating> ratings = new ArrayList<>();
        MinigameDefinition challengeDefinition = null;
        synchronized (this) {
            progress = progression.getOrCreate(player.getUUID(), player.getName().getString());
            String requested = ContentId.normalize(requestedMinigameId);
            if (!requested.isBlank()) challengeDefinition = definitions.get(requested);
            if (challengeDefinition == null) {
                MinigameMatch activeMatch = matchFor(player.getUUID());
                if (activeMatch != null) challengeDefinition = definitions.get(activeMatch.minigameId);
            }
            if (challengeDefinition == null) {
                String queued = playerQueues.get(player.getUUID());
                if (queued != null) challengeDefinition = definitions.get(queued);
            }
            if (challengeDefinition == null) {
                for (MinigameDefinition definition : definitions.values()) {
                    if (definition != null && definition.enabled) {
                        challengeDefinition = definition;
                        break;
                    }
                }
            }
            if (challengeDefinition == null && !definitions.isEmpty()) {
                challengeDefinition = definitions.values().iterator().next();
            }
            for (MinigameDefinition definition : definitions.values()) {
                if (definition == null) continue;
                ratings.add(new MinigameProfilePayload.Rating(definition.id, definition.displayName,
                        progress.rating(definition.id)));
            }
        }
        ratings.sort(Comparator.comparing(MinigameProfilePayload.Rating::rating).reversed()
                .thenComparing(MinigameProfilePayload.Rating::displayName, String.CASE_INSENSITIVE_ORDER));
        MinigameExperienceRules challengeRules = challengeDefinition == null || challengeDefinition.experience == null
                ? new MinigameExperienceRules() : challengeDefinition.experience;
        challengeRules.normalize();
        PacketDistributor.sendToPlayer(player, new MinigameProfilePayload(progress.level,
                MinigameProgressionData.experienceIntoLevel(progress.experience),
                MinigameProgressionData.experienceForNextLevel(progress.level),
                progress.matchesPlayed, progress.matchesWon, progress.selectedTitle,
                progress.selectedVictoryEffect, progress.weeklyMatches, progress.weeklyWins,
                progress.weeklyContribution,
                challengeDefinition == null ? "" : challengeDefinition.id,
                challengeDefinition == null ? "" : challengeDefinition.displayName,
                challengeRules.weeklyChallengesEnabled,
                challengeRules.weeklyMatchesRequired, challengeRules.weeklyMatchesExperience,
                challengeRules.weeklyWinsRequired, challengeRules.weeklyWinsExperience,
                challengeRules.weeklyContributionRequired, challengeRules.weeklyContributionExperience,
                progress.badges(), progress.unlockedTitles(), progress.unlockedVictoryEffects(),
                ratings, notice, error, requestId));
    }

    private String castPostGameVote(ServerPlayer player, String action) {
        if (player == null) throw new IllegalArgumentException("Player is unavailable.");
        String vote = switch (action) {
            case "vote_rematch" -> "rematch";
            case "vote_next" -> "next";
            default -> "leave";
        };
        synchronized (this) {
            MinigameMatch match = matchFor(player.getUUID());
            MinigameDefinition definition = match == null ? null : definitions.get(match.minigameId);
            if (match == null || match.state != MinigameMatchState.POST_GAME) {
                throw new IllegalArgumentException("There is no post-game vote in progress.");
            }
            if (definition == null || definition.experience == null || !definition.experience.postGameVotingEnabled) {
                throw new IllegalArgumentException("Post-game voting is disabled.");
            }
            match.postGameVotes.put(player.getUUID(), vote);
        }
        return "Vote recorded: " + vote.replace('_', ' ') + ".";
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
        return joinQueue(player, rawId, MinigameRole.DPS.id());
    }

    public String joinQueue(ServerPlayer player, String rawId, String rawPreferredRole) {
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
            UUID playerId = player.getUUID();
            String concreteQueue = null;
            for (Map.Entry<String, LinkedHashMap<UUID, Long>> queueEntry : queues.entrySet()) {
                if (!queueEntry.getValue().containsKey(playerId)) continue;
                if (concreteQueue == null) concreteQueue = queueEntry.getKey();
                else queueEntry.getValue().remove(playerId);
            }
            if (concreteQueue != null) {
                playerQueues.put(playerId, concreteQueue);
                throw new IllegalArgumentException("You are already queued for a minigame.");
            }
            if (playerQueues.containsKey(playerId)) throw new IllegalArgumentException("You are already queued for a minigame.");
            if (playerMatches.containsKey(playerId)) throw new IllegalArgumentException("You are already in a minigame match.");
            MinigameDefinition definition = definitions.get(id);
            if (definition == null || !definition.enabled) throw new IllegalArgumentException("That minigame is unavailable.");
            MinigameRoleRules roleRules = roleRules(definition);
            MinigameRole preferredRole = roleRules != null && roleRules.enabled
                    ? MinigameRole.parse(rawPreferredRole) : MinigameRole.DPS;
            var condition = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(definition.prerequisites,
                    new ContentConditionContext(server, player, "minigames", definition.id,
                            Map.of("minigame", definition.id)));
            if (!condition.matched()) throw new IllegalArgumentException(condition.reason());
            if (tryCountdownJoin(player, definition, preferredRole)) {
                return "Joined the preparing match for " + definition.displayName + ".";
            }
            LinkedHashMap<UUID, Long> queue = queues.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
            if (queue.size() >= MAX_QUEUE_SIZE) throw new IllegalArgumentException("This minigame queue is full.");
            queue.put(player.getUUID(), System.currentTimeMillis());
            playerQueues.put(player.getUUID(), id);
            playerRolePreferences.put(player.getUUID(), preferredRole);
        }
        publish(player, ContentEventTypes.MINIGAME_QUEUE_JOINED, id, 1L, Map.of());
        MinigameDefinition queuedDefinition;
        synchronized (this) { queuedDefinition = definitions.get(id); }
        MinigameRoleRules queuedRoleRules = roleRules(queuedDefinition);
        MinigameRole preferred = queuedRoleRules != null && queuedRoleRules.enabled
                ? MinigameRole.parse(rawPreferredRole) : MinigameRole.DPS;
        return "Joined the queue for " + displayName(id)
                + (queuedRoleRules != null && queuedRoleRules.enabled
                ? " with preferred role " + preferred.label() + "." : ".");
    }

    /**
     * Countdown is an open preparation stage rather than a closed match. New players
     * may fill remaining slots until RUNNING begins; role composition is recomputed
     * before their recovery-safe lobby teleport.
     */
    private boolean tryCountdownJoin(ServerPlayer player, MinigameDefinition definition,
                                     MinigameRole preferredRole) {
        if (player == null || player.isDeadOrDying()) return false;
        MinigameMatch target = null;
        MinigameArenaDefinition arena = null;
        for (MinigameMatch candidate : matches.values()) {
            if (!candidate.minigameId.equals(definition.id)
                    || candidate.state != MinigameMatchState.COUNTDOWN
                    || candidate.teams.size() >= definition.maxPlayers) continue;
            MinigameArenaDefinition candidateArena = arena(definition, candidate.arenaId);
            if (candidateArena == null || !locationsResolvable(definition, candidateArena)) continue;
            target = candidate;
            arena = candidateArena;
            break;
        }
        if (target == null || arena == null) return false;

        UUID playerId = player.getUUID();
        int selectedTeam = leastPopulatedTeam(target, definition.teamCount);
        MinigameRole normalizedPreference = preferredRole == null ? MinigameRole.DPS : preferredRole;
        player.closeContainer();
        MinigamePlayerState state = MinigamePlayerState.capture(player);
        MinigameLocation returnLocation = MinigameLocation.of(player);
        Map<UUID, MinigameRole> previousRoles = new LinkedHashMap<>(target.roles);

        target.teams.put(playerId, selectedTeam);
        target.preferredRoles.put(playerId, normalizedPreference);
        target.scores.put(playerId, 0L);
        target.performance(playerId);
        target.lastActivityTicks.put(playerId, serverTicks);
        target.lastActivityLocations.put(playerId, MinigameLocation.of(player));
        target.joinOrder.add(playerId);
        target.playerStates.put(playerId, state);
        target.returnLocations.put(playerId, returnLocation);
        target.roles.clear();
        if (!assignMatchRoles(target, definition, false)) {
            target.teams.remove(playerId);
            target.preferredRoles.remove(playerId);
            target.scores.remove(playerId);
            target.joinOrder.remove(playerId);
            target.playerStates.remove(playerId);
            target.returnLocations.remove(playerId);
            target.roles.clear();
            target.roles.putAll(previousRoles);
            return false;
        }

        recoveries.put(playerId, new MinigameRecoveryData.Entry(playerId, definition.id,
                target.id.toString(), returnLocation.copy(), state));
        playerMatches.put(playerId, target.id);
        if (!saveRecoveryDurably("countdown join " + target.id + " for " + playerId)) {
            target.teams.remove(playerId);
            target.preferredRoles.remove(playerId);
            target.roles.clear();
            target.roles.putAll(previousRoles);
            target.roleCooldowns.remove(playerId);
            target.scores.remove(playerId);
            target.joinOrder.remove(playerId);
            target.playerStates.remove(playerId);
            target.returnLocations.remove(playerId);
            target.eliminated.remove(playerId);
            playerMatches.remove(playerId);
            throw new IllegalArgumentException(
                    "The minigame could not safely store your recovery data. No inventory or gamemode changes were made.");
        }

        prepareCountdownPlayer(player, definition, arena, target);
        long elapsed = Math.max(0L, (serverTicks - target.stateStartedTick) / 20L);
        long remaining = Math.max(0L, definition.countdownSeconds - elapsed);
        player.sendSystemMessage(Component.literal("Joined " + definition.displayName
                + " during preparation on team " + selectedTeam + ". Starts in " + remaining + " seconds."));
        publish(player, ContentEventTypes.MINIGAME_STARTED, definition.id, 1L,
                Map.of("match", target.id.toString(), "arena", target.arenaId,
                        "team", Integer.toString(selectedTeam), "phase", "countdown_join"));
        return true;
    }

    private boolean tryLateJoin(ServerPlayer player, MinigameDefinition definition,
                                MinigameRole preferredRole) {
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
        MinigameRoleRules activeRoleRules = roleRules(definition);
        MinigameRole normalizedPreference = preferredRole == null ? MinigameRole.DPS : preferredRole;
        MinigameRole assignedRole = activeRoleRules != null && activeRoleRules.enabled
                ? selectLateJoinRole(target, activeRoleRules, selectedTeam, normalizedPreference)
                : MinigameRole.DPS;
        // A full role composition should not turn Join queue into an error. Simply
        // skip late joining and let the caller enqueue the player normally.
        if (assignedRole == null) return false;
        UUID playerId = player.getUUID();
        player.closeContainer();
        // Capture before mutating the live match. An unserializable inventory must
        // reject the late join without leaving a partial participant record behind.
        MinigamePlayerState state = MinigamePlayerState.capture(player);
        MinigameLocation returnLocation = MinigameLocation.of(player);
        target.teams.put(playerId, selectedTeam);
        target.preferredRoles.put(playerId, normalizedPreference);
        target.roles.put(playerId, assignedRole);
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
            target.preferredRoles.remove(playerId);
            target.roles.remove(playerId);
            target.roleCooldowns.remove(playerId);
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
        player.sendSystemMessage(Component.literal("Joined " + definition.displayName + " on team " + selectedTeam
                + (activeRoleRules != null && activeRoleRules.enabled
                ? " as " + assignedRole.label() + "." : ".")));
        publish(player, ContentEventTypes.MINIGAME_STARTED, definition.id, 1L,
                Map.of("match", target.id.toString(), "arena", target.arenaId,
                        "team", Integer.toString(selectedTeam), "phase", "late_join"));
        return true;
    }

    private static MinigameRole selectLateJoinRole(MinigameMatch match, MinigameRoleRules rules,
                                                    int team, MinigameRole preferred) {
        Map<MinigameRole, Integer> counts = new LinkedHashMap<>();
        for (MinigameRole role : MinigameRole.values()) counts.put(role, 0);
        for (Map.Entry<UUID, MinigameRole> entry : match.roles.entrySet()) {
            if (match.team(entry.getKey()) == team) {
                counts.put(entry.getValue(), counts.getOrDefault(entry.getValue(), 0) + 1);
            }
        }
        MinigameRole normalized = preferred == null ? MinigameRole.DPS : preferred;
        if (counts.get(normalized) < rules.maximum(normalized)) return normalized;
        MinigameRole selected = null;
        int smallestCount = Integer.MAX_VALUE;
        for (MinigameRole role : MinigameRole.values()) {
            int count = counts.get(role);
            if (count < rules.maximum(role) && count < smallestCount) {
                selected = role;
                smallestCount = count;
            }
        }
        return selected;
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
                playerRolePreferences.remove(playerId);
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
            match.preferredRoles.remove(playerId);
            match.roles.remove(playerId);
            match.roleCooldowns.remove(playerId);
            match.scores.remove(playerId);
            match.eliminated.remove(playerId);
            match.pendingRespawns.remove(playerId);
            match.boostRegenerationExpires.remove(playerId);
            match.boostRegenerationNextHeal.remove(playerId);
            match.boostArmorExpires.remove(playerId);
            match.boostOriginalArmorBase.remove(playerId);
            playerMatches.remove(playerId);
            runtimeBorderSyncStates.remove(playerId);
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
            boolean compositionAvailable = definition != null && match.teams.size() >= definition.minPlayers;
            if (compositionAvailable) {
                match.roles.clear();
                compositionAvailable = assignMatchRoles(match, definition, !match.rewardsEnabled);
            }
            if (!compositionAvailable) {
                cancelCountdown(match, definition, "Countdown cancelled because the required team composition is no longer available.");
            } else {
                announce(match, "A player left. Roles were rebalanced for the remaining "
                        + match.teams.size() + " player(s).");
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
        Map<UUID, MinigameRole> requeuePreferences = new LinkedHashMap<>(match.preferredRoles);
        match.rewardsDelivered = true;
        match.rewardsEnabled = false;
        MinigameArenaDefinition arena = definition == null ? null : arena(definition, match.arenaId);
        if (!cleanup(match, definition, arena)) {
            announceImportant(match, "Match return paused",
                    "SSU could not durably store player recovery data. No state was discarded.");
            return;
        }
        for (UUID playerId : requeue) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.isDeadOrDying()) continue;
            sendImportantMessage(player, "Match cancelled", reason);
            if (definition == null || !definition.enabled) continue;
            try {
                joinQueue(player, definition.id,
                        requeuePreferences.getOrDefault(playerId, MinigameRole.DPS).id());
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
            if (match.state != MinigameMatchState.COUNTDOWN && match.state != MinigameMatchState.RUNNING) continue;
            MinigameDefinition definition = definitions.get(match.minigameId);
            if (definition == null) continue;
            if (definition.lockInventory) tickLockedInventories(match);
            if (match.state != MinigameMatchState.RUNNING) continue;
            MinigameArenaDefinition arena = arena(definition, match.arenaId);
            if (arena == null) continue;
            MinigameGameType type = MinigameGameType.parse(definition.gameType);
            if (type == MinigameGameType.DOMINATION || type == MinigameGameType.CAPTURE_THE_FLAG) {
                enforceRespawnModeNeeds(match);
                tickPendingRespawns(match, definition, arena);
            }
            if (type == MinigameGameType.DOMINATION) tickDominationRealtime(match, definition, arena);
            else if (type == MinigameGameType.CAPTURE_THE_FLAG) tickCtfRealtime(match, definition, arena);
            if (type == MinigameGameType.DOMINATION || type == MinigameGameType.CAPTURE_THE_FLAG) {
                tickBoosts(match, definition, arena);
            }
        }
    }

    private void enforceRespawnModeNeeds(MinigameMatch match) {
        if (match == null) return;
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) setCombatNeeds(player);
        }
    }

    private void tickPendingRespawns(MinigameMatch match, MinigameDefinition definition,
                                     MinigameArenaDefinition arena) {
        if (match == null || definition == null || arena == null) return;
        for (Map.Entry<UUID, MinigameMatch.PendingRespawn> entry
                : List.copyOf(match.pendingRespawns.entrySet())) {
            UUID playerId = entry.getKey();
            MinigameMatch.PendingRespawn pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;

            setCombatNeeds(player);
            if (serverTicks < pending.completesTick) {
                if (!player.isSpectator()) player.setGameMode(GameType.SPECTATOR);
                long seconds = Math.max(1L, (pending.completesTick - serverTicks + 19L) / 20L);
                if (pending.lastDisplayedSecond != seconds) {
                    pending.lastDisplayedSecond = seconds;
                    showRespawnCountdown(player, seconds);
                }
                continue;
            }

            match.pendingRespawns.remove(playerId);
            clearRespawnTitle(player);
            player.stopUsingItem();
            player.setGameMode(GameType.SURVIVAL);
            player.removeAllEffects();
            player.setAbsorptionAmount(0.0F);
            player.setRemainingFireTicks(0);
            player.setHealth(player.getMaxHealth());
            setCombatNeeds(player);
            teleport(player, pending.destination);
            player.sendSystemMessage(Component.literal("Respawned."), true);
        }
    }

    private static void setCombatNeeds(ServerPlayer player) {
        if (player == null) return;
        player.getFoodData().setFoodLevel(COMBAT_FOOD_LEVEL);
        player.getFoodData().setSaturation(0.0F);
    }

    private static void showRespawnCountdown(ServerPlayer player, long seconds) {
        if (player == null) return;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 0));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal(Long.toString(Math.max(1L, seconds)))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Respawning")));
    }

    private void showPreparationCountdown(MinigameMatch match, long seconds) {
        if (match == null || seconds < 1L || seconds > 10L) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:block.note_block.hat")).orElse(null);
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 18, 2));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(Long.toString(seconds))));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Match starts in")));
            if (sound != null) {
                float pitch = seconds <= 3L ? 1.35F : seconds <= 5L ? 1.15F : 1.0F;
                player.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.MASTER,
                        player.getX(), player.getY(), player.getZ(), 0.9F, pitch,
                        serverTicks ^ player.getUUID().getLeastSignificantBits() ^ seconds));
            }
        }
    }

    private void showMatchStart(MinigameMatch match, MinigameDefinition definition) {
        if (match == null) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:entity.player.levelup")).orElse(null);
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 5));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("GO!")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                    definition == null ? "" : definition.displayName)));
            if (sound != null) {
                player.connection.send(new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.MASTER,
                        player.getX(), player.getY(), player.getZ(), 0.8F, 1.2F,
                        serverTicks ^ player.getUUID().getMostSignificantBits()));
            }
        }
    }

    private static void clearRespawnTitle(ServerPlayer player) {
        if (player == null) return;
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
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
            if (player.isUsingItem()) {
                interruptDominationCast(match, playerId, "Capture interrupted because you used an item.");
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
            match.dominationClaimers.put(point.id, playerId);
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
            UUID claimerId = match.dominationClaimers.remove(entry.getKey());
            match.dominationOwners.put(entry.getKey(), claim.claimingTeam());
            if (claimerId != null) recordObjectiveCapture(match, definition, claimerId,
                    point == null ? entry.getKey() : point.displayName);
            if (point != null) {
                placeDominationMarker(definition, point, claim.claimingTeam());
                announce(match, definition.domination.teamName(claim.claimingTeam()) + " captured "
                        + point.displayName + "!");
                playObjectiveCaptureResultSounds(match, claim.claimingTeam());
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
            if (player.isUsingItem()) {
                interruptCtfCast(match, playerId, "Flag capture interrupted because you used an item.");
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
            playerRolePreferences.remove(entry.getKey());
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
        Map<UUID, Integer> balancedTeams = assignBalancedTeams(definition, candidates);
        for (ServerPlayer player : candidates) {
            UUID playerId = player.getUUID();
            match.teams.put(playerId, balancedTeams.getOrDefault(playerId, 1));
            match.preferredRoles.put(playerId,
                    playerRolePreferences.getOrDefault(playerId, MinigameRole.DPS));
            match.scores.put(playerId, 0L);
            match.performance(playerId);
            match.lastActivityTicks.put(playerId, serverTicks);
            match.lastActivityLocations.put(playerId, MinigameLocation.of(player));
            match.joinOrder.add(playerId);
            MinigamePlayerState state = capturedStates.get(playerId);
            MinigameLocation returnLocation = capturedReturns.get(playerId);
            match.playerStates.put(playerId, state);
            match.returnLocations.put(playerId, returnLocation);
            recoveries.put(playerId, new MinigameRecoveryData.Entry(playerId, definition.id,
                    match.id.toString(), returnLocation.copy(), state));
            playerMatches.put(playerId, match.id);
        }
        if (!assignMatchRoles(match, definition, forced)) {
            for (ServerPlayer player : candidates) {
                recoveries.remove(player.getUUID());
                playerMatches.remove(player.getUUID());
            }
            MinigameSetupToolService.restorePhysicalSetupMarkers(server, definition, arena);
            return null;
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
            playerRolePreferences.remove(player.getUUID());
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

    private Map<UUID, Integer> assignBalancedTeams(MinigameDefinition definition, List<ServerPlayer> candidates) {
        LinkedHashMap<UUID, Integer> result = new LinkedHashMap<>();
        if (candidates == null || candidates.isEmpty()) return result;
        int teamCount = Math.max(1, definition.teamCount);
        MinigameExperienceRules rules = definition.experience == null
                ? new MinigameExperienceRules() : definition.experience;
        ArrayList<ServerPlayer> ordered = new ArrayList<>(candidates);
        if (rules.performanceBalancingEnabled && teamCount > 1) {
            ordered.sort(Comparator.comparingInt((ServerPlayer player) ->
                    progression.getOrCreate(player.getUUID(), player.getName().getString()).rating(definition.id)).reversed()
                    .thenComparing(player -> player.getUUID().toString()));
        }
        int[] counts = new int[teamCount + 1];
        double[] strength = new double[teamCount + 1];
        for (ServerPlayer player : ordered) {
            int rating = progression.getOrCreate(player.getUUID(), player.getName().getString()).rating(definition.id);
            int selected = 1;
            double best = Double.POSITIVE_INFINITY;
            for (int team = 1; team <= teamCount; team++) {
                double countPressure = counts[team] * 1_000.0D;
                double ratingPressure = strength[team] * Math.max(0.0D, Math.min(1.0D, rules.performanceBalanceWeight));
                double score = countPressure + ratingPressure;
                if (score < best) { best = score; selected = team; }
            }
            result.put(player.getUUID(), selected);
            counts[selected]++;
            strength[selected] += rating;
        }
        return result;
    }

    private boolean assignMatchRoles(MinigameMatch match, MinigameDefinition definition, boolean forced) {
        MinigameRoleRules rules = roleRules(definition);
        if (rules == null || !rules.enabled) {
            for (UUID playerId : match.joinOrder) match.roles.put(playerId, MinigameRole.DPS);
            return true;
        }
        for (int team = 1; team <= definition.teamCount; team++) {
            ArrayList<UUID> players = new ArrayList<>();
            for (UUID playerId : match.joinOrder) if (match.team(playerId) == team) players.add(playerId);
            if (!assignTeamRoles(match, players, rules, forced)) return false;
        }
        return true;
    }

    private static boolean assignTeamRoles(MinigameMatch match, List<UUID> players,
                                           MinigameRoleRules rules, boolean ignoreMinimums) {
        LinkedHashSet<UUID> unassigned = new LinkedHashSet<>(players);
        Map<MinigameRole, Integer> counts = new LinkedHashMap<>();
        for (MinigameRole role : MinigameRole.values()) counts.put(role, 0);

        if (!ignoreMinimums) {
            for (MinigameRole role : MinigameRole.values()) {
                int required = rules.minimum(role);
                for (int slot = 0; slot < required; slot++) {
                    UUID selected = null;
                    for (UUID playerId : unassigned) {
                        if (match.preferredRoles.getOrDefault(playerId, MinigameRole.DPS) == role) {
                            selected = playerId;
                            break;
                        }
                    }
                    if (selected == null && !unassigned.isEmpty()) selected = unassigned.iterator().next();
                    if (selected == null) return false;
                    match.roles.put(selected, role);
                    counts.put(role, counts.get(role) + 1);
                    unassigned.remove(selected);
                }
            }
        }

        for (UUID playerId : List.copyOf(unassigned)) {
            MinigameRole preferred = match.preferredRoles.getOrDefault(playerId, MinigameRole.DPS);
            MinigameRole selected = counts.get(preferred) < rules.maximum(preferred) ? preferred : null;
            if (selected == null) {
                int bestCount = Integer.MAX_VALUE;
                for (MinigameRole role : MinigameRole.values()) {
                    int count = counts.get(role);
                    if (count < rules.maximum(role) && count < bestCount) {
                        selected = role;
                        bestCount = count;
                    }
                }
            }
            if (selected == null) return false;
            match.roles.put(playerId, selected);
            counts.put(selected, counts.get(selected) + 1);
        }
        return true;
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
        tickActivityAndDisconnects(match, definition, arena);
        if (!matches.containsKey(match.id)) return;
        switch (match.state) {
            case COUNTDOWN -> {
                long remaining = Math.max(0L, definition.countdownSeconds - elapsedSeconds);
                if (remaining != match.lastAnnouncementSecond) {
                    match.lastAnnouncementSecond = remaining;
                    if (remaining >= 1L && remaining <= 10L) {
                        showPreparationCountdown(match, remaining);
                    } else if (remaining > 10L && remaining % 30L == 0L) {
                        announce(match, "Match starts in " + remaining + "…");
                    }
                }
                if (elapsedSeconds >= definition.countdownSeconds) {
                    match.state = MinigameMatchState.RUNNING;
                    match.stateStartedTick = serverTicks;
                    match.lastAnnouncementSecond = Long.MIN_VALUE;
                    showMatchStart(match, definition);
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
                    if (startingType == MinigameGameType.CAPTURE_THE_FLAG || startingType == MinigameGameType.DOMINATION) {
                        initializeBoosts(match, definition, arena);
                    }
                    announce(match, definition.displayName + " has started!");
                    publishMatch(match, ContentEventTypes.MINIGAME_STARTED, "started");
                }
            }
            case RUNNING -> {
                MinigameGameType runningType = MinigameGameType.parse(definition.gameType);
                tickObjectiveTime(match, definition, runningType);
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
                if (match.overtime) {
                    if (serverTicks >= match.overtimeCompletesTick) {
                        match.winningTeams = determineWinners(match);
                        finish(match, "Overtime expired.");
                    }
                } else if (definition.matchDurationSeconds > 0 && elapsedSeconds >= definition.matchDurationSeconds) {
                    Set<Integer> timedWinners = determineWinners(match);
                    if (definition.experience != null && definition.experience.overtimeEnabled
                            && timedWinners.isEmpty() && (runningType == MinigameGameType.CAPTURE_THE_FLAG
                            || runningType == MinigameGameType.DOMINATION)) {
                        match.overtime = true;
                        match.overtimeCompletesTick = safeAdd(serverTicks,
                                Math.max(5L, definition.experience.overtimeSeconds) * 20L);
                        announce(match, "Overtime! The next objective capture wins. Maximum "
                                + definition.experience.overtimeSeconds + " seconds.");
                    } else {
                        match.winningTeams = timedWinners;
                        finish(match, "Time limit reached.");
                    }
                }
            }
            case POST_GAME -> {
                long postGameDuration = definition.postGameSeconds;
                if (definition.experience != null && definition.experience.postGameVotingEnabled) {
                    postGameDuration = Math.max(postGameDuration, definition.experience.postGameVoteSeconds);
                }
                if (elapsedSeconds >= postGameDuration) {
                    match.postGameDecision = resolvePostGameDecision(match, definition);
                    cleanup(match, definition, arena);
                }
            }
            case RESETTING, FINISHED -> { }
        }
    }

    public void recordActivity(ServerPlayer player) {
        if (player == null) return;
        synchronized (this) {
            MinigameMatch match = matchFor(player.getUUID());
            if (match == null || match.state == MinigameMatchState.POST_GAME
                    || match.state == MinigameMatchState.RESETTING || match.state == MinigameMatchState.FINISHED) return;
            match.lastActivityTicks.put(player.getUUID(), serverTicks);
            match.lastActivityLocations.put(player.getUUID(), MinigameLocation.of(player));
            match.afkWarned.remove(player.getUUID());
        }
    }

    private void tickActivityAndDisconnects(MinigameMatch match, MinigameDefinition definition,
                                            MinigameArenaDefinition arena) {
        MinigameExperienceRules rules = definition.experience;
        for (UUID playerId : List.copyOf(match.teams.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                MinigameLocation previous = match.lastActivityLocations.get(playerId);
                MinigameLocation current = MinigameLocation.of(player);
                if (previous == null || !previous.dimension.equals(current.dimension)
                        || distanceSquared(previous, current) > 0.04D) {
                    match.lastActivityTicks.put(playerId, serverTicks);
                    match.lastActivityLocations.put(playerId, current);
                    match.afkWarned.remove(playerId);
                }
            }
        }
        for (Map.Entry<UUID, MinigameMatch.DisconnectedParticipant> entry
                : List.copyOf(match.disconnected.entrySet())) {
            if (serverTicks < entry.getValue().expiresTick()) continue;
            expireDisconnectedParticipant(match, definition, arena, entry.getKey());
        }
        if (rules == null || !rules.afkDetectionEnabled || match.state != MinigameMatchState.RUNNING) return;
        long timeoutTicks = Math.max(30L, rules.afkTimeoutSeconds) * 20L;
        long warningTicks = Math.max(5L, rules.afkWarningSeconds) * 20L;
        for (UUID playerId : List.copyOf(match.teams.keySet())) {
            if (match.disconnected.containsKey(playerId) || match.pendingRespawns.containsKey(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            long inactive = Math.max(0L, serverTicks - match.lastActivityTicks.getOrDefault(playerId, match.stateStartedTick));
            if (inactive >= timeoutTicks) {
                player.sendSystemMessage(Component.literal("You were removed from the minigame for inactivity."));
                withdrawFromMatch(playerId, "Player removed for inactivity.");
                publish(player, ContentEventTypes.MINIGAME_QUEUE_LEFT, definition.id, 1L,
                        Map.of("reason", "afk", "match", match.id.toString()));
            } else if (inactive >= timeoutTicks - warningTicks && match.afkWarned.add(playerId)) {
                long seconds = Math.max(1L, (timeoutTicks - inactive + 19L) / 20L);
                player.sendSystemMessage(Component.literal("Move or act within " + seconds
                        + " seconds or you will be removed for inactivity."), true);
            }
        }
    }

    private void expireDisconnectedParticipant(MinigameMatch match, MinigameDefinition definition,
                                               MinigameArenaDefinition arena, UUID playerId) {
        match.disconnected.remove(playerId);
        if (MinigameGameType.parse(definition.gameType) == MinigameGameType.CAPTURE_THE_FLAG && arena != null) {
            returnFlagsCarriedBy(match, definition, arena, playerId, "A disconnected carrier's flag returned to base.");
        }
        synchronized (this) {
            match.joinOrder.remove(playerId);
            match.teams.remove(playerId);
            match.preferredRoles.remove(playerId);
            match.roles.remove(playerId);
            match.roleCooldowns.remove(playerId);
            match.pendingRespawns.remove(playerId);
            match.eliminated.remove(playerId);
            playerMatches.remove(playerId);
            runtimeBorderSyncStates.remove(playerId);
        }
        announce(match, "A disconnected participant's rejoin time expired.");
        if (match.state == MinigameMatchState.COUNTDOWN && match.teams.size() < definition.minPlayers) {
            cancelCountdown(match, definition, "Countdown cancelled because too few players remain.");
        } else if (match.state == MinigameMatchState.RUNNING && activeTeams(match).size() <= 1
                && definition.teamCount > 1) {
            Set<Integer> alive = activeTeams(match);
            match.winningTeams = alive.isEmpty() ? Set.of() : alive;
            finish(match, "The opposing team ran out of connected participants.");
        }
    }

    private static double distanceSquared(MinigameLocation first, MinigameLocation second) {
        double x = first.x - second.x, y = first.y - second.y, z = first.z - second.z;
        return x * x + y * y + z * z;
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
        MinigameMatch activeMatch;
        MinigameDefinition activeDefinition;
        MinigameArenaDefinition activeArena;
        synchronized (this) {
            activeMatch = matchFor(player.getUUID());
            activeDefinition = activeMatch == null ? null : definitions.get(activeMatch.minigameId);
            activeArena = activeDefinition == null || activeMatch == null ? null : arena(activeDefinition, activeMatch.arenaId);
            recovery = activeMatch == null ? recoveries.get(player.getUUID()) : null;
        }
        if (activeMatch != null && activeDefinition != null && activeArena != null) {
            MinigameMatch.DisconnectedParticipant disconnected = activeMatch.disconnected.get(player.getUUID());
            boolean withinGrace = disconnected != null && serverTicks <= disconnected.expiresTick();
            if (withinGrace && activeDefinition.experience != null && activeDefinition.experience.rejoinEnabled) {
                synchronized (this) {
                    activeMatch.disconnected.remove(player.getUUID());
                    activeMatch.lastActivityTicks.put(player.getUUID(), serverTicks);
                    activeMatch.lastActivityLocations.put(player.getUUID(), MinigameLocation.of(player));
                }
                if (activeMatch.state == MinigameMatchState.COUNTDOWN) {
                    prepareCountdownPlayer(player, activeDefinition, activeArena, activeMatch);
                } else if (activeMatch.pendingRespawns.containsKey(player.getUUID())) {
                    player.setGameMode(GameType.SPECTATOR);
                    teleport(player, activeArena.spectator);
                } else if (activeMatch.state == MinigameMatchState.RUNNING) {
                    beginParticipant(player, activeDefinition, activeArena, activeMatch);
                } else {
                    player.setGameMode(GameType.SPECTATOR);
                    teleport(player, activeArena.spectator);
                }
                player.sendSystemMessage(Component.literal("Rejoined " + activeDefinition.displayName
                        + " within the reconnect grace period."));
                syncRuntimeBorders(player);
                return;
            }
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
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            arena = definition == null || match == null ? null : arena(definition, match.arenaId);
        }
        boolean grace = match != null && definition != null && definition.experience != null
                && definition.experience.rejoinEnabled
                && (match.state == MinigameMatchState.COUNTDOWN || match.state == MinigameMatchState.RUNNING);
        if (grace) {
            long expires = serverTicks + Math.max(10L, definition.experience.rejoinGraceSeconds) * 20L;
            synchronized (this) {
                match.disconnected.put(player.getUUID(), new MinigameMatch.DisconnectedParticipant(serverTicks, expires));
                match.afkWarned.remove(player.getUUID());
            }
            MinigameGameType type = MinigameGameType.parse(definition.gameType);
            if (type == MinigameGameType.CAPTURE_THE_FLAG && arena != null) {
                interruptCtfCast(match, player.getUUID(), "Flag capture interrupted by disconnect.");
                returnFlagsCarriedBy(match, definition, arena, player.getUUID(),
                        "A disconnected carrier's flag returned to base.");
            } else if (type == MinigameGameType.DOMINATION) {
                interruptDominationCast(match, player.getUUID(), "Objective capture interrupted by disconnect.");
            }
            announce(match, player.getName().getString() + " disconnected and may rejoin for "
                    + definition.experience.rejoinGraceSeconds + " seconds.");
        } else {
            leave(player, false);
        }
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
        if (finishingType == MinigameGameType.CAPTURE_THE_FLAG || finishingType == MinigameGameType.DOMINATION) {
            clearBoosts(match);
        }
        celebrateWinners(match, definition);
        String winners = winnerAnnouncement(match, definition);
        announceImportant(match, winners, match.finishReason.isBlank()
                ? definition.displayName + " has ended." : match.finishReason);
        prepareMatchExperiencePreview(match, definition);
        sendResults(match, definition, definition.experience == null ? 0 : definition.experience.postGameVoteSeconds);
    }

    /**
     * Computes the result-screen XP and projected level without changing durable progression.
     * Definitive progression is settled only after configured rewards have committed.
     */
    private void prepareMatchExperiencePreview(MinigameMatch match, MinigameDefinition definition) {
        if (match == null || definition == null) return;
        match.experienceGained.clear();
        match.priorLevels.clear();
        match.resultingLevels.clear();
        MinigameExperienceRules rules = definition.experience == null
                ? new MinigameExperienceRules() : definition.experience;
        for (UUID playerId : match.joinOrder) {
            String name = participantName(playerId);
            MinigameProgressionData.PlayerProgress progress = progressionPreview(playerId, name);
            boolean won = match.winningTeams.contains(match.team(playerId));
            int experienceGained = calculateExperienceGain(match, rules, progress, won,
                    match.performance(playerId), false);
            long projectedExperience = saturatingAdd(progress.experience, experienceGained);
            match.experienceGained.put(playerId, experienceGained);
            match.priorLevels.put(playerId, progress.level);
            match.resultingLevels.put(playerId, MinigameProgressionData.levelForExperience(projectedExperience));
        }
    }

    private MinigameProgressionData.PlayerProgress progressionPreview(UUID playerId, String name) {
        MinigameProgressionData.PlayerProgress stored = progression.players.get(playerId.toString());
        if (stored != null) {
            stored.normalize(playerId);
            return stored;
        }
        MinigameProgressionData.PlayerProgress preview = new MinigameProgressionData.PlayerProgress();
        preview.uuid = playerId.toString();
        preview.lastKnownName = name == null || name.isBlank() ? shortPlayerName(playerId) : name;
        preview.normalize(playerId);
        return preview;
    }

    private int calculateExperienceGain(MinigameMatch match, MinigameExperienceRules rules,
                                        MinigameProgressionData.PlayerProgress progress,
                                        boolean won, MinigamePerformance performance,
                                        boolean commitWeekly) {
        if (match == null || rules == null || progress == null || performance == null
                || !rules.progressionEnabled || !match.rewardsEnabled) return 0;
        long objective = Math.min(rules.objectiveExperienceCap,
                Math.max(0L, performance.contributionScore() / 100L));
        int weeklyBonus = commitWeekly
                ? progress.recordWeekly(won, performance.contributionScore(), rules)
                : progress.previewWeeklyBonus(won, performance.contributionScore(), rules);
        long calculated = (long) rules.participationExperience
                + (won ? rules.winnerExperience : 0L) + objective + weeklyBonus;
        return (int) Math.min(1_000_000L, Math.max(0L, calculated));
    }

    /**
     * Applies progression/history only after rewards are committed, then flushes and verifies
     * both files before any participant is returned. The persisted settlement receipt makes
     * retries idempotent even when cleanup is paused by storage or mail failures.
     */
    private boolean finalizeMatchExperienceAfterRewards(MinigameMatch match, MinigameDefinition definition) {
        if (match == null || definition == null || match.state != MinigameMatchState.POST_GAME) return true;
        if (!match.experienceSettlementDurable) {
            if (!progression.isSettled(match.id)) {
                applyExperienceSettlementState(match, definition);
            } else if (match.experienceGained.isEmpty()) {
                hydrateExperienceFromHistory(match);
            }
            if (!saveExperienceDataDurably("minigame settlement " + match.id)) return false;
            match.experienceSettlementDurable = true;
        }
        if (!match.experienceSideEffectsPublished) {
            if (!deliverAllMatchSummaryMails(match, definition)) return false;
            // Mark before non-transactional statistics/events so an unexpected handler failure
            // can never make a cleanup retry increment counters twice in this server session.
            match.experienceSideEffectsPublished = true;
            publishExperienceSideEffects(match, definition);
        }
        return true;
    }

    private void applyExperienceSettlementState(MinigameMatch match, MinigameDefinition definition) {
        MinigameMatchHistory.Entry historyEntry = new MinigameMatchHistory.Entry();
        historyEntry.matchId = match.id.toString();
        historyEntry.minigameId = definition.id;
        historyEntry.displayName = definition.displayName;
        historyEntry.arenaId = match.arenaId;
        historyEntry.startedAtEpochMilli = match.startedEpochMilli;
        historyEntry.finishedAtEpochMilli = System.currentTimeMillis();
        historyEntry.finishReason = match.finishReason;
        historyEntry.winningTeams = new ArrayList<>(match.winningTeams);
        MinigameExperienceRules rules = definition.experience == null
                ? new MinigameExperienceRules() : definition.experience;
        match.experienceGained.clear();
        match.priorLevels.clear();
        match.resultingLevels.clear();
        for (UUID playerId : match.joinOrder) {
            int team = match.team(playerId);
            boolean won = match.winningTeams.contains(team);
            String name = participantName(playerId);
            MinigamePerformance performance = match.performance(playerId);
            MinigameProgressionData.PlayerProgress progress = progression.getOrCreate(playerId, name);
            int priorLevel = progress.level;
            int experienceGained = calculateExperienceGain(match, rules, progress, won, performance, true);
            if (rules.progressionEnabled && match.rewardsEnabled) {
                progress.experience = saturatingAdd(progress.experience, experienceGained);
                progress.gameExperience.put(definition.id, saturatingAdd(
                        progress.gameExperience.getOrDefault(definition.id, 0L), experienceGained));
                progress.matchesPlayed = saturatingAdd(progress.matchesPlayed, 1L);
                if (won) progress.matchesWon = saturatingAdd(progress.matchesWon, 1L);
                int rating = progress.rating(definition.id);
                int adjustment = match.winningTeams.isEmpty() ? 0 : won ? 18 : -12;
                progress.ratings.put(definition.id, Math.max(100, Math.min(4_000, rating + adjustment)));
                progress.updatedAtEpochMilli = System.currentTimeMillis();
            }
            progress.normalize(playerId);
            match.experienceGained.put(playerId, experienceGained);
            match.priorLevels.put(playerId, priorLevel);
            match.resultingLevels.put(playerId, progress.level);

            MinigameMatchHistory.PlayerEntry playerEntry = new MinigameMatchHistory.PlayerEntry();
            playerEntry.name = name;
            playerEntry.team = team;
            playerEntry.role = match.role(playerId).id();
            playerEntry.won = won;
            playerEntry.score = match.score(playerId);
            playerEntry.performance = performance.copy();
            playerEntry.experienceGained = experienceGained;
            playerEntry.resultingLevel = progress.level;
            historyEntry.players.put(playerId.toString(), playerEntry);
        }
        historyEntry.normalize();
        history.matches.removeIf(entry -> entry != null && match.id.toString().equals(entry.matchId));
        history.matches.add(historyEntry);
        history.normalize();
        progression.rememberSettlement(match.id, definition.id, match.joinOrder.size());
        progression.normalize();
    }

    private void hydrateExperienceFromHistory(MinigameMatch match) {
        for (MinigameMatchHistory.Entry entry : history.matches) {
            if (entry == null || !match.id.toString().equals(entry.matchId)) continue;
            for (Map.Entry<String, MinigameMatchHistory.PlayerEntry> playerEntry : entry.players.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(playerEntry.getKey());
                    MinigameMatchHistory.PlayerEntry value = playerEntry.getValue();
                    if (value == null) continue;
                    match.experienceGained.put(playerId, value.experienceGained);
                    match.resultingLevels.put(playerId, value.resultingLevel);
                } catch (RuntimeException ignored) {
                }
            }
            return;
        }
    }

    private boolean deliverAllMatchSummaryMails(MinigameMatch match, MinigameDefinition definition) {
        MinigameExperienceRules rules = definition.experience == null
                ? new MinigameExperienceRules() : definition.experience;
        if (!rules.matchSummaryMailEnabled || !match.rewardsEnabled) return true;
        for (UUID playerId : match.joinOrder) {
            String name = participantName(playerId);
            if (!deliverMatchSummaryMail(match, definition, playerId, name,
                    match.winningTeams.contains(match.team(playerId)),
                    match.experienceGained.getOrDefault(playerId, 0),
                    match.resultingLevels.getOrDefault(playerId, 1), match.performance(playerId))) {
                return false;
            }
        }
        return true;
    }

    private void publishExperienceSideEffects(MinigameMatch match, MinigameDefinition definition) {
        if (!match.rewardsEnabled) return;
        for (UUID playerId : match.joinOrder) {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online == null) continue;
            boolean won = match.winningTeams.contains(match.team(playerId));
            try {
                SimpleServerUtilities.STATISTICS.increment(
                        online, StatisticEventType.MINIGAME_COMPLETED, definition.id, 1L);
                if (won) SimpleServerUtilities.STATISTICS.increment(
                        online, StatisticEventType.MINIGAME_WIN, definition.id, 1L);
                int before = match.priorLevels.getOrDefault(playerId,
                        match.resultingLevels.getOrDefault(playerId, 1));
                int after = match.resultingLevels.getOrDefault(playerId, before);
                if (after > before) publish(online, ContentEventTypes.MINIGAME_LEVEL_UP,
                        definition.id, after, Map.of("match", match.id.toString(),
                                "level", Integer.toString(after)));
            } catch (RuntimeException exception) {
                SimpleServerUtilities.LOGGER.error(
                        "Could not publish post-settlement minigame statistics for {} in match {}.",
                        online.getName().getString(), match.id, exception);
            }
        }
    }

    private boolean deliverMatchSummaryMail(MinigameMatch match, MinigameDefinition definition,
                                            UUID playerId, String playerName, boolean won,
                                            int experienceGained, int level, MinigamePerformance performance) {
        if (!Config.ENABLE_MAIL.get() || !SimpleServerUtilities.CORE.modules().isActive("mail")) return true;
        String result = match.winningTeams.isEmpty() ? "Draw" : won ? "Victory" : "Defeat";
        String body = result + " in " + definition.displayName + "\n"
                + "Arena: " + match.arenaId + "\n"
                + "Score: " + match.score(playerId) + "\n"
                + "Kills / deaths / assists: " + performance.kills + " / " + performance.deaths
                + " / " + performance.assists + "\n"
                + "Damage: " + formatHealth(performance.damageDealt / 100.0D)
                + " health • Healing: " + formatHealth(performance.healingDone / 100.0D) + " health\n"
                + "Captures: " + performance.captures + " • Defenses: " + performance.defenses
                + " • Objective time: " + performance.objectiveSeconds() + "s\n"
                + "Progression: +" + experienceGained + " XP • Level " + level;
        MailOperationResult delivered = SimpleServerUtilities.MAIL.deliverSystemMail(playerId, playerName,
                "Minigame summary: " + definition.displayName, body, List.of(), 0L,
                MailSource.MINIGAME, "minigame-summary:" + match.id + ":" + playerId);
        if (!delivered.successful()) {
            SimpleServerUtilities.LOGGER.error(
                    "Paused minigame cleanup because summary mail for {} in match {} failed: {}",
                    playerName, match.id, delivered.message());
            return false;
        }
        return true;
    }

    private void sendResults(MinigameMatch match, MinigameDefinition definition, int voteRemaining) {
        if (match == null || definition == null || definition.experience == null
                || !definition.experience.resultsScreenEnabled) return;
        ArrayList<MinigameResultsPayload.PlayerRow> rows = new ArrayList<>();
        for (UUID playerId : match.joinOrder) {
            String name = participantName(playerId);
            MinigamePerformance p = match.performance(playerId);
            rows.add(new MinigameResultsPayload.PlayerRow(playerId.toString(), name, match.team(playerId),
                    match.role(playerId).id(), match.winningTeams.contains(match.team(playerId)), match.score(playerId),
                    p.kills, p.deaths, p.assists, p.damageDealt, p.healingDone, p.captures, p.defenses,
                    p.objectiveSeconds(), p.contributionScore()));
        }
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            MinigameProgressionData.PlayerProgress progress = progressionPreview(
                    playerId, player.getName().getString());
            int gained = match.experienceGained.getOrDefault(playerId, 0);
            long projectedExperience = saturatingAdd(progress.experience, gained);
            int projectedLevel = match.resultingLevels.getOrDefault(
                    playerId, MinigameProgressionData.levelForExperience(projectedExperience));
            long requestId;
            synchronized (this) { requestId = lastRequests.getOrDefault(playerId, 0L); }
            PacketDistributor.sendToPlayer(player, new MinigameResultsPayload(true, match.id.toString(),
                    definition.id, winnerAnnouncement(match, definition), match.finishReason,
                    definition.experience.postGameVotingEnabled ? voteRemaining : 0,
                    gained, projectedLevel,
                    MinigameProgressionData.experienceIntoLevel(projectedExperience),
                    MinigameProgressionData.experienceForNextLevel(projectedLevel),
                    progress.badges(), rows, requestId));
        }
        match.resultsPublished = true;
    }

    private String resolvePostGameDecision(MinigameMatch match, MinigameDefinition definition) {
        if (definition.experience == null || !definition.experience.postGameVotingEnabled) return "leave";
        int rematch = 0, next = 0, leave = 0;
        for (String vote : match.postGameVotes.values()) {
            if ("rematch".equals(vote)) rematch++;
            else if ("next".equals(vote)) next++;
            else leave++;
        }
        if (rematch == 0 && next == 0) return "leave";
        if (next > rematch && next >= leave) return "next";
        if (rematch >= next && rematch >= leave) return "rematch";
        return "leave";
    }

    private String participantName(UUID playerId) {
        if (playerId == null) return "Player";
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (online != null) return online.getName().getString();
        MinigameProgressionData.PlayerProgress known = progression.players.get(playerId.toString());
        if (known != null && known.lastKnownName != null && !known.lastKnownName.isBlank()) {
            return known.lastKnownName;
        }
        return shortPlayerName(playerId);
    }

    private static String shortPlayerName(UUID playerId) {
        String value = playerId == null ? "unknown" : playerId.toString();
        return "Player-" + value.substring(0, Math.min(8, value.length()));
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
            roleBurst(winner, color, 2.4D);
            roleVerticalBurst(winner, color, 2.0D);
            playRoleSound(match, winner, "minecraft:entity.firework_rocket.launch", 1.35F, 1.0F);
            playUnlockedVictoryCosmetic(level, winner, color);
        }
    }

    private void playUnlockedVictoryCosmetic(ServerLevel level, ServerPlayer winner, int color) {
        MinigameProgressionData.PlayerProgress progress = progression.getOrCreate(
                winner.getUUID(), winner.getName().getString());
        if ("spark".equals(progress.selectedVictoryEffect)
                && progress.unlockedCosmetics.contains("victory:spark")) {
            level.sendParticles(new DustParticleOptions(color & 0x00FFFFFF, 1.5F),
                    winner.getX(), winner.getY() + 1.0D, winner.getZ(),
                    42, 0.65D, 0.9D, 0.65D, 0.06D);
        } else if ("star".equals(progress.selectedVictoryEffect)
                && progress.unlockedCosmetics.contains("victory:star")) {
            level.sendParticles(ParticleTypes.END_ROD, winner.getX(), winner.getY() + 1.2D, winner.getZ(),
                    28, 0.7D, 0.8D, 0.7D, 0.08D);
        }
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

    private record RuntimeBorderSyncState(UUID matchId, String playerDimension, boolean gameVisible,
                                          boolean spectatorVisible, long settingsRevision,
                                          String gameDimension, String spectatorDimension) { }

    private boolean cleanup(MinigameMatch match, MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (definition == null) definition = definition(match.minigameId);
        if (definition != null && arena == null) arena = arena(definition, match.arenaId);
        List<UUID> postGamePlayers = List.copyOf(match.joinOrder);
        Map<UUID, MinigameRole> postGamePreferences = new LinkedHashMap<>(match.preferredRoles);
        String postGameDecision = match.postGameDecision == null ? "leave" : match.postGameDecision;
        String previousArenaId = match.arenaId;

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
        if (definition != null && match.state == MinigameMatchState.POST_GAME
                && !finalizeMatchExperienceAfterRewards(match, definition)) {
            return false;
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
        if (definition != null && !"leave".equals(postGameDecision)) {
            if ("next".equals(postGameDecision)) rotateAfterArena(definition, previousArenaId);
            for (UUID playerId : postGamePlayers) {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || player.isDeadOrDying()) continue;
                try {
                    joinQueue(player, definition.id,
                            postGamePreferences.getOrDefault(playerId, MinigameRole.DPS).id());
                    player.sendSystemMessage(Component.literal("Post-game vote: queued for "
                            + ("next".equals(postGameDecision) ? "the next arena." : "a rematch.")));
                } catch (RuntimeException exception) {
                    player.sendSystemMessage(Component.literal("Post-game requeue failed safely: " + exception.getMessage()));
                }
            }
        }
        for (UUID playerId : postGamePlayers) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) PacketDistributor.sendToPlayer(player, MinigameResultsPayload.clear());
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
            runtimeBorderSyncStates.remove(playerId);
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
            if (type == MinigameGameType.CAPTURE_THE_FLAG || type == MinigameGameType.DOMINATION) {
                setCombatNeeds(player);
            } else {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
            }
        }
        teleport(player, arena.lobby);
        if (definition.lockInventory) captureLockedInventory(match, player);
        else match.lockedInventories.remove(player.getUUID());
        updateHud(match, definition, 0L);
    }

    private void beginParticipant(ServerPlayer player, MinigameDefinition definition,
                                  MinigameArenaDefinition arena, MinigameMatch match) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.SPLEEF || type == MinigameGameType.CAPTURE_THE_FLAG
                || type == MinigameGameType.DOMINATION) {
            clearMatchInventory(player);
            player.setGameMode(GameType.SURVIVAL);
            int team = match.team(player.getUUID());
            if (type == MinigameGameType.SPLEEF) {
                giveRegistryItem(player, definition.spleef.toolItem);
            } else {
                equipCosmeticTeamArmor(player, definition, team);
                MinigameRoleRules roles = roleRules(definition);
                if (roles != null && roles.enabled) {
                    MinigameRole role = match.role(player.getUUID());
                    applyRoleAttributes(player, roles.profile(role));
                    giveRoleLoadout(player, definition, match, role, team);
                    player.sendSystemMessage(Component.literal("Assigned role: " + role.label()
                            + (match.preferredRoles.getOrDefault(player.getUUID(), MinigameRole.DPS) == role
                            ? " (preferred)" : " (team composition)")), true);
                } else {
                    giveRegistryItem(player, type == MinigameGameType.DOMINATION
                            ? definition.domination.weaponItem : definition.captureTheFlag.weaponItem);
                }
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.setHealth(player.getMaxHealth());
            if (type == MinigameGameType.CAPTURE_THE_FLAG || type == MinigameGameType.DOMINATION) {
                setCombatNeeds(player);
            } else {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
            }
        }
        if (definition.lockInventory) captureLockedInventory(match, player);
        else match.lockedInventories.remove(player.getUUID());
        int team = match.team(player.getUUID());
        teleport(player, matchSpawn(definition, arena, match, player.getUUID(), team));
    }

    private static void giveRegistryItem(ServerPlayer player, String itemId) {
        ItemStack tool = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId))
                .map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
        if (!tool.isEmpty()) player.getInventory().add(tool);
    }

    private static void applyRoleAttributes(ServerPlayer player, MinigameRoleProfile profile) {
        setBaseAttribute(player, Attributes.MAX_HEALTH, profile.maxHealth);
        setBaseAttribute(player, Attributes.ARMOR, profile.armor);
        setBaseAttribute(player, Attributes.ARMOR_TOUGHNESS, profile.armorToughness);
    }

    private static void setBaseAttribute(ServerPlayer player,
                                         Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                         double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private void giveRoleLoadout(ServerPlayer player, MinigameDefinition definition,
                                 MinigameMatch match, MinigameRole role, int team) {
        switch (role) {
            case DPS -> {
                player.getInventory().setItem(0, durableRoleItem(Items.DIAMOND_SWORD, ROLE_DPS_SWORD));
                player.getInventory().setItem(1, durableRoleItem(Items.BOW, ROLE_DPS_BOW));
                player.getInventory().setItem(2, namedMatchItem(Items.ARROW, 1, ROLE_DPS_ARROW));
            }
            case TANK -> {
                player.getInventory().setItem(0, durableRoleItem(Items.STONE_SWORD, ROLE_TANK_SWORD));
                player.getInventory().setItem(1, abilityMatchItem(Items.HEART_OF_THE_SEA, ROLE_TANK_FIELD));
                player.setItemSlot(EquipmentSlot.OFFHAND, teamShield(player, definition, team));
            }
            case HEALER -> {
                player.getInventory().setItem(0, durableRoleItem(Items.STONE_SWORD, ROLE_HEALER_SWORD));
                player.getInventory().setItem(1, abilityMatchItem(Items.AMETHYST_SHARD, ROLE_HEAL_SINGLE));
                player.getInventory().setItem(2, abilityMatchItem(Items.GLISTERING_MELON_SLICE, ROLE_HEAL_AOE));
                player.getInventory().setItem(3, abilityMatchItem(Items.GHAST_TEAR, ROLE_HEAL_SELF));
            }
        }
        syncInventory(player);
    }

    private static ItemStack durableRoleItem(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = namedMatchItem(item, 1, name);
        stack.remove(DataComponents.MAX_DAMAGE);
        stack.remove(DataComponents.DAMAGE);
        return stack;
    }

    private static ItemStack namedMatchItem(net.minecraft.world.item.Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack abilityMatchItem(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = namedMatchItem(item, 1, name);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static void equipCosmeticTeamArmor(ServerPlayer player, MinigameDefinition definition, int team) {
        int color = switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> definition.captureTheFlag.color(team);
            case DOMINATION -> definition.domination.color(team);
            default -> 0xA06540;
        };
        player.setItemSlot(EquipmentSlot.HEAD, cosmeticLeather(Items.LEATHER_HELMET, color, ROLE_TEAM_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, cosmeticLeather(Items.LEATHER_CHESTPLATE, color, ROLE_TEAM_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, cosmeticLeather(Items.LEATHER_LEGGINGS, color, ROLE_TEAM_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, cosmeticLeather(Items.LEATHER_BOOTS, color, ROLE_TEAM_BOOTS));
    }

    private static ItemStack cosmeticLeather(net.minecraft.world.item.Item item, int color, String name) {
        ItemStack stack = namedMatchItem(item, 1, name);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color & 0x00FFFFFF));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        stack.remove(DataComponents.MAX_DAMAGE);
        stack.remove(DataComponents.DAMAGE);
        return stack;
    }

    private static ItemStack teamShield(ServerPlayer player, MinigameDefinition definition, int team) {
        ItemStack shield = new ItemStack(Items.SHIELD);
        DyeColor base = teamDyeColor(definition, team);
        shield.set(DataComponents.BASE_COLOR, base);
        Holder<BannerPattern> logo = player.level().registryAccess()
                .lookupOrThrow(Registries.BANNER_PATTERN)
                .getOrThrow(BannerPatterns.RHOMBUS_MIDDLE);
        shield.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .add(logo, base == DyeColor.WHITE ? DyeColor.BLACK : DyeColor.WHITE)
                .build());
        shield.set(DataComponents.CUSTOM_NAME, Component.literal(ROLE_TANK_SHIELD));
        shield.remove(DataComponents.MAX_DAMAGE);
        shield.remove(DataComponents.DAMAGE);
        return shield;
    }

    private static DyeColor teamDyeColor(MinigameDefinition definition, int team) {
        String blockId = switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> definition.captureTheFlag.flagBlock(team);
            case DOMINATION -> definition.domination.bannerBlock(team);
            default -> "minecraft:white_banner";
        };
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId)).orElse(null);
        return block instanceof AbstractBannerBlock banner ? banner.getColor() : DyeColor.WHITE;
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
        LinkedHashSet<UUID> spectators = new LinkedHashSet<>(match.eliminated);
        spectators.addAll(match.pendingRespawns.keySet());
        for (UUID playerId : spectators) {
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

    private void tickObjectiveTime(MinigameMatch match, MinigameDefinition definition,
                                   MinigameGameType type) {
        LinkedHashSet<UUID> objectivePlayers = new LinkedHashSet<>();
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            objectivePlayers.addAll(match.flagCarriers.values());
            objectivePlayers.addAll(match.ctfCasts.keySet());
        } else if (type == MinigameGameType.DOMINATION) {
            objectivePlayers.addAll(match.dominationCasts.keySet());
        }
        for (UUID playerId : objectivePlayers) {
            if (!match.active(playerId)) continue;
            match.performance(playerId).objectiveTicks = saturatingAdd(
                    match.performance(playerId).objectiveTicks, 20L);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && match.rewardsEnabled) {
                SimpleServerUtilities.STATISTICS.increment(player,
                        StatisticEventType.MINIGAME_OBJECTIVE_TIME, definition.id, 1L);
                publish(player, ContentEventTypes.MINIGAME_OBJECTIVE_TIME, definition.id, 1L,
                        Map.of("match", match.id.toString()));
            }
        }
    }

    private void recordObjectiveCapture(MinigameMatch match, MinigameDefinition definition,
                                        UUID playerId, String objective) {
        if (match == null || definition == null || playerId == null) return;
        match.performance(playerId).captures = saturatingAdd(match.performance(playerId).captures, 1L);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            if (match.rewardsEnabled) {
                SimpleServerUtilities.STATISTICS.increment(player, StatisticEventType.MINIGAME_CAPTURE, definition.id, 1L);
                publish(player, ContentEventTypes.MINIGAME_CAPTURE, definition.id, 1L,
                        Map.of("match", match.id.toString(), "objective", objective == null ? "" : objective));
            }
            sendKillFeed(match, definition, player.getName().getString() + " captured " + objective, 0xFF83E39A);
        }
        if (match.overtime && match.state == MinigameMatchState.RUNNING) {
            int team = match.team(playerId);
            if (team > 0) {
                match.winningTeams = Set.of(team);
                finish(match, "Sudden-death objective captured in overtime.");
            }
        }
    }

    private void recordObjectiveDefense(MinigameMatch match, MinigameDefinition definition,
                                        UUID playerId, String objective) {
        if (match == null || definition == null || playerId == null) return;
        match.performance(playerId).defenses = saturatingAdd(match.performance(playerId).defenses, 1L);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            if (match.rewardsEnabled) {
                SimpleServerUtilities.STATISTICS.increment(player, StatisticEventType.MINIGAME_DEFENSE, definition.id, 1L);
                publish(player, ContentEventTypes.MINIGAME_DEFENSE, definition.id, 1L,
                        Map.of("match", match.id.toString(), "objective", objective == null ? "" : objective));
            }
            sendKillFeed(match, definition, player.getName().getString() + " defended " + objective, 0xFF7FC8FF);
        }
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
            if (player == null || !match.active(playerId)) continue;
            if (!locationInsideRegion(MinigameLocation.of(player), region, 12.0D)) {
                interruptDominationCast(match, playerId, "Capture interrupted because you left the arena.");
                int team = match.team(playerId);
                teleport(player, randomTeamSpawn(arena, match, team));
                player.setHealth(player.getMaxHealth());
                player.removeAllEffects();
                player.setAbsorptionAmount(0.0F);
                player.setRemainingFireTicks(0);
                setCombatNeeds(player);
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
                + " flag. Do not move, attack, use items, or take damage."), true);
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
            recordObjectiveDefense(match, definition, player.getUUID(),
                    definition.captureTheFlag.teamName(flagTeam) + " flag");
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
                    match.dominationClaimers.remove(point.id);
                    match.dominationOwners.put(point.id, claim.previousOwner());
                    recordObjectiveDefense(match, definition, player.getUUID(), point.displayName);
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
                match.dominationClaimers.remove(point.id);
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
                + ". Do not move, attack, use items, or take damage."), true);
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
            if (player == null || !match.active(playerId)) continue;
            if (!locationInsideRegion(MinigameLocation.of(player), region, 12.0D)) {
                interruptCtfCast(match, playerId, "Flag capture interrupted because you left the arena.");
                returnFlagsCarriedBy(match, definition, arena, playerId, "The flag returned because its carrier left the arena.");
                int team = match.team(playerId);
                teleport(player, randomTeamSpawn(arena, match, team));
                player.setHealth(player.getMaxHealth());
                setCombatNeeds(player);
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
            recordObjectiveCapture(match, definition, carrierId,
                    definition.captureTheFlag.teamName(enemyFlagTeam) + " flag");
            placeCtfFlagAtBase(definition, arena, enemyFlagTeam);
            int score = match.ctfScores.getOrDefault(carrierTeam, 0);
            announce(match, carrier.getName().getString() + " captured the "
                    + definition.captureTheFlag.teamName(enemyFlagTeam) + " flag! "
                    + definition.captureTheFlag.teamName(carrierTeam) + " " + score + "–"
                    + match.ctfScores.getOrDefault(carrierTeam == 1 ? 2 : 1, 0));
            playObjectiveCaptureResultSounds(match, carrierTeam);
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

    /** Returns whether this player is currently channeling a CTF flag or Domination objective. */
    public boolean hasActiveObjectiveCast(ServerPlayer player) {
        if (player == null) return false;
        synchronized (this) {
            MinigameMatch match = matchFor(player.getUUID());
            return match != null && (match.ctfCasts.containsKey(player.getUUID())
                    || match.dominationCasts.containsKey(player.getUUID()));
        }
    }

    /**
     * True only during the server tick in which the objective interaction created the cast.
     * NeoForge can continue the same right-click through the other hand/item stages; those
     * follow-up stages must be consumed rather than interpreted as a new interrupting action.
     */
    public boolean objectiveCastStartedThisTick(ServerPlayer player) {
        if (player == null) return false;
        synchronized (this) {
            MinigameMatch match = matchFor(player.getUUID());
            if (match == null) return false;
            MinigameMatch.CtfCast ctf = match.ctfCasts.get(player.getUUID());
            if (ctf != null && ctf.startedTick() == serverTicks) return true;
            MinigameMatch.DominationCast domination = match.dominationCasts.get(player.getUUID());
            return domination != null && domination.startedTick() == serverTicks;
        }
    }

    /**
     * Cancels an active objective cast before the attempted gameplay action is processed.
     * The caller should cancel the triggering event when this returns true so the action
     * cannot both interrupt the cast and still affect the match world or another player.
     */
    public boolean interruptActiveCastForAction(ServerPlayer player, String action) {
        if (player == null) return false;
        MinigameMatch match;
        boolean domination;
        boolean ctf;
        synchronized (this) {
            match = matchFor(player.getUUID());
            if (match == null) return false;
            domination = match.dominationCasts.containsKey(player.getUUID());
            ctf = match.ctfCasts.containsKey(player.getUUID());
        }
        if (!domination && !ctf) return false;
        String detail = action == null || action.isBlank() ? "performed another action" : action.trim();
        if (domination) {
            interruptDominationCast(match, player.getUUID(), "Capture interrupted because you " + detail + ".");
        }
        if (ctf) {
            interruptCtfCast(match, player.getUUID(), "Flag capture interrupted because you " + detail + ".");
        }
        return true;
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

    private int cleanOrphanedBoostEntities() {
        if (server == null) return 0;
        LinkedHashSet<UUID> referenced = new LinkedHashSet<>();
        for (MinigameMatch match : matches.values()) {
            for (MinigameMatch.ActiveBoost boost : match.activeBoosts.values()) {
                if (boost.entityId != null) referenced.add(boost.entityId);
            }
        }
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                Component customName = entity.getCustomName();
                if (customName != null && BOOST_ENTITY_TAG.equals(customName.getString())
                        && !referenced.contains(entity.getUUID())) {
                    entity.discard();
                    removed++;
                }
            }
        }
        return removed;
    }

    private void removeOrphanMinigameBoosts() {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                Component customName = entity.getCustomName();
                if (customName != null && BOOST_ENTITY_TAG.equals(customName.getString())) entity.discard();
            }
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

    /**
     * Celebrates a completed CTF score or Domination base capture for the scoring team
     * with the vanilla Ponder goat horn. Every opposing team member receives a short,
     * clearly negative non-horn sound instead.
     */
    private void playObjectiveCaptureResultSounds(MinigameMatch match, int capturingTeam) {
        if (match == null || capturingTeam <= 0) return;
        SoundEvent ponder = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:item.goat_horn.sound.0")).orElse(null);
        SoundEvent lost = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse("minecraft:block.beacon.deactivate")).orElse(null);
        if (ponder == null && lost == null) return;
        for (Map.Entry<UUID, Integer> entry : match.teams.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            boolean captured = entry.getValue() == capturingTeam;
            SoundEvent sound = captured ? ponder : lost;
            if (sound == null) continue;
            player.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.MASTER,
                    player.getX(), player.getY(), player.getZ(),
                    captured ? 16.0F : 1.5F,
                    captured ? 1.0F : 0.85F,
                    serverTicks ^ player.getUUID().getLeastSignificantBits() ^ capturingTeam));
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

    public void recordCombatDamage(ServerPlayer attacker, ServerPlayer victim, float inflictedDamage) {
        if (victim == null || inflictedDamage <= 0.0F) return;
        MinigameMatch match;
        MinigameDefinition definition;
        long amount = Math.max(0L, Math.round(inflictedDamage * 100.0F));
        synchronized (this) {
            match = matchFor(victim.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            if (match == null || definition == null || match.state != MinigameMatchState.RUNNING
                    || match.pendingRespawns.containsKey(victim.getUUID())) return;
            match.performance(victim.getUUID()).damageTaken = saturatingAdd(
                    match.performance(victim.getUUID()).damageTaken, amount);
            if (attacker != null && !attacker.getUUID().equals(victim.getUUID())
                    && matchFor(attacker.getUUID()) == match && match.team(attacker.getUUID()) != match.team(victim.getUUID())) {
                match.performance(attacker.getUUID()).damageDealt = saturatingAdd(
                        match.performance(attacker.getUUID()).damageDealt, amount);
                Map<UUID, MinigameMatch.DamageContribution> contributions = match.recentDamage.computeIfAbsent(
                        victim.getUUID(), ignored -> new LinkedHashMap<>());
                MinigameMatch.DamageContribution previous = contributions.get(attacker.getUUID());
                long total = amount + (previous == null ? 0L : previous.amountHundredths());
                contributions.put(attacker.getUUID(), new MinigameMatch.DamageContribution(total, serverTicks));
            }
        }
        recordActivity(victim);
        if (attacker != null) recordActivity(attacker);
        if (attacker != null && matchFor(attacker.getUUID()) == match && match.rewardsEnabled) {
            SimpleServerUtilities.STATISTICS.increment(attacker, StatisticEventType.MINIGAME_DAMAGE, definition.id, amount);
            publish(attacker, ContentEventTypes.MINIGAME_DAMAGE, definition.id, amount,
                    Map.of("match", match.id.toString(), "victim", victim.getUUID().toString()));
        }
    }

    public void recordDeathStatistics(ServerPlayer victim, ServerPlayer directKiller) {
        if (victim == null) return;
        MinigameMatch match;
        MinigameDefinition definition;
        ArrayList<UUID> assists = new ArrayList<>();
        ServerPlayer killer = directKiller;
        synchronized (this) {
            match = matchFor(victim.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            if (match == null || definition == null || match.state != MinigameMatchState.RUNNING) return;
            match.performance(victim.getUUID()).deaths = saturatingAdd(match.performance(victim.getUUID()).deaths, 1L);
            if (killer != null && (matchFor(killer.getUUID()) != match
                    || match.team(killer.getUUID()) == match.team(victim.getUUID()))) killer = null;
            if (killer != null) {
                match.performance(killer.getUUID()).kills = saturatingAdd(match.performance(killer.getUUID()).kills, 1L);
            }
            Map<UUID, MinigameMatch.DamageContribution> contributors = match.recentDamage.remove(victim.getUUID());
            if (contributors != null) {
                for (Map.Entry<UUID, MinigameMatch.DamageContribution> entry : contributors.entrySet()) {
                    if (killer != null && entry.getKey().equals(killer.getUUID())) continue;
                    if (serverTicks - entry.getValue().lastHitTick() > 200L || entry.getValue().amountHundredths() <= 0L) continue;
                    if (!match.teams.containsKey(entry.getKey()) || match.team(entry.getKey()) == match.team(victim.getUUID())) continue;
                    match.performance(entry.getKey()).assists = saturatingAdd(match.performance(entry.getKey()).assists, 1L);
                    assists.add(entry.getKey());
                }
            }
        }
        if (match.rewardsEnabled) {
            SimpleServerUtilities.STATISTICS.increment(victim, StatisticEventType.MINIGAME_DEATH, definition.id, 1L);
            publish(victim, ContentEventTypes.MINIGAME_DEATH, definition.id, 1L,
                    Map.of("match", match.id.toString()));
            if (killer != null) {
                SimpleServerUtilities.STATISTICS.increment(killer, StatisticEventType.MINIGAME_KILL, definition.id, 1L);
                publish(killer, ContentEventTypes.MINIGAME_KILL, definition.id, 1L,
                        Map.of("match", match.id.toString(), "victim", victim.getUUID().toString()));
            }
            for (UUID assistId : assists) {
                ServerPlayer assist = server.getPlayerList().getPlayer(assistId);
                if (assist != null) {
                    SimpleServerUtilities.STATISTICS.increment(assist, StatisticEventType.MINIGAME_ASSIST, definition.id, 1L);
                    publish(assist, ContentEventTypes.MINIGAME_ASSIST, definition.id, 1L,
                            Map.of("match", match.id.toString(), "victim", victim.getUUID().toString()));
                }
            }
        }
        String line = killer == null ? victim.getName().getString() + " was defeated"
                : killer.getName().getString() + " defeated " + victim.getName().getString();
        if (!assists.isEmpty()) line += " (assist" + (assists.size() == 1 ? "" : "s") + ": " + assists.size() + ")";
        sendKillFeed(match, definition, line, 0xFFF0F3F6);
    }

    private void sendKillFeed(MinigameMatch match, MinigameDefinition definition, String text, int color) {
        if (match == null || definition == null || definition.experience == null
                || !definition.experience.killFeedEnabled || text == null || text.isBlank()) return;
        MinigameKillFeedPayload payload = new MinigameKillFeedPayload(text, color, 120);
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static long saturatingAdd(long first, long second) {
        if (second <= 0L) return Math.max(0L, first);
        if (first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return Math.max(0L, first) + second;
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
        if (match.pendingRespawns.containsKey(player.getUUID())) return true;

        MinigameLocation deathLocation = MinigameLocation.of(player);
        int team = match.team(player.getUUID());
        MinigameLocation destination = type == MinigameGameType.DOMINATION
                ? dominationRespawn(match, arena, team, deathLocation)
                : randomTeamSpawn(arena, match, team);

        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            interruptCtfCast(match, player.getUUID(), "Flag capture interrupted because you were defeated.");
            dropFlagsCarriedBy(match, definition, arena, player.getUUID(), deathLocation,
                    player.getName().getString() + " dropped the carried flag.");
        } else {
            interruptDominationCast(match, player.getUUID(), "Capture interrupted because you were defeated.");
        }

        long delayTicks = Math.max(20L, definition.respawnDelaySeconds * 20L);
        MinigameMatch.PendingRespawn pending = new MinigameMatch.PendingRespawn(
                destination, safeAdd(serverTicks, delayTicks));
        match.pendingRespawns.put(player.getUUID(), pending);
        match.boostRegenerationExpires.remove(player.getUUID());
        match.boostRegenerationNextHeal.remove(player.getUUID());

        player.stopUsingItem();
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.setAbsorptionAmount(0.0F);
        player.setRemainingFireTicks(0);
        setCombatNeeds(player);
        player.setGameMode(GameType.SPECTATOR);
        teleport(player, arena.spectator);
        showRespawnCountdown(player, definition.respawnDelaySeconds);
        pending.lastDisplayedSecond = definition.respawnDelaySeconds;
        player.sendSystemMessage(Component.literal("You were defeated. Respawning in "
                + definition.respawnDelaySeconds + " seconds."), true);
        return true;
    }

    private void initializeBoosts(MinigameMatch match, MinigameDefinition definition,
                                  MinigameArenaDefinition arena) {
        clearBoosts(match);
        MinigameBoostRules rules = boostRules(definition);
        if (rules == null || !rules.enabled) return;
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) return;
        if (rules.automatic()) buildAutomaticBoostCandidates(match, definition, arena, region);
        else {
            for (MinigameLocation raw : arena.boostSpawns) {
                MinigameLocation safe = safeManualBoostLocation(raw, region);
                if (safe != null) addBoostCandidate(match, safe, 1.0D);
            }
        }
        if (match.boostCandidateLocations.isEmpty()) {
            announce(match, rules.automatic()
                    ? "No safe automatic boost positions were found; this match will continue without boosts."
                    : "Boosts are enabled, but this arena has no manual boost spawn points.");
            return;
        }
        match.boostsInitialized = true;
        long first = safeAdd(serverTicks, rules.initialSpawnDelaySeconds * 20L);
        for (int index = 0; index < rules.maximumActive; index++) {
            match.boostSpawnSchedule.add(safeAdd(first, index * 10L));
        }
    }

    private MinigameLocation safeManualBoostLocation(MinigameLocation raw, Region region) {
        if (raw == null || region == null || !locationInsideRegion(raw, region, 4.0D)) return null;
        ServerLevel level = resolveLevel(raw.dimension);
        if (level == null) return null;
        BlockPos feet = BlockPos.containing(raw.x, raw.y, raw.z);
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockState floorState = level.getBlockState(feet.below());
        if (!feetState.getCollisionShape(level, feet).isEmpty()
                || !headState.getCollisionShape(level, feet.above()).isEmpty()
                || !feetState.getFluidState().isEmpty()
                || !headState.getFluidState().isEmpty()
                || !floorState.isFaceSturdy(level, feet.below(), Direction.UP)) return null;
        return new MinigameLocation(raw.dimension, feet.getX() + 0.5D, feet.getY() + 0.15D,
                feet.getZ() + 0.5D, raw.yaw, raw.pitch);
    }

    private void buildAutomaticBoostCandidates(MinigameMatch match, MinigameDefinition definition,
                                               MinigameArenaDefinition arena, Region region) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        ServerLevel level = server.getLevel(region.getDimension());
        if (level == null) return;
        int salt = 0;
        if (type == MinigameGameType.DOMINATION) {
            for (MinigameControlPoint point : arena.controlPoints) {
                if (point == null || point.location == null) continue;
                MinigameLocation center = point.respawn == null ? point.location : point.respawn;
                int centerX = (int) Math.floor(center.x);
                int centerZ = (int) Math.floor(center.z);
                int preferredY = (int) Math.floor(center.y);
                for (int attempt = 0; attempt < 12; attempt++) {
                    double angle = Math.PI * 2.0D * randomUnit(match, ++salt);
                    int radius = 2 + randomInt(match, 0, 5, ++salt);
                    int x = centerX + (int) Math.round(Math.cos(angle) * radius);
                    int z = centerZ + (int) Math.round(Math.sin(angle) * radius);
                    MinigameLocation found = findBoostGround(level, region, x, z, preferredY);
                    if (found != null) addBoostCandidate(match, found, 2.0D);
                }
            }
        } else {
            int width = Math.max(1, region.getMaxX() - region.getMinX() + 1);
            int depth = Math.max(1, region.getMaxZ() - region.getMinZ() + 1);
            for (int attempt = 0; attempt < 96 && match.boostCandidateLocations.size() < 48; attempt++) {
                int x = region.getMinX() + randomInt(match, 0, width - 1, ++salt);
                int z = region.getMinZ() + randomInt(match, 0, depth - 1, ++salt);
                MinigameLocation found = findBoostGround(level, region, x, z, region.getMaxY());
                if (found != null) addBoostCandidate(match, found, 3.0D);
            }
        }
    }

    private MinigameLocation findBoostGround(ServerLevel level, Region region, int x, int z, int preferredY) {
        if (level == null || region == null || x < region.getMinX() || x > region.getMaxX()
                || z < region.getMinZ() || z > region.getMaxZ()) return null;
        int top = Math.min(region.getMaxY() + 1, Math.max(region.getMinY() + 1, preferredY + 6));
        for (int y = top; y >= region.getMinY() + 1; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            BlockState ground = level.getBlockState(feet.below());
            if (!level.getBlockState(feet).getFluidState().isEmpty()
                    || !level.getBlockState(feet.above()).getFluidState().isEmpty()
                    || !ground.isFaceSturdy(level, feet.below(), Direction.UP)) continue;
            return new MinigameLocation(region.getDimension().identifier().toString(),
                    x + 0.5D, y + 0.15D, z + 0.5D, 0.0F, 0.0F);
        }
        return null;
    }

    private static void addBoostCandidate(MinigameMatch match, MinigameLocation candidate, double minimumDistance) {
        if (match == null || candidate == null) return;
        double minimumSquared = minimumDistance * minimumDistance;
        for (MinigameLocation existing : match.boostCandidateLocations) {
            if (!existing.dimension.equals(candidate.dimension)) continue;
            double dx = existing.x - candidate.x, dy = existing.y - candidate.y, dz = existing.z - candidate.z;
            if (dx * dx + dy * dy + dz * dz < minimumSquared) return;
        }
        match.boostCandidateLocations.add(candidate);
    }

    private void tickBoosts(MinigameMatch match, MinigameDefinition definition,
                            MinigameArenaDefinition arena) {
        tickRegenerationBoosts(match);
        MinigameBoostRules rules = boostRules(definition);
        if (rules == null || !rules.enabled || !match.boostsInitialized) return;
        expireArmorBoosts(match);
        if (!match.boostSpawnSchedule.isEmpty()) {
            for (Long due : List.copyOf(match.boostSpawnSchedule)) {
                if (due == null || due > serverTicks || match.activeBoosts.size() >= rules.maximumActive) continue;
                match.boostSpawnSchedule.remove(due);
                if (!spawnRandomBoost(match, rules)) {
                    match.boostSpawnSchedule.add(safeAdd(serverTicks, 100L));
                }
            }
        }
        for (MinigameMatch.ActiveBoost boost : List.copyOf(match.activeBoosts.values())) {
            ServerLevel level = resolveLevel(boost.location.dimension);
            if (level == null) {
                consumeBoost(match, boost, rules, null);
                continue;
            }
            Entity visual = boost.entityId == null ? null : level.getEntity(boost.entityId);
            if (!(visual instanceof ItemEntity) || !visual.isAlive()) spawnBoostVisual(level, boost);
            if ((serverTicks & 1L) == 0L) emitBoostMist(level, boost, rules.color(boost.type));
            ServerPlayer collector = nearestBoostCollector(match, boost);
            if (collector != null) consumeBoost(match, boost, rules, collector);
        }
    }

    private boolean spawnRandomBoost(MinigameMatch match, MinigameBoostRules rules) {
        List<MinigameBoostType> types = rules.enabledTypes();
        if (types.isEmpty() || match.boostCandidateLocations.isEmpty()) return false;
        ArrayList<MinigameLocation> available = new ArrayList<>();
        double minimumSquared = rules.minimumSpacing * rules.minimumSpacing;
        for (MinigameLocation candidate : match.boostCandidateLocations) {
            boolean close = false;
            for (MinigameMatch.ActiveBoost active : match.activeBoosts.values()) {
                if (!candidate.dimension.equals(active.location.dimension)) continue;
                double dx = candidate.x - active.location.x;
                double dy = candidate.y - active.location.y;
                double dz = candidate.z - active.location.z;
                if (dx * dx + dy * dy + dz * dz < minimumSquared) { close = true; break; }
            }
            if (!close) available.add(candidate);
        }
        if (available.isEmpty()) return false;
        int locationIndex = randomInt(match, 0, available.size() - 1,
                match.activeBoosts.size() * 31 + match.boostSpawnSchedule.size() * 17 + 7);
        int typeIndex = randomInt(match, 0, types.size() - 1,
                match.activeBoosts.size() * 43 + match.boostSpawnSchedule.size() * 13 + 11);
        MinigameMatch.ActiveBoost boost = new MinigameMatch.ActiveBoost(UUID.randomUUID(), types.get(typeIndex),
                available.get(locationIndex).copy(), null);
        match.activeBoosts.put(boost.id, boost);
        ServerLevel level = resolveLevel(boost.location.dimension);
        if (level != null) spawnBoostVisual(level, boost);
        return true;
    }

    private void spawnBoostVisual(ServerLevel level, MinigameMatch.ActiveBoost boost) {
        ItemStack icon = BuiltInRegistries.ITEM.getOptional(Identifier.parse(boost.type.itemId()))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (icon.isEmpty()) return;
        icon.set(DataComponents.CUSTOM_NAME, Component.literal(boost.type.label() + " Boost"));
        ItemEntity entity = new ItemEntity(level, boost.location.x, boost.location.y + 0.45D, boost.location.z, icon);
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setPickUpDelay(Integer.MAX_VALUE);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        entity.setCustomName(Component.literal(BOOST_ENTITY_TAG));
        entity.setCustomNameVisible(false);
        level.addFreshEntity(entity);
        boost.entityId = entity.getUUID();
    }

    private static void emitBoostMist(ServerLevel level, MinigameMatch.ActiveBoost boost, int color) {
        DustParticleOptions dust = new DustParticleOptions(color & 0x00FFFFFF, 1.15F);
        level.sendParticles(dust, boost.location.x, boost.location.y + 0.55D, boost.location.z,
                5, 0.36D, 0.22D, 0.36D, 0.0D);
    }

    private ServerPlayer nearestBoostCollector(MinigameMatch match, MinigameMatch.ActiveBoost boost) {
        ServerPlayer best = null;
        double bestDistance = 2.25D;
        for (UUID playerId : match.teams.keySet()) {
            if (!match.active(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.level().dimension().identifier().toString().equals(boost.location.dimension)) continue;
            double dx = player.getX() - boost.location.x;
            double dy = player.getY() + 0.5D - boost.location.y;
            double dz = player.getZ() - boost.location.z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) { bestDistance = distance; best = player; }
        }
        return best;
    }

    private void consumeBoost(MinigameMatch match, MinigameMatch.ActiveBoost boost,
                              MinigameBoostRules rules, ServerPlayer player) {
        if (boost == null || match.activeBoosts.remove(boost.id) == null) return;
        ServerLevel level = resolveLevel(boost.location.dimension);
        if (level != null && boost.entityId != null) {
            Entity visual = level.getEntity(boost.entityId);
            if (visual != null) visual.discard();
        }
        if (player != null) {
            match.performance(player.getUUID()).boostsCollected = saturatingAdd(
                    match.performance(player.getUUID()).boostsCollected, 1L);
            MinigameDefinition definition = definitions.get(match.minigameId);
            if (definition != null && match.rewardsEnabled) publish(player, ContentEventTypes.MINIGAME_BOOST_COLLECTED,
                    definition.id, 1L, Map.of("match", match.id.toString(), "boost", boost.type.id()));
            applyBoost(match, player, boost.type, rules);
            playBoostPickupSound(match, player, boost.type);
            player.sendSystemMessage(Component.literal(boost.type.label() + " boost activated for "
                    + rules.durationSeconds(boost.type) + " seconds."), true);
        }
        int delay = randomInt(match, rules.respawnMinSeconds, rules.respawnMaxSeconds,
                boost.id.hashCode() ^ (int) serverTicks);
        match.boostSpawnSchedule.add(safeAdd(serverTicks, delay * 20L));
    }

    private void playBoostPickupSound(MinigameMatch match, ServerPlayer collector,
                                      MinigameBoostType type) {
        if (match == null || collector == null || type == null) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(
                Identifier.parse(type.soundId())).orElse(null);
        if (sound == null) return;
        for (UUID playerId : match.teams.keySet()) {
            if (!match.active(playerId)) continue;
            ServerPlayer listener = server.getPlayerList().getPlayer(playerId);
            if (listener == null || listener.level() != collector.level()) continue;
            listener.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.PLAYERS,
                    collector.getX(), collector.getY(), collector.getZ(),
                    type.soundVolume(), type.soundPitch(),
                    serverTicks ^ collector.getUUID().getMostSignificantBits()
                            ^ listener.getUUID().getLeastSignificantBits()));
        }
    }

    private void applyBoost(MinigameMatch match, ServerPlayer player, MinigameBoostType type,
                            MinigameBoostRules rules) {
        int durationTicks = rules.durationSeconds(type) * 20;
        if (type == MinigameBoostType.ARMOR) {
            AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
            if (armor == null) return;
            double original = match.boostOriginalArmorBase.computeIfAbsent(player.getUUID(), ignored -> armor.getBaseValue());
            armor.setBaseValue(original + rules.armorPoints);
            match.boostArmorExpires.put(player.getUUID(), safeAdd(serverTicks, durationTicks));
            return;
        }
        if (type == MinigameBoostType.REGENERATION) {
            match.boostRegenerationExpires.put(player.getUUID(), safeAdd(serverTicks, durationTicks));
            match.boostRegenerationNextHeal.put(player.getUUID(), safeAdd(serverTicks, 20L));
        }
        String effectId = switch (type) {
            case SPEED -> "minecraft:speed";
            case REGENERATION -> "minecraft:regeneration";
            case JUMP -> "minecraft:jump_boost";
            case ARMOR -> "";
        };
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(Identifier.parse(effectId)).orElse(null);
        if (effect == null) return;
        int amplifier = type == MinigameBoostType.REGENERATION ? 0 : 1;
        player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                durationTicks, amplifier, false, true, true));
    }

    private void tickRegenerationBoosts(MinigameMatch match) {
        if (match == null || match.boostRegenerationExpires.isEmpty()) return;
        for (Map.Entry<UUID, Long> entry : List.copyOf(match.boostRegenerationExpires.entrySet())) {
            UUID playerId = entry.getKey();
            long expires = entry.getValue();
            if (expires <= serverTicks) {
                match.boostRegenerationExpires.remove(playerId);
                match.boostRegenerationNextHeal.remove(playerId);
                continue;
            }
            long next = match.boostRegenerationNextHeal.getOrDefault(playerId, serverTicks);
            if (next > serverTicks) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            MinigameDefinition definition = definitions.get(match.minigameId);
            MinigameBoostRules rules = definition == null ? null : boostRules(definition);
            double amount = rules == null ? 2.0D : rules.regenerationHealthPerSecond;
            if (player != null && match.active(playerId)) healPlayer(player, amount);
            match.boostRegenerationNextHeal.put(playerId, safeAdd(serverTicks, 20L));
        }
    }

    private void expireArmorBoosts(MinigameMatch match) {
        for (Map.Entry<UUID, Long> entry : List.copyOf(match.boostArmorExpires.entrySet())) {
            if (entry.getValue() > serverTicks) continue;
            UUID playerId = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            Double original = match.boostOriginalArmorBase.remove(playerId);
            match.boostArmorExpires.remove(playerId);
            if (player != null && original != null) {
                AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
                if (armor != null) armor.setBaseValue(original);
            }
        }
    }

    private void clearBoosts(MinigameMatch match) {
        if (match == null) return;
        for (MinigameMatch.ActiveBoost boost : List.copyOf(match.activeBoosts.values())) {
            ServerLevel level = resolveLevel(boost.location.dimension);
            if (level != null && boost.entityId != null) {
                Entity visual = level.getEntity(boost.entityId);
                if (visual != null) visual.discard();
            }
        }
        for (Map.Entry<UUID, Double> entry : List.copyOf(match.boostOriginalArmorBase.entrySet())) {
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
            if (armor != null) armor.setBaseValue(entry.getValue());
        }
        match.activeBoosts.clear();
        match.boostCandidateLocations.clear();
        match.boostSpawnSchedule.clear();
        match.boostOriginalArmorBase.clear();
        match.boostArmorExpires.clear();
        match.boostRegenerationExpires.clear();
        match.boostRegenerationNextHeal.clear();
        match.boostsInitialized = false;
    }

    private static MinigameBoostRules boostRules(MinigameDefinition definition) {
        if (definition == null) return null;
        return switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> definition.captureTheFlag.boosts;
            case DOMINATION -> definition.domination.boosts;
            default -> null;
        };
    }

    private static MinigameRoleRules roleRules(MinigameDefinition definition) {
        if (definition == null) return null;
        return switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> definition.captureTheFlag.roles;
            case DOMINATION -> definition.domination.roles;
            default -> null;
        };
    }

    private static int randomInt(MinigameMatch match, int minimum, int maximum, int salt) {
        if (maximum <= minimum) return minimum;
        long value = match.id.getMostSignificantBits() ^ Long.rotateLeft(match.id.getLeastSignificantBits(), 21)
                ^ Long.rotateLeft(match.stateStartedTick, 9) ^ ((long) salt * 0x9E3779B97F4A7C15L)
                ^ System.nanoTime();
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return minimum + (int) Math.floorMod(value, (long) maximum - minimum + 1L);
    }

    private static double randomUnit(MinigameMatch match, int salt) {
        return randomInt(match, 0, 1_000_000, salt) / 1_000_000.0D;
    }

    private static final List<EquipmentSlot> LOCKED_EQUIPMENT_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);

    private static void captureLockedInventory(MinigameMatch match, ServerPlayer player) {
        if (match == null || player == null) return;
        ArrayList<ItemStack> inventory = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) inventory.add(player.getInventory().getItem(slot).copy());
        LinkedHashMap<EquipmentSlot, ItemStack> equipment = new LinkedHashMap<>();
        for (EquipmentSlot slot : LOCKED_EQUIPMENT_SLOTS) equipment.put(slot, player.getItemBySlot(slot).copy());
        match.lockedInventories.put(player.getUUID(), new MinigameMatch.LockedInventory(inventory, equipment));
    }

    private void tickLockedInventories(MinigameMatch match) {
        for (UUID playerId : match.teams.keySet()) {
            if (!match.active(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            enforceLockedInventory(match, player);
        }
    }

    /** Restores the exact server-owned layout and clears any cursor-carried duplicate. */
    private static void enforceLockedInventory(MinigameMatch match, ServerPlayer player) {
        MinigameMatch.LockedInventory expected = match.lockedInventories.get(player.getUUID());
        if (expected == null || expected.inventory.size() < 36) {
            captureLockedInventory(match, player);
            return;
        }
        boolean changed = false;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack wanted = expected.inventory.get(slot);
            ItemStack current = player.getInventory().getItem(slot);
            if (!ItemStack.matches(current, wanted)) {
                player.getInventory().setItem(slot, wanted.copy());
                changed = true;
            }
        }
        boolean carryingFlag = match.flagCarriers.containsValue(player.getUUID());
        for (EquipmentSlot slot : LOCKED_EQUIPMENT_SLOTS) {
            // The CTF carrier banner is an intentional temporary replacement of the locked helmet.
            if (carryingFlag && slot == EquipmentSlot.HEAD) continue;
            ItemStack wanted = expected.equipment.getOrDefault(slot, ItemStack.EMPTY);
            ItemStack current = player.getItemBySlot(slot);
            if (!ItemStack.matches(current, wanted)) {
                player.setItemSlot(slot, wanted.copy());
                changed = true;
            }
        }
        if (!player.containerMenu.getCarried().isEmpty()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            changed = true;
        }
        if (changed) syncInventory(player);
    }

    private void tickRoleRuntime(MinigameMatch match, MinigameDefinition definition) {
        MinigameRoleRules rules = roleRules(definition);
        if (rules == null || !rules.enabled) return;
        for (UUID playerId : match.teams.keySet()) {
            if (!match.active(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            enforceRoleInventory(player, definition, match, match.role(playerId), match.team(playerId));
        }
    }

    /**
     * Match equipment is server-owned. Players may open their inventory, but moving a
     * role weapon, ability, cosmetic armor piece or Tank shield is corrected on the
     * next server tick without duplicating the temporary stack.
     */
    private void enforceRoleInventory(ServerPlayer player, MinigameDefinition definition,
                                      MinigameMatch match, MinigameRole role, int team) {
        boolean carryingFlag = match.flagCarriers.containsValue(player.getUUID());
        if (roleInventoryCorrect(player, role, carryingFlag)) return;

        Map<String, ItemStack> preserved = new LinkedHashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            String name = roleLockedName(stack);
            if (name.isBlank()) continue;
            preserved.putIfAbsent(name, stack.copy());
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
            if (carryingFlag && slot == EquipmentSlot.HEAD) continue;
            ItemStack stack = player.getItemBySlot(slot);
            String name = roleLockedName(stack);
            if (name.isBlank()) continue;
            preserved.putIfAbsent(name, stack.copy());
            player.setItemSlot(slot, ItemStack.EMPTY);
        }

        int color = switch (MinigameGameType.parse(definition.gameType)) {
            case CAPTURE_THE_FLAG -> definition.captureTheFlag.color(team);
            case DOMINATION -> definition.domination.color(team);
            default -> 0xA06540;
        };
        if (!carryingFlag) player.setItemSlot(EquipmentSlot.HEAD,
                preservedOr(preserved, ROLE_TEAM_HELMET,
                        cosmeticLeather(Items.LEATHER_HELMET, color, ROLE_TEAM_HELMET)));
        player.setItemSlot(EquipmentSlot.CHEST,
                preservedOr(preserved, ROLE_TEAM_CHESTPLATE,
                        cosmeticLeather(Items.LEATHER_CHESTPLATE, color, ROLE_TEAM_CHESTPLATE)));
        player.setItemSlot(EquipmentSlot.LEGS,
                preservedOr(preserved, ROLE_TEAM_LEGGINGS,
                        cosmeticLeather(Items.LEATHER_LEGGINGS, color, ROLE_TEAM_LEGGINGS)));
        player.setItemSlot(EquipmentSlot.FEET,
                preservedOr(preserved, ROLE_TEAM_BOOTS,
                        cosmeticLeather(Items.LEATHER_BOOTS, color, ROLE_TEAM_BOOTS)));

        switch (role) {
            case DPS -> {
                player.getInventory().setItem(0, preservedOr(preserved, ROLE_DPS_SWORD,
                        durableRoleItem(Items.DIAMOND_SWORD, ROLE_DPS_SWORD)));
                player.getInventory().setItem(1, preservedOr(preserved, ROLE_DPS_BOW,
                        durableRoleItem(Items.BOW, ROLE_DPS_BOW)));
                ItemStack arrow = preservedOr(preserved, ROLE_DPS_ARROW,
                        namedMatchItem(Items.ARROW, 1, ROLE_DPS_ARROW));
                arrow.setCount(1);
                player.getInventory().setItem(2, arrow);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
            case TANK -> {
                player.getInventory().setItem(0, preservedOr(preserved, ROLE_TANK_SWORD,
                        durableRoleItem(Items.STONE_SWORD, ROLE_TANK_SWORD)));
                player.getInventory().setItem(1, preservedOr(preserved, ROLE_TANK_FIELD,
                        abilityMatchItem(Items.HEART_OF_THE_SEA, ROLE_TANK_FIELD)));
                player.setItemSlot(EquipmentSlot.OFFHAND, preservedOr(preserved, ROLE_TANK_SHIELD,
                        teamShield(player, definition, team)));
            }
            case HEALER -> {
                player.getInventory().setItem(0, preservedOr(preserved, ROLE_HEALER_SWORD,
                        durableRoleItem(Items.STONE_SWORD, ROLE_HEALER_SWORD)));
                player.getInventory().setItem(1, preservedOr(preserved, ROLE_HEAL_SINGLE,
                        abilityMatchItem(Items.AMETHYST_SHARD, ROLE_HEAL_SINGLE)));
                player.getInventory().setItem(2, preservedOr(preserved, ROLE_HEAL_AOE,
                        abilityMatchItem(Items.GLISTERING_MELON_SLICE, ROLE_HEAL_AOE)));
                player.getInventory().setItem(3, preservedOr(preserved, ROLE_HEAL_SELF,
                        abilityMatchItem(Items.GHAST_TEAR, ROLE_HEAL_SELF)));
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
        }
        syncInventory(player);
    }

    private static ItemStack preservedOr(Map<String, ItemStack> preserved, String name, ItemStack fallback) {
        ItemStack value = preserved.get(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static boolean roleInventoryCorrect(ServerPlayer player, MinigameRole role, boolean carryingFlag) {
        if (!carryingFlag && !isNamedMatchItem(player.getItemBySlot(EquipmentSlot.HEAD),
                Items.LEATHER_HELMET, ROLE_TEAM_HELMET)) return false;
        if (!isNamedMatchItem(player.getItemBySlot(EquipmentSlot.CHEST),
                Items.LEATHER_CHESTPLATE, ROLE_TEAM_CHESTPLATE)
                || !isNamedMatchItem(player.getItemBySlot(EquipmentSlot.LEGS),
                Items.LEATHER_LEGGINGS, ROLE_TEAM_LEGGINGS)
                || !isNamedMatchItem(player.getItemBySlot(EquipmentSlot.FEET),
                Items.LEATHER_BOOTS, ROLE_TEAM_BOOTS)) return false;
        boolean expected = switch (role) {
            case DPS -> isNamedMatchItem(player.getInventory().getItem(0), Items.DIAMOND_SWORD, ROLE_DPS_SWORD)
                    && isNamedMatchItem(player.getInventory().getItem(1), Items.BOW, ROLE_DPS_BOW)
                    && isNamedMatchItem(player.getInventory().getItem(2), Items.ARROW, ROLE_DPS_ARROW)
                    && player.getInventory().getItem(2).getCount() == 1
                    && player.getOffhandItem().isEmpty();
            case TANK -> isNamedMatchItem(player.getInventory().getItem(0), Items.STONE_SWORD, ROLE_TANK_SWORD)
                    && isNamedMatchItem(player.getInventory().getItem(1), Items.HEART_OF_THE_SEA, ROLE_TANK_FIELD)
                    && isNamedMatchItem(player.getOffhandItem(), Items.SHIELD, ROLE_TANK_SHIELD);
            case HEALER -> isNamedMatchItem(player.getInventory().getItem(0), Items.STONE_SWORD, ROLE_HEALER_SWORD)
                    && isNamedMatchItem(player.getInventory().getItem(1), Items.AMETHYST_SHARD, ROLE_HEAL_SINGLE)
                    && isNamedMatchItem(player.getInventory().getItem(2), Items.GLISTERING_MELON_SLICE, ROLE_HEAL_AOE)
                    && isNamedMatchItem(player.getInventory().getItem(3), Items.GHAST_TEAR, ROLE_HEAL_SELF)
                    && player.getOffhandItem().isEmpty();
        };
        if (!expected) return false;
        Map<String, Integer> occurrences = new HashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            String name = roleLockedName(player.getInventory().getItem(slot));
            if (!name.isBlank()) occurrences.merge(name, 1, Integer::sum);
        }
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
            if (carryingFlag && slot == EquipmentSlot.HEAD) continue;
            String name = roleLockedName(player.getItemBySlot(slot));
            if (!name.isBlank()) occurrences.merge(name, 1, Integer::sum);
        }
        for (int count : occurrences.values()) if (count > 1) return false;
        return true;
    }

    private static String roleLockedName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null) return "";
        String value = name.getString();
        return ROLE_LOCKED_NAMES.contains(value) ? value : "";
    }

    /** Handles server-authoritative role ability items and their visible vanilla cooldown overlay. */
    public boolean handleRoleAbility(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        MinigameRoleRules rules = roleRules(definition);
        if (match == null || definition == null || rules == null || !rules.enabled
                || match.state != MinigameMatchState.RUNNING || !match.active(player.getUUID())) return false;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;
        String ability = roleAbilityId(held);
        if (ability.isBlank()) return false;
        MinigameRole role = match.role(player.getUUID());
        if (!abilityAllowedForRole(ability, role)) {
            player.sendSystemMessage(Component.literal("That ability does not belong to your assigned role."), true);
            return true;
        }
        if (!roleAbilityReady(match, player, held, ability)) return true;

        MinigameAbilityDefinition abilityDefinition = roleAbilityDefinition(ability, rules);
        if (abilityDefinition == null) return true;
        boolean used = executeRoleAbility(match, rules, player, abilityDefinition);
        if (!used) return true;
        match.performance(player.getUUID()).abilitiesUsed = saturatingAdd(
                match.performance(player.getUUID()).abilitiesUsed, 1L);
        recordActivity(player);
        startRoleCooldown(match, player, held, ability, abilityDefinition.cooldownSeconds());
        return true;
    }

    private static MinigameAbilityDefinition roleAbilityDefinition(String ability, MinigameRoleRules rules) {
        if (rules == null || ability == null) return null;
        return switch (ability) {
            case "tank_slow" -> new MinigameAbilityDefinition("tank_slow", ROLE_TANK_FIELD,
                    MinigameAbilityTarget.ENEMY_AOE, 0.0D, rules.tankSlowRadius,
                    rules.tankSlowCooldownSeconds, 0x5DADE2, 0xA7D8FF,
                    "minecraft:block.beacon.activate", 1.35F, 0.72F,
                    List.of(new MinigameAbilityEffect(MinigameAbilityEffect.Type.SLOW, 1.0D,
                                    rules.tankSlowDurationSeconds * 20, 0),
                            new MinigameAbilityEffect(MinigameAbilityEffect.Type.KNOCKBACK,
                                    rules.tankKnockbackStrength, 0, 0)));
            case "healer_single" -> new MinigameAbilityDefinition("healer_single", ROLE_HEAL_SINGLE,
                    MinigameAbilityTarget.ALLY_RAY, 8.0D, 0.0D,
                    rules.healerSingleHealCooldownSeconds, 0x5CFF8A, 0xD4FFE0,
                    "minecraft:block.amethyst_block.chime", 1.2F, 1.3F,
                    List.of(new MinigameAbilityEffect(MinigameAbilityEffect.Type.HEAL,
                            rules.healerSingleHealAmount, 0, 0)));
            case "healer_aoe" -> new MinigameAbilityDefinition("healer_aoe", ROLE_HEAL_AOE,
                    MinigameAbilityTarget.ALLY_AOE, 0.0D, rules.healerAoeHealRadius,
                    rules.healerAoeHealCooldownSeconds, 0x5CFF8A, 0xC7FFD8,
                    "minecraft:block.beacon.power_select", 1.25F, 1.18F,
                    List.of(new MinigameAbilityEffect(MinigameAbilityEffect.Type.HEAL,
                            rules.healerAoeHealAmount, 0, 0)));
            case "healer_self" -> new MinigameAbilityDefinition("healer_self", ROLE_HEAL_SELF,
                    MinigameAbilityTarget.SELF, 0.0D, 0.0D,
                    rules.healerSelfHealCooldownSeconds, 0xFFF176, 0xFFF9B0,
                    "minecraft:entity.player.levelup", 1.05F, 1.35F,
                    List.of(new MinigameAbilityEffect(MinigameAbilityEffect.Type.HEAL, 0.25D, 0, 0)));
            default -> null;
        };
    }

    private boolean executeRoleAbility(MinigameMatch match, MinigameRoleRules rules, ServerPlayer player,
                                       MinigameAbilityDefinition definition) {
        return switch (definition.id()) {
            case "tank_slow" -> useTankSlow(match, rules, player);
            case "healer_single" -> useHealerSingle(match, rules, player);
            case "healer_aoe" -> useHealerAoe(match, rules, player);
            case "healer_self" -> useHealerSelf(match, rules, player);
            default -> false;
        };
    }

    private static String roleAbilityId(ItemStack stack) {
        if (isNamedMatchItem(stack, Items.HEART_OF_THE_SEA, ROLE_TANK_FIELD)) return "tank_slow";
        if (isNamedMatchItem(stack, Items.AMETHYST_SHARD, ROLE_HEAL_SINGLE)) return "healer_single";
        if (isNamedMatchItem(stack, Items.GLISTERING_MELON_SLICE, ROLE_HEAL_AOE)) return "healer_aoe";
        if (isNamedMatchItem(stack, Items.GHAST_TEAR, ROLE_HEAL_SELF)) return "healer_self";
        return "";
    }

    private static boolean abilityAllowedForRole(String ability, MinigameRole role) {
        return ability.startsWith("tank_") ? role == MinigameRole.TANK
                : ability.startsWith("healer_") && role == MinigameRole.HEALER;
    }

    private boolean roleAbilityReady(MinigameMatch match, ServerPlayer player, ItemStack held, String ability) {
        long available = match.roleCooldowns
                .computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>())
                .getOrDefault(ability, 0L);
        if (serverTicks >= available) return true;
        int remaining = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, available - serverTicks));
        player.getCooldowns().addCooldown(held, remaining);
        player.sendSystemMessage(Component.literal("Ability cooldown: "
                + String.format(Locale.ROOT, "%.1f", remaining / 20.0D) + "s"), true);
        return false;
    }

    private void startRoleCooldown(MinigameMatch match, ServerPlayer player, ItemStack held,
                                   String ability, int seconds) {
        int ticks = Math.max(1, seconds * 20);
        match.roleCooldowns.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>())
                .put(ability, safeAdd(serverTicks, ticks));
        player.getCooldowns().addCooldown(held, ticks);
    }

    private boolean useTankSlow(MinigameMatch match, MinigameRoleRules rules, ServerPlayer tank) {
        ArrayList<ServerPlayer> targets = new ArrayList<>();
        int team = match.team(tank.getUUID());
        double radius = rules.tankSlowRadius;
        double radiusSqr = radius * radius;
        for (ServerPlayer candidate : tank.level().getEntitiesOfClass(ServerPlayer.class,
                tank.getBoundingBox().inflate(radius))) {
            if (candidate == tank || !match.active(candidate.getUUID())
                    || match.team(candidate.getUUID()) == team
                    || candidate.distanceToSqr(tank) > radiusSqr) continue;
            targets.add(candidate);
        }
        MobEffect slow = BuiltInRegistries.MOB_EFFECT.getOptional(Identifier.parse("minecraft:slowness")).orElse(null);
        for (ServerPlayer target : targets) {
            if (slow != null) {
                target.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(slow),
                        rules.tankSlowDurationSeconds * 20, 0, false, true, true));
            }
            if (rules.tankKnockbackStrength > 0.0D) {
                Vec3 away = target.position().subtract(tank.position());
                Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
                if (horizontal.lengthSqr() < 1.0E-6D) {
                    Vec3 look = tank.getLookAngle();
                    horizontal = new Vec3(look.x, 0.0D, look.z);
                }
                horizontal = horizontal.normalize().scale(rules.tankKnockbackStrength);
                Vec3 current = target.getDeltaMovement();
                double upward = Math.max(current.y, Math.min(0.45D, 0.16D + rules.tankKnockbackStrength * 0.08D));
                target.setDeltaMovement(horizontal.x, upward, horizontal.z);
                target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(target.getId(), target.getDeltaMovement()));
            }
        }
        roleBurst(tank, 0x5DADE2, radius);
        roleVerticalBurst(tank, 0xA7D8FF, radius);
        playRoleSound(match, tank, "minecraft:entity.lightning_bolt.impact", 1.35F, 0.92F);
        tank.sendSystemMessage(Component.literal(targets.isEmpty()
                ? "Defensive field activated, but no enemy was inside the AOE."
                : "Defensive field slowed and pushed " + targets.size() + " enemy player(s)."), true);
        return true;
    }

    private boolean useHealerSingle(MinigameMatch match, MinigameRoleRules rules, ServerPlayer healer) {
        ServerPlayer target = raycastFriendlyPlayer(match, healer, 8.0D);
        Vec3 beamEnd = target == null
                ? healer.getEyePosition().add(healer.getLookAngle().normalize().scale(8.0D))
                : target.getBoundingBox().getCenter();
        roleBeam(healer, beamEnd, 0x5CFF8A);
        roleImpact(healer, beamEnd, 0xD4FFE0);
        playRoleSound(match, healer, "minecraft:block.amethyst_block.chime", 1.2F, 1.3F);
        if (target == null) {
            healer.sendSystemMessage(Component.literal("Healing beam missed; cooldown consumed."), true);
            return true;
        }
        boolean healed = healAndRecord(match, healer, target, rules.healerSingleHealAmount);
        healer.sendSystemMessage(Component.literal(healed
                ? "Healed " + target.getName().getString() + " for up to "
                    + formatHealth(rules.healerSingleHealAmount) + " hearts."
                : "The healing beam hit " + target.getName().getString()
                    + ", but they were already at full health."), true);
        return true;
    }

    private boolean useHealerAoe(MinigameMatch match, MinigameRoleRules rules, ServerPlayer healer) {
        int team = match.team(healer.getUUID());
        int healed = 0;
        double radius = rules.healerAoeHealRadius;
        double radiusSqr = radius * radius;
        for (ServerPlayer candidate : healer.level().getEntitiesOfClass(ServerPlayer.class,
                healer.getBoundingBox().inflate(radius))) {
            if (!match.active(candidate.getUUID())
                    || match.team(candidate.getUUID()) != team
                    || candidate.distanceToSqr(healer) > radiusSqr) continue;
            if (healAndRecord(match, healer, candidate, rules.healerAoeHealAmount)) healed++;
        }
        roleBurst(healer, 0x5CFF8A, radius);
        roleVerticalBurst(healer, 0xC7FFD8, radius);
        playRoleSound(match, healer, "minecraft:block.beacon.power_select", 1.25F, 1.18F);
        healer.sendSystemMessage(Component.literal(healed == 0
                ? "AOE heal activated, but no injured ally was inside the AOE."
                : "AOE healed " + healed + " allied player(s) for up to "
                    + formatHealth(rules.healerAoeHealAmount) + " hearts each."), true);
        return true;
    }

    private boolean useHealerSelf(MinigameMatch match, MinigameRoleRules rules, ServerPlayer healer) {
        double amount = healer.getMaxHealth() * 0.25D;
        boolean healed = healAndRecord(match, healer, healer, amount);
        roleBurst(healer, 0xFFF176, 1.5D);
        roleVerticalBurst(healer, 0xFFF9B0, 1.25D);
        playRoleSound(match, healer, "minecraft:entity.player.levelup", 1.05F, 1.35F);
        healer.sendSystemMessage(Component.literal(healed
                ? "Self heal restored 25% of your maximum health."
                : "Self heal activated at full health; cooldown consumed."), true);
        return true;
    }

    private boolean healAndRecord(MinigameMatch match, ServerPlayer healer,
                                  ServerPlayer target, double amount) {
        if (match == null || healer == null || target == null) return false;
        float before = target.getHealth();
        boolean healed = healPlayer(target, amount);
        if (!healed) return false;
        long hundredths = Math.max(0L, Math.round((target.getHealth() - before) * 100.0F));
        match.performance(healer.getUUID()).healingDone = saturatingAdd(
                match.performance(healer.getUUID()).healingDone, hundredths);
        MinigameDefinition definition = definitions.get(match.minigameId);
        if (definition != null && hundredths > 0L && match.rewardsEnabled) {
            SimpleServerUtilities.STATISTICS.increment(healer, StatisticEventType.MINIGAME_HEALING,
                    definition.id, hundredths);
            publish(healer, ContentEventTypes.MINIGAME_HEALING, definition.id, hundredths,
                    Map.of("match", match.id.toString(), "target", target.getUUID().toString()));
        }
        return true;
    }

    private static boolean healPlayer(ServerPlayer player, double amount) {
        float before = player.getHealth();
        float after = Math.min(player.getMaxHealth(), before + (float) Math.max(0.0D, amount));
        if (after <= before) return false;
        player.setHealth(after);
        return true;
    }

    private static String formatHealth(double healthPoints) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0D, healthPoints) / 2.0D);
    }

    private ServerPlayer raycastFriendlyPlayer(MinigameMatch match, ServerPlayer healer, double range) {
        Vec3 start = healer.getEyePosition();
        Vec3 look = healer.getLookAngle().normalize();
        int team = match.team(healer.getUUID());
        ServerPlayer selected = null;
        double selectedDistance = Double.MAX_VALUE;
        for (UUID playerId : match.teams.keySet()) {
            if (playerId.equals(healer.getUUID()) || !match.active(playerId)
                    || match.team(playerId) != team) continue;
            ServerPlayer candidate = server.getPlayerList().getPlayer(playerId);
            if (candidate == null || candidate.level() != healer.level() || !healer.hasLineOfSight(candidate)) continue;
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 offset = center.subtract(start);
            double along = offset.dot(look);
            if (along < 0.0D || along > range || along >= selectedDistance) continue;
            Vec3 closest = start.add(look.scale(along));
            double hitRadius = Math.max(0.75D, candidate.getBbWidth() * 0.75D);
            if (center.distanceToSqr(closest) > hitRadius * hitRadius) continue;
            selected = candidate;
            selectedDistance = along;
        }
        return selected;
    }

    private static void roleBeam(ServerPlayer source, Vec3 end, int color) {
        if (!(source.level() instanceof ServerLevel level) || end == null) return;
        Vec3 start = source.getEyePosition();
        Vec3 delta = end.subtract(start);
        int steps = Math.max(4, (int) Math.ceil(delta.length() * 7.0D));
        DustParticleOptions core = new DustParticleOptions(color & 0x00FFFFFF, 1.15F);
        DustParticleOptions glow = new DustParticleOptions(0xE8FFF0, 0.65F);
        for (int step = 0; step <= steps; step++) {
            Vec3 point = start.add(delta.scale(step / (double) steps));
            level.sendParticles(core, point.x, point.y, point.z, 1, 0.008D, 0.008D, 0.008D, 0.0D);
            if (step % 3 == 0) level.sendParticles(glow, point.x, point.y, point.z,
                    1, 0.025D, 0.025D, 0.025D, 0.0D);
        }
    }

    private static void roleImpact(ServerPlayer source, Vec3 point, int color) {
        if (!(source.level() instanceof ServerLevel level) || point == null) return;
        DustParticleOptions dust = new DustParticleOptions(color & 0x00FFFFFF, 1.0F);
        level.sendParticles(dust, point.x, point.y, point.z, 18, 0.22D, 0.22D, 0.22D, 0.01D);
    }

    private static void roleBurst(ServerPlayer source, int color, double radius) {
        if (!(source.level() instanceof ServerLevel level)) return;
        DustParticleOptions dust = new DustParticleOptions(color & 0x00FFFFFF, 1.15F);
        int particles = Math.max(32, (int) Math.ceil(radius * 18.0D));
        for (int index = 0; index < particles; index++) {
            double angle = Math.PI * 2.0D * index / particles;
            double x = source.getX() + Math.cos(angle) * radius;
            double z = source.getZ() + Math.sin(angle) * radius;
            level.sendParticles(dust, x, source.getY() + 0.8D, z, 1, 0.03D, 0.10D, 0.03D, 0.0D);
        }
    }

    private static void roleVerticalBurst(ServerPlayer source, int color, double radius) {
        if (!(source.level() instanceof ServerLevel level)) return;
        DustParticleOptions dust = new DustParticleOptions(color & 0x00FFFFFF, 0.85F);
        int columns = 12;
        for (int index = 0; index < columns; index++) {
            double angle = Math.PI * 2.0D * index / columns;
            double distance = radius * (0.35D + 0.65D * (index % 3) / 2.0D);
            double x = source.getX() + Math.cos(angle) * distance;
            double z = source.getZ() + Math.sin(angle) * distance;
            level.sendParticles(dust, x, source.getY() + 0.3D, z, 5,
                    0.04D, 0.55D, 0.04D, 0.02D);
        }
    }

    private void playRoleSound(MinigameMatch match, ServerPlayer source, String soundId,
                               float volume, float pitch) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(soundId)).orElse(null);
        if (sound == null) return;
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer listener = server.getPlayerList().getPlayer(playerId);
            if (listener == null || listener.level() != source.level()) continue;
            listener.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                    source.getX(), source.getY(), source.getZ(), volume, pitch,
                    serverTicks ^ source.getUUID().getLeastSignificantBits()
                            ^ listener.getUUID().getMostSignificantBits()));
        }
    }

    /** Adds the configured DPS arrow effect only when an assigned DPS hits an enemy player. */
    public void handleRoleProjectileImpact(Projectile projectile, HitResult hitResult) {
        if (!(projectile instanceof AbstractArrow) || !(hitResult instanceof EntityHitResult entityHit)
                || !(projectile.getOwner() instanceof ServerPlayer attacker)
                || !(entityHit.getEntity() instanceof ServerPlayer victim)) return;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(attacker.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        MinigameRoleRules rules = roleRules(definition);
        if (match == null || definition == null || rules == null || !rules.enabled
                || match.state != MinigameMatchState.RUNNING
                || match.role(attacker.getUUID()) != MinigameRole.DPS
                || !match.active(attacker.getUUID()) || !match.active(victim.getUUID())
                || match.team(attacker.getUUID()) == match.team(victim.getUUID())) return;
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(
                Identifier.parse(rules.dpsArrowEffect)).orElse(null);
        if (effect == null) return;
        victim.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                rules.dpsArrowEffectDurationSeconds * 20, rules.dpsArrowEffectAmplifier,
                false, true, true));
    }

    private void tickSpleefProjectiles(MinigameMatch match, MinigameDefinition definition) {
        SpleefRules rules = definition.spleef;
        long elapsedSeconds = Math.max(0L, (serverTicks - match.stateStartedTick) / 20L);
        if (rules.standardProjectileEnabled && !match.spleefStandardProjectileUnlocked
                && elapsedSeconds >= rules.standardProjectileUnlockSeconds) {
            match.spleefStandardProjectileUnlocked = true;
            announce(match, "The infinite Spleef projectile is now available. It breaks one floor block and has a cooldown.");
        }
        if (match.spleefStandardProjectileUnlocked) {
            for (UUID playerId : match.teams.keySet()) {
                if (!match.active(playerId)) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) continue;
                int standardCount = countNamedMatchItem(player, Items.SNOWBALL, "Infinite Spleef Projectile");
                if (standardCount <= 0) {
                    giveNamedMatchItem(player, Items.SNOWBALL, 1, "Infinite Spleef Projectile");
                } else if (standardCount > 1) {
                    trimNamedMatchItemCount(player, Items.SNOWBALL, "Infinite Spleef Projectile", 1);
                }
            }
        }
        if (!rules.burstProjectileEnabled) return;
        if (!match.spleefBurstScheduleStarted && elapsedSeconds >= rules.burstProjectileStartSeconds) {
            match.spleefBurstScheduleStarted = true;
            match.spleefNextBurstGrantTick = safeAdd(serverTicks,
                    randomInt(match, rules.burstProjectileMinIntervalSeconds,
                            rules.burstProjectileMaxIntervalSeconds, 901) * 20L);
            announce(match, "Power projectiles have entered the match and will be awarded to random players.");
        }
        if (!match.spleefBurstScheduleStarted || serverTicks < match.spleefNextBurstGrantTick) return;
        ArrayList<ServerPlayer> eligible = new ArrayList<>();
        for (UUID playerId : match.teams.keySet()) {
            if (!match.active(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && countNamedMatchItem(player, Items.EGG, "Power Spleef Projectile") < rules.burstProjectileMaximumStack) eligible.add(player);
        }
        if (!eligible.isEmpty()) {
            ServerPlayer recipient = eligible.get(randomInt(match, 0, eligible.size() - 1, (int) serverTicks));
            if (giveNamedMatchItem(recipient, Items.EGG, 1, "Power Spleef Projectile")) {
                recipient.sendSystemMessage(Component.literal("You received a Power Spleef Projectile. It breaks a five-block cross."), true);
            }
        }
        match.spleefNextBurstGrantTick = safeAdd(serverTicks,
                randomInt(match, rules.burstProjectileMinIntervalSeconds,
                        rules.burstProjectileMaxIntervalSeconds, (int) (serverTicks ^ 0x51EAF00DL)) * 20L);
    }

    private static int countNamedMatchItem(ServerPlayer player, net.minecraft.world.item.Item item,
                                           String expectedName) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isNamedMatchItem(stack, item, expectedName)) count += stack.getCount();
        }
        return count;
    }

    private static boolean isNamedMatchItem(ItemStack stack, net.minecraft.world.item.Item item,
                                            String expectedName) {
        if (stack == null || stack.isEmpty() || stack.getItem() != item) return false;
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName != null && expectedName.equals(customName.getString());
    }

    private static ItemStack findNamedMatchItem(ServerPlayer player, net.minecraft.world.item.Item item,
                                                String expectedName) {
        if (player == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isNamedMatchItem(stack, item, expectedName)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Keeps a temporary match item at an exact upper bound after vanilla use processing. */
    private static void trimNamedMatchItemCount(ServerPlayer player, net.minecraft.world.item.Item item,
                                                String expectedName, int maximum) {
        if (player == null) return;
        int remaining = Math.max(0, maximum);
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isNamedMatchItem(stack, item, expectedName)) continue;
            int keep = Math.min(stack.getCount(), remaining);
            if (keep <= 0) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                changed = true;
            } else {
                if (stack.getCount() != keep) {
                    stack.setCount(keep);
                    changed = true;
                }
                remaining -= keep;
            }
        }
        if (changed) syncInventory(player);
    }

    private static boolean giveNamedMatchItem(ServerPlayer player, net.minecraft.world.item.Item item,
                                              int count, String name) {
        ItemStack stack = namedMatchItem(item, count, name);
        boolean added = player.getInventory().add(stack);
        if (added) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return added;
    }

    /** Raises the Tank's offhand shield without allowing the targeted block/entity to be used. */
    public boolean handleRoleShieldUse(ServerPlayer player) {
        if (player == null) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        MinigameRoleRules rules = roleRules(definition);
        if (match == null || definition == null || rules == null || !rules.enabled
                || match.state != MinigameMatchState.RUNNING || !match.active(player.getUUID())
                || match.role(player.getUUID()) != MinigameRole.TANK
                || !isNamedMatchItem(player.getOffhandItem(), Items.SHIELD, ROLE_TANK_SHIELD)) return false;
        player.startUsingItem(InteractionHand.OFF_HAND);
        return true;
    }

    /** Starts the DPS bow even when the crosshair is on a protected block or entity. */
    public boolean handleRoleBowUse(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        MinigameRoleRules rules = roleRules(definition);
        ItemStack held = player.getItemInHand(hand);
        if (match == null || definition == null || rules == null || !rules.enabled
                || match.state != MinigameMatchState.RUNNING || !match.active(player.getUUID())
                || match.role(player.getUUID()) != MinigameRole.DPS
                || !isNamedMatchItem(held, Items.BOW, ROLE_DPS_BOW)) return false;
        player.startUsingItem(hand);
        return true;
    }

    /** Allows only the two controlled Spleef projectile items to use vanilla throwing behavior. */
    public boolean allowRightClickItem(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        if (match == null || definition == null || match.state != MinigameMatchState.RUNNING
                || !match.active(player.getUUID())) return false;
        MinigameRoleRules activeRoleRules = roleRules(definition);
        ItemStack held = player.getItemInHand(hand);
        if (activeRoleRules != null && activeRoleRules.enabled) {
            MinigameRole role = match.role(player.getUUID());
            if (role == MinigameRole.DPS && isNamedMatchItem(held, Items.BOW, ROLE_DPS_BOW)) return true;
            if (role == MinigameRole.TANK && hand == InteractionHand.OFF_HAND
                    && isNamedMatchItem(held, Items.SHIELD, ROLE_TANK_SHIELD)) return true;
        }
        if (MinigameGameType.parse(definition.gameType) != MinigameGameType.SPLEEF) return false;
        if (held.isEmpty()) return false;
        if (isNamedMatchItem(held, Items.SNOWBALL, "Infinite Spleef Projectile")
                && definition.spleef.standardProjectileEnabled && match.spleefStandardProjectileUnlocked) {
            long availableTick = match.spleefStandardProjectileCooldowns.getOrDefault(player.getUUID(), 0L);
            if (serverTicks < availableTick || player.getCooldowns().isOnCooldown(held)) {
                int remainingTicks = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, availableTick - serverTicks));
                if (remainingTicks > 0) player.getCooldowns().addCooldown(held, remainingTicks);
                long tenths = Math.max(1L, (Math.max(1L, availableTick - serverTicks) + 1L) / 2L);
                player.sendSystemMessage(Component.literal("Projectile cooldown: " + (tenths / 10.0D) + "s"), true);
                syncInventory(player);
                return false;
            }
            int cooldownTicks = Math.max(1, definition.spleef.standardProjectileCooldownSeconds * 20);
            match.spleefStandardProjectileCooldowns.put(player.getUUID(), safeAdd(serverTicks, cooldownTicks));
            // Keep one visible infinite projectile after vanilla consumes the thrown copy.
            if (held.getCount() == 1) held.grow(1);
            syncInventory(player);
            return true;
        }
        return isNamedMatchItem(held, Items.EGG, "Power Spleef Projectile")
                && definition.spleef.burstProjectileEnabled;
    }

    public boolean isControlledSpleefProjectile(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        if (match == null || definition == null || match.state != MinigameMatchState.RUNNING
                || MinigameGameType.parse(definition.gameType) != MinigameGameType.SPLEEF) return false;
        ItemStack held = player.getItemInHand(hand);
        return isNamedMatchItem(held, Items.SNOWBALL, "Infinite Spleef Projectile")
                || isNamedMatchItem(held, Items.EGG, "Power Spleef Projectile");
    }

    private static void syncInventory(ServerPlayer player) {
        if (player == null) return;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    /** Converts SSU Spleef throws into fast, gravity-free, zero-spread projectiles. */
    public void prepareSpleefProjectile(Projectile projectile) {
        if (projectile == null || !(projectile.getOwner() instanceof ServerPlayer player)) return;
        MinigameMatch match;
        MinigameDefinition definition;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
        }
        if (match == null || definition == null || match.state != MinigameMatchState.RUNNING
                || !match.active(player.getUUID())
                || MinigameGameType.parse(definition.gameType) != MinigameGameType.SPLEEF) return;
        String projectileId = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString();
        boolean standard = "minecraft:snowball".equals(projectileId)
                && definition.spleef.standardProjectileEnabled && match.spleefStandardProjectileUnlocked;
        boolean burst = "minecraft:egg".equals(projectileId) && definition.spleef.burstProjectileEnabled;
        if (!standard && !burst) return;
        Vec3 direction = player.getLookAngle();
        if (direction.lengthSqr() < 1.0E-8D) return;
        direction = direction.normalize();
        projectile.setNoGravity(true);
        projectile.setDeltaMovement(direction.scale(standard ? 2.65D : 2.35D));
        projectile.setYRot(player.getYRot());
        projectile.setXRot(player.getXRot());
        if (standard) {
            ItemStack cooldownStack = findNamedMatchItem(player, Items.SNOWBALL, "Infinite Spleef Projectile");
            if (!cooldownStack.isEmpty()) {
                int cooldownTicks = Math.max(1, definition.spleef.standardProjectileCooldownSeconds * 20);
                player.getCooldowns().addCooldown(cooldownStack, cooldownTicks);
                syncInventory(player);
            }
        }
    }

    /** Breaks only configured Spleef floor blocks when one of SSU's temporary projectiles impacts. */
    public boolean handleSpleefProjectileImpact(Projectile projectile, HitResult hitResult) {
        if (projectile == null || !(projectile.getOwner() instanceof ServerPlayer player)) return false;
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            arena = definition == null || match == null ? null : arena(definition, match.arenaId);
        }
        if (match == null || definition == null || arena == null
                || match.state != MinigameMatchState.RUNNING || !match.active(player.getUUID())
                || MinigameGameType.parse(definition.gameType) != MinigameGameType.SPLEEF) return false;
        String projectileId = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).toString();
        boolean standard = "minecraft:snowball".equals(projectileId);
        boolean burst = "minecraft:egg".equals(projectileId);
        if ((!standard || !definition.spleef.standardProjectileEnabled)
                && (!burst || !definition.spleef.burstProjectileEnabled)) return false;
        if (hitResult instanceof BlockHitResult blockHit) {
            if (standard) breakSpleefProjectileBlock(player, arena, definition, blockHit.getBlockPos());
            else {
                BlockPos center = blockHit.getBlockPos();
                breakSpleefProjectileBlock(player, arena, definition, center);
                for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    breakSpleefProjectileBlock(player, arena, definition, center.relative(direction));
                }
            }
        }
        projectile.discard();
        return true;
    }

    private void breakSpleefProjectileBlock(ServerPlayer player, MinigameArenaDefinition arena,
                                            MinigameDefinition definition, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null || !region.contains(level.dimension(), pos)) return;
        if (arena.playFloor != null && arena.playFloor.configured()
                && !arena.playFloor.contains(level.dimension(), pos)) return;
        BlockState state = level.getBlockState(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!definition.spleef.canBreak(blockId)) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private void tickSpleef(MinigameMatch match, MinigameDefinition definition, MinigameArenaDefinition arena) {
        tickSpleefProjectiles(match, definition);
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

    /** Blocks vanilla hunger, potion and other automatic healing in live respawn modes.
     * Healer abilities and SSU regeneration boosts use controlled direct health updates. */
    public boolean shouldCancelAutomaticHealing(ServerPlayer player) {
        if (player == null) return false;
        synchronized (this) {
            MinigameMatch match = matchFor(player.getUUID());
            if (match == null || match.state != MinigameMatchState.RUNNING) return false;
            MinigameDefinition definition = definitions.get(match.minigameId);
            if (definition == null) return false;
            MinigameGameType type = MinigameGameType.parse(definition.gameType);
            return type == MinigameGameType.CAPTURE_THE_FLAG || type == MinigameGameType.DOMINATION;
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
            if (victimMatch.state != MinigameMatchState.RUNNING || !victimMatch.active(victim.getUUID())) return true;
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

    /** Immediately refreshes this player's in-match game and spectator border overlays. */
    public void syncRuntimeBorders(ServerPlayer player) {
        syncRuntimeBorders(player, true);
    }

    private void syncRuntimeBorders(ServerPlayer player, boolean force) {
        if (player == null) return;
        MinigameMatch match;
        MinigameDefinition definition;
        MinigameArenaDefinition arena;
        synchronized (this) {
            match = matchFor(player.getUUID());
            definition = match == null ? null : definitions.get(match.minigameId);
            arena = definition == null || match == null ? null : arena(definition, match.arenaId);
        }

        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        Region gameRegion = arena == null ? null : SimpleServerUtilities.REGIONS.get(arena.regionId);
        boolean showGame = match != null && arena != null && gameRegion != null
                && preferences.isMinigameGameBorderVisible();
        boolean showSpectator = match != null && arena != null && arena.spectatorBounds.configured()
                && preferences.isMinigameSpectatorBorderVisible();
        String playerDimension = player.level().dimension().identifier().toString();
        RuntimeBorderSyncState next = new RuntimeBorderSyncState(
                match == null ? null : match.id, playerDimension, showGame, showSpectator,
                SimpleServerUtilities.BORDER_SETTINGS.revision(),
                showGame ? gameRegion.getDimension().identifier().toString() : "",
                showSpectator ? arena.spectatorBounds.dimension : ""
        );
        RuntimeBorderSyncState previous;
        synchronized (this) { previous = runtimeBorderSyncStates.get(player.getUUID()); }
        if (!force && next.equals(previous)) return;

        if (showGame) {
            BorderVisualizationPayload.Entry entry = new BorderVisualizationPayload.Entry(
                    BorderCategory.MINIGAME_GAME_AREA,
                    definition.displayName + " game border",
                    settings.getStrokeArgb(BorderCategory.MINIGAME_GAME_AREA),
                    settings.getFillArgb(BorderCategory.MINIGAME_GAME_AREA),
                    MINIGAME_GAME_BORDER_WIDTH,
                    true,
                    List.of(new BorderVisualizationPayload.Box(
                            gameRegion.getMinX(), gameRegion.getMinY(), gameRegion.getMinZ(),
                            gameRegion.getMaxX(), gameRegion.getMaxY(), gameRegion.getMaxZ()
                    )),
                    List.of()
            );
            PacketDistributor.sendToPlayer(player, new BorderVisualizationPayload(
                    BorderLayer.MINIGAME_GAME, true, gameRegion.getDimension().identifier().toString(),
                    settings.getClaimVerticalRange(), settings.getRegionRenderDistanceBlocks(), List.of(entry)
            ));
        } else {
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.MINIGAME_GAME));
        }

        if (showSpectator) {
            MinigameAreaBounds bounds = arena.spectatorBounds;
            BorderVisualizationPayload.Entry entry = new BorderVisualizationPayload.Entry(
                    BorderCategory.MINIGAME_SPECTATOR_AREA,
                    definition.displayName + " spectator border",
                    settings.getStrokeArgb(BorderCategory.MINIGAME_SPECTATOR_AREA),
                    settings.getFillArgb(BorderCategory.MINIGAME_SPECTATOR_AREA),
                    MINIGAME_SPECTATOR_BORDER_WIDTH,
                    true,
                    List.of(new BorderVisualizationPayload.Box(
                            bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ
                    )),
                    List.of()
            );
            PacketDistributor.sendToPlayer(player, new BorderVisualizationPayload(
                    BorderLayer.MINIGAME_SPECTATOR, true, bounds.dimension,
                    settings.getClaimVerticalRange(), settings.getRegionRenderDistanceBlocks(), List.of(entry)
            ));
        } else {
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.MINIGAME_SPECTATOR));
        }
        synchronized (this) { runtimeBorderSyncStates.put(player.getUUID(), next); }
    }

    private void clearRuntimeBorders(ServerPlayer player) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.MINIGAME_GAME));
        PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.MINIGAME_SPECTATOR));
        synchronized (this) { runtimeBorderSyncStates.remove(player.getUUID()); }
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
            MinigameRoleRules activeRoleRules = roleRules(definition);
            if (activeRoleRules != null && activeRoleRules.enabled)
                lines.add("Role: " + match.role(playerId).label());
            MinigameMatch.PendingRespawn pendingRespawn = match.pendingRespawns.get(playerId);
            if (pendingRespawn != null) {
                long respawnSeconds = Math.max(1L,
                        (pendingRespawn.completesTick - serverTicks + 19L) / 20L);
                lines.add("Respawning in: " + respawnSeconds + "s");
            }
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
            syncRuntimeBorders(player, false);
        }
    }

    private static String formatSeconds(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(Locale.ROOT, "%d:%02d", safe / 60L, safe % 60L);
    }

    private void clearHud(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, MinigameHudPayload.clear());
        PacketDistributor.sendToPlayer(player, MinigameCastBarPayload.clear());
        clearRespawnTitle(player);
        clearRuntimeBorders(player);
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
        if (definition == null || definition.arenas.isEmpty()) return null;
        int start = Math.floorMod(arenaRotationCursors.getOrDefault(definition.id, 0), definition.arenas.size());
        for (int offset = 0; offset < definition.arenas.size(); offset++) {
            int index = Math.floorMod(start + offset, definition.arenas.size());
            MinigameArenaDefinition arena = definition.arenas.get(index);
            if (!arena.enabled) continue;
            String key = arenaKey(definition.id, arena.id);
            if (!arenaReservations.containsKey(key) && !resettingArenas.contains(key) && !blockedArenas.contains(key)) {
                arenaRotationCursors.put(definition.id, Math.floorMod(index + 1, definition.arenas.size()));
                return arena;
            }
        }
        return null;
    }

    private synchronized void rotateAfterArena(MinigameDefinition definition, String previousArenaId) {
        if (definition == null || definition.arenas.isEmpty()) return;
        for (int index = 0; index < definition.arenas.size(); index++) {
            if (definition.arenas.get(index).id.equals(previousArenaId)) {
                arenaRotationCursors.put(definition.id, Math.floorMod(index + 1, definition.arenas.size()));
                return;
            }
        }
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

    private void announceImportant(MinigameMatch match, String title, String detail) {
        if (match == null) return;
        for (UUID playerId : match.teams.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) sendImportantMessage(player, title, detail);
        }
    }

    private static void sendImportantMessage(ServerPlayer player, String title, String detail) {
        if (player == null) return;
        String safeTitle = title == null || title.isBlank() ? "Minigame update" : title;
        String safeDetail = detail == null ? "" : detail;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(safeTitle)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(safeDetail)));
        player.sendSystemMessage(Component.literal(safeTitle + (safeDetail.isBlank() ? "" : ": " + safeDetail)));
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

    private void sendDiagnostics(ServerPlayer player, String notice, boolean error, boolean includeIntegrity,
                                 long requestId) {
        ArrayList<MinigameDiagnosticsPayload.Line> lines = new ArrayList<>();
        synchronized (this) {
            int queued = queues.values().stream().mapToInt(Map::size).sum();
            int activeBoosts = matches.values().stream().mapToInt(match -> match.activeBoosts.size()).sum();
            int pendingRespawns = matches.values().stream().mapToInt(match -> match.pendingRespawns.size()).sum();
            int disconnectGrace = matches.values().stream().mapToInt(match -> match.disconnected.size()).sum();
            int casts = matches.values().stream().mapToInt(match -> match.ctfCasts.size() + match.dominationCasts.size()).sum();
            lines.add(diag("info", "Definitions", definitions.size() + " configured"));
            lines.add(diag(queued > 0 ? "info" : "ok", "Queues", queued + " player(s) in " + queues.size() + " queue(s)"));
            lines.add(diag(matches.isEmpty() ? "ok" : "info", "Live matches", matches.size() + " active lifecycle(s)"));
            lines.add(diag(arenaReservations.isEmpty() ? "ok" : "info", "Arena reservations", arenaReservations.size() + " reserved"));
            lines.add(diag(resettingArenas.isEmpty() ? "ok" : "warning", "Arena resets", resettingArenas.size() + " resetting"));
            lines.add(diag(blockedArenas.isEmpty() ? "ok" : "warning", "Blocked arenas", blockedArenas.size() + " blocked"));
            lines.add(diag(unsafeArenas.isEmpty() ? "ok" : "error", "Unsafe arena markers", unsafeArenas.size() + " require recovery"));
            lines.add(diag(recoveries.isEmpty() ? "ok" : "warning", "Player recoveries", recoveries.size() + " pending"));
            lines.add(diag(recoverySafetyHalted ? "error" : "ok", "Recovery storage",
                    recoverySafetyHalted ? "New state replacement is paused" : "Operational"));
            lines.add(diag(disconnectGrace == 0 ? "ok" : "info", "Rejoin grace", disconnectGrace + " disconnected participant(s)"));
            lines.add(diag(pendingRespawns == 0 ? "ok" : "info", "Pending respawns", Integer.toString(pendingRespawns)));
            lines.add(diag(casts == 0 ? "ok" : "info", "Objective casts", Integer.toString(casts)));
            lines.add(diag(activeBoosts == 0 ? "ok" : "info", "Active boosts", Integer.toString(activeBoosts)));
            lines.add(diag("info", "Progression profiles", Integer.toString(progression.players.size())));
            lines.add(diag("info", "Settlement receipts", progression.settledMatches.size()
                    + "/" + MinigameProgressionData.MAX_SETTLEMENTS));
            lines.add(diag("info", "Match history", history.matches.size() + "/" + MinigameMatchHistory.MAX_MATCHES));

            for (MinigameDefinition definition : definitions.values()) {
                int valid = 0, warning = 0, invalid = 0;
                for (MinigameArenaDefinition arena : definition.arenas) {
                    MinigameArenaValidation.Report report = MinigameArenaValidation.validate(server, definition, arena);
                    if (report.errors() > 0) invalid++;
                    else if (report.warnings() > 0) warning++;
                    else valid++;
                    if (includeIntegrity && (report.errors() > 0 || report.warnings() > 0)) {
                        lines.add(diag(report.errors() > 0 ? "error" : "warning",
                                definition.displayName + " / " + arena.displayName,
                                report.errors() + " error(s), " + report.warnings() + " warning(s)"));
                    }
                }
                lines.add(diag(invalid > 0 ? "error" : warning > 0 ? "warning" : "ok",
                        definition.displayName,
                        valid + " valid, " + warning + " warning, " + invalid + " invalid arena(s)"));
            }
        }
        PacketDistributor.sendToPlayer(player, new MinigameDiagnosticsPayload(
                "Minigame System Health", notice, error, lines, requestId));
    }

    private static MinigameDiagnosticsPayload.Line diag(String severity, String label, String value) {
        return new MinigameDiagnosticsPayload.Line(severity, label, value);
    }

    private synchronized int cleanOrphanedRuntimeData() {
        int cleaned = 0;
        for (var iterator = playerMatches.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            MinigameMatch match = matches.get(entry.getValue());
            if (match == null || !match.teams.containsKey(entry.getKey())) {
                iterator.remove();
                cleaned++;
            }
        }
        for (var iterator = playerQueues.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, String> entry = iterator.next();
            Map<UUID, Long> queue = queues.get(entry.getValue());
            if (queue == null || !queue.containsKey(entry.getKey()) || playerMatches.containsKey(entry.getKey())) {
                iterator.remove();
                playerRolePreferences.remove(entry.getKey());
                cleaned++;
            }
        }
        for (Map.Entry<String, LinkedHashMap<UUID, Long>> queueEntry : queues.entrySet()) {
            for (var iterator = queueEntry.getValue().keySet().iterator(); iterator.hasNext();) {
                UUID playerId = iterator.next();
                if (playerMatches.containsKey(playerId)
                        || !queueEntry.getKey().equals(playerQueues.get(playerId))) {
                    iterator.remove();
                    cleaned++;
                }
            }
        }
        for (var iterator = arenaReservations.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, UUID> entry = iterator.next();
            MinigameMatch match = matches.get(entry.getValue());
            if (match == null || !entry.getKey().equals(arenaKey(match.minigameId, match.arenaId))) {
                iterator.remove();
                cleaned++;
            }
        }
        spectatorCursors.keySet().removeIf(playerId -> !playerMatches.containsKey(playerId));
        runtimeBorderSyncStates.keySet().removeIf(playerId -> !playerMatches.containsKey(playerId));
        cleaned += cleanOrphanedBoostEntities();
        removeOrphanCtfBackFlags();
        return cleaned;
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
                MinigameRoleRules listedRoleRules = roleRules(definition);
                boolean rolesEnabled = listedRoleRules != null && listedRoleRules.enabled;
                entries.add(new MinigameLobbyDataPayload.GameEntry(definition.id, definition.displayName,
                        definition.description, definition.iconItem, definition.gameType, definition.enabled, definition.minPlayers,
                        definition.maxPlayers, definition.teamCount, queueSize(definition.id), running, free, blocked,
                        definition.victoryMode, availability.matched(), availability.reason(), inThisQueue, inThisMatch,
                        rolesEnabled,
                        inThisQueue && rolesEnabled ? playerRolePreferences.getOrDefault(player.getUUID(), MinigameRole.DPS).id() : "",
                        inThisMatch && rolesEnabled ? ownMatch.role(player.getUUID()).id() : "",
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
        playerRolePreferences.clear();
        arenaReservations.clear();
        resettingArenas.clear();
        blockedArenas.clear();
        lastRequests.clear();
        runtimeBorderSyncStates.clear();
        spectatorCursors.clear();
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
        experienceStore.reset();
        progression = new MinigameProgressionData();
        history = new MinigameMatchHistory();
        spectatorCursors.clear();
        definitionFolder = null;
        recoveryFile = null;
        progressionFile = null;
        historyFile = null;
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
