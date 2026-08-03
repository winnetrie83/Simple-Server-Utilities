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
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
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
import net.minecraft.world.item.component.ItemAttributeModifiers;
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
    private final Map<UUID, Long> nextSchedulePathTick = new LinkedHashMap<>();
    private final Map<UUID, Long> nextScheduleActivityTick = new LinkedHashMap<>();
    private final Map<UUID, Long> nextCombatAttackTick = new LinkedHashMap<>();
    private final Set<UUID> staticPhysicsInstances = new LinkedHashSet<>();
    private final Set<UUID> scheduledInstances = new LinkedHashSet<>();
    private final Set<UUID> relationInstances = new LinkedHashSet<>();
    private final Map<UUID, List<NpcLabelSyncPayload.Entry>> lastLabelSnapshots = new LinkedHashMap<>();
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
        nextSchedulePathTick.clear();
        nextScheduleActivityTick.clear();
        nextCombatAttackTick.clear();
        staticPhysicsInstances.clear();
        scheduledInstances.clear();
        relationInstances.clear();
        lastLabelSnapshots.clear();
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
                value.normalize();
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
        for (NpcInstance value : instances.values()) value.normalize();
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

    public synchronized Collection<NpcInstance> instances() {
        List<NpcInstance> result = new ArrayList<>(instances.values());
        result.sort(Comparator.comparing(value -> value.definitionId + ":" + value.id));
        return List.copyOf(result);
    }

    public synchronized RuntimeStatistics runtimeStatistics() {
        return new RuntimeStatistics(
                definitions.size(), instances.size(), staticPhysicsInstances.size(),
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
        definitions.put(value.id, value);
        if (respawnRuntime) respawnDefinition(value.id);
        else refreshDefinition(value.id);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    public synchronized boolean create(NpcDefinition definition, NpcInstance instance) {
        if (definition == null || instance == null) return false;
        definition.normalize();
        instance.normalize();
        ServerLevel validationLevel = level(instance.dimension);
        if (!isSupportedLivingEntityType(validationLevel, definition.entityType)) return false;
        if (instances.containsKey(instance.uuid()) || instances.size() >= MAX_INSTANCES) return false;
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
                || instances.size() >= MAX_INSTANCES) return false;
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
        instance.respawnAtEpochMillis = instance.respawnEnabled
                ? System.currentTimeMillis() + (long) instance.respawnDelaySeconds * 1_000L : 0L;
        // Keep the runtime binding through LivingDropsEvent so the configured SSU loot table is available.
        saveAll();
    }

    public synchronized boolean saveInstance(NpcInstance value) {
        if (value == null) return false;
        value.normalize();
        if (!definitions.containsKey(value.definitionId)) return false;
        if (!instances.containsKey(value.uuid()) && instances.size() >= MAX_INSTANCES) return false;
        NpcInstance previous = instances.put(value.uuid(), value);
        if (previous != null && previous.runtimeUuid() != null && !previous.runtimeUuid().equals(value.runtimeUuid())) {
            instanceByRuntimeEntity.remove(previous.runtimeUuid());
        }
        activeScheduleEntry.remove(value.uuid());
        nextSchedulePathTick.remove(value.uuid());
        nextScheduleActivityTick.remove(value.uuid());
        nextCombatAttackTick.remove(value.uuid());
        reconcile(value, true);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    public synchronized NpcInstance duplicateLinked(NpcInstance source, String dimension,
                                                     double x, double y, double z, float yaw, float pitch) {
        if (source == null || !definitions.containsKey(source.definitionId) || instances.size() >= MAX_INSTANCES) return null;
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
                .filter(value -> id.equals(value.definitionId)).toList();
        if (!linked.isEmpty() && !deletePlacements) return false;
        for (NpcInstance value : linked) {
            removeRuntime(value);
            instances.remove(value.uuid());
            nextCombatAttackTick.remove(value.uuid());
        }
        definitions.remove(id);
        saveAll();
        syncAllLabels(true);
        return true;
    }

    public synchronized boolean isSupportedEntityType(String rawType) {
        try {
            Identifier id = Identifier.parse(rawType == null ? "" : rawType.trim());
            if (UNSAFE_NATIVE_TYPES.contains(id.toString())) return false;
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            return type.isPresent() && type.get() != EntityTypes.PLAYER;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isSupportedLivingEntityType(ServerLevel level, String rawType) {
        if (level == null || !isSupportedEntityType(rawType)) return false;
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", rawType.trim());
            Entity entity = EntityType.loadEntityRecursive(
                    tag, level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), EntityProcessor.NOP);
            return entity instanceof LivingEntity && entity.getType() != EntityTypes.PLAYER;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Sorted living entity model IDs for the searchable admin model picker. */
    public synchronized List<String> supportedLivingEntityTypes(ServerLevel level) {
        if (!supportedModelCache.isEmpty()) return supportedModelCache;
        if (level == null) return List.of("minecraft:villager");
        List<String> result = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            String value = id.toString();
            if (isSupportedLivingEntityType(level, value)) result.add(value);
        }
        result.sort(String::compareTo);
        if (result.isEmpty()) result.add("minecraft:villager");
        supportedModelCache = List.copyOf(result);
        return supportedModelCache;
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
        tickStaticPhysics();
        tickSchedules(activeServer, tick);
        if (tick >= nextRelationTick) {
            nextRelationTick = tick + 10L;
            tickRelations(tick);
        }
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
            living.setNoGravity(false);
            if (!living.onGround()) {
                Vec3 velocity = living.getDeltaMovement();
                double nextY = Math.max(-3.92D, velocity.y - 0.08D);
                living.setDeltaMovement(0.0D, nextY, 0.0D);
                living.move(MoverType.SELF, living.getDeltaMovement());
                living.setDeltaMovement(0.0D, nextY * 0.98D, 0.0D);
            } else {
                living.setDeltaMovement(Vec3.ZERO);
                if (Math.abs(placement.y - living.getY()) > 0.01D
                        || Math.abs(placement.x - living.getX()) > 0.01D
                        || Math.abs(placement.z - living.getZ()) > 0.01D) {
                    placement.x = living.getX(); placement.y = living.getY(); placement.z = living.getZ();
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
        Map<String, Map<Long, List<NpcInstance>>> nearbyIndex = buildRelationSpatialIndex();
        for (UUID sourceId : List.copyOf(relationInstances)) {
            NpcInstance sourcePlacement = instances.get(sourceId);
            if (sourcePlacement == null || !sourcePlacement.enabled || sourcePlacement.dead) continue;
            NpcDefinition sourceDefinition = definitions.get(sourcePlacement.definitionId);
            Entity sourceEntity = findRuntime(sourcePlacement);
            if (sourceDefinition == null || sourceDefinition.noAi || !(sourceEntity instanceof Mob mob)
                    || !sourceDefinition.enabled || mob.isRemoved() || !mob.isAlive()) continue;
            double followRange = sourceDefinition.followRange > 0.0D ? sourceDefinition.followRange : 16.0D;
            double maximumDistance = followRange * followRange;
            LivingEntity current = mob.getTarget();
            LivingEntity nearest = current != null && current.isAlive() && !current.isRemoved()
                    && isHostileTarget(sourceDefinition, current)
                    && mob.distanceToSqr(current) <= maximumDistance ? current : null;
            double nearestDistance = nearest == null ? maximumDistance : mob.distanceToSqr(nearest);
            if (sourceDefinition.attitudeTowardPlayers() == NpcAttitude.HOSTILE) {
                for (Player player : mob.level().players()) {
                    if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
                    double distance = mob.distanceToSqr(player);
                    if (distance < nearestDistance) { nearest = player; nearestDistance = distance; }
                }
            }

            for (NpcInstance targetPlacement : relationCandidates(
                    nearbyIndex, sourcePlacement.dimension, mob.getX(), mob.getZ(), followRange)) {
                if (targetPlacement == sourcePlacement || targetPlacement.dead || !targetPlacement.enabled) continue;
                Entity targetEntity = findRuntime(targetPlacement);
                if (!(targetEntity instanceof LivingEntity living) || living.level() != mob.level()
                        || !living.isAlive() || living.isRemoved()) continue;
                NpcDefinition targetDefinition = definitions.get(targetPlacement.definitionId);
                if (targetDefinition == null
                        || sourceDefinition.attitudeTowardFaction(targetDefinition.factionId) != NpcAttitude.HOSTILE) continue;
                double distance = mob.distanceToSqr(living);
                if (distance < nearestDistance) { nearest = living; nearestDistance = distance; }
            }
            mob.setTarget(nearest);
            if (nearest == null) {
                mob.getNavigation().stop();
                mob.setAggressive(false);
                nextCombatAttackTick.remove(sourcePlacement.uuid());
                continue;
            }

            // Some vanilla model shells have no combat goal of their own. SSU supplies the basic
            // chase/attack loop so faction hostility remains functional for every Mob model.
            mob.getNavigation().moveTo(nearest, 1.0D);
            if (!(mob.level() instanceof ServerLevel level) || !mob.isWithinMeleeAttackRange(nearest)) continue;
            long nextAttack = nextCombatAttackTick.getOrDefault(sourcePlacement.uuid(), 0L);
            if (serverTick < nextAttack) continue;
            mob.lookAt(nearest, 30.0F, 30.0F);
            mob.setAggressive(true);
            mob.doHurtTarget(level, nearest);
            nextCombatAttackTick.put(sourcePlacement.uuid(), serverTick + 20L);
        }
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
            int minute = GameCalendar.fromClockTime(level.getDefaultClockTime()).minuteOfDay();
            int index = activeScheduleIndex(placement.schedule, minute);
            if (index < 0) continue;
            NpcScheduleEntry entry = placement.schedule.get(index);
            UUID instanceId = placement.uuid();
            Integer previousIndex = activeScheduleEntry.put(instanceId, index);
            boolean changed = previousIndex == null || previousIndex.intValue() != index;
            if (changed && NpcScheduleEntry.MOVEMENT_TELEPORT.equals(entry.movement)) {
                living.snapTo(entry.x, entry.y, entry.z, entry.yaw, living.getXRot());
                living.setDeltaMovement(Vec3.ZERO);
            }
            double distance = living.distanceToSqr(entry.x, entry.y, entry.z);
            if (distance > 1.0D && !NpcScheduleEntry.MOVEMENT_TELEPORT.equals(entry.movement)) {
                moveScheduledNpc(living, definition, entry, serverTick, instanceId);
            } else {
                if (living instanceof Mob mob) mob.getNavigation().stop();
                living.setYRot(entry.yaw);
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

    private void moveScheduledNpc(LivingEntity entity, NpcDefinition definition, NpcScheduleEntry entry,
            long serverTick, UUID instanceId) {
        Vec3 difference = new Vec3(entry.x - entity.getX(), entry.y - entity.getY(), entry.z - entity.getZ());
        boolean waterTravel = definition.canSwim && entity.isInWater();
        if (definition.canFly || waterTravel) {
            double speed = Math.min(0.8D, Math.max(0.04D, 0.18D * entry.speed));
            Vec3 velocity = difference.normalize().scale(speed);
            entity.setDeltaMovement(velocity);
            entity.setNoGravity(true);
            return;
        }
        entity.setNoGravity(!definition.affectedByGravity);
        if (entity instanceof Mob mob) {
            long next = nextSchedulePathTick.getOrDefault(instanceId, 0L);
            if (serverTick >= next) {
                mob.getNavigation().moveTo(entry.x, entry.y, entry.z, entry.speed);
                nextSchedulePathTick.put(instanceId, serverTick + 20L);
            }
        } else {
            double step = Math.min(0.25D * entry.speed, difference.length());
            Vec3 delta = difference.normalize().scale(step);
            entity.snapTo(entity.getX() + delta.x, entity.getY() + delta.y, entity.getZ() + delta.z,
                    entity.getYRot(), entity.getXRot());
        }
    }

    private void runScheduleActivity(LivingEntity entity, NpcScheduleEntry entry, long serverTick, UUID instanceId) {
        long next = nextScheduleActivityTick.getOrDefault(instanceId, 0L);
        if (serverTick < next) return;
        if (NpcScheduleEntry.ACTIVITY_CHOP_TREE.equals(entry.activity)) {
            entity.swing(InteractionHand.MAIN_HAND);
            nextScheduleActivityTick.put(instanceId, serverTick + 16L);
        } else if (NpcScheduleEntry.ACTIVITY_LOOK_AROUND.equals(entry.activity)) {
            entity.setYRot(entity.getYRot() + 35.0F);
            nextScheduleActivityTick.put(instanceId, serverTick + 40L);
        } else {
            nextScheduleActivityTick.put(instanceId, serverTick + 40L);
        }
    }

    private void rebuildActiveTickSets() {
        staticPhysicsInstances.clear();
        scheduledInstances.clear();
        relationInstances.clear();
        for (NpcInstance placement : instances.values()) {
            if (!placement.enabled || placement.dead) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled) continue;
            if (placement.scheduleEnabled && !placement.schedule.isEmpty()) {
                scheduledInstances.add(placement.uuid());
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
            if (!definition.noAi && (definition.attitudeTowardPlayers() == NpcAttitude.HOSTILE || hostileFaction)) {
                relationInstances.add(placement.uuid());
            }
        }
    }

    public synchronized void refreshAll() {
        for (NpcInstance value : List.copyOf(instances.values())) reconcile(value, true);
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
        return !left.entityType.equals(right.entityType)
                || Double.compare(left.maxHealth, right.maxHealth) != 0
                || Double.compare(left.movementSpeed, right.movementSpeed) != 0
                || Double.compare(left.attackDamage, right.attackDamage) != 0
                || Double.compare(left.armor, right.armor) != 0
                || Double.compare(left.armorToughness, right.armorToughness) != 0
                || Double.compare(left.followRange, right.followRange) != 0
                || Double.compare(left.knockbackResistance, right.knockbackResistance) != 0
                || Double.compare(left.scale, right.scale) != 0
                || !Objects.equals(left.mainHandStack, right.mainHandStack)
                || !Objects.equals(left.offHandStack, right.offHandStack)
                || !Objects.equals(left.headStack, right.headStack)
                || !Objects.equals(left.chestStack, right.chestStack)
                || !Objects.equals(left.legsStack, right.legsStack)
                || !Objects.equals(left.feetStack, right.feetStack)
                || !left.mainHandItem.equals(right.mainHandItem)
                || !left.offHandItem.equals(right.offHandItem)
                || !left.headItem.equals(right.headItem)
                || !left.chestItem.equals(right.chestItem)
                || !left.legsItem.equals(right.legsItem)
                || !left.feetItem.equals(right.feetItem)
                || left.affectedByGravity != right.affectedByGravity
                || left.canSwim != right.canSwim
                || left.canFly != right.canFly;
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
        if (current != null && !current.entityTags().contains(runtimeTag(instance))) {
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
        if (current != null && !definition.entityType.equals(BuiltInRegistries.ENTITY_TYPE.getKey(current.getType()).toString())) {
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
        if (Math.abs(entity.getY() - instance.y) <= 0.01D) return;
        instance.x = entity.getX(); instance.y = entity.getY(); instance.z = entity.getZ();
        saveAll();
    }

    private Entity spawn(ServerLevel level, NpcDefinition definition, NpcInstance instance) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", definition.entityType);
            Entity entity = EntityType.loadEntityRecursive(
                    tag, level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), EntityProcessor.NOP);
            if (!(entity instanceof LivingEntity) || entity.getType() == EntityTypes.PLAYER) return null;
            apply(entity, definition, instance, true, true);
            if (!level.addFreshEntity(entity)) return null;
            UUID oldRuntime = instance.runtimeUuid();
            if (oldRuntime != null) instanceByRuntimeEntity.remove(oldRuntime);
            instance.runtimeEntityId = entity.getUUID().toString();
            instanceByRuntimeEntity.put(entity.getUUID(), instance.uuid());
            saveAll();
            return entity;
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error(
                    "Could not spawn SSU NPC '{}' using entity type '{}'.",
                    definition.id, definition.entityType, exception);
            return null;
        }
    }

    private static void apply(Entity entity, NpcDefinition definition, NpcInstance instance,
            boolean forceMove, boolean freshSpawn) {
        double returnDistanceSquared;
        if (instance.scheduleEnabled && !instance.schedule.isEmpty()) {
            returnDistanceSquared = Double.POSITIVE_INFINITY;
        } else if (definition.noAi) {
            boolean gravityControlled = definition.affectedByGravity && !definition.canFly && !entity.onGround();
            returnDistanceSquared = gravityControlled ? Double.POSITIVE_INFINITY : 0.04D;
        } else if (definition.homeRadius <= 0.0D) {
            returnDistanceSquared = Double.POSITIVE_INFINITY;
        } else {
            returnDistanceSquared = definition.homeRadius * definition.homeRadius;
        }
        if (forceMove || entity.distanceToSqr(instance.x, instance.y, instance.z) > returnDistanceSquared) {
            entity.snapTo(instance.x, instance.y, instance.z, instance.yaw, instance.pitch);
        }
        entity.setCustomName(Component.literal(definition.displayName));
        // SSU renders role, name and faction as one three-line label to avoid a duplicate vanilla nameplate.
        entity.setCustomNameVisible(false);
        entity.setInvulnerable(definition.invulnerable);
        entity.setSilent(definition.silent);
        entity.setGlowingTag(definition.glowing);
        entity.setNoGravity(definition.canFly || !definition.affectedByGravity);
        entity.addTag("ssu_npc");
        entity.addTag(runtimeTag(instance));
        if (entity instanceof LivingEntity living) {
            applyAttribute(living, Attributes.MAX_HEALTH, definition.maxHealth);
            applyAttribute(living, Attributes.MOVEMENT_SPEED, definition.movementSpeed);
            applyAttribute(living, Attributes.ATTACK_DAMAGE, definition.attackDamage);
            applyAttribute(living, Attributes.ARMOR, definition.armor);
            applyAttribute(living, Attributes.ARMOR_TOUGHNESS, definition.armorToughness);
            applyAttribute(living, Attributes.FOLLOW_RANGE, definition.followRange);
            applyAttribute(living, Attributes.KNOCKBACK_RESISTANCE, definition.knockbackResistance);
            applyAttribute(living, Attributes.SCALE, definition.scale);
            applyEquipment(living, definition);
            if (freshSpawn) {
                living.setHealth(living.getMaxHealth());
            } else if (living.getHealth() > living.getMaxHealth()) {
                living.setHealth(living.getMaxHealth());
            }
        }
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setNoAi(definition.noAi && !(instance.scheduleEnabled && !instance.schedule.isEmpty()));
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
        // Empty means an empty visual slot. NPC equipment is entirely controlled by SSU.
        ItemStack configured = NpcItemCodec.decode(entity.level().registryAccess(), encoded, legacyItemId, 1);
        if (!configured.isEmpty()) configured = configured.copyWithCount(1);
        stripGameplayComponents(configured);
        ItemStack current = entity.getItemBySlot(slot);
        if (!ItemStack.matches(current, configured)) entity.setItemSlot(slot, configured);
        // Configured NPC equipment is display-only and must never become loot.
        if (entity instanceof Mob mob) mob.setDropChance(slot, 0.0F);
    }

    /**
     * Converts a copied stack into a display-only stack. Visual identity, dyes, trims, custom names,
     * custom models and enchantment glint are retained while vanilla combat and armor effects are removed.
     */
    private static void stripGameplayComponents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        boolean hadGlint = stack.hasFoil();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        stack.remove(DataComponents.ENCHANTMENTS);
        stack.remove(DataComponents.STORED_ENCHANTMENTS);
        stack.remove(DataComponents.WEAPON);
        stack.remove(DataComponents.ATTACK_RANGE);
        stack.remove(DataComponents.MINIMUM_ATTACK_CHARGE);
        stack.remove(DataComponents.KINETIC_WEAPON);
        stack.remove(DataComponents.PIERCING_WEAPON);
        stack.remove(DataComponents.BLOCKS_ATTACKS);
        stack.remove(DataComponents.GLIDER);
        stack.remove(DataComponents.DEATH_PROTECTION);
        if (hadGlint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
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
            stripGameplayComponents(configured);
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
                entity -> entity.entityTags().contains(tag) && !entity.isRemoved());
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
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(rawDimension));
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removeRuntime(NpcInstance instance) {
        UUID instanceId = instance.uuid();
        activeScheduleEntry.remove(instanceId);
        nextSchedulePathTick.remove(instanceId);
        nextScheduleActivityTick.remove(instanceId);
        nextCombatAttackTick.remove(instanceId);
        UUID runtime = instance.runtimeUuid();
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
        nextSchedulePathTick.clear();
        nextScheduleActivityTick.clear();
        nextCombatAttackTick.clear();
        staticPhysicsInstances.clear();
        scheduledInstances.clear();
        relationInstances.clear();
        lastLabelSnapshots.clear();
        labelsEnabledLastTick = false;
        supportedModelCache = List.of();
    }

    /** Sends the current dimension's lightweight three-line NPC labels to one player. */
    public synchronized void syncLabels(ServerPlayer player) {
        syncLabels(player, true);
    }

    public synchronized void forgetLabelViewer(UUID playerId) {
        if (playerId != null) lastLabelSnapshots.remove(playerId);
    }

    private void syncAllLabels(boolean force) {
        if (server == null) return;
        Set<UUID> online = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            syncLabels(player, force);
        }
        lastLabelSnapshots.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    private void syncLabels(ServerPlayer player, boolean force) {
        if (player == null) return;
        List<NpcLabelSyncPayload.Entry> entries = buildLabelSnapshot(player);
        List<NpcLabelSyncPayload.Entry> previous = lastLabelSnapshots.get(player.getUUID());
        if (!force && entries.equals(previous)) return;
        lastLabelSnapshots.put(player.getUUID(), entries);
        PacketDistributor.sendToPlayer(player, new NpcLabelSyncPayload(entries));
    }

    private List<NpcLabelSyncPayload.Entry> buildLabelSnapshot(ServerPlayer player) {
        if (!Config.ENABLE_NPCS.get()) return List.of();
        String dimension = player.level().dimension().identifier().toString();
        ArrayList<NpcLabelSyncPayload.Entry> entries = new ArrayList<>();
        for (NpcInstance placement : instances.values()) {
            if (!placement.enabled || placement.dead || !dimension.equals(placement.dimension)) continue;
            NpcDefinition definition = definitions.get(placement.definitionId);
            if (definition == null || !definition.enabled || !definition.customNameVisible) continue;
            Entity entity = findRuntime(placement);
            if (entity == null || entity.isRemoved()) continue;
            entries.add(new NpcLabelSyncPayload.Entry(entity.getId(), entity.getUUID().toString(),
                    definition.displayName, definition.roleId, definition.factionLabel(), definition.playerAttitude));
        }
        entries.sort(Comparator.comparingInt(NpcLabelSyncPayload.Entry::entityId));
        return List.copyOf(entries);
    }

    private static boolean sameDefinition(NpcDefinition left, NpcDefinition right) {
        if (left == null || right == null) return false;
        return left.id.equals(right.id)
                && left.displayName.equals(right.displayName)
                && left.entityType.equals(right.entityType)
                && left.interactionText.equals(right.interactionText)
                && left.dialogueId.equals(right.dialogueId)
                && left.roleId.equals(right.roleId)
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
                && left.factionId.equals(right.factionId)
                && left.factionDisplayName.equals(right.factionDisplayName)
                && left.minimumReputation == right.minimumReputation
                && left.reputationDeniedText.equals(right.reputationDeniedText)
                && left.reputationLossOnAttack == right.reputationLossOnAttack
                && left.playerAttitude.equals(right.playerAttitude)
                && sameRelations(left.factionRelations, right.factionRelations)
                && Double.compare(left.maxHealth, right.maxHealth) == 0
                && Double.compare(left.movementSpeed, right.movementSpeed) == 0
                && Double.compare(left.attackDamage, right.attackDamage) == 0
                && Double.compare(left.armor, right.armor) == 0
                && Double.compare(left.armorToughness, right.armorToughness) == 0
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
}
