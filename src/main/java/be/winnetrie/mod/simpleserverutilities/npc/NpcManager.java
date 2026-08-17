package be.winnetrie.mod.simpleserverutilities.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.network.NpcLabelSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcTextureSyncPayload;
import be.winnetrie.mod.simpleserverutilities.quest.QuestNpcBridge;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Persistent NPC runtime. Definitions are reusable templates; placements are independent instances.
 * Vanilla and modded living entities remain the renderer/model shells while SSU applies persistent
 * template attributes, equipment and placement rules server-side.
 */
public final class NpcManager {
    public static final int MAX_DEFINITIONS = 512;
    public static final int MAX_INSTANCES = 2_048;
    public static final int MAX_DYNAMIC_INSTANCES = 2_048;
    public static final int STORAGE_SCHEMA = 1;
    private static final long RECONCILE_INTERVAL_TICKS = 40L;
    private static final Set<String> UNSAFE_NATIVE_TYPES = Set.of(
            "minecraft:ender_dragon", "minecraft:wither");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, NpcInstance> instances = new LinkedHashMap<>();
    private final Map<UUID, UUID> instanceByRuntimeEntity = new LinkedHashMap<>();
    private final DirtyJsonRecordStore definitionStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore instanceStore = new DirtyJsonRecordStore();
    private MinecraftServer server;
    private Path definitionFolder;
    private Path instanceFolder;
    private long nextReconcileTick;
    private long nextRelationTick;
    private final Map<UUID, Integer> activeScheduleEntry = new LinkedHashMap<>();
    private final Map<UUID, Long> nextScheduleActivityTick = new LinkedHashMap<>();
    private final Map<UUID, Long> nextCombatAttackTick = new LinkedHashMap<>();
    /** Short-lived explicit reactions caused by attacks; these may target otherwise neutral entities. */
    private final Map<UUID, CombatIntent> reactiveCombat = new LinkedHashMap<>();
    /** Recent attacks on managed NPCs are the input for nearby ally-assist reactions. */
    private final Map<UUID, RecentNpcAttack> recentNpcAttacks = new LinkedHashMap<>();
    /** Exact target currently authorized by SSU combat, including retaliation/assist targets. */
    private final Map<UUID, UUID> authorizedCombatTargets = new LinkedHashMap<>();
    private final NpcNavigationController navigation = new NpcNavigationController();
    private final NpcAbilityLibraryManager abilityLibrary;
    private final NpcAbilityController abilityController;
    private final NpcAttackPatternController attackPatternController = new NpcAttackPatternController();
    private final NpcThreatController threatController = new NpcThreatController();
    private final Map<UUID, ServerBossEvent> bossBars = new LinkedHashMap<>();
    private final Map<UUID, String> activeBossPhase = new LinkedHashMap<>();
    /** Boss instances whose current encounter has started and has not reset yet. */
    private final Set<UUID> activeBossEncounters = new LinkedHashSet<>();
    /** Dynamic adds owned by a boss encounter; never persisted and always cleaned on reset/death. */
    private final Map<UUID, Set<UUID>> bossEncounterAdds = new LinkedHashMap<>();
    private final Map<UUID, Long> lastBossCombatTick = new LinkedHashMap<>();
    private final Set<UUID> staticPhysicsInstances = new LinkedHashSet<>();
    private final Set<UUID> scheduledInstances = new LinkedHashSet<>();
    private final Set<UUID> behaviorInstances = new LinkedHashSet<>();
    private final Set<UUID> relationInstances = new LinkedHashSet<>();
    /** Placements whose ambient route was interrupted by combat and must resume cleanly afterwards. */
    private final Set<UUID> movementInterruptedByCombat = new LinkedHashSet<>();
    /** Non-scheduled placements currently walking back to their configured home after combat/leash escape. */
    private final Set<UUID> returningHomeInstances = new LinkedHashSet<>();
    /** Scheduled placements pathfinding back to the active schedule destination after combat. */
    private final Set<UUID> returningScheduleInstances = new LinkedHashSet<>();

    public NpcManager(NpcAbilityLibraryManager abilityLibrary) {
        this.abilityLibrary = java.util.Objects.requireNonNull(abilityLibrary, "abilityLibrary");
        this.abilityController = new NpcAbilityController(abilityLibrary);
        abilityController.setHealingObserver(this::noteHealingThreat);
    }
    private final Map<UUID, Long> nextBehaviorDecisionTick = new LinkedHashMap<>();
    private final Map<UUID, Vec3> wanderTargets = new LinkedHashMap<>();
    private final Map<UUID, Integer> activePatrolPoint = new LinkedHashMap<>();
    private final Map<UUID, Integer> patrolDirection = new LinkedHashMap<>();
    private final Map<UUID, Long> patrolPauseUntilTick = new LinkedHashMap<>();
    private final Map<UUID, List<NpcLabelSyncPayload.Entry>> lastLabelSnapshots = new LinkedHashMap<>();
    private final Map<UUID, Map<String, String>> lastTextureSnapshots = new LinkedHashMap<>();
    private final NpcTextureAssetService textureAssets = new NpcTextureAssetService();
    private boolean labelsEnabledLastTick;
    private List<String> supportedModelCache = List.of();

