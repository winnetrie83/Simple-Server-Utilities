package be.winnetrie.mod.simpleserverutilities.npc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/** Runtime population engine for natural and vanilla-spawner-backed SSU NPC profiles. */
public final class NpcSpawnManager {
    public static final int MAX_PROFILES = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long CLEANUP_INTERVAL_TICKS = 40L;

    private final NpcManager npcs;
    private final Map<String, NpcSpawnProfile> profiles = new LinkedHashMap<>();
    private final DirtyJsonRecordStore store = new DirtyJsonRecordStore();
    private final Map<String, Long> nextProfileTick = new LinkedHashMap<>();
    private MinecraftServer server;
    private Path folder;
    private long nextCleanupTick;

    public NpcSpawnManager(NpcManager npcs) {
        this.npcs = npcs;
    }

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        this.folder = StoragePaths.npcSpawnProfiles(StoragePaths.root(server));
        profiles.clear();
        nextProfileTick.clear();
        nextCleanupTick = 0L;
        store.reset();
        try {
            java.nio.file.Files.createDirectories(folder);
            store.discover(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    NpcSpawnProfile profile = JsonStorage.read(GSON, file, NpcSpawnProfile.class);
                    if (profile == null) continue;
                    profile.normalize();
                    profiles.put(profile.id, profile);
                } catch (Exception exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load NPC spawn profile; archived as {}.", archived, exception);
                }
            }
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to initialize NPC spawn profiles.", exception);
        }
    }

    public synchronized void saveAll() {
        if (folder == null) return;
        Set<Path> files = new LinkedHashSet<>();
        for (NpcSpawnProfile profile : profiles.values()) {
            profile.normalize();
            Path file = StoragePaths.jsonFile(folder, profile.id);
            files.add(file.toAbsolutePath().normalize());
            store.queueJson(GSON, file, profile);
        }
        store.queueDeleteMissing(files);
    }

    public synchronized void clear() {
        profiles.clear();
        nextProfileTick.clear();
        store.reset();
        server = null;
        folder = null;
        nextCleanupTick = 0L;
    }

    public synchronized Collection<NpcSpawnProfile> profiles() {
        List<NpcSpawnProfile> result = new ArrayList<>();
        for (NpcSpawnProfile profile : profiles.values()) result.add(profile.copy());
        result.sort(Comparator.comparing(value -> value.id));
        return List.copyOf(result);
    }

    public synchronized NpcSpawnProfile profile(String rawId) {
        NpcSpawnProfile value = profiles.get(NpcDefinition.sanitizeId(rawId));
        return value == null ? null : value.copy();
    }

    public synchronized boolean saveProfile(String rawOriginalId, NpcSpawnProfile value) {
        if (value == null) return false;
        value.normalize();
        if (npcs.definition(value.definitionId) == null) return false;
        String originalId = rawOriginalId == null || rawOriginalId.isBlank() ? "" : NpcDefinition.sanitizeId(rawOriginalId);
        if (!originalId.equals(value.id) && profiles.containsKey(value.id)) return false;
        if (originalId.isBlank() && !profiles.containsKey(value.id) && profiles.size() >= MAX_PROFILES) return false;
        if (!originalId.isBlank() && !originalId.equals(value.id)) {
            profiles.remove(originalId);
            nextProfileTick.remove(originalId);
            removePopulation(originalId);
        }
        profiles.put(value.id, value.copy());
        nextProfileTick.remove(value.id);
        saveAll();
        return true;
    }

    public synchronized boolean deleteProfile(String rawId) {
        String id = NpcDefinition.sanitizeId(rawId);
        if (profiles.remove(id) == null) return false;
        nextProfileTick.remove(id);
        removePopulation(id);
        saveAll();
        return true;
    }

    public synchronized void tick(MinecraftServer activeServer) {
        if (server == null || activeServer != server) return;
        long tick = activeServer.getTickCount();
        if (tick >= nextCleanupTick) {
            nextCleanupTick = tick + CLEANUP_INTERVAL_TICKS;
            cleanupDynamicPopulation();
        }
        for (NpcSpawnProfile profile : List.copyOf(profiles.values())) {
            if (!profile.enabled || npcs.definition(profile.definitionId) == null) continue;
            long next = nextProfileTick.getOrDefault(profile.id, 0L);
            if (tick < next) continue;
            if (profile.source() == NpcSpawnSource.SPAWNER) {
                nextProfileTick.put(profile.id, tick + (long) profile.spawnerCooldownSeconds * 20L);
                tickSpawner(profile);
            } else {
                nextProfileTick.put(profile.id, tick + (long) profile.naturalCycleSeconds * 20L);
                tickNatural(profile);
            }
        }
    }

    public synchronized int liveCount(String rawProfileId) {
        return npcs.dynamicCount(rawProfileId);
    }

    public synchronized boolean usesDefinition(String rawDefinitionId) {
        String definitionId = NpcDefinition.sanitizeId(rawDefinitionId);
        for (NpcSpawnProfile profile : profiles.values()) if (definitionId.equals(profile.definitionId)) return true;
        return false;
    }

    /** True when this exact vanilla Spawner block is currently owned by an enabled SSU spawn profile. */
    public synchronized boolean controlsSpawner(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String dimension = level.dimension().identifier().toString();
        for (NpcSpawnProfile profile : profiles.values()) {
            if (!profile.enabled || profile.source() != NpcSpawnSource.SPAWNER) continue;
            String profileDimension = profile.spawnerDimension.isBlank() ? profile.dimension : profile.spawnerDimension;
            if (!dimension.equals(profileDimension)) continue;
            if (profile.spawnerX == pos.getX() && profile.spawnerY == pos.getY() && profile.spawnerZ == pos.getZ()) return true;
        }
        return false;
    }

    /** Keeps spawn profiles linked when an NPC template is renamed in the normal NPC editor. */
    public synchronized void renameDefinition(String rawOldId, String rawNewId) {
        String oldId = NpcDefinition.sanitizeId(rawOldId);
        String newId = NpcDefinition.sanitizeId(rawNewId);
        if (oldId.equals(newId)) return;
        boolean changed = false;
        for (NpcSpawnProfile profile : profiles.values()) {
            if (!oldId.equals(profile.definitionId)) continue;
            profile.definitionId = newId;
            changed = true;
        }
        if (changed) saveAll();
    }

    public synchronized boolean spawnTest(ServerPlayer player, String rawProfileId) {
        NpcSpawnProfile profile = profiles.get(NpcDefinition.sanitizeId(rawProfileId));
        if (player == null || profile == null || npcs.definition(profile.definitionId) == null) return false;
        if (profile.source() == NpcSpawnSource.SPAWNER) return spawnSpawnerGroup(profile, true) > 0;
        ServerLevel level = player.level();
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(player.blockPosition().getX() + 3, 0, player.blockPosition().getZ() + 3));
        return spawnGroup(profile, level, base, player.getRandom(), 1, true) > 0;
    }

    private void tickNatural(NpcSpawnProfile profile) {
        if (npcs.dynamicCount(profile.id) >= profile.globalCap) return;
        ServerLevel level = level(profile.dimension);
        if (level == null) return;
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.isSpectator()) continue;
            if (npcs.dynamicCount(profile.id) >= profile.globalCap) break;
            int nearby = countNearby(profile.id, level, player.getX(), player.getY(), player.getZ(), profile.maxPlayerDistance + 16.0D);
            if (nearby >= profile.maxNearby) continue;
            RandomSource random = player.getRandom();
            if (random.nextDouble() > profile.naturalChance) continue;
            for (int attempt = 0; attempt < profile.attemptsPerCycle; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = profile.minPlayerDistance
                        + random.nextDouble() * (profile.maxPlayerDistance - profile.minPlayerDistance);
                int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
                int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
                BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (!validNaturalPosition(profile, level, candidate, player)) continue;
                int wanted = Math.min(randomGroupSize(profile, random), profile.maxNearby - nearby);
                spawnGroup(profile, level, candidate, random, wanted, false);
                break;
            }
        }
    }

    private void tickSpawner(NpcSpawnProfile profile) {
        if (npcs.dynamicCount(profile.id) >= profile.globalCap) return;
        spawnSpawnerGroup(profile, false);
    }

    private int spawnSpawnerGroup(NpcSpawnProfile profile, boolean test) {
        ServerLevel level = level(profile.spawnerDimension.isBlank() ? profile.dimension : profile.spawnerDimension);
        if (level == null) return 0;
        BlockPos anchor = new BlockPos(profile.spawnerX, profile.spawnerY, profile.spawnerZ);
        if (!level.isLoaded(anchor) || !level.getBlockState(anchor).is(Blocks.SPAWNER)) return 0;
        if (!test && !hasActivePlayer(level, anchor, profile.spawnerActivationRange)) return 0;
        int nearby = countNearby(profile.id, level, anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D,
                Math.max(8.0D, profile.spawnerRadius + 4.0D));
        if (!test && nearby >= profile.maxNearby) return 0;
        RandomSource random = RandomSource.create();
        int wanted = test ? 1 : Math.min(randomGroupSize(profile, random), profile.maxNearby - nearby);
        int spawned = 0;
        for (int i = 0; i < wanted && npcs.dynamicCount(profile.id) < profile.globalCap; i++) {
            int x = anchor.getX() + random.nextInt((int) Math.ceil(profile.spawnerRadius * 2.0D) + 1)
                    - (int) Math.ceil(profile.spawnerRadius);
            int z = anchor.getZ() + random.nextInt((int) Math.ceil(profile.spawnerRadius * 2.0D) + 1)
                    - (int) Math.ceil(profile.spawnerRadius);
            BlockPos candidate = findFreeNear(level, new BlockPos(x, anchor.getY(), z));
            if (candidate == null || (!test && !validCommonPosition(profile, level, candidate))) continue;
            if (spawnOne(profile, level, candidate, random)) spawned++;
        }
        return spawned;
    }

    private int spawnGroup(NpcSpawnProfile profile, ServerLevel level, BlockPos center, RandomSource random,
            int wanted, boolean test) {
        int spawned = 0;
        for (int i = 0; i < wanted && npcs.dynamicCount(profile.id) < profile.globalCap; i++) {
            int x = center.getX() + (i == 0 ? 0 : random.nextInt(7) - 3);
            int z = center.getZ() + (i == 0 ? 0 : random.nextInt(7) - 3);
            BlockPos candidate = i == 0 ? center
                    : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if ((!test && !validCommonPosition(profile, level, candidate)) || (test && !basicSpace(level, candidate))) continue;
            if (spawnOne(profile, level, candidate, random)) spawned++;
        }
        return spawned;
    }

    private boolean spawnOne(NpcSpawnProfile profile, ServerLevel level, BlockPos position, RandomSource random) {
        float yaw = random.nextFloat() * 360.0F;
        NpcInstance instance = npcs.createDynamicPlacement(profile.definitionId, level,
                position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, yaw,
                profile.id, profile.despawnDistance);
        return instance != null;
    }

    private boolean validNaturalPosition(NpcSpawnProfile profile, ServerLevel level, BlockPos position, ServerPlayer origin) {
        if (!validCommonPosition(profile, level, position)) return false;
        double distanceSq = origin.distanceToSqr(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        if (distanceSq < profile.minPlayerDistance * profile.minPlayerDistance
                || distanceSq > (profile.maxPlayerDistance + 4.0D) * (profile.maxPlayerDistance + 4.0D)) return false;
        return true;
    }

    private boolean validCommonPosition(NpcSpawnProfile profile, ServerLevel level, BlockPos position) {
        if (position.getY() < profile.minY || position.getY() > profile.maxY || !basicSpace(level, position)) return false;
        String biome = level.getBiome(position).unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
        if (!profile.biomes.isEmpty() && !profile.biomes.contains(biome)) return false;
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, position), level.getBrightness(LightLayer.SKY, position));
        if (light < profile.minLight || light > profile.maxLight) return false;
        long dayTime = Math.floorMod(level.getDefaultClockTime(), 24_000L);
        boolean daylight = dayTime < 13_000L;
        return switch (profile.time()) {
            case DAY -> daylight;
            case NIGHT -> !daylight;
            case ANY -> true;
        };
    }

    private static boolean basicSpace(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position) || position.getY() <= level.getMinY() + 1 || position.getY() >= level.getMaxY() - 2) return false;
        if (!level.getBlockState(position).getCollisionShape(level, position).isEmpty()) return false;
        BlockPos above = position.above();
        if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) return false;
        BlockPos below = position.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static BlockPos findFreeNear(ServerLevel level, BlockPos origin) {
        int[] offsets = {0, 1, -1, 2, -2, 3, -3};
        for (int offset : offsets) {
            BlockPos candidate = origin.offset(0, offset, 0);
            if (basicSpace(level, candidate)) return candidate;
        }
        return null;
    }

    private static int randomGroupSize(NpcSpawnProfile profile, RandomSource random) {
        return profile.minGroup >= profile.maxGroup ? profile.minGroup
                : profile.minGroup + random.nextInt(profile.maxGroup - profile.minGroup + 1);
    }

    private int countNearby(String profileId, ServerLevel level, double x, double y, double z, double range) {
        double maxSq = range * range;
        int count = 0;
        for (NpcInstance instance : npcs.dynamicInstances()) {
            if (!profileId.equals(instance.dynamicSpawnProfileId) || instance.dead) continue;
            Entity entity = npcs.runtimeEntity(instance);
            if (entity == null || entity.level() != level || entity.isRemoved()) continue;
            if (entity.distanceToSqr(x, y, z) <= maxSq) count++;
        }
        return count;
    }

    private static boolean hasActivePlayer(ServerLevel level, BlockPos anchor, double range) {
        double maxSq = range * range;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D) <= maxSq) return true;
        }
        return false;
    }

    private void cleanupDynamicPopulation() {
        for (NpcInstance instance : List.copyOf(npcs.dynamicInstances())) {
            Entity entity = npcs.runtimeEntity(instance);
            if (instance.dead) {
                if (entity == null || entity.isRemoved()) npcs.removeDynamicInstance(instance.uuid(), false);
                continue;
            }
            if (entity == null || entity.isRemoved()) {
                npcs.removeDynamicInstance(instance.uuid(), false);
                continue;
            }
            double despawn = Math.max(16.0D, instance.dynamicDespawnDistance);
            boolean playerNear = false;
            for (Player player : entity.level().players()) {
                if (player.isSpectator()) continue;
                if (player.distanceToSqr(entity) <= despawn * despawn) { playerNear = true; break; }
            }
            if (!playerNear) npcs.removeDynamicInstance(instance.uuid(), true);
        }
    }

    private void removePopulation(String profileId) {
        for (NpcInstance instance : List.copyOf(npcs.dynamicInstances())) {
            if (profileId.equals(instance.dynamicSpawnProfileId)) npcs.removeDynamicInstance(instance.uuid(), true);
        }
    }

    private ServerLevel level(String rawDimension) {
        if (server == null) return null;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(rawDimension));
            return server.getLevel(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