    public synchronized void load(MinecraftServer server) {
        shutdownRuntime(false);
        this.server = server;
        Path root = StoragePaths.npcs(StoragePaths.root(server));
        this.definitionFolder = StoragePaths.npcDefinitions(StoragePaths.root(server));
        this.instanceFolder = StoragePaths.npcInstances(StoragePaths.root(server));
        this.nextReconcileTick = 0L;
        this.nextRelationTick = 0L;
        definitions.clear();
        instances.clear();
        instanceByRuntimeEntity.clear();
        activeScheduleEntry.clear();
        navigation.clear();
        abilityController.clear();
        attackPatternController.clear();
        threatController.clear();
        clearBossBars();
        activeBossPhase.clear();
        activeBossEncounters.clear();
        bossEncounterAdds.clear();
        lastBossCombatTick.clear();
        nextScheduleActivityTick.clear();
        nextCombatAttackTick.clear();
        reactiveCombat.clear();
        recentNpcAttacks.clear();
        authorizedCombatTargets.clear();
        staticPhysicsInstances.clear();
        scheduledInstances.clear();
        behaviorInstances.clear();
        relationInstances.clear();
        movementInterruptedByCombat.clear();
        returningHomeInstances.clear();
        returningScheduleInstances.clear();
        nextBehaviorDecisionTick.clear();
        wanderTargets.clear();
        activePatrolPoint.clear();
        patrolDirection.clear();
        patrolPauseUntilTick.clear();
        lastLabelSnapshots.clear();
        lastTextureSnapshots.clear();
        textureAssets.configure(server);
        labelsEnabledLastTick = Config.ENABLE_NPCS.get();
        supportedModelCache = List.of();
        definitionStore.reset();
        instanceStore.reset();

        try {
            Files.createDirectories(root);
            Files.createDirectories(definitionFolder);
            Files.createDirectories(instanceFolder);
            definitionStore.discover(definitionFolder);
            instanceStore.discover(instanceFolder);
            loadDefinitions();
            loadInstances();
            saveAll();
            SimpleServerUtilities.LOGGER.info(
                    "Loaded {} SSU NPC definitions and {} placements.", definitions.size(), instances.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU NPC data.", exception);
        }
    }

    private void loadDefinitions() throws Exception {
        for (Path file : JsonStorage.listJsonFiles(definitionFolder)) {
            try {
                NpcDefinition value = JsonStorage.read(GSON, file, NpcDefinition.class);
                if (value == null) continue;
                String legacyInteractionText = value.interactionText == null ? "" : value.interactionText.trim();
                if (value.schemaVersion < 19 && value.abilities != null && !value.abilities.isEmpty()) {
                    if (value.schemaVersion < 18) {
                        for (NpcAbilityDefinition legacyAbility : value.abilities) NpcDefinition.migrateLegacyAbility18(legacyAbility);
                    }
                    java.util.Map<String, String> migratedIds = abilityLibrary.importLegacy(value.id, value.abilities);
                    java.util.ArrayList<NpcAbilityAssignment> assignments = new java.util.ArrayList<>();
                    for (NpcAbilityDefinition legacyAbility : value.abilities) {
                        if (legacyAbility == null) continue;
                        String migratedId = migratedIds.get(legacyAbility.id);
                        if (migratedId == null || migratedId.isBlank()) continue;
                        NpcAbilityAssignment assignment = new NpcAbilityAssignment();
                        assignment.abilityId = migratedId;
                        assignment.phaseId = legacyAbility.phaseId == null ? "" : legacyAbility.phaseId;
                        assignments.add(assignment.normalize());
                    }
                    value.abilityAssignments = assignments;
                    for (NpcAttackPatternStep step : value.attackPattern == null ? java.util.List.<NpcAttackPatternStep>of() : value.attackPattern) {
                        if (step != null && step.actionType() == NpcAttackPatternAction.ABILITY) {
                            step.abilityId = migratedIds.getOrDefault(step.abilityId, step.abilityId);
                        }
                    }
                    for (NpcBossPhase phase : value.bossPhases == null ? java.util.List.<NpcBossPhase>of() : value.bossPhases) {
                        if (phase == null || phase.actions == null) continue;
                        for (NpcBossPhaseAction action : phase.actions) {
                            if (action != null && action.actionType() == NpcBossPhaseActionType.TRIGGER_ABILITY) {
                                action.value = migratedIds.getOrDefault(action.value, action.value);
                            }
                        }
                    }
                    value.abilities = new java.util.ArrayList<>();
                }
                value.normalize();
                value.abilityAssignments.removeIf(a -> a == null || abilityLibrary.resolved(a.abilityId) == null);
                if (value.dialogueId.isBlank() && !legacyInteractionText.isBlank()) {
                    String migratedDialogueId = NpcDefinition.sanitizeId(value.id + "_dialogue");
                    NpcDialogueDefinition migrated = SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.ensureSimple(
                            migratedDialogueId, value.displayName, legacyInteractionText);
                    if (migrated != null) {
                        value.dialogueId = migratedDialogueId;
                        value.interactionText = "";
                    }
                }
                definitions.put(value.id, value);
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load NPC definition; archived as {}.", archived, exception);
            }
        }
    }

    private void loadInstances() throws Exception {
        for (Path file : JsonStorage.listJsonFiles(instanceFolder)) {
            try {
                NpcInstance value = JsonStorage.read(GSON, file, NpcInstance.class);
                if (value == null) continue;
                value.normalize();
                instances.put(value.uuid(), value);
                UUID runtime = value.runtimeUuid();
                if (runtime != null) instanceByRuntimeEntity.put(runtime, value.uuid());
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load NPC placement; archived as {}.", archived, exception);
            }
        }
    }

    public synchronized void saveAll() {
        if (definitionFolder == null || instanceFolder == null) return;
        for (NpcDefinition value : definitions.values()) value.normalize();
        for (NpcInstance value : instances.values()) if (!value.dynamic) value.normalize();
        rebuildActiveTickSets();
        Set<Path> definitionFiles = new LinkedHashSet<>();
        for (NpcDefinition value : definitions.values()) {
            Path file = StoragePaths.jsonFile(definitionFolder, value.id);
            definitionFiles.add(file.toAbsolutePath().normalize());
            definitionStore.queueJson(GSON, file, value);
        }
        definitionStore.queueDeleteMissing(definitionFiles);

        Set<Path> instanceFiles = new LinkedHashSet<>();
        for (NpcInstance value : instances.values()) {
            if (value.dynamic) continue;
            Path file = StoragePaths.jsonFile(instanceFolder, value.id);
            instanceFiles.add(file.toAbsolutePath().normalize());
            instanceStore.queueJson(GSON, file, value);
        }
        instanceStore.queueDeleteMissing(instanceFiles);
    }

    public synchronized Collection<NpcDefinition> definitions() {
        List<NpcDefinition> result = new ArrayList<>(definitions.values());
        result.sort(Comparator.comparing(value -> value.id));
        return List.copyOf(result);
    }

    /** Persistent admin-created placements only. Dynamic population is intentionally excluded. */
    public synchronized Collection<NpcInstance> instances() {
        List<NpcInstance> result = instances.values().stream().filter(value -> !value.dynamic).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        result.sort(Comparator.comparing(value -> value.definitionId + ":" + value.id));
        return List.copyOf(result);
    }

    /** Runtime-only NPCs created by natural/spawner profiles. */
    public synchronized Collection<NpcInstance> dynamicInstances() {
        List<NpcInstance> result = instances.values().stream().filter(value -> value.dynamic).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return List.copyOf(result);
    }

    public synchronized int dynamicCount(String rawProfileId) {
        String profileId = NpcDefinition.sanitizeId(rawProfileId);
        int count = 0;
        for (NpcInstance value : instances.values()) {
            if (value.dynamic && profileId.equals(value.dynamicSpawnProfileId) && !value.dead) count++;
        }
        return count;
    }

    private int persistentInstanceCount() {
        int count = 0;
        for (NpcInstance value : instances.values()) if (!value.dynamic) count++;
        return count;
    }

    private int dynamicInstanceCount() {
        int count = 0;
        for (NpcInstance value : instances.values()) if (value.dynamic) count++;
        return count;
    }

    public synchronized RuntimeStatistics runtimeStatistics() {
        return new RuntimeStatistics(
                definitions.size(), persistentInstanceCount() + dynamicInstanceCount(), staticPhysicsInstances.size(),
                scheduledInstances.size(), relationInstances.size());
    }

    public record RuntimeStatistics(
            int definitions,
            int placements,
            int staticPhysicsPlacements,
            int scheduledPlacements,
            int relationPlacements
    ) {
    }

    public synchronized NpcDefinition definition(String rawId) {
        return definitions.get(NpcDefinition.sanitizeId(rawId));
    }

    public synchronized NpcInstance instance(String rawId) {
        try {
            return instances.get(UUID.fromString(rawId));
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized NpcInstance instance(UUID id) {
        return id == null ? null : instances.get(id);
    }

    public synchronized NpcInstance instanceForEntity(UUID entityId) {
        UUID instanceId = entityId == null ? null : instanceByRuntimeEntity.get(entityId);
        return instanceId == null ? null : instances.get(instanceId);
    }

    public synchronized NpcDefinition definitionFor(NpcInstance instance) {
        return instance == null ? null : definitions.get(instance.definitionId);
    }

    /** Current loaded entity shell for remote administration; null when its chunk is not active. */
    public synchronized Entity runtimeEntity(NpcInstance instance) {
        return instance == null ? null : findRuntime(instance);
    }

    public synchronized boolean isManagedEntity(UUID entityId) {
        return entityId != null && instanceByRuntimeEntity.containsKey(entityId);
    }

    /** Creates a non-persistent runtime NPC that still participates in SSU labels, factions, loot and combat. */
    public synchronized NpcInstance createDynamicPlacement(String rawDefinitionId, ServerLevel level,
            double x, double y, double z, float yaw, String rawSpawnProfileId, double despawnDistance) {
        if (server == null || level == null || dynamicInstanceCount() >= MAX_DYNAMIC_INSTANCES) return null;
        String definitionId = NpcDefinition.sanitizeId(rawDefinitionId);
        NpcDefinition definition = definitions.get(definitionId);
        if (definition == null || !definition.enabled) return null;
        NpcInstance instance = new NpcInstance();
        instance.definitionId = definitionId;
        instance.dimension = level.dimension().location().toString();
        instance.x = x; instance.y = y; instance.z = z; instance.yaw = yaw; instance.pitch = 0.0F;
        instance.respawnDimension = instance.dimension;
        instance.respawnX = x; instance.respawnY = y; instance.respawnZ = z;
        instance.respawnYaw = yaw; instance.respawnPitch = 0.0F;
        instance.respawnEnabled = false;
        instance.dynamic = true;
        instance.dynamicSpawnProfileId = NpcDefinition.sanitizeId(rawSpawnProfileId);
        instance.dynamicDespawnDistance = Double.isFinite(despawnDistance) ? Math.max(16.0D, despawnDistance) : 96.0D;
        instance.dynamicSpawnedAtTick = server.getTickCount();
        instances.put(instance.uuid(), instance);
        reconcile(instance, true);
        if (instance.runtimeUuid() == null) {
            instances.remove(instance.uuid());
            return null;
        }
        rebuildActiveTickSets();
        return instance;
    }

    /** Removes runtime population without touching persistent placement storage. */
    public synchronized boolean removeDynamicInstance(UUID instanceId, boolean discardEntity) {
        NpcInstance value = instanceId == null ? null : instances.get(instanceId);
        if (value == null || !value.dynamic) return false;
        if (discardEntity) removeRuntime(value); else clearRuntimeBinding(value);
        instances.remove(instanceId);
        rebuildActiveTickSets();
        return true;
    }

    public synchronized boolean removeDynamicInstance(String rawId, boolean discardEntity) {
        try { return removeDynamicInstance(UUID.fromString(rawId), discardEntity); }
        catch (Exception ignored) { return false; }
    }

    public synchronized boolean saveDefinition(String rawOriginalId, NpcDefinition value) {
        if (value == null) return false;
        value.normalize();
        String originalId = rawOriginalId == null || rawOriginalId.isBlank()
                ? "" : NpcDefinition.sanitizeId(rawOriginalId);
        NpcDefinition existing = originalId.isBlank() ? null : definitions.get(originalId);
        boolean respawnRuntime = existing != null && requiresRuntimeRespawn(existing, value);
        if (existing == null && !definitions.containsKey(value.id) && definitions.size() >= MAX_DEFINITIONS) return false;
        if (!originalId.equals(value.id) && definitions.containsKey(value.id)) return false;
        ServerLevel validationLevel = server == null ? null : server.getLevel(Level.OVERWORLD);
        if (!isSupportedLivingEntityType(validationLevel, value.entityType)) return false;

        if (existing != null && !originalId.equals(value.id)) {
            definitions.remove(originalId);
            for (NpcInstance instance : instances.values()) {
                if (originalId.equals(instance.definitionId)) instance.definitionId = value.id;
            }
        }
        if (!originalId.isBlank()) textureAssets.invalidate(originalId);
        textureAssets.invalidate(value.id);
        definitions.put(value.id, value);
        // Definitions can be edited while linked NPCs are fighting. Never let an in-flight cast or
        // cooldown from the old definition execute against the newly saved ability/boss setup.
        for (NpcInstance instance : instances.values()) {
            if (!value.id.equals(instance.definitionId)) continue;
            UUID instanceId = instance.uuid();
            abilityController.forget(instanceId);
            attackPatternController.forget(instanceId);
            threatController.forget(instanceId);
            navigation.forget(instanceId);
            activeBossPhase.remove(instanceId);
            lastBossCombatTick.remove(instanceId);
            if (!value.bossEnabled || !value.bossBarVisible) removeBossBar(instanceId);
        }
        if (respawnRuntime) respawnDefinition(value.id);
        else refreshDefinition(value.id);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    /** Shared ability edits apply live to every assigned NPC; clear in-flight combat state so old casts cannot linger. */
    public synchronized void onAbilityLibraryChanged(String abilityId) {
        abilityController.clear();
        attackPatternController.clear();
        nextCombatAttackTick.clear();
    }

    public synchronized boolean create(NpcDefinition definition, NpcInstance instance) {
        if (definition == null || instance == null) return false;
        definition.normalize();
        instance.normalize();
        ServerLevel validationLevel = level(instance.dimension);
        if (!isSupportedLivingEntityType(validationLevel, definition.entityType)) return false;
        if (instances.containsKey(instance.uuid()) || persistentInstanceCount() >= MAX_INSTANCES) return false;
        if (!definitions.containsKey(definition.id)) {
            if (definitions.size() >= MAX_DEFINITIONS) return false;
            definitions.put(definition.id, definition);
        } else {
            NpcDefinition current = definitions.get(definition.id);
            if (!sameDefinition(current, definition)) return false;
        }
        instance.definitionId = definition.id;
        instances.put(instance.uuid(), instance);
        reconcile(instance, true);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    /** Creates a placement for an existing reusable definition. */
    public synchronized boolean createPlacement(NpcInstance instance) {
        if (instance == null) return false;
        instance.normalize();
        if (!definitions.containsKey(instance.definitionId) || instances.containsKey(instance.uuid())
                || persistentInstanceCount() >= MAX_INSTANCES) return false;
        if (level(instance.dimension) == null) return false;
        instances.put(instance.uuid(), instance);
        reconcile(instance, true);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    /** Immediately revives a placement at its configured respawn anchor. */
    public synchronized boolean respawnNow(String rawId) {
        NpcInstance instance = instance(rawId);
        if (instance == null || !definitions.containsKey(instance.definitionId)) return false;
        removeRuntime(instance);
        instance.dead = false;
        instance.respawnAtEpochMillis = 0L;
        instance.dimension = instance.respawnDimension;
        instance.x = instance.respawnX; instance.y = instance.respawnY; instance.z = instance.respawnZ;
        instance.yaw = instance.respawnYaw; instance.pitch = instance.respawnPitch;
        reconcile(instance, true);
        saveAll();
        syncAllLabels(true);
        return instance.runtimeUuid() != null;
    }

    /** Records death without allowing normal reconciliation to recreate the NPC immediately. */
    public synchronized void markDead(LivingEntity entity) {
        if (entity == null) return;
        NpcInstance instance = instanceForEntity(entity.getUUID());
        if (instance == null) return;
        instance.dead = true;
        NpcDefinition deadDefinition = definitionFor(instance);
        if (deadDefinition != null && deadDefinition.bossEnabled) cleanupBossEncounter(instance.uuid());
        instance.respawnAtEpochMillis = instance.dynamic ? 0L : instance.respawnEnabled
                ? System.currentTimeMillis() + (long) instance.respawnDelaySeconds * 1_000L : 0L;
        // Keep the runtime binding through LivingDropsEvent so the configured SSU loot table is available.
        if (!instance.dynamic) saveAll();
    }

    /** Saves placement metadata without taking movement ownership from a live NPC. */
    public synchronized boolean saveInstance(NpcInstance value) {
        return saveInstance(value, false);
    }

    /**
     * Saves placement metadata and optionally performs an explicit administrator move.
     * Route/schedule edits use the normal path (forceMove=false); only a deliberate placement
     * coordinate/dimension change, Bring or equivalent admin action may hard-position the shell.
     */
    public synchronized boolean saveInstance(NpcInstance value, boolean forceMove) {
        if (value == null || value.dynamic) return false;
        value.normalize();
        if (!definitions.containsKey(value.definitionId)) return false;
        if (!instances.containsKey(value.uuid()) && persistentInstanceCount() >= MAX_INSTANCES) return false;
        NpcInstance previous = instances.put(value.uuid(), value);
        if (previous != null && previous.runtimeUuid() != null && !previous.runtimeUuid().equals(value.runtimeUuid())) {
            instanceByRuntimeEntity.remove(previous.runtimeUuid());
        }
        activeScheduleEntry.remove(value.uuid());
        navigation.forget(value.uuid());
        nextScheduleActivityTick.remove(value.uuid());
        nextBehaviorDecisionTick.remove(value.uuid());
        wanderTargets.remove(value.uuid());
        activePatrolPoint.remove(value.uuid());
        patrolDirection.remove(value.uuid());
        patrolPauseUntilTick.remove(value.uuid());
        movementInterruptedByCombat.remove(value.uuid());
        returningHomeInstances.remove(value.uuid());
        returningScheduleInstances.remove(value.uuid());
        nextCombatAttackTick.remove(value.uuid());
        reconcile(value, forceMove);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    public synchronized NpcInstance duplicateLinked(NpcInstance source, String dimension,
                                                     double x, double y, double z, float yaw, float pitch) {
        if (source == null || source.dynamic || !definitions.containsKey(source.definitionId) || persistentInstanceCount() >= MAX_INSTANCES) return null;
        NpcInstance copy = source.copyAt(dimension, x, y, z, yaw, pitch);
        instances.put(copy.uuid(), copy);
        reconcile(copy, true);
        saveAll();
        syncAllLabels(true);
        return copy;
    }

    public synchronized boolean deleteInstance(String rawId) {
        NpcInstance value = instance(rawId);
        if (value == null) return false;
        removeRuntime(value);
        instances.remove(value.uuid());
        nextCombatAttackTick.remove(value.uuid());
        saveAll();
        syncAllLabels(true);
        return true;
    }

    public synchronized boolean deleteDefinition(String rawId, boolean deletePlacements) {
        String id = NpcDefinition.sanitizeId(rawId);
        if (!definitions.containsKey(id)) return false;
        List<NpcInstance> linked = instances.values().stream()
                .filter(value -> !value.dynamic && id.equals(value.definitionId)).toList();
        if (!linked.isEmpty() && !deletePlacements) return false;
        for (NpcInstance value : List.copyOf(instances.values())) {
            if (value.dynamic && id.equals(value.definitionId)) removeDynamicInstance(value.uuid(), true);
        }
        for (NpcInstance value : linked) {
            removeRuntime(value);
            instances.remove(value.uuid());
            nextCombatAttackTick.remove(value.uuid());
        }
        definitions.remove(id);
        textureAssets.invalidate(id);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    public synchronized boolean isSupportedEntityType(String rawType) {
        try {
            ResourceLocation id = ResourceLocation.parse(rawType == null ? "" : rawType.trim());
            if (UNSAFE_NATIVE_TYPES.contains(id.toString())) return false;
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            return type.isPresent() && type.get() != EntityType.PLAYER;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isSupportedLivingEntityType(ServerLevel level, String rawType) {
        if (level == null || !isSupportedEntityType(rawType)) return false;
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", rawType.trim());
            Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
            return entity instanceof LivingEntity && entity.getType() != EntityType.PLAYER;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Sorted living entity model IDs for the searchable admin model picker. */
    public synchronized List<String> supportedLivingEntityTypes(ServerLevel level) {
        if (!supportedModelCache.isEmpty()) return supportedModelCache;
        if (level == null) return List.of("minecraft:villager");
        List<String> result = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            String value = id.toString();
            if (ModNpcEntities.PLAYER_NPC_ID.equals(value)) continue;
            if (isSupportedLivingEntityType(level, value)) result.add(value);
        }
        result.sort(String::compareTo);
        if (result.isEmpty()) result.add("minecraft:villager");
        supportedModelCache = List.copyOf(result);
        return supportedModelCache;
    }


    public synchronized List<String> localTextureNames() {
        return textureAssets.localSkins();
    }

    public synchronized String validateLocalTexture(String relativePath, boolean playerSkin) {
        return textureAssets.validateLocalPath(relativePath, playerSkin);
    }

    /** Human-readable AI family used by the editor debug snapshot. */
    public synchronized String aiProfileLabel(NpcInstance placement) {
        if (placement == null) return "Unknown";
        Entity runtime = findRuntime(placement);
        if (runtime instanceof LivingEntity living && !living.isRemoved()) return NpcAiProfile.resolve(living).label();
        return NpcAiProfile.infer(definitionFor(placement)).label();
    }

    /** Compact runtime snapshot for diagnosing patrol/schedule/combat state from the editor. */
    public synchronized String aiRuntimeSummary(NpcInstance placement) {
        if (placement == null) return "No placement";
        if (placement.dead) return "Dead / waiting for respawn";
        NpcDefinition definition = definitionFor(placement);
        Entity runtime = findRuntime(placement);
        if (definition == null) return "Missing definition";
        if (!(runtime instanceof LivingEntity living) || runtime.isRemoved()) return "Runtime entity not loaded";
        UUID instanceId = placement.uuid();
        String path = living instanceof Mob mob ? (mob.getNavigation().isDone() ? "idle" : "pathing") : "direct movement";
        if (combatBusy(instanceId)) {
            return "Combat • " + path + " • " + NpcAiProfile.resolve(living).label();
        }
        if (returningScheduleInstances.contains(instanceId)) {
            return "Returning to schedule • " + path + " • route ×" + trimDebugNumber(definition.walkingSpeed);
        }
        if (returningHomeInstances.contains(instanceId)) {
            return "Returning home • " + path + " • route ×" + trimDebugNumber(definition.walkingSpeed);
        }
        if (movementInterruptedByCombat.contains(instanceId)) {
            return "Combat route interrupted • " + path;
        }
        if (placement.scheduleEnabled && !placement.schedule.isEmpty()) {
            int minute = living.level() instanceof ServerLevel serverLevel
                    ? GameCalendar.fromClockTime(serverLevel.getDayTime()).minuteOfDay() : 0;
            int index = activeScheduleEntry.getOrDefault(instanceId, activeScheduleIndex(placement.schedule, minute));
            return "Schedule " + (Math.max(0, index) + 1) + "/" + placement.schedule.size() + " • " + path;
        }
        return switch (definition.behaviorMode()) {
            case PATROL -> "Patrol " + (activePatrolPoint.getOrDefault(instanceId, 0) + 1) + "/"
                    + Math.max(1, placement.patrol == null ? 0 : placement.patrol.size()) + " • " + path
                    + " • route ×" + trimDebugNumber(definition.walkingSpeed);
            case WANDER -> "Wander • " + path + " • route ×" + trimDebugNumber(definition.walkingSpeed);
            case LOOK_AT_PLAYERS -> "Look at players • idle";
            case NATIVE -> "Native AI • " + path;
            case STATIONARY -> "Stationary";
        };
    }

    private static String trimDebugNumber(double value) {
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public synchronized void tick(MinecraftServer activeServer) {
        if (server == null || activeServer != server) return;
        boolean enabled = Config.ENABLE_NPCS.get();
        if (!enabled) {
            if (labelsEnabledLastTick) {
                labelsEnabledLastTick = false;
                syncAllLabels(true);
            }
            return;
        }
        if (!labelsEnabledLastTick) {
            labelsEnabledLastTick = true;
            nextReconcileTick = 0L;
        }
        long tick = activeServer.getTickCount();
        if (tick % 20L == 0L) tickEquipmentIntegrity();
        abilityController.tickPersistentEffects(tick);
        tickStaticPhysics();
        tickSchedules(activeServer, tick);
        tickBehaviors(activeServer, tick);
        if (tick >= nextRelationTick) {
            nextRelationTick = tick + 5L;
            tickRelations(tick);
        }
        tickBossEncounters(tick);
        if (tick < nextReconcileTick) return;
        nextReconcileTick = tick + RECONCILE_INTERVAL_TICKS;
        for (NpcInstance value : List.copyOf(instances.values())) reconcile(value, false);
        syncAllLabels(false);
    }

    /** No-AI vanilla shells do not reliably run normal fall physics, so SSU applies gravity itself. */
    private void tickStaticPhysics() {
        List<NpcInstance> changedPlacements = new ArrayList<>();
        for (UUID instanceId : List.copyOf(staticPhysicsInstances)) {
            NpcInstance placement = instances.get(instanceId);
            if (placement == null || !placement.enabled || placement.dead) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled || !definition.noAi
                    || !definition.affectedByGravity || definition.canFly) continue;
            Entity entity = findRuntime(placement);
            if (!(entity instanceof LivingEntity living) || entity.isRemoved()) continue;
            // Native flying shells (Vex/Ghast/Bat/etc.) own their airborne physics even when their
            // high-level AI is frozen by a stationary SSU behavior. Never apply ground-shell gravity.
            if (NpcLocomotionProfile.resolve(living).nativeFlying()) continue;
            // Only frozen/no-AI shells need SSU's manual gravity fallback. A native PathfinderMob
            // that is currently allowed to move must keep ownership of its own velocity; zeroing it
            // here fights MoveControl and produces stop/start jitter.
            if (living instanceof Mob mobileShell && !mobileShell.isNoAi()) continue;
            living.setNoGravity(false);
            if (!living.onGround()) {
                Vec3 velocity = living.getDeltaMovement();
                double nextY = Math.max(-3.92D, velocity.y - 0.08D);
                living.setDeltaMovement(0.0D, nextY, 0.0D);
                living.move(MoverType.SELF, living.getDeltaMovement());
                living.setDeltaMovement(0.0D, nextY * 0.98D, 0.0D);
            } else {
                living.setDeltaMovement(Vec3.ZERO);
                // Static gravity settling may correct only the vertical home coordinate and only
                // while the shell is still at its configured X/Z anchor. Never drag the placement
                // anchor to a combat/knockback position.
                if (placement.scheduleEnabled && !placement.schedule.isEmpty()) continue;
                double dx = living.getX() - placement.x;
                double dz = living.getZ() - placement.z;
                if (dx * dx + dz * dz <= 0.0625D
                        && Math.abs(placement.y - living.getY()) > 0.01D) {
                    placement.y = living.getY();
                    changedPlacements.add(placement);
                }
            }
        }
        for (NpcInstance placement : changedPlacements) queueInstanceSave(placement);
    }

    private void queueInstanceSave(NpcInstance value) {
        if (value == null || instanceFolder == null) return;
        value.normalize();
        instanceStore.queueJson(GSON, StoragePaths.jsonFile(instanceFolder, value.id), value);
    }

    private void tickRelations(long serverTick) {
        reactiveCombat.entrySet().removeIf(entry -> entry.getValue().expiresTick < serverTick);
        recentNpcAttacks.entrySet().removeIf(entry -> entry.getValue().expiresTick < serverTick);
        Map<String, Map<Long, List<NpcInstance>>> nearbyIndex = buildRelationSpatialIndex();
        for (UUID sourceId : List.copyOf(relationInstances)) {
            NpcInstance sourcePlacement = instances.get(sourceId);
            if (sourcePlacement == null || !sourcePlacement.enabled || sourcePlacement.dead) continue;
            NpcDefinition sourceDefinition = definitions.get(sourcePlacement.definitionId);
            Entity sourceEntity = findRuntime(sourcePlacement);
            if (sourceDefinition == null || !(sourceEntity instanceof LivingEntity source)
                    || !sourceDefinition.enabled || source.isRemoved() || !source.isAlive()) continue;

            double followRange = sourceDefinition.followRange > 0.0D ? sourceDefinition.followRange : 16.0D;
            CombatIntent reactive = reactiveCombat.get(sourceId);
            LivingEntity explicitThreat = reactive == null ? null : livingEntity(source, reactive.targetEntityId);
            if (explicitThreat == null && reactive != null) {
                reactiveCombat.remove(sourceId);
                reactive = null;
            }

            // Direct flee/retaliation always wins over ambient faction sight reactions.
            if (reactive != null && reactive.mode == CombatMode.FLEE) {
                authorizedCombatTargets.remove(sourceId);
                fleeFromThreat(sourceId, source, sourceDefinition, explicitThreat, serverTick);
                if (source instanceof Mob mob) { if (mob.isNoAi()) mob.setNoAi(false); mob.setTarget(null); mob.setAggressive(false); }
                continue;
            }

            LivingEntity target = null;
            double targetDistance = Double.POSITIVE_INFINITY;
            if (reactive != null && reactive.mode == CombatMode.FIGHT
                    && sourceDefinition.combatProfile() != NpcCombatProfile.PASSIVE) {
                target = explicitThreat;
                targetDistance = source.distanceToSqr(explicitThreat);
            }

            // A nearby friendly NPC being attacked can create an assist target even when the
            // attacker is normally neutral. This is deliberately separate from faction attitude.
            if (target == null && sourceDefinition.whenFriendlyAttacked() != NpcFriendlyDefenseReaction.IGNORE
                    && sourceDefinition.combatProfile() != NpcCombatProfile.PASSIVE) {
                for (Map.Entry<UUID, RecentNpcAttack> entry : recentNpcAttacks.entrySet()) {
                    NpcInstance victimPlacement = instances.get(entry.getKey());
                    if (victimPlacement == null || victimPlacement == sourcePlacement || victimPlacement.dead) continue;
                    NpcDefinition victimDefinition = definitions.get(victimPlacement.definitionId);
                    Entity victimEntity = findRuntime(victimPlacement);
                    if (victimDefinition == null || !(victimEntity instanceof LivingEntity victim)
                            || victim.level() != source.level() || !victim.isAlive()) continue;
                    if (sourceDefinition.attitudeTowardFaction(victimDefinition.factionId) != NpcAttitude.FRIENDLY) continue;
                    double assistRange = sourceDefinition.assistRange
                            * (entry.getValue().calledAllies ? 1.5D : 1.0D)
                            * (sourceDefinition.whenFriendlyAttacked() == NpcFriendlyDefenseReaction.CALL_ALLIES ? 1.5D : 1.0D);
                    if (source.distanceToSqr(victim) > assistRange * assistRange) continue;
                    LivingEntity attacker = livingEntity(source, entry.getValue().attackerEntityId);
                    if (attacker == null || attacker == source || !attacker.isAlive()) continue;
                    if (isFriendlyTarget(sourceDefinition, attacker)) continue;
                    double distance = source.distanceToSqr(attacker);
                    if (distance < targetDistance) { target = attacker; targetDistance = distance; }
                }
            }

            // Normal hostile sight acquisition is controlled by its own reaction setting.
            LivingEntity seenHostile = nearestHostile(sourceDefinition, sourcePlacement, source, nearbyIndex, followRange);
            if (sourceDefinition.whenHostileSeen() == NpcHostileSightReaction.AVOID && seenHostile != null
                    && target == null && reactive == null) {
                reactiveCombat.put(sourceId, new CombatIntent(seenHostile.getUUID(), CombatMode.FLEE, serverTick + 20L));
                authorizedCombatTargets.remove(sourceId);
                fleeFromThreat(sourceId, source, sourceDefinition, seenHostile, serverTick);
                if (source instanceof Mob mob) { if (mob.isNoAi()) mob.setNoAi(false); mob.setTarget(null); mob.setAggressive(false); }
                continue;
            }
            if (target == null && sourceDefinition.whenHostileSeen() == NpcHostileSightReaction.ATTACK
                    && sourceDefinition.combatProfile() != NpcCombatProfile.PASSIVE && seenHostile != null) {
                target = seenHostile;
                targetDistance = source.distanceToSqr(seenHostile);
            }

            // Threat is layered on top of the existing relation/reaction acquisition. Opted-in
            // NPCs seed/use a normal threat table. The selector is still consulted for every
            // non-passive combatant because encounter mechanics such as Boss Fixate install a
            // bounded forced target even when regular threat is disabled.
            if (sourceDefinition.combatProfile() != NpcCombatProfile.PASSIVE) {
                if (sourceDefinition.threatEnabled && target != null) {
                    double seedThreat = reactive != null && reactive.mode == CombatMode.FIGHT
                            && explicitThreat == target ? 10.0D : 1.0D;
                    threatController.ensure(sourceId, target, seedThreat, serverTick);
                }
                LivingEntity selectedTarget = threatController.select(sourceId, source, sourceDefinition, serverTick,
                        authorizedCombatTargets.get(sourceId), candidate -> !isFriendlyTarget(sourceDefinition, candidate));
                if (selectedTarget != null) target = selectedTarget;
            }

            if (target == null) {
                UUID previousAuthorized = authorizedCombatTargets.remove(sourceId);
                nextCombatAttackTick.remove(sourceId);
                if (abilityController.casting(sourceId)) abilityController.forget(sourceId);
                attackPatternController.forget(sourceId);
                navigation.forget(sourceId);
                if (source instanceof Mob mob) {
                    LivingEntity current = mob.getTarget();
                    if (previousAuthorized != null && current != null && previousAuthorized.equals(current.getUUID())) {
                        mob.setTarget(null);
                        mob.getNavigation().stop();
                    }
                    // Clear the synced combat pose even when Minecraft has already discarded a
                    // dead/despawned target and getTarget() is null by the time this branch runs.
                    if (previousAuthorized != null) mob.setAggressive(false);
                    restoreConfiguredNoAi(mob, sourceDefinition, sourcePlacement);
                }
                continue;
            }

            authorizedCombatTargets.put(sourceId, target.getUUID());
            // Remember that combat took ownership of movement even for STATIONARY/NATIVE NPCs.
            // The first non-combat behavior tick will resume patrol/schedule or path back home.
            movementInterruptedByCombat.add(sourceId);
            returningHomeInstances.remove(sourceId);
            if (source instanceof Mob mob) {
                // Stationary/look-at NPCs may still defend themselves. Native AI is enabled only
                // while SSU owns a combat target and restored when combat ends.
                if (mob.isNoAi()) mob.setNoAi(false);
                mob.setTarget(target);
                // Mob's aggressive flag is synced to clients. The SSU player renderer uses it
                // only as a visual combat signal for bow/crossbow/shield arm poses.
                mob.setAggressive(true);
            }
            NpcCombatProfile profile = sourceDefinition.combatProfile();
            NpcBossPhase bossPhase = sourceDefinition.bossPhase(source.getHealth(), source.getMaxHealth());
            final LivingEntity combatTarget = target;
            NpcAbilityController.DamageFilter damageFilter = candidate -> candidate == combatTarget
                    || isHostileTarget(sourceDefinition, candidate);
            boolean patternMode = sourceDefinition.attackPatternEnabled
                    && sourceDefinition.attackPattern != null && !sourceDefinition.attackPattern.isEmpty();
            NpcAttackPatternController.Selection patternSelection = patternMode
                    ? attackPatternController.select(sourceId, sourceDefinition, bossPhase, source, target) : null;
            boolean suppressMelee = patternMode && patternSelection == null;
            boolean abilityOwnsMovement = false;

            if (patternMode) {
                if (abilityController.casting(sourceId)) {
                    String requestedId = patternSelection != null
                            && patternSelection.step().actionType() == NpcAttackPatternAction.ABILITY
                            ? patternSelection.step().abilityId : "";
                    NpcAbilityController.RequestedTickResult result = abilityController.tickRequested(
                            sourceId, source, target, sourceDefinition, bossPhase, serverTick, requestedId, damageFilter);
                    abilityOwnsMovement = result.ownsMovement();
                    if (result.status() == NpcAbilityController.RequestStatus.ACTIVE) suppressMelee = true;
                } else if (patternSelection != null
                        && patternSelection.step().actionType() == NpcAttackPatternAction.ABILITY) {
                    suppressMelee = true;
                    NpcAbilityController.RequestedTickResult result = abilityController.tickRequested(
                            sourceId, source, target, sourceDefinition, bossPhase, serverTick,
                            patternSelection.step().abilityId, damageFilter);
                    abilityOwnsMovement = result.ownsMovement();
                    if (result.advancePattern()) {
                        attackPatternController.advance(sourceId, sourceDefinition, patternSelection.index());
                    }
                }
            } else {
                abilityOwnsMovement = abilityController.tick(sourceId, source, target, sourceDefinition, bossPhase,
                        serverTick, damageFilter);
            }
            if (abilityOwnsMovement) {
                // The ability controller owns movement during casts. Do not stop native navigation here:
                // Charge uses it deliberately; stationary channels simply never issue a move request.
                navigation.forget(sourceId);
                continue;
            }

            double phaseSpeed = bossPhase == null ? 1.0D : bossPhase.movementSpeedMultiplier;
            NpcAiProfile aiProfile = NpcAiProfile.resolve(source);
            Vec3 combatDestination = aiProfile.combatDestination(target);
            navigation.move(sourceId, source, sourceDefinition,
                    combatDestination.x, combatDestination.y, combatDestination.z,
                    sourceDefinition.runningSpeed * phaseSpeed, serverTick, aiProfile.combatRepathTicks());

            if (suppressMelee || !(source instanceof Mob mob) || !(source.level() instanceof ServerLevel level)) continue;
            long nextAttack = nextCombatAttackTick.getOrDefault(sourceId, 0L);
            if (serverTick < nextAttack) continue;

            boolean inMeleeRange = mob.isWithinMeleeAttackRange(target);
            boolean rangedAvailable = sourceDefinition.rangedAttacksEnabled && NpcCombatEquipment.hasRangedWeapon(source);
            boolean preferRanged = rangedAvailable && NpcCombatEquipment.mainHandIsRanged(source);
            double distance = Math.sqrt(source.distanceToSqr(target));
            double rangedReach = Math.max(8.0D, sourceDefinition.followRange > 0.0D ? sourceDefinition.followRange : 28.0D);
            boolean performedAttack = false;
            if (preferRanged && distance <= rangedReach) {
                mob.lookAt(target, 30.0F, 30.0F);
                mob.setAggressive(true);
                NpcAnimationBridge.trigger(mob, NpcAnimationState.ATTACK, serverTick + 8L);
                performedAttack = performEquipmentRangedAttack(level, source, target, sourceDefinition);
            } else if (sourceDefinition.meleeAttacksEnabled && inMeleeRange) {
                mob.lookAt(target, 30.0F, 30.0F);
                mob.setAggressive(true);
                NpcAnimationBridge.trigger(mob, NpcAnimationState.ATTACK, serverTick + 8L);
                performedAttack = performEquipmentMeleeAttack(level, mob, target, sourceDefinition);
            } else if (rangedAvailable && distance <= rangedReach) {
                mob.lookAt(target, 30.0F, 30.0F);
                mob.setAggressive(true);
                NpcAnimationBridge.trigger(mob, NpcAnimationState.ATTACK, serverTick + 8L);
                performedAttack = performEquipmentRangedAttack(level, source, target, sourceDefinition);
            }
            if (!performedAttack) continue;

            double phaseCooldown = bossPhase == null ? 1.0D : bossPhase.cooldownMultiplier;
            long cooldown = Math.max(4L, Math.round(sourceDefinition.attackCooldownTicks
                    * profile.attackCooldownMultiplier() * phaseCooldown));
            nextCombatAttackTick.put(sourceId, serverTick + cooldown);
            if (patternMode && patternSelection != null
                    && patternSelection.step().actionType() == NpcAttackPatternAction.MELEE) {
                attackPatternController.advance(sourceId, sourceDefinition, patternSelection.index());
            }
        }
    }

    /** Ordinary melee delegates to vanilla Mob#doHurtTarget when possible so gameplay enchantments and hit effects participate. */
    private boolean performEquipmentMeleeAttack(ServerLevel level, LivingEntity source, LivingEntity target, NpcDefinition definition) {
        if (source instanceof Mob mob && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            return performVanillaEquipmentMelee(level, mob, target, Math.max(0.0D, definition.meleeDamageMultiplier));
        }
        double amount = NpcCombatEquipment.meleeDamage(source);
        return hurtWithNpcScaling(level, source, target, NpcAttackKind.MELEE, NpcDamageSchool.PHYSICAL, amount);
    }

    /**
     * Equipment-backed melee ability hit. Physical melee uses the vanilla mob attack pipeline so
     * weapon enchantments remain gameplay-active; non-physical schools use SSU's typed damage path.
     */
    synchronized boolean performEquipmentAbilityHit(ServerLevel level, LivingEntity source, LivingEntity target,
            NpcAttackKind kind, NpcDamageSchool school, double abilityMultiplier) {
        if (kind == NpcAttackKind.MELEE && school == NpcDamageSchool.PHYSICAL
                && source instanceof Mob mob && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            NpcDefinition definition = definitionFor(instanceForEntity(source.getUUID()));
            double channel = definition == null ? 1.0D : definition.meleeDamageMultiplier;
            return performVanillaEquipmentMelee(level, mob, target, Math.max(0.0D, channel * abilityMultiplier));
        }
        double base = kind == NpcAttackKind.RANGED ? NpcCombatEquipment.rangedDamage(source) : NpcCombatEquipment.meleeDamage(source);
        return hurtWithNpcScaling(level, source, target, kind, school, base * Math.max(0.0D, abilityMultiplier));
    }

    private static boolean performVanillaEquipmentMelee(ServerLevel level, Mob source, LivingEntity target, double multiplier) {
        AttributeInstance attack = source.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null || !(multiplier > 0.0D) || target == null || !target.isAlive()) return false;
        double oldBase = attack.getBaseValue();
        double currentTotal = Math.max(0.0D, source.getAttributeValue(Attributes.ATTACK_DAMAGE));
        // Preserve all equipment/gameplay modifiers and shift only the base enough to scale the current total.
        attack.setBaseValue(oldBase + currentTotal * (multiplier - 1.0D));
        try {
            source.doHurtTarget(target);
            NpcCombatEquipment.repairEquipped(source);
            return true;
        } finally {
            attack.setBaseValue(oldBase);
            NpcCombatEquipment.repairEquipped(source);
        }
    }

    /** Generic ranged weapon executor. The equipped weapon determines the base hit power. */
    private boolean performEquipmentRangedAttack(ServerLevel level, LivingEntity source, LivingEntity target, NpcDefinition definition) {
        double amount = NpcCombatEquipment.rangedDamage(source);
        if (!(amount > 0.0D)) return false;
        source.swing(NpcCombatEquipment.rangedHand(source));
        Vec3 start = source.getEyePosition();
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 delta = end.subtract(start);
        int steps = Math.max(4, Math.min(40, (int) Math.ceil(delta.length() * 2.0D)));
        for (int index = 1; index <= steps; index++) {
            Vec3 point = start.add(delta.scale(index / (double) steps));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, point.x, point.y, point.z,
                    1, 0.015D, 0.015D, 0.015D, 0.0D);
        }
        boolean hit = hurtWithNpcScaling(level, source, target, NpcAttackKind.RANGED, NpcDamageSchool.PHYSICAL, amount);
        if (hit && NpcCombatEquipment.rangedFlame(source)) {
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
        }
        int punch = NpcCombatEquipment.rangedPunch(source);
        if (hit && punch > 0) {
            Vec3 away = target.position().subtract(source.position());
            Vec3 flat = new Vec3(away.x, 0.0D, away.z);
            if (flat.lengthSqr() > 1.0E-6D) {
                flat = flat.normalize().scale(0.35D * punch);
                target.push(flat.x, 0.08D * punch, flat.z);
            }
        }
        NpcCombatEquipment.repairEquipped(source);
        return hit;
    }

    /** Shared SSU damage gate for equipment attacks and the custom ability engine. */
    synchronized double adjustedNpcDamage(LivingEntity source, LivingEntity target, NpcAttackKind kind,
            NpcDamageSchool school, double rawAmount) {
        if (!(rawAmount > 0.0D)) return 0.0D;
        double amount = rawAmount;
        NpcDefinition sourceDefinition = definitionFor(instanceForEntity(source == null ? null : source.getUUID()));
        if (sourceDefinition != null && kind != null) amount *= sourceDefinition.damageMultiplier(kind);
        // Magic resistance is intentionally separate from vanilla armor/enchantment reduction.
        if (target != null && school != null && school.magical()) {
            NpcDefinition targetDefinition = definitionFor(instanceForEntity(target.getUUID()));
            if (targetDefinition != null) amount *= 1.0D - targetDefinition.magicResistance;
        }
        return Math.max(0.0D, Math.min(2_048.0D, amount));
    }

    synchronized boolean hurtWithNpcScaling(ServerLevel level, LivingEntity source, LivingEntity target,
            NpcAttackKind kind, NpcDamageSchool school, double rawAmount) {
        double amount = adjustedNpcDamage(source, target, kind, school, rawAmount);
        if (!(amount > 0.0D) || target == null || !target.isAlive()) return false;
        DamageSource damageSource = new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MOB_ATTACK), source);
        return target.hurt(damageSource, (float) amount);
    }

    private void tickBossEncounters(long serverTick) {
        Set<UUID> seenBosses = new LinkedHashSet<>();
        for (NpcInstance placement : List.copyOf(instances.values())) {
            if (placement == null || !placement.enabled || placement.dead) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled || !definition.bossEnabled) continue;
            Entity runtime = findRuntime(placement);
            if (!(runtime instanceof LivingEntity boss) || !boss.isAlive() || boss.isRemoved()
                    || !(boss.level() instanceof ServerLevel level)) continue;
            UUID instanceId = placement.uuid();
            seenBosses.add(instanceId);

            double homeDistanceSqr = boss.distanceToSqr(placement.x, placement.y, placement.z);
            double resetDistanceSqr = definition.bossResetDistance * definition.bossResetDistance;
            boolean combatActive = authorizedCombatTargets.containsKey(instanceId) || reactiveCombat.containsKey(instanceId)
                    || abilityController.casting(instanceId) || threatController.hasThreat(instanceId);
            NpcBossPhase phase = definition.bossPhase(boss.getHealth(), boss.getMaxHealth());
            updateBossPhase(instanceId, definition, boss, phase, combatActive, serverTick);
            updateBossBar(instanceId, definition, boss, level);

            if (combatActive && homeDistanceSqr <= resetDistanceSqr) {
                lastBossCombatTick.put(instanceId, serverTick);
                continue;
            }
            if (combatActive) {
                authorizedCombatTargets.remove(instanceId); reactiveCombat.remove(instanceId); nextCombatAttackTick.remove(instanceId);
                abilityController.resetCooldowns(instanceId); attackPatternController.reset(instanceId); threatController.forget(instanceId);
                if (boss instanceof Mob mob) { mob.setTarget(null); mob.setAggressive(false); mob.getNavigation().stop(); }
            }
            long lastCombat = lastBossCombatTick.computeIfAbsent(instanceId, ignored -> serverTick);
            if (!combatActive && serverTick - lastCombat < (long) definition.bossResetSeconds * 20L) continue;
            boolean damaged = boss.getHealth() + 0.01F < boss.getMaxHealth();
            boolean outsideArena = homeDistanceSqr > resetDistanceSqr;
            if (!damaged && !outsideArena && !activeBossEncounters.contains(instanceId)) continue;
            if (homeDistanceSqr > 2.25D) {
                if (boss instanceof Mob mob && mob.isNoAi()) mob.setNoAi(false);
                navigation.move(instanceId, boss, definition, placement.x, placement.y, placement.z,
                        Math.max(0.8D, definition.walkingSpeed), serverTick,
                        NpcAiProfile.resolve(boss).routeRepathTicks());
                continue;
            }
            navigation.stop(instanceId, boss);
            if (damaged && definition.bossHealOnReset) boss.setHealth(boss.getMaxHealth());
            abilityController.resetCooldowns(instanceId);
            attackPatternController.reset(instanceId);
            threatController.forget(instanceId);
            cleanupBossEncounter(instanceId);
            activeBossPhase.remove(instanceId);
            if (boss instanceof Mob mob) restoreConfiguredNoAi(mob, definition, placement);
            lastBossCombatTick.put(instanceId, serverTick);
        }
        for (UUID instanceId : List.copyOf(bossBars.keySet())) if (!seenBosses.contains(instanceId)) removeBossBar(instanceId);
        for (UUID instanceId : List.copyOf(activeBossEncounters)) if (!seenBosses.contains(instanceId)) cleanupBossEncounter(instanceId);
        activeBossPhase.keySet().removeIf(id -> !seenBosses.contains(id));
        lastBossCombatTick.keySet().removeIf(id -> !seenBosses.contains(id));
    }

    private void updateBossPhase(UUID instanceId, NpcDefinition definition, LivingEntity boss, NpcBossPhase phase,
            boolean combatActive, long serverTick) {
        if (phase == null) return;
        String previous = activeBossPhase.put(instanceId, phase.id);
        boolean encounterJustStarted = combatActive && activeBossEncounters.add(instanceId);
        boolean phaseChanged = previous != null && !previous.equals(phase.id);
        boolean activePhaseChanged = phaseChanged && activeBossEncounters.contains(instanceId);
        if (!encounterJustStarted && !activePhaseChanged) return;
        attackPatternController.reset(instanceId);
        if (activePhaseChanged && phase.actions.stream().noneMatch(a -> a != null && a.actionType() == NpcBossPhaseActionType.ANNOUNCE)) {
            announceBossPhase(definition, boss, phase.displayName);
        }
        executeBossPhaseActions(instanceId, definition, boss, phase, serverTick);
    }

    private void executeBossPhaseActions(UUID instanceId, NpcDefinition definition, LivingEntity boss,
            NpcBossPhase phase, long serverTick) {
        if (!(boss.level() instanceof ServerLevel level) || phase.actions == null || phase.actions.isEmpty()) return;
        LivingEntity target = boss instanceof Mob mob ? mob.getTarget() : null;
        if (target == null) {
            UUID targetId = authorizedCombatTargets.get(instanceId);
            Entity candidate = targetId == null ? null : level.getEntity(targetId);
            if (candidate instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) target = living;
        }
        for (NpcBossPhaseAction raw : List.copyOf(phase.actions)) {
            if (raw == null) continue;
            NpcBossPhaseAction action = raw.copy();
            switch (action.actionType()) {
                case ANNOUNCE -> announceBossPhase(definition, boss,
                        action.value.isBlank() ? phase.displayName : action.value);
                case TRIGGER_ABILITY -> abilityController.triggerScripted(instanceId, boss, target, definition, phase,
                        serverTick, action.value, candidate -> !isFriendlyTarget(definition, candidate));
                case SPAWN_ADDS -> spawnBossAdds(instanceId, definition, boss, level, action);
                case HEAL_PERCENT -> {
                    if (action.amount <= 0.0D) break;
                    float heal = (float) (boss.getMaxHealth() * Math.min(100.0D, action.amount) / 100.0D);
                    boss.heal(heal);
                }
                case THREAT_RESET -> threatController.forget(instanceId);
                case FIXATE_RANDOM -> fixateRandomPlayer(instanceId, definition, boss, level, action, serverTick);
                case DESPAWN_ADDS -> removeBossAdds(instanceId);
            }
        }
    }

    private void announceBossPhase(NpcDefinition definition, LivingEntity boss, String text) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        String safe = text == null || text.isBlank() ? definition.displayName : text.trim();
        Component message = Component.literal(definition.displayName + " — " + safe);
        double rangeSqr = definition.bossBarRange * definition.bossBarRange;
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(boss) <= rangeSqr) player.sendSystemMessage(message, true);
    }

    private void spawnBossAdds(UUID bossInstanceId, NpcDefinition bossDefinition, LivingEntity boss,
            ServerLevel level, NpcBossPhaseAction action) {
        String addDefinitionId = NpcDefinition.sanitizeId(action.value);
        NpcDefinition addDefinition = definitions.get(addDefinitionId);
        if (addDefinition == null || !addDefinition.enabled || addDefinition.bossEnabled || addDefinitionId.equals(bossDefinition.id)) return;
        int count = Math.max(1, Math.min(16, (int) Math.round(action.amount)));
        double radius = Math.max(0.5D, Math.min(32.0D, action.radius));
        Set<UUID> owned = bossEncounterAdds.computeIfAbsent(bossInstanceId, ignored -> new LinkedHashSet<>());
        for (int i = 0; i < count; i++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.35D + boss.getRandom().nextDouble() * 0.65D);
            double x = boss.getX() + Math.cos(angle) * distance;
            double z = boss.getZ() + Math.sin(angle) * distance;
            double y = boss.getY();
            NpcInstance add = createDynamicPlacement(addDefinitionId, level, x, y, z, boss.getYRot(),
                    "boss_add_" + bossInstanceId, Math.max(48.0D, bossDefinition.bossBarRange + 24.0D));
            if (add != null) owned.add(add.uuid());
        }
        if (owned.isEmpty()) bossEncounterAdds.remove(bossInstanceId);
    }

    private void fixateRandomPlayer(UUID instanceId, NpcDefinition definition, LivingEntity boss, ServerLevel level,
            NpcBossPhaseAction action, long serverTick) {
        List<ServerPlayer> candidates = new ArrayList<>();
        double rangeSqr = definition.bossBarRange * definition.bossBarRange;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.distanceToSqr(boss) > rangeSqr
                    || isFriendlyTarget(definition, player)) continue;
            candidates.add(player);
        }
        if (candidates.isEmpty()) return;
        ServerPlayer chosen = candidates.get(boss.getRandom().nextInt(candidates.size()));
        long duration = Math.max(20L, Math.min(1_200L, Math.round(action.amount * 20.0D)));
        threatController.taunt(instanceId, chosen, 1_000_000.0D, serverTick, duration);
        authorizedCombatTargets.put(instanceId, chosen.getUUID());
        if (boss instanceof Mob mob) mob.setTarget(chosen);
    }

    private void removeBossAdds(UUID bossInstanceId) {
        Set<UUID> owned = bossEncounterAdds.remove(bossInstanceId);
        if (owned == null || owned.isEmpty()) return;
        for (UUID addId : List.copyOf(owned)) removeDynamicInstance(addId, true);
    }

    private void cleanupBossEncounter(UUID instanceId) {
        activeBossEncounters.remove(instanceId);
        removeBossAdds(instanceId);
    }

    private void updateBossBar(UUID instanceId, NpcDefinition definition, LivingEntity boss, ServerLevel level) {
        if (!definition.bossBarVisible) { removeBossBar(instanceId); return; }
        ServerBossEvent event = bossBars.computeIfAbsent(instanceId, id -> new ServerBossEvent(
                Component.literal(definition.displayName), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
        event.setName(Component.literal(definition.displayName));
        float progress = boss.getMaxHealth() <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, boss.getHealth() / boss.getMaxHealth()));
        event.setProgress(progress);
        double rangeSqr = definition.bossBarRange * definition.bossBarRange;
        Set<UUID> visible = new LinkedHashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(boss) > rangeSqr) continue;
            visible.add(player.getUUID()); event.addPlayer(player);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!visible.contains(player.getUUID())) event.removePlayer(player);
        }
    }

    private void removeBossBar(UUID instanceId) {
        ServerBossEvent event = bossBars.remove(instanceId);
        if (event != null) event.removeAllPlayers();
    }

    private void clearBossBars() {
        for (ServerBossEvent event : bossBars.values()) event.removeAllPlayers();
        bossBars.clear();
    }

    private LivingEntity nearestHostile(NpcDefinition sourceDefinition, NpcInstance sourcePlacement, LivingEntity source,
            Map<String, Map<Long, List<NpcInstance>>> nearbyIndex, double followRange) {
        LivingEntity nearest = null;
        double nearestDistance = followRange * followRange;
        if (sourceDefinition.attitudeTowardPlayers() == NpcAttitude.HOSTILE) {
            for (Player player : source.level().players()) {
                if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
                double distance = source.distanceToSqr(player);
                if (distance < nearestDistance) { nearest = player; nearestDistance = distance; }
            }
        }
        for (NpcInstance targetPlacement : relationCandidates(
                nearbyIndex, sourcePlacement.dimension, source.getX(), source.getZ(), followRange)) {
            if (targetPlacement == sourcePlacement || targetPlacement.dead || !targetPlacement.enabled) continue;
            Entity targetEntity = findRuntime(targetPlacement);
            if (!(targetEntity instanceof LivingEntity living) || living.level() != source.level()
                    || !living.isAlive() || living.isRemoved()) continue;
            NpcDefinition targetDefinition = definitions.get(targetPlacement.definitionId);
            if (targetDefinition == null
                    || sourceDefinition.attitudeTowardFaction(targetDefinition.factionId) != NpcAttitude.HOSTILE) continue;
            double distance = source.distanceToSqr(living);
            if (distance < nearestDistance) { nearest = living; nearestDistance = distance; }
        }
        return nearest;
    }

    private void fleeFromThreat(UUID sourceId, LivingEntity source, NpcDefinition definition,
            LivingEntity threat, long serverTick) {
        if (threat == null) return;
        Vec3 away = source.position().subtract(threat.position());
        if (away.lengthSqr() < 0.001D) away = new Vec3(1.0D, 0.0D, 0.0D);
        NpcLocomotionProfile locomotion = NpcLocomotionProfile.resolve(source);
        boolean verticalSteering = definition.canFly || locomotion.allowsVerticalSteering();
        Vec3 horizontal = new Vec3(away.x, verticalSteering ? away.y : 0.0D, away.z).normalize();
        Vec3 target = source.position().add(horizontal.scale(definition.fleeDistance));
        navigation.move(sourceId, source, definition, target.x, target.y, target.z, 1.2D, serverTick,
                NpcAiProfile.resolve(source).combatRepathTicks());
    }

    private LivingEntity livingEntity(LivingEntity source, UUID entityId) {
        if (source == null || entityId == null || !(source.level() instanceof ServerLevel level)) return null;
        Entity entity = level.getEntity(entityId);
        return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }

    private boolean isFriendlyTarget(NpcDefinition source, LivingEntity target) {
        if (source == null || target == null) return false;
        if (target instanceof Player) return source.attitudeTowardPlayers() == NpcAttitude.FRIENDLY;
        NpcDefinition targetDefinition = definitionFor(instanceForEntity(target.getUUID()));
        return targetDefinition != null
                && source.attitudeTowardFaction(targetDefinition.factionId) == NpcAttitude.FRIENDLY;
    }

    private static void restoreConfiguredNoAi(Mob mob, NpcDefinition definition, NpcInstance placement) {
        boolean scheduleControlsMovement = placement.scheduleEnabled && !placement.schedule.isEmpty();
        NpcBehaviorMode behaviorMode = definition.behaviorMode();
        boolean suppressNativeAi = !scheduleControlsMovement
                && (behaviorMode == NpcBehaviorMode.STATIONARY || behaviorMode == NpcBehaviorMode.LOOK_AT_PLAYERS);
        mob.setNoAi(suppressNativeAi);
    }

    /** Records a real or attempted attack so the NPC brain can retaliate, flee or call allies. */
    public synchronized void noteAttack(LivingEntity victim, LivingEntity attacker) {
        if (server == null || victim == null || attacker == null || victim == attacker) return;
        NpcInstance placement = instanceForEntity(victim.getUUID());
        NpcDefinition definition = definitionFor(placement);
        if (placement == null || definition == null || placement.dead || !placement.enabled) return;
        long tick = server.getTickCount();
        NpcSelfDefenseReaction reaction = definition.whenAttacked();
        recentNpcAttacks.put(placement.uuid(), new RecentNpcAttack(attacker.getUUID(), tick + 200L,
                reaction == NpcSelfDefenseReaction.CALL_ALLIES));
        if (definition.threatEnabled && definition.combatProfile() != NpcCombatProfile.PASSIVE
                && reaction != NpcSelfDefenseReaction.IGNORE && reaction != NpcSelfDefenseReaction.FLEE) {
            threatController.ensure(placement.uuid(), attacker, 1.0D, tick);
        }
        switch (reaction) {
            case FLEE -> reactiveCombat.put(placement.uuid(), new CombatIntent(attacker.getUUID(), CombatMode.FLEE, tick + 200L));
            case FIGHT, CALL_ALLIES -> {
                if (definition.combatProfile() != NpcCombatProfile.PASSIVE) {
                    reactiveCombat.put(placement.uuid(), new CombatIntent(attacker.getUUID(), CombatMode.FIGHT, tick + 200L));
                }
            }
            case IGNORE -> { }
        }
    }

    /** Adds threat from confirmed incoming damage after all SSU damage-cancellation checks passed. */
    public synchronized void noteDamage(LivingEntity victim, LivingEntity attacker, double amount) {
        if (server == null || victim == null || attacker == null || victim == attacker || !(amount > 0.0D)) return;
        NpcInstance placement = instanceForEntity(victim.getUUID());
        NpcDefinition definition = definitionFor(placement);
        if (placement != null) abilityController.noteDamaged(placement.uuid(), server.getTickCount());
        if (placement == null || definition == null || !definition.threatEnabled || placement.dead || !placement.enabled
                || definition.combatProfile() == NpcCombatProfile.PASSIVE
                || definition.whenAttacked() == NpcSelfDefenseReaction.IGNORE
                || definition.whenAttacked() == NpcSelfDefenseReaction.FLEE
                || isFriendlyTarget(definition, attacker)) return;
        threatController.add(placement.uuid(), attacker, amount * definition.threatDamageMultiplier, server.getTickCount());
    }

    /**
     * Adds source-attributed healing threat to enemies already engaged with the healed entity.
     * Generic vanilla/NeoForge healing has no healer source, so SSU systems call this hook when they know it.
     */
    public synchronized void noteHealingThreat(LivingEntity healer, LivingEntity healed, double amount) {
        if (server == null || healer == null || healed == null || !(amount > 0.0D)) return;
        long tick = server.getTickCount();
        for (UUID instanceId : List.copyOf(relationInstances)) {
            NpcInstance placement = instances.get(instanceId);
            NpcDefinition definition = definitionFor(placement);
            Entity runtime = placement == null ? null : findRuntime(placement);
            if (definition == null || !definition.threatEnabled || !(runtime instanceof LivingEntity source)
                    || !source.isAlive() || source.level() != healer.level() || isFriendlyTarget(definition, healer)) continue;
            UUID authorized = authorizedCombatTargets.get(instanceId);
            if (!healed.getUUID().equals(authorized) && !threatController.contains(instanceId, healed.getUUID())) continue;
            double range = definition.threatRange;
            if (source.distanceToSqr(healer) > range * range) continue;
            threatController.add(instanceId, healer, amount * definition.threatHealingMultiplier, tick);
        }
    }

    /** Forces a threat-enabled NPC onto one target for a bounded duration. Intended for tank/taunt mechanics. */
    public synchronized boolean taunt(UUID instanceId, LivingEntity target, double bonusThreat, int durationTicks) {
        if (server == null || instanceId == null || target == null) return false;
        NpcInstance placement = instances.get(instanceId);
        NpcDefinition definition = definitionFor(placement);
        Entity runtime = placement == null ? null : findRuntime(placement);
        if (definition == null || !definition.threatEnabled || !(runtime instanceof LivingEntity source)
                || source.level() != target.level() || isFriendlyTarget(definition, target)) return false;
        NpcBossPhase phase = definition.bossPhase(source.getHealth(), source.getMaxHealth());
        if (definition.bossEnabled && phase != null && phase.tauntImmune) return false;
        threatController.taunt(instanceId, target, bonusThreat, server.getTickCount(), Math.max(1, durationTicks));
        return true;
    }

    /** True only for targets selected by faction hostility, retaliation or ally-assist logic. */
    public synchronized boolean mayDamage(LivingEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) return false;
        NpcInstance source = instanceForEntity(attacker.getUUID());
        NpcDefinition definition = definitionFor(source);
        if (source == null || definition == null) return true;
        if (isHostileTarget(definition, target)) return true;
        UUID authorized = authorizedCombatTargets.get(source.uuid());
        return target.getUUID().equals(authorized);
    }

    private boolean combatBusy(UUID instanceId) {
        return instanceId != null && (authorizedCombatTargets.containsKey(instanceId)
                || reactiveCombat.containsKey(instanceId) || abilityController.casting(instanceId)
                || threatController.hasThreat(instanceId));
    }

    private Map<String, Map<Long, List<NpcInstance>>> buildRelationSpatialIndex() {
        Map<String, Map<Long, List<NpcInstance>>> index = new LinkedHashMap<>();
        for (NpcInstance placement : instances.values()) {
            if (!placement.enabled || placement.dead) continue;
            Entity entity = findRuntime(placement);
            if (!(entity instanceof LivingEntity living) || living.isRemoved() || !living.isAlive()) continue;
            int chunkX = ((int) Math.floor(living.getX())) >> 4;
            int chunkZ = ((int) Math.floor(living.getZ())) >> 4;
            index.computeIfAbsent(placement.dimension, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>())
                    .add(placement);
        }
        return index;
    }

    private static List<NpcInstance> relationCandidates(
            Map<String, Map<Long, List<NpcInstance>>> index,
            String dimension,
            double x,
            double z,
            double range
    ) {
        Map<Long, List<NpcInstance>> dimensionIndex = index.get(dimension);
        if (dimensionIndex == null || dimensionIndex.isEmpty()) return List.of();
        int centerX = ((int) Math.floor(x)) >> 4;
        int centerZ = ((int) Math.floor(z)) >> 4;
        int radius = Math.max(1, (int) Math.ceil(range / 16.0D));
        List<NpcInstance> result = new ArrayList<>();
        for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
            for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
                List<NpcInstance> bucket = dimensionIndex.get(chunkKey(chunkX, chunkZ));
                if (bucket != null) result.addAll(bucket);
            }
        }
        return result;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((long) chunkZ << 32);
    }

    public synchronized boolean isHostileTarget(NpcDefinition source, LivingEntity target) {
        if (source == null || target == null) return false;
        if (target instanceof Player player) {
            return !player.isSpectator() && !player.isCreative()
                    && source.attitudeTowardPlayers() == NpcAttitude.HOSTILE;
        }
        NpcDefinition targetDefinition = definitionFor(instanceForEntity(target.getUUID()));
        return targetDefinition != null
                && source.attitudeTowardFaction(targetDefinition.factionId) == NpcAttitude.HOSTILE;
    }

    /**
     * Combat owns movement while active. On the first ambient tick after combat, discard the old
     * chase path once and let patrol/schedule immediately install a clean route back to its logical
     * destination. This keeps the route index/schedule slot intact rather than restarting the NPC.
     */
    private boolean ambientMovementInterruptedByCombat(UUID instanceId, LivingEntity living) {
        if (combatBusy(instanceId)) {
            movementInterruptedByCombat.add(instanceId);
            navigation.forget(instanceId);
            return true;
        }
        if (movementInterruptedByCombat.remove(instanceId)) {
            navigation.beginNewRoute(instanceId, living);
        }
        return false;
    }

    private void tickSchedules(MinecraftServer activeServer, long serverTick) {
        for (UUID scheduledId : List.copyOf(scheduledInstances)) {
            NpcInstance placement = instances.get(scheduledId);
            if (placement == null || !placement.enabled) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled) continue;
            ServerLevel level = level(placement.dimension);
            UUID runtimeId = placement.runtimeUuid();
            Entity entity = level == null || runtimeId == null ? null : level.getEntity(runtimeId);
            if (!(entity instanceof LivingEntity living) || entity.isRemoved()) continue;
            if (definition.canSwim) living.setAirSupply(living.getMaxAirSupply());
            if (!placement.scheduleEnabled || placement.schedule.isEmpty()) continue;
            // Combat temporarily interrupts the schedule. As soon as the hostile target is gone,
            // the active time slot naturally resumes and the NPC returns to its schedule target.
            UUID instanceId = placement.uuid();
            boolean resumingFromCombat = movementInterruptedByCombat.contains(instanceId);
            if (ambientMovementInterruptedByCombat(instanceId, living)) {
                returningScheduleInstances.remove(instanceId);
                continue;
            }
            if (resumingFromCombat) returningScheduleInstances.add(instanceId);
            int minute = GameCalendar.fromClockTime(level.getDayTime()).minuteOfDay();
            int index = activeScheduleIndex(placement.schedule, minute);
            if (index < 0) continue;
            NpcScheduleEntry entry = placement.schedule.get(index);
            Integer previousIndex = activeScheduleEntry.put(instanceId, index);
            boolean changed = previousIndex == null || previousIndex.intValue() != index;
            boolean recoveringSchedule = returningScheduleInstances.contains(instanceId);
            if (changed && NpcScheduleEntry.MOVEMENT_TELEPORT.equals(entry.movement) && !recoveringSchedule) {
                living.moveTo(entry.x, entry.y, entry.z, entry.yaw, living.getXRot());
                living.setDeltaMovement(Vec3.ZERO);
            }
            boolean arrived = routePointReached(living, entry.x, entry.y, entry.z);
            if (arrived) returningScheduleInstances.remove(instanceId);
            // Explicit TELEPORT schedule entries may teleport when their time slot activates, but
            // combat recovery always walks back until arrival. That keeps combat movement ownership
            // free of snaps even when a repath is needed on a later schedule tick.
            if (!arrived && (recoveringSchedule || !NpcScheduleEntry.MOVEMENT_TELEPORT.equals(entry.movement))) {
                NpcNavigationController.MoveResult movement = moveScheduledNpc(
                        living, definition, entry, serverTick, instanceId);
                if (movement == NpcNavigationController.MoveResult.STUCK) {
                    // Do not phase/teleport an unreachable NPC through geometry. The shared
                    // controller cools down and retries; the next schedule slot may also provide
                    // a different reachable destination.
                    nextScheduleActivityTick.remove(instanceId);
                }
            } else {
                navigation.stop(instanceId, living);
                living.setYRot(entry.yaw);
                living.setYHeadRot(entry.yaw);
                runScheduleActivity(living, entry, serverTick, instanceId);
            }
        }
    }

    private static int activeScheduleIndex(List<NpcScheduleEntry> entries, int minute) {
        int selected = entries.size() - 1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).minuteOfDay <= minute) selected = i;
            else break;
        }
        return selected;
    }

    private NpcNavigationController.MoveResult moveScheduledNpc(LivingEntity entity, NpcDefinition definition,
            NpcScheduleEntry entry, long serverTick, UUID instanceId) {
        return navigation.move(instanceId, entity, definition,
                entry.x, entry.y, entry.z, entry.speed, serverTick,
                NpcAiProfile.resolve(entity).routeRepathTicks());
    }

    private void runScheduleActivity(LivingEntity entity, NpcScheduleEntry entry, long serverTick, UUID instanceId) {
        long next = nextScheduleActivityTick.getOrDefault(instanceId, 0L);
        if (serverTick < next) return;
        NpcScheduleActivity activity = NpcScheduleActivity.parse(entry.activity);
        switch (activity) {
            case WORK, CHOP_TREE -> {
                entity.swing(InteractionHand.MAIN_HAND);
                nextScheduleActivityTick.put(instanceId, serverTick + 16L);
            }
            case LOOK_AROUND -> {
                entity.setYRot(entity.getYRot() + 35.0F);
                entity.setYHeadRot(entity.getYRot());
                nextScheduleActivityTick.put(instanceId, serverTick + 40L);
            }
            case GUARD -> {
                // Guard is intentionally simple in the AI-foundation build: hostile relation
                // handling already interrupts the schedule, while this idle scan makes the
                // arrival action visibly different until the richer combat brain is added.
                entity.setYRot(entity.getYRot() + 55.0F);
                entity.setYHeadRot(entity.getYRot());
                nextScheduleActivityTick.put(instanceId, serverTick + 30L);
            }
            case IDLE -> nextScheduleActivityTick.put(instanceId, serverTick + 40L);
        }
    }



    private void tickBehaviors(MinecraftServer activeServer, long serverTick) {
        if ((serverTick & 3L) != 0L) return; // 5 Hz is enough for look/path updates.
        for (UUID instanceId : List.copyOf(behaviorInstances)) {
            NpcInstance placement = instances.get(instanceId);
            if (placement == null || !placement.enabled || placement.dead
                    || (placement.scheduleEnabled && !placement.schedule.isEmpty())) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled) continue;
            ServerLevel level = level(placement.dimension);
            Entity raw = level == null ? null : findRuntime(placement);
            if (!(raw instanceof LivingEntity living) || raw.isRemoved()) continue;
            if (definition.canSwim) living.setAirSupply(living.getMaxAirSupply());

            // Combat owns movement completely. Remember the transition so non-patrol ambient
            // behaviors can walk back to the placement anchor instead of being teleported there.
            boolean wasInterruptedByCombat = movementInterruptedByCombat.contains(instanceId);
            if (ambientMovementInterruptedByCombat(instanceId, living)) continue;
            NpcBehaviorMode behavior = definition.behaviorMode();
            if (wasInterruptedByCombat && behavior != NpcBehaviorMode.PATROL) {
                returningHomeInstances.add(instanceId);
                wanderTargets.remove(instanceId);
                nextBehaviorDecisionTick.remove(instanceId);
            }

            // Replace the old periodic snap-back leash with native pathfinding. Patrol resumes its
            // existing logical waypoint; all other non-scheduled modes return to the home anchor.
            if (behavior != NpcBehaviorMode.PATROL
                    && shouldReturnHome(living, definition, placement, behavior, instanceId)) {
                returningHomeInstances.add(instanceId);
            }
            if (returningHomeInstances.contains(instanceId)
                    && tickReturnHome(living, definition, placement, serverTick, instanceId)) {
                continue;
            }

            switch (behavior) {
                case LOOK_AT_PLAYERS -> tickLookAtPlayers(activeServer, living, definition);
                case WANDER -> tickWander(living, definition, placement, serverTick, instanceId);
                case PATROL -> tickPatrol(living, definition, placement, serverTick, instanceId);
                default -> {
                }
            }
        }
    }

    private boolean shouldReturnHome(LivingEntity living, NpcDefinition definition, NpcInstance placement,
            NpcBehaviorMode behavior, UUID instanceId) {
        if (returningHomeInstances.contains(instanceId)) return true;
        double distanceSquared = living.distanceToSqr(placement.x, placement.y, placement.z);
        return switch (behavior) {
            // Vanilla PathNavigation considers roughly one block close enough. Use that same
            // tolerance so a stationary shell does not oscillate between "arrived" and "return".
            case STATIONARY, LOOK_AT_PLAYERS -> distanceSquared > 1.0D;
            case WANDER -> {
                double leash = Math.max(definition.homeRadius, definition.wanderRadius + 4.0D);
                yield leash > 0.0D && distanceSquared > leash * leash;
            }
            case NATIVE -> definition.homeRadius > 0.0D
                    && distanceSquared > definition.homeRadius * definition.homeRadius;
            case PATROL -> false;
        };
    }

    /**
     * Walk a displaced NPC back to its configured placement anchor. This deliberately goes through
     * the shared navigation controller: ordinary reconcile/sync is never allowed to own position.
     */
    private boolean tickReturnHome(LivingEntity living, NpcDefinition definition, NpcInstance placement,
            long serverTick, UUID instanceId) {
        if (routePointReached(living, placement.x, placement.y, placement.z)) {
            returningHomeInstances.remove(instanceId);
            navigation.stop(instanceId, living);
            wanderTargets.remove(instanceId);
            nextBehaviorDecisionTick.remove(instanceId);
            if (living instanceof Mob mob) restoreConfiguredNoAi(mob, definition, placement);
            if (definition.behaviorMode() == NpcBehaviorMode.STATIONARY) {
                living.setYRot(placement.yaw);
                living.setYHeadRot(placement.yaw);
            }
            return false;
        }
        if (living instanceof Mob mob && mob.isNoAi()) mob.setNoAi(false);
        moveBehaviorNpc(living, definition, placement.x, placement.y, placement.z,
                definition.walkingSpeed, serverTick, instanceId);
        return true;
    }

    private void tickLookAtPlayers(MinecraftServer activeServer, LivingEntity living, NpcDefinition definition) {
        if (definition.lookAtRange <= 0.0D) return;
        ServerPlayer nearest = null;
        double best = definition.lookAtRange * definition.lookAtRange;
        for (ServerPlayer player : activeServer.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive() || player.level() != living.level()) continue;
            double distance = player.distanceToSqr(living);
            if (distance > best) continue;
            best = distance;
            nearest = player;
        }
        if (nearest == null) return;

        double dx = nearest.getX() - living.getX();
        double dz = nearest.getZ() - living.getZ();
        double dy = nearest.getEyeY() - living.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.0001D, horizontal)));
        living.setXRot(Math.max(-90.0F, Math.min(90.0F, pitch)));
        living.setYHeadRot(yaw);
        if (definition.lookAtBody) living.setYRot(yaw);
    }

    private void tickWander(LivingEntity living, NpcDefinition definition, NpcInstance placement,
            long serverTick, UUID instanceId) {
        if (definition.wanderRadius <= 0.0D) {
            navigation.stop(instanceId, living);
            return;
        }
        Vec3 target = wanderTargets.get(instanceId);
        boolean reached = target != null && routePointReached(living, target.x, target.y, target.z);
        long nextDecision = nextBehaviorDecisionTick.getOrDefault(instanceId, 0L);
        if (target == null || reached || serverTick >= nextDecision) {
            NpcAiProfile aiProfile = NpcAiProfile.resolve(living);
            target = chooseWanderTarget(living, definition, placement, aiProfile);
            wanderTargets.put(instanceId, target);
            nextBehaviorDecisionTick.put(instanceId,
                    serverTick + aiProfile.wanderDecisionTicks(definition.wanderIntervalSeconds));
        }
        NpcNavigationController.MoveResult movement = moveBehaviorNpc(living, definition,
                target.x, target.y, target.z, definition.walkingSpeed, serverTick, instanceId);
        if (movement == NpcNavigationController.MoveResult.STUCK) {
            // Pick a different wander destination instead of endlessly pressing into an obstacle.
            wanderTargets.remove(instanceId);
            nextBehaviorDecisionTick.put(instanceId, serverTick + 20L);
        }
    }

    private Vec3 chooseWanderTarget(LivingEntity living, NpcDefinition definition, NpcInstance placement,
            NpcAiProfile aiProfile) {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        Vec3 fallback = new Vec3(placement.x, placement.y, placement.z);
        int attempts = aiProfile.threeDimensionalWander(living) ? 10 : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
            // sqrt() keeps destinations spread over the full configured disc.
            double radius = Math.sqrt(random.nextDouble()) * definition.wanderRadius;
            double x = placement.x + Math.cos(angle) * radius;
            double z = placement.z + Math.sin(angle) * radius;
            double y = placement.y;
            if (aiProfile.threeDimensionalWander(living)) {
                double verticalRange = Math.min(6.0D, Math.max(1.5D, definition.wanderRadius * 0.5D));
                y += random.nextDouble(-verticalRange, verticalRange);
            }
            Vec3 candidate = new Vec3(x, y, z);
            fallback = candidate;
            BlockPos block = BlockPos.containing(candidate.x, candidate.y, candidate.z);
            if (aiProfile.needsWaterWander(living)) {
                if (living.level().getFluidState(block).is(FluidTags.WATER)) return candidate;
            } else if (aiProfile == NpcAiProfile.FLYING) {
                if (living.level().getBlockState(block).isAir()) return candidate;
            } else {
                return candidate;
            }
        }
        return fallback;
    }

    private void tickPatrol(LivingEntity living, NpcDefinition definition, NpcInstance placement,
            long serverTick, UUID instanceId) {
        if (placement.patrol == null || placement.patrol.isEmpty()) {
            navigation.stop(instanceId, living);
            return;
        }
        int size = placement.patrol.size();
        int index = Math.max(0, Math.min(size - 1, activePatrolPoint.getOrDefault(instanceId, 0)));
        NpcPatrolPoint point = placement.patrol.get(index);

        // Do not require the mob's feet to hit one exact floating-point coordinate. Vanilla
        // PathNavigation may legitimately finish on the target block while the entity is a little
        // over one block from the stored center (terrain height, collision and node-centre choices
        // all contribute). If SSU keeps waiting for <= 1.0 exact 3-D distance in that situation,
        // the NPC visibly reaches waypoint 1 but the patrol index never advances.
        if (patrolPointReached(living, point)) {
            long pauseUntil = patrolPauseUntilTick.getOrDefault(instanceId, 0L);

            if (point.pauseSeconds > 0) {
                // A configured pause is an intentional hard stop and may also face the stored yaw.
                navigation.stop(instanceId, living);
                living.setYRot(point.yaw);
                living.setYHeadRot(point.yaw);
                if (pauseUntil == 0L) {
                    patrolPauseUntilTick.put(instanceId, serverTick + (long) point.pauseSeconds * 20L);
                    return;
                }
                if (serverTick < pauseUntil) return;
                patrolPauseUntilTick.remove(instanceId);
            } else {
                patrolPauseUntilTick.remove(instanceId);
            }

            if (size <= 1) {
                navigation.stop(instanceId, living);
                return;
            }
            index = advancePatrolSegment(placement, living, instanceId, index);
            point = placement.patrol.get(index);
        }

        NpcNavigationController.MoveResult movement = moveBehaviorNpc(living, definition,
                point.x, point.y, point.z, definition.walkingSpeed, serverTick, instanceId);
        if (movement == NpcNavigationController.MoveResult.STUCK && size > 1) {
            // An unreachable patrol point should not freeze the complete route forever.
            patrolPauseUntilTick.remove(instanceId);
            int next = advancePatrolSegment(placement, living, instanceId, index);
            NpcPatrolPoint nextPoint = placement.patrol.get(next);
            // Start the replacement route immediately instead of waiting another 5 Hz behavior tick.
            moveBehaviorNpc(living, definition, nextPoint.x, nextPoint.y, nextPoint.z,
                    definition.walkingSpeed, serverTick, instanceId);
        }
    }

    private static boolean patrolPointReached(LivingEntity living, NpcPatrolPoint point) {
        return routePointReached(living, point.x, point.y, point.z);
    }

    /**
     * Species-aware route arrival shared by patrol, schedule and wander. Native pathfinding is
     * allowed to finish on a nearby valid node instead of forcing every shell to hit one exact XYZ.
     * This is especially important for hoppers, flyers, swimmers and mobs with wider collision.
     */
    private static boolean routePointReached(LivingEntity living, double x, double y, double z) {
        if (living == null) return false;
        NpcAiProfile aiProfile = NpcAiProfile.resolve(living);
        double dx = living.getX() - x;
        double dz = living.getZ() - z;
        double horizontalSqr = dx * dx + dz * dz;
        double vertical = Math.abs(living.getY() - y);
        if (vertical <= aiProfile.arrivalVertical() && horizontalSqr <= aiProfile.arrivalHorizontalSqr()) {
            return true;
        }
        return living instanceof Mob mob
                && mob.getNavigation().isDone()
                && vertical <= aiProfile.arrivalVertical()
                && horizontalSqr <= aiProfile.finishedPathHorizontalSqr();
    }

    /**
     * Advances to a genuinely new patrol route segment. Clear both SSU's route cache and the
     * completed native path, but deliberately keep entity velocity. The next move call in this same
     * behavior tick installs the new path, allowing continuous movement without the old path holding
     * the mob on the previous waypoint.
     */
    private int advancePatrolSegment(NpcInstance placement, LivingEntity living, UUID instanceId, int current) {
        int next = nextPatrolIndex(placement, instanceId, current);
        activePatrolPoint.put(instanceId, next);
        patrolPauseUntilTick.remove(instanceId);
        navigation.beginNewRoute(instanceId, living);
        return next;
    }

    private int nextPatrolIndex(NpcInstance placement, UUID instanceId, int current) {
        int size = placement.patrol.size();
        if (size <= 1) return 0;
        return switch (NpcPatrolMode.parse(placement.patrolMode)) {
            case LOOP -> (current + 1) % size;
            case RANDOM -> {
                int next = java.util.concurrent.ThreadLocalRandom.current().nextInt(size - 1);
                yield next >= current ? next + 1 : next;
            }
            case PING_PONG -> {
                int direction = patrolDirection.getOrDefault(instanceId, 1);
                int next = current + direction;
                if (next >= size) {
                    direction = -1;
                    next = size - 2;
                } else if (next < 0) {
                    direction = 1;
                    next = 1;
                }
                patrolDirection.put(instanceId, direction);
                yield next;
            }
        };
    }

    private NpcNavigationController.MoveResult moveBehaviorNpc(LivingEntity entity, NpcDefinition definition,
            double targetX, double targetY, double targetZ, double speedMultiplier,
            long serverTick, UUID instanceId) {
        return navigation.move(instanceId, entity, definition,
                targetX, targetY, targetZ, speedMultiplier, serverTick,
                NpcAiProfile.resolve(entity).routeRepathTicks());
    }

    private void rebuildActiveTickSets() {
        staticPhysicsInstances.clear();
        scheduledInstances.clear();
        behaviorInstances.clear();
        relationInstances.clear();
        for (NpcInstance placement : instances.values()) {
            if (!placement.enabled || placement.dead) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled) continue;
            boolean scheduled = placement.scheduleEnabled && !placement.schedule.isEmpty();
            if (scheduled) {
                scheduledInstances.add(placement.uuid());
            } else {
                // Every non-scheduled NPC gets the lightweight behavior tick. STATIONARY and
                // NATIVE need it as well so displacement/combat return can path back home.
                behaviorInstances.add(placement.uuid());
            }
            if (definition.noAi && definition.affectedByGravity && !definition.canFly) {
                staticPhysicsInstances.add(placement.uuid());
            }
            boolean hostileFaction = false;
            for (NpcFactionRelation relation : definition.factionRelations) {
                if (relation != null && NpcAttitude.parse(relation.attitude) == NpcAttitude.HOSTILE) {
                    hostileFaction = true;
                    break;
                }
            }
            boolean combatRelevant = definition.threatEnabled
                    || definition.whenAttacked() != NpcSelfDefenseReaction.IGNORE
                    || definition.whenFriendlyAttacked() != NpcFriendlyDefenseReaction.IGNORE
                    || (definition.whenHostileSeen() != NpcHostileSightReaction.IGNORE
                        && (definition.attitudeTowardPlayers() == NpcAttitude.HOSTILE || hostileFaction));
            if (combatRelevant) relationInstances.add(placement.uuid());
        }
    }

    public synchronized void refreshAll() {
        // A refresh synchronizes definition/runtime state only. Runtime position belongs to AI,
        // combat and navigation; hard positioning is reserved for spawn/respawn/admin moves.
        for (NpcInstance value : List.copyOf(instances.values())) reconcile(value, false);
    }

    /**
     * Forces a full runtime refresh and immediately resends all NPC labels/quest markers.
     * Use this after admin-side changes that can affect per-player NPC presentation.
     */
    public synchronized void syncAll() {
        refreshAll();
        syncAllLabels(true);
    }

    private void refreshDefinition(String definitionId) {
        for (NpcInstance value : instances.values()) {
            if (definitionId.equals(value.definitionId)) reconcile(value, false);
        }
    }

    private void respawnDefinition(String definitionId) {
        for (NpcInstance value : List.copyOf(instances.values())) {
            if (!definitionId.equals(value.definitionId)) continue;
            removeRuntime(value);
            reconcile(value, true);
        }
    }

    private static boolean requiresRuntimeRespawn(NpcDefinition left, NpcDefinition right) {
        // Attribute, loadout, behavior and presentation edits are live-applicable through apply(...).
        // Recreating the entity for those edits would teleport an active NPC back to its placement.
        // Only a physical shell/entity-type change requires a respawn.
        return !left.runtimeEntityType().equals(right.runtimeEntityType());
    }

    private void reconcile(NpcInstance instance, boolean forceMove) {
        if (server == null || instance == null) return;
        NpcDefinition definition = definitions.get(instance.definitionId);
        if (definition == null || !definition.enabled || !instance.enabled) {
            removeRuntime(instance);
            return;
        }
        if (instance.dead) {
            Entity dying = findRuntime(instance);
            if (dying != null && !dying.isRemoved()) return;
            clearRuntimeBinding(instance);
            if (!instance.respawnEnabled || System.currentTimeMillis() < instance.respawnAtEpochMillis) return;
            instance.dead = false;
            instance.respawnAtEpochMillis = 0L;
            instance.dimension = instance.respawnDimension;
            instance.x = instance.respawnX; instance.y = instance.respawnY; instance.z = instance.respawnZ;
            instance.yaw = instance.respawnYaw; instance.pitch = instance.respawnPitch;
            saveAll();
        }
        ServerLevel level = level(instance.dimension);
        if (level == null) {
            removeRuntime(instance);
            return;
        }
        BlockPos position = BlockPos.containing(instance.x, instance.y, instance.z);
        Entity current = findRuntime(instance);
        if (current != null && !current.getTags().contains(runtimeTag(instance))) {
            clearRuntimeBinding(instance);
            current = null;
        }
        if (!level.isLoaded(position)) {
            // An explicit edit must not leave the NPC at its previous placement. Normal native AI may
            // remain active until the saved home chunk is available again for distance reconciliation.
            if (forceMove && current != null) removeRuntime(instance);
            return;
        }
        if (current == null) {
            current = findTaggedRuntime(level, instance);
            if (current != null) {
                UUID oldRuntime = instance.runtimeUuid();
                if (oldRuntime != null) instanceByRuntimeEntity.remove(oldRuntime);
                instance.runtimeEntityId = current.getUUID().toString();
                instanceByRuntimeEntity.put(current.getUUID(), instance.uuid());
                saveAll();
            }
        }
        if (current != null && current.level() != level) {
            current.discard();
            clearRuntimeBinding(instance);
            current = null;
        }
        if (current != null && !definition.runtimeEntityType().equals(BuiltInRegistries.ENTITY_TYPE.getKey(current.getType()).toString())) {
            current.discard();
            clearRuntimeBinding(instance);
            current = null;
        }
        if (current == null || current.isRemoved()) {
            spawn(level, definition, instance);
            return;
        }
        instanceByRuntimeEntity.put(current.getUUID(), instance.uuid());
        settleStaticGravityPlacement(current, definition, instance);
        apply(current, definition, instance, forceMove, false);
    }

    private void settleStaticGravityPlacement(Entity entity, NpcDefinition definition, NpcInstance instance) {
        if (!definition.noAi || !definition.affectedByGravity || definition.canFly
                || instance.scheduleEnabled || entity == null || !entity.onGround()) return;
        if (entity instanceof LivingEntity living && NpcLocomotionProfile.resolve(living).nativeFlying()) return;
        // While combat/pathfinding temporarily enables a frozen Mob's AI, reconcile must not
        // reinterpret its current combat position as a new placement/home anchor.
        if (entity instanceof Mob mob && !mob.isNoAi()) return;
        double dx = entity.getX() - instance.x;
        double dz = entity.getZ() - instance.z;
        if (dx * dx + dz * dz > 0.0625D || Math.abs(entity.getY() - instance.y) <= 0.01D) return;
        instance.y = entity.getY();
        saveAll();
    }

    private Entity spawn(ServerLevel level, NpcDefinition definition, NpcInstance instance) {
        try {
            CompoundTag tag = new CompoundTag();
            String runtimeEntityType = definition.runtimeEntityType();
            tag.putString("id", runtimeEntityType);
            Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
            if (!(entity instanceof LivingEntity) || entity.getType() == EntityType.PLAYER) return null;
            apply(entity, definition, instance, true, true);
            UUID oldRuntime = instance.runtimeUuid();
            if (oldRuntime != null) instanceByRuntimeEntity.remove(oldRuntime);
            instance.runtimeEntityId = entity.getUUID().toString();
            instanceByRuntimeEntity.put(entity.getUUID(), instance.uuid());
            // Bind before addFreshEntity: EntityJoinLevelEvent can now distinguish a freshly-created
            // managed dynamic NPC from an orphaned dynamic entity restored from chunk data.
            if (!level.addFreshEntity(entity)) {
                clearRuntimeBinding(instance);
                return null;
            }
            if (!instance.dynamic) saveAll();
            return entity;
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error(
                    "Could not spawn SSU NPC '{}' using entity type '{}'.",
                    definition.id, definition.runtimeEntityType(), exception);
            return null;
        }
    }

    private void apply(Entity entity, NpcDefinition definition, NpcInstance instance,
            boolean forceMove, boolean freshSpawn) {
        // Never use periodic reconcile as a movement/leash system. A hard snap is only legal for
        // explicit placement ownership changes (spawn, respawn, teleport/bring/admin edit). Normal
        // combat/ambient movement returns through NpcNavigationController instead.
        if (forceMove) {
            entity.moveTo(instance.x, instance.y, instance.z, instance.yaw, instance.pitch);
        }
        // SSU renders role, name and faction itself. Do not keep a vanilla CustomName on the
        // runtime shell: Minecraft may reveal hidden custom names while the entity is targeted,
        // creating a second large nameplate on top of SSU's identity label.
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);
        entity.setInvulnerable(definition.invulnerable);
        entity.setSilent(definition.silent);
        entity.setGlowingTag(definition.glowing);
        boolean nativeFlying = entity instanceof LivingEntity livingShell
                && NpcLocomotionProfile.resolve(livingShell).nativeFlying();
        entity.setNoGravity(definition.canFly || nativeFlying || !definition.affectedByGravity);
        entity.addTag("ssu_npc");
        entity.addTag(runtimeTag(instance));
        if (instance.dynamic) entity.addTag("ssu_npc_dynamic");
        if (entity instanceof LivingEntity living) {
            applyAttribute(living, Attributes.MAX_HEALTH, definition.maxHealth);
            applyMovementSpeedAttribute(living, definition);
            // Schema 18+: ordinary weapon damage and defense come from the equipped ItemStacks.
            // Keep shell base combat attributes neutral so the equipment is the canonical source.
            applyAttribute(living, Attributes.ATTACK_DAMAGE, 1.0D);
            applyAttribute(living, Attributes.ARMOR, 0.0D);
            applyAttribute(living, Attributes.ARMOR_TOUGHNESS, 0.0D);
            applyAttribute(living, Attributes.FOLLOW_RANGE, definition.followRange);
            applyAttribute(living, Attributes.KNOCKBACK_RESISTANCE, definition.knockbackResistance);
            applyAttribute(living, Attributes.SCALE, definition.scale);
            applyEquipment(living, definition);
            applyEquipmentDefenseMultiplier(living, definition);
            NpcCombatEquipment.repairEquipped(living);
            if (freshSpawn) {
                living.setHealth(living.getMaxHealth());
            } else if (living.getHealth() > living.getMaxHealth()) {
                living.setHealth(living.getMaxHealth());
            }
        }
        if (entity instanceof Mob mob) {
            if (!instance.dynamic) mob.setPersistenceRequired();
            boolean scheduleControlsMovement = instance.scheduleEnabled && !instance.schedule.isEmpty();
            NpcBehaviorMode behaviorMode = definition.behaviorMode();
            boolean suppressNativeAi = !scheduleControlsMovement
                    && (behaviorMode == NpcBehaviorMode.STATIONARY || behaviorMode == NpcBehaviorMode.LOOK_AT_PLAYERS);
            // A periodic reconcile may live-apply settings while combat/return navigation owns the
            // shell. Do not freeze a STATIONARY/LOOK_AT NPC in the middle of that route; the
            // combat/return completion path restores the configured no-AI state afterwards.
            UUID instanceId = instance.uuid();
            boolean movementOwnsAi = combatBusy(instanceId)
                    || returningHomeInstances.contains(instanceId)
                    || returningScheduleInstances.contains(instanceId);
            mob.setNoAi(suppressNativeAi && !movementOwnsAi);
        }
    }

    private static void applyMovementSpeedAttribute(LivingEntity entity, NpcDefinition definition) {
        // Route/chase speed is expressed through walkingSpeed/runningSpeed multipliers. Keep native
        // shell movement attributes untouched, except the custom Player shell whose stable base is
        // owned by SSU. This preserves species-specific movement such as slimes, animals and flyers.
        if (entity.getType() == ModNpcEntities.PLAYER_NPC.get()) {
            applyAttribute(entity, Attributes.MOVEMENT_SPEED, ModNpcEntities.PLAYER_NPC_BASE_MOVEMENT_SPEED);
        }
    }

    private static void applyEquipmentDefenseMultiplier(LivingEntity entity, NpcDefinition definition) {
        double multiplier = Math.max(0.0D, definition.armorMultiplier);
        AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            double equipmentValue = Math.max(0.0D, entity.getAttributeValue(Attributes.ARMOR));
            armor.setBaseValue(equipmentValue * (multiplier - 1.0D));
        }
        AttributeInstance toughness = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null) {
            double equipmentValue = Math.max(0.0D, entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            toughness.setBaseValue(equipmentValue * (multiplier - 1.0D));
        }
    }

    private static void applyAttribute(LivingEntity entity, Holder<Attribute> attribute, double configuredValue) {
        if (configuredValue < 0.0D) return;
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && Double.compare(instance.getBaseValue(), configuredValue) != 0) {
            instance.setBaseValue(configuredValue);
        }
    }

    private static void applyEquipment(LivingEntity entity, NpcDefinition definition) {
        setEquipment(entity, EquipmentSlot.MAINHAND, definition.mainHandStack, definition.mainHandItem);
        setEquipment(entity, EquipmentSlot.OFFHAND, definition.offHandStack, definition.offHandItem);
        setEquipment(entity, EquipmentSlot.HEAD, definition.headStack, definition.headItem);
        setEquipment(entity, EquipmentSlot.CHEST, definition.chestStack, definition.chestItem);
        setEquipment(entity, EquipmentSlot.LEGS, definition.legsStack, definition.legsItem);
        setEquipment(entity, EquipmentSlot.FEET, definition.feetStack, definition.feetItem);
    }

    private static void setEquipment(LivingEntity entity, EquipmentSlot slot,
            com.google.gson.JsonElement encoded, String legacyItemId) {
        // Empty means an empty equipment slot. NPC equipment is entirely controlled by SSU.
        ItemStack configured = NpcItemCodec.decode(entity.level().registryAccess(), encoded, legacyItemId, 1);
        if (!configured.isEmpty()) configured = configured.copyWithCount(1);
        if (!configured.isEmpty() && configured.isDamageableItem()) configured.setDamageValue(0);
        ItemStack current = entity.getItemBySlot(slot);
        if (!ItemStack.matches(current, configured)) entity.setItemSlot(slot, configured);
        // Configured NPC equipment is gameplay-active but must never become death loot.
        if (entity instanceof Mob mob) mob.setDropChance(slot, 0.0F);
    }

    /** Reapplies the authoritative configured loadout after a damage sequence. This also restores an item that would have broken. */
    synchronized void restoreManagedEquipment(LivingEntity entity) {
        if (entity == null) return;
        NpcInstance instance = instanceForEntity(entity.getUUID());
        NpcDefinition definition = definitionFor(instance);
        if (definition == null) return;
        applyAttribute(entity, Attributes.ATTACK_DAMAGE, 1.0D);
        applyAttribute(entity, Attributes.ARMOR, 0.0D);
        applyAttribute(entity, Attributes.ARMOR_TOUGHNESS, 0.0D);
        applyEquipment(entity, definition);
        applyEquipmentDefenseMultiplier(entity, definition);
        NpcCombatEquipment.repairEquipped(entity);
    }

    /** Repairs managed equipment continuously so NPC gear can never wear out or break. */
    private void tickEquipmentIntegrity() {
        for (NpcInstance placement : List.copyOf(instances.values())) {
            if (placement == null || !placement.enabled || placement.dead) continue;
            Entity runtime = findRuntime(placement);
            if (runtime instanceof LivingEntity living && living.isAlive() && !living.isRemoved()) {
                NpcCombatEquipment.repairEquipped(living);
            }
        }
    }

    /** Returns custom NPC drops. Empty means either no custom table or no successful rolls. */
    public synchronized List<ItemStack> customLootFor(LivingEntity entity) {
        if (entity == null) return List.of();
        NpcInstance placement = instanceForEntity(entity.getUUID());
        NpcDefinition definition = definitionFor(placement);
        if (definition == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (int roll = 0; roll < definition.lootRolls; roll++) {
            for (NpcLootEntry entry : definition.loot) {
                if (entry == null || !entry.configured()) continue;
                if (entity.getRandom().nextInt(10_000) >= entry.chanceHundredthPercent) continue;
                ItemStack stack = NpcItemCodec.decode(entity.level().registryAccess(),
                        entry.stack, entry.itemId, entry.count);
                if (stack.isEmpty()) continue;
                stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), stack.getCount())));
                result.add(stack.copy());
            }
        }
        return List.copyOf(result);
    }


    /** Removes configured display equipment from native death drops as a second safety layer. */
    public synchronized void removeVisualEquipmentDrops(LivingEntity entity, Collection<ItemEntity> drops) {
        if (entity == null || drops == null || drops.isEmpty()) return;
        NpcDefinition definition = definitionFor(instanceForEntity(entity.getUUID()));
        if (definition == null) return;
        List<ItemStack> visualEquipment = List.of(
                NpcItemCodec.decode(entity.level().registryAccess(), definition.mainHandStack, definition.mainHandItem, 1),
                NpcItemCodec.decode(entity.level().registryAccess(), definition.offHandStack, definition.offHandItem, 1),
                NpcItemCodec.decode(entity.level().registryAccess(), definition.headStack, definition.headItem, 1),
                NpcItemCodec.decode(entity.level().registryAccess(), definition.chestStack, definition.chestItem, 1),
                NpcItemCodec.decode(entity.level().registryAccess(), definition.legsStack, definition.legsItem, 1),
                NpcItemCodec.decode(entity.level().registryAccess(), definition.feetStack, definition.feetItem, 1));
        for (ItemStack configured : visualEquipment) {
            if (configured.isEmpty()) continue;
            configured = configured.copyWithCount(1);
            if (configured.isDamageableItem()) configured.setDamageValue(0);
            for (var iterator = drops.iterator(); iterator.hasNext();) {
                ItemEntity drop = iterator.next();
                ItemStack dropped = drop.getItem();
                if (!dropped.isEmpty() && ItemStack.matches(dropped.copyWithCount(1), configured)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    public synchronized boolean usesCustomLoot(LivingEntity entity) {
        NpcDefinition definition = definitionFor(instanceForEntity(entity == null ? null : entity.getUUID()));
        return definition != null;
    }

    private Entity findRuntime(NpcInstance placement) {
        if (server == null || placement == null) return null;
        UUID runtimeId = placement.runtimeUuid();
        if (runtimeId == null) return null;
        ServerLevel expectedLevel = level(placement.dimension);
        if (expectedLevel != null) {
            Entity entity = expectedLevel.getEntity(runtimeId);
            if (entity != null) return entity;
        }
        // Compatibility fallback for an old placement whose saved dimension no longer matches
        // the still-loaded entity. Reconciliation will move/rebind it afterwards.
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate == expectedLevel) continue;
            Entity entity = candidate.getEntity(runtimeId);
            if (entity != null) return entity;
        }
        return null;
    }

    private static Entity findTaggedRuntime(ServerLevel level, NpcInstance instance) {
        String tag = runtimeTag(instance);
        AABB bounds = new AABB(instance.x - 32.0D, instance.y - 32.0D, instance.z - 32.0D,
                instance.x + 32.0D, instance.y + 32.0D, instance.z + 32.0D);
        List<Entity> matches = level.getEntitiesOfClass(Entity.class, bounds,
                entity -> entity.getTags().contains(tag) && !entity.isRemoved());
        if (matches.isEmpty()) return null;
        Entity keep = matches.getFirst();
        for (int index = 1; index < matches.size(); index++) matches.get(index).discard();
        return keep;
    }

    private static String runtimeTag(NpcInstance instance) {
        return "ssu_npc_instance_" + instance.id.replace('-', '_');
    }

    private ServerLevel level(String rawDimension) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(rawDimension));
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removeRuntime(NpcInstance instance) {
        UUID instanceId = instance.uuid();
        activeScheduleEntry.remove(instanceId);
        navigation.forget(instanceId);
        nextScheduleActivityTick.remove(instanceId);
        nextBehaviorDecisionTick.remove(instanceId);
        wanderTargets.remove(instanceId);
        activePatrolPoint.remove(instanceId);
        patrolDirection.remove(instanceId);
        patrolPauseUntilTick.remove(instanceId);
        nextCombatAttackTick.remove(instanceId);
        abilityController.forget(instanceId);
        attackPatternController.forget(instanceId);
        threatController.forget(instanceId);
        removeBossBar(instanceId);
        activeBossPhase.remove(instanceId);
        lastBossCombatTick.remove(instanceId);
        reactiveCombat.remove(instanceId);
        recentNpcAttacks.remove(instanceId);
        authorizedCombatTargets.remove(instanceId);
        staticPhysicsInstances.remove(instanceId);
        scheduledInstances.remove(instanceId);
        behaviorInstances.remove(instanceId);
        relationInstances.remove(instanceId);
        movementInterruptedByCombat.remove(instanceId);
        returningHomeInstances.remove(instanceId);
        returningScheduleInstances.remove(instanceId);
        UUID runtime = instance.runtimeUuid();
        NpcAnimationBridge.forget(runtime);
        Entity entity = findRuntime(instance);
        if (entity != null) entity.discard();
        clearRuntimeBinding(instance);
    }

    private void clearRuntimeBinding(NpcInstance instance) {
        UUID runtime = instance.runtimeUuid();
        if (runtime != null) instanceByRuntimeEntity.remove(runtime);
        instance.runtimeEntityId = "";
    }

    public synchronized void shutdownRuntime(boolean persist) {
        for (NpcInstance value : instances.values()) removeRuntime(value);
        if (persist) saveAll();
        instanceByRuntimeEntity.clear();
    }

    public synchronized void clear() {
        NpcAnimationBridge.clear();
        definitions.clear();
        instances.clear();
        instanceByRuntimeEntity.clear();
        definitionStore.reset();
        instanceStore.reset();
        server = null;
        definitionFolder = null;
        instanceFolder = null;
        nextReconcileTick = 0L;
        nextRelationTick = 0L;
        activeScheduleEntry.clear();
        navigation.clear();
        abilityController.clear();
        attackPatternController.clear();
        threatController.clear();
        clearBossBars();
        activeBossPhase.clear();
        activeBossEncounters.clear();
        bossEncounterAdds.clear();
        lastBossCombatTick.clear();
        nextScheduleActivityTick.clear();
        nextCombatAttackTick.clear();
        reactiveCombat.clear();
        recentNpcAttacks.clear();
        authorizedCombatTargets.clear();
        staticPhysicsInstances.clear();
        scheduledInstances.clear();
        behaviorInstances.clear();
        relationInstances.clear();
        movementInterruptedByCombat.clear();
        returningHomeInstances.clear();
        returningScheduleInstances.clear();
        nextBehaviorDecisionTick.clear();
        wanderTargets.clear();
        activePatrolPoint.clear();
        patrolDirection.clear();
        patrolPauseUntilTick.clear();
        lastLabelSnapshots.clear();
        lastTextureSnapshots.clear();
        textureAssets.clear();
        labelsEnabledLastTick = false;
        supportedModelCache = List.of();
    }

    /** Sends the current dimension's lightweight three-line NPC labels to one player. */
    public synchronized void syncLabels(ServerPlayer player) {
        syncLabels(player, true);
    }

    public synchronized void forgetLabelViewer(UUID playerId) {
        if (playerId != null) {
            lastLabelSnapshots.remove(playerId);
            lastTextureSnapshots.remove(playerId);
        }
    }

    private void syncAllLabels(boolean force) {
        if (server == null) return;
        Set<UUID> online = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            syncLabels(player, force);
        }
        lastLabelSnapshots.keySet().removeIf(uuid -> !online.contains(uuid));
        lastTextureSnapshots.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    private void syncLabels(ServerPlayer player, boolean force) {
        if (player == null) return;
        List<NpcLabelSyncPayload.Entry> entries = buildLabelSnapshot(player);
        List<NpcLabelSyncPayload.Entry> previous = lastLabelSnapshots.get(player.getUUID());
        if (force || !entries.equals(previous)) {
            lastLabelSnapshots.put(player.getUUID(), entries);
            PacketDistributor.sendToPlayer(player, new NpcLabelSyncPayload(entries));
        }
        syncTextures(player, entries, force);
    }

    private void syncTextures(ServerPlayer player, List<NpcLabelSyncPayload.Entry> labels, boolean force) {
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        ArrayList<NpcTextureSyncPayload.Entry> changed = new ArrayList<>();
        LinkedHashSet<String> visibleDefinitions = new LinkedHashSet<>();
        for (NpcLabelSyncPayload.Entry label : labels) visibleDefinitions.add(label.definitionId());
        Map<String, String> previous = lastTextureSnapshots.getOrDefault(player.getUUID(), Map.of());
        for (String definitionId : visibleDefinitions) {
            NpcDefinition definition = definitions.get(definitionId);
            if (definition == null) continue;
            String model = "slim".equalsIgnoreCase(definition.textureModel) ? "slim" : "wide";
            if (definition.hasCustomTexture()) {
                NpcTextureAssetService.Asset asset = textureAssets.asset(definition);
                if (asset == null) continue;
                String signature = asset.hash() + "|" + model;
                hashes.put(definitionId, signature);
                if (force || !signature.equals(previous.get(definitionId))) {
                    changed.add(new NpcTextureSyncPayload.Entry(definitionId, asset.hash(), model, asset.bytes()));
                }
            } else if (definition.usesPlayerSkin()) {
                // The native Player renderer needs Wide/Slim even when no custom PNG is configured.
                String signature = "default|" + model;
                hashes.put(definitionId, signature);
                if (force || !signature.equals(previous.get(definitionId))) {
                    changed.add(new NpcTextureSyncPayload.Entry(definitionId, "", model, new byte[0]));
                }
            }
        }
        for (String previousDefinition : previous.keySet()) {
            if (!hashes.containsKey(previousDefinition)) {
                changed.add(new NpcTextureSyncPayload.Entry(previousDefinition, "", "remove", new byte[0]));
            }
        }
        lastTextureSnapshots.put(player.getUUID(), Map.copyOf(hashes));
        // Keep each binary texture asset in its own packet. A single valid PNG can be hundreds of KiB,
        // so batching many textures into one custom payload would create avoidably large packets.
        for (NpcTextureSyncPayload.Entry entry : changed) {
            PacketDistributor.sendToPlayer(player, new NpcTextureSyncPayload(List.of(entry)));
        }
    }

    private List<NpcLabelSyncPayload.Entry> buildLabelSnapshot(ServerPlayer player) {
        if (!Config.ENABLE_NPCS.get()) return List.of();
        String dimension = player.level().dimension().location().toString();
        ArrayList<NpcLabelSyncPayload.Entry> entries = new ArrayList<>();
        for (NpcInstance placement : instances.values()) {
            if (!placement.enabled || placement.dead || !dimension.equals(placement.dimension)) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled) continue;
            Entity entity = findRuntime(placement);
            if (entity == null || entity.isRemoved()) continue;
            String questMarker = Config.ENABLE_QUESTS.get() && SimpleServerUtilities.CORE.modules().isActive("quests")
                    ? QuestNpcBridge.markerFor(SimpleServerUtilities.QUESTS, player, placement, definition,
                            SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS)
                    : "";
            entries.add(new NpcLabelSyncPayload.Entry(entity.getId(), entity.getUUID().toString(), definition.id,
                    definition.customNameVisible, definition.displayName, definition.roleId, definition.roleColor, definition.factionLabel(),
                    definition.playerAttitude, questMarker));
        }
        entries.sort(Comparator.comparingInt(NpcLabelSyncPayload.Entry::entityId));
        return List.copyOf(entries);
    }

    private static boolean sameDefinition(NpcDefinition left, NpcDefinition right) {
        if (left == null || right == null) return false;
        return left.id.equals(right.id)
                && left.displayName.equals(right.displayName)
                && left.entityType.equals(right.entityType)
                && left.visualMode.equals(right.visualMode)
                && left.textureSource.equals(right.textureSource)
                && left.textureValue.equals(right.textureValue)
                && left.textureModel.equals(right.textureModel)
                && left.customModelResource.equals(right.customModelResource)
                && left.customTextureResource.equals(right.customTextureResource)
                && left.customAnimationResource.equals(right.customAnimationResource)
                && left.idleAnimation.equals(right.idleAnimation)
                && left.walkAnimation.equals(right.walkAnimation)
                && left.attackAnimation.equals(right.attackAnimation)
                && left.castAnimation.equals(right.castAnimation)
                && left.hurtAnimation.equals(right.hurtAnimation)
                && left.deathAnimation.equals(right.deathAnimation)
                && left.interactionText.equals(right.interactionText)
                && left.dialogueId.equals(right.dialogueId)
                && left.roleId.equals(right.roleId)
                && left.roleColor == right.roleColor
                && left.shopId.equals(right.shopId)
                && left.interactionMode.equals(right.interactionMode)
                && sameFunctions(left.functions, right.functions)
                && left.enabled == right.enabled
                && left.customNameVisible == right.customNameVisible
                && left.noAi == right.noAi
                && left.invulnerable == right.invulnerable
                && left.silent == right.silent
                && left.glowing == right.glowing
                && left.affectedByGravity == right.affectedByGravity
                && left.canSwim == right.canSwim
                && left.canFly == right.canFly
                && left.behaviorMode.equals(right.behaviorMode)
                && Double.compare(left.lookAtRange, right.lookAtRange) == 0
                && left.lookAtBody == right.lookAtBody
                && Double.compare(left.wanderRadius, right.wanderRadius) == 0
                && left.wanderIntervalSeconds == right.wanderIntervalSeconds
                && Double.compare(left.behaviorSpeed, right.behaviorSpeed) == 0
                && left.factionId.equals(right.factionId)
                && left.factionDisplayName.equals(right.factionDisplayName)
                && left.minimumReputation == right.minimumReputation
                && left.reputationDeniedText.equals(right.reputationDeniedText)
                && left.reputationLossOnAttack == right.reputationLossOnAttack
                && left.playerAttitude.equals(right.playerAttitude)
                && sameRelations(left.factionRelations, right.factionRelations)
                && left.whenAttacked.equals(right.whenAttacked)
                && left.whenFriendlyAttacked.equals(right.whenFriendlyAttacked)
                && left.whenHostileSeen.equals(right.whenHostileSeen)
                && left.combatProfile.equals(right.combatProfile)
                && Double.compare(left.assistRange, right.assistRange) == 0
                && Double.compare(left.fleeDistance, right.fleeDistance) == 0
                && left.attackCooldownTicks == right.attackCooldownTicks
                && left.meleeAttacksEnabled == right.meleeAttacksEnabled
                && left.rangedAttacksEnabled == right.rangedAttacksEnabled
                && left.magicAttacksEnabled == right.magicAttacksEnabled
                && left.threatEnabled == right.threatEnabled
                && Double.compare(left.threatRange, right.threatRange) == 0
                && Double.compare(left.threatDamageMultiplier, right.threatDamageMultiplier) == 0
                && Double.compare(left.threatHealingMultiplier, right.threatHealingMultiplier) == 0
                && Double.compare(left.threatDecayPerSecond, right.threatDecayPerSecond) == 0
                && Double.compare(left.threatSwitchRatio, right.threatSwitchRatio) == 0
                && left.attackPatternEnabled == right.attackPatternEnabled
                && sameAttackPattern(left.attackPattern, right.attackPattern)
                && sameAbilityAssignments(left.abilityAssignments, right.abilityAssignments)
                && left.bossEnabled == right.bossEnabled
                && left.bossBarVisible == right.bossBarVisible
                && Double.compare(left.bossBarRange, right.bossBarRange) == 0
                && Double.compare(left.bossResetDistance, right.bossResetDistance) == 0
                && left.bossResetSeconds == right.bossResetSeconds
                && left.bossHealOnReset == right.bossHealOnReset
                && sameBossPhases(left.bossPhases, right.bossPhases)
                && Double.compare(left.maxHealth, right.maxHealth) == 0
                && Double.compare(left.magicResistance, right.magicResistance) == 0
                && Double.compare(left.armorMultiplier, right.armorMultiplier) == 0
                && Double.compare(left.meleeDamageMultiplier, right.meleeDamageMultiplier) == 0
                && Double.compare(left.rangedDamageMultiplier, right.rangedDamageMultiplier) == 0
                && Double.compare(left.magicDamageMultiplier, right.magicDamageMultiplier) == 0
                && Double.compare(left.walkingSpeed, right.walkingSpeed) == 0
                && Double.compare(left.runningSpeed, right.runningSpeed) == 0
                && Double.compare(left.followRange, right.followRange) == 0
                && Double.compare(left.knockbackResistance, right.knockbackResistance) == 0
                && Double.compare(left.scale, right.scale) == 0
                && Double.compare(left.homeRadius, right.homeRadius) == 0
                && Objects.equals(left.mainHandStack, right.mainHandStack)
                && Objects.equals(left.offHandStack, right.offHandStack)
                && Objects.equals(left.headStack, right.headStack)
                && Objects.equals(left.chestStack, right.chestStack)
                && Objects.equals(left.legsStack, right.legsStack)
                && Objects.equals(left.feetStack, right.feetStack)
                && left.mainHandItem.equals(right.mainHandItem)
                && left.offHandItem.equals(right.offHandItem)
                && left.headItem.equals(right.headItem)
                && left.chestItem.equals(right.chestItem)
                && left.legsItem.equals(right.legsItem)
                && left.feetItem.equals(right.feetItem)
                && left.lootRolls == right.lootRolls
                && sameLoot(left.loot, right.loot);
    }


    private static boolean sameAttackPattern(List<NpcAttackPatternStep> left, List<NpcAttackPatternStep> right) {
        if (left == right) return true;
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            NpcAttackPatternStep a = left.get(i), b = right.get(i);
            if (a == b) continue;
            if (a == null || b == null || a.enabled != b.enabled || !a.action.equals(b.action)
                    || !a.abilityId.equals(b.abilityId) || !a.phaseId.equals(b.phaseId)
                    || Double.compare(a.minRange, b.minRange) != 0 || Double.compare(a.maxRange, b.maxRange) != 0
                    || Double.compare(a.minHealthPercent, b.minHealthPercent) != 0
                    || Double.compare(a.maxHealthPercent, b.maxHealthPercent) != 0) return false;
        }
        return true;
    }

    private static boolean sameAbilityAssignments(List<NpcAbilityAssignment> left, List<NpcAbilityAssignment> right) {
        if (left == right) return true;
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            NpcAbilityAssignment a = left.get(i), b = right.get(i);
            if (a == b) continue;
            if (a == null || b == null || !a.abilityId.equals(b.abilityId) || !a.phaseId.equals(b.phaseId)) return false;
        }
        return true;
    }

    private static boolean sameBossPhases(List<NpcBossPhase> left, List<NpcBossPhase> right) {
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            NpcBossPhase a=left.get(i), b=right.get(i);
            if (a==null || b==null) { if(a!=b)return false; continue; }
            if (!a.id.equals(b.id) || !a.displayName.equals(b.displayName)
                    || Double.compare(a.healthThresholdPercent,b.healthThresholdPercent)!=0
                    || Double.compare(a.movementSpeedMultiplier,b.movementSpeedMultiplier)!=0
                    || Double.compare(a.cooldownMultiplier,b.cooldownMultiplier)!=0
                    || Double.compare(a.abilityDamageMultiplier,b.abilityDamageMultiplier)!=0
                    || a.tauntImmune != b.tauntImmune
                    || !sameBossPhaseActions(a.actions, b.actions)) return false;
        }
        return true;
    }

    private static boolean sameBossPhaseActions(List<NpcBossPhaseAction> left, List<NpcBossPhaseAction> right) {
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            NpcBossPhaseAction a = left.get(i), b = right.get(i);
            if (a == null || b == null) { if (a != b) return false; continue; }
            if (!a.type.equals(b.type) || !a.value.equals(b.value)
                    || Double.compare(a.amount, b.amount) != 0 || Double.compare(a.radius, b.radius) != 0) return false;
        }
        return true;
    }

    private static boolean sameFunctions(List<NpcFunction> left, List<NpcFunction> right) {
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            NpcFunction a = left.get(i), b = right.get(i);
            if (a == null || b == null) { if (a != b) return false; continue; }
            if (!a.id.equals(b.id) || !a.label.equals(b.label) || !a.service.equals(b.service)
                    || !a.target.equals(b.target) || a.enabled != b.enabled) return false;
        }
        return true;
    }

    private static boolean sameRelations(List<NpcFactionRelation> left, List<NpcFactionRelation> right) {
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            NpcFactionRelation a = left.get(i), b = right.get(i);
            if (a == null || b == null || !a.factionId.equals(b.factionId) || !a.attitude.equals(b.attitude)) return false;
        }
        return true;
    }

    private static boolean sameLoot(List<NpcLootEntry> left, List<NpcLootEntry> right) {
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            NpcLootEntry a = left.get(i), b = right.get(i);
            if (!Objects.equals(a.stack, b.stack) || !a.itemId.equals(b.itemId) || a.count != b.count
                    || a.chanceHundredthPercent != b.chanceHundredthPercent) return false;
        }
        return true;
    }

    private enum CombatMode { FIGHT, FLEE }

    private record CombatIntent(UUID targetEntityId, CombatMode mode, long expiresTick) { }

    private record RecentNpcAttack(UUID attackerEntityId, long expiresTick, boolean calledAllies) { }

}
