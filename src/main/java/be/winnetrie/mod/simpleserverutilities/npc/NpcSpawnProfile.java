package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Persistent rules that turn a reusable NPC template into natural or spawner-driven world population. */
public final class NpcSpawnProfile {
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = "spawn_profile";
    public String definitionId = "npc";
    public boolean enabled = true;
    public String source = NpcSpawnSource.NATURAL.id();

    /** Exact dimension ID used for natural spawning. */
    public String dimension = "minecraft:overworld";
    /** Exact biome IDs. Empty means any biome. */
    public List<String> biomes = new ArrayList<>();
    public String time = NpcSpawnTime.ANY.id();
    public int minY = -64;
    public int maxY = 320;
    public int minLight = 0;
    public int maxLight = 15;

    public int minGroup = 1;
    public int maxGroup = 1;
    /** Maximum live dynamic NPCs from this profile around one spawning origin/player. */
    public int maxNearby = 8;
    /** Maximum live dynamic NPCs for this profile server-wide. */
    public int globalCap = 48;
    /** Distance from all players beyond which a dynamic NPC may be cleaned up. */
    public double despawnDistance = 96.0D;

    /** Natural-spawn attempt chance per player/cycle. */
    public double naturalChance = 0.35D;
    public int naturalCycleSeconds = 8;
    public int attemptsPerCycle = 3;
    public double minPlayerDistance = 24.0D;
    public double maxPlayerDistance = 64.0D;

    /** Vanilla spawner block anchor. */
    public String spawnerDimension = "";
    public int spawnerX;
    public int spawnerY;
    public int spawnerZ;
    public int spawnerCooldownSeconds = 20;
    public double spawnerRadius = 4.0D;
    public double spawnerActivationRange = 16.0D;

    public NpcSpawnProfile normalize() {
        schemaVersion = SCHEMA_VERSION;
        id = NpcDefinition.sanitizeId(id);
        definitionId = NpcDefinition.sanitizeId(definitionId);
        source = NpcSpawnSource.parse(source).id();
        dimension = normalizeRegistryId(dimension, "minecraft:overworld");
        time = NpcSpawnTime.parse(time).id();
        minY = clamp(minY, -2_048, 2_048);
        maxY = clamp(maxY, -2_048, 2_048);
        if (minY > maxY) { int swap = minY; minY = maxY; maxY = swap; }
        minLight = clamp(minLight, 0, 15);
        maxLight = clamp(maxLight, 0, 15);
        if (minLight > maxLight) { int swap = minLight; minLight = maxLight; maxLight = swap; }
        minGroup = clamp(minGroup, 1, 16);
        maxGroup = clamp(maxGroup, 1, 16);
        if (minGroup > maxGroup) { int swap = minGroup; minGroup = maxGroup; maxGroup = swap; }
        maxNearby = clamp(maxNearby, 1, 128);
        globalCap = clamp(globalCap, 1, 1_024);
        despawnDistance = finiteClamp(despawnDistance, 16.0D, 512.0D, 96.0D);
        naturalChance = finiteClamp(naturalChance, 0.0D, 1.0D, 0.35D);
        naturalCycleSeconds = clamp(naturalCycleSeconds, 1, 300);
        attemptsPerCycle = clamp(attemptsPerCycle, 1, 16);
        minPlayerDistance = finiteClamp(minPlayerDistance, 8.0D, 256.0D, 24.0D);
        maxPlayerDistance = finiteClamp(maxPlayerDistance, 8.0D, 384.0D, 64.0D);
        if (minPlayerDistance > maxPlayerDistance) {
            double swap = minPlayerDistance; minPlayerDistance = maxPlayerDistance; maxPlayerDistance = swap;
        }
        spawnerDimension = spawnerDimension == null || spawnerDimension.isBlank()
                ? "" : normalizeRegistryId(spawnerDimension, "minecraft:overworld");
        spawnerCooldownSeconds = clamp(spawnerCooldownSeconds, 1, 3_600);
        spawnerRadius = finiteClamp(spawnerRadius, 1.0D, 32.0D, 4.0D);
        spawnerActivationRange = finiteClamp(spawnerActivationRange, 1.0D, 128.0D, 16.0D);
        if (biomes == null) biomes = new ArrayList<>();
        Set<String> normalizedBiomes = new LinkedHashSet<>();
        for (String biome : biomes) {
            if (biome == null || biome.isBlank()) continue;
            normalizedBiomes.add(normalizeRegistryId(biome, "minecraft:plains"));
            if (normalizedBiomes.size() >= 64) break;
        }
        biomes = new ArrayList<>(normalizedBiomes);
        return this;
    }

    public NpcSpawnSource source() { return NpcSpawnSource.parse(source); }
    public NpcSpawnTime time() { return NpcSpawnTime.parse(time); }

    public String biomesCsv() { return String.join(", ", biomes); }

    public void setBiomesCsv(String raw) {
        biomes = new ArrayList<>();
        if (raw == null || raw.isBlank()) return;
        for (String value : raw.split("[,;\\s]+")) if (!value.isBlank()) biomes.add(value.trim());
    }

    public NpcSpawnProfile copy() {
        NpcSpawnProfile copy = new NpcSpawnProfile();
        copy.schemaVersion = schemaVersion;
        copy.id = id; copy.definitionId = definitionId; copy.enabled = enabled; copy.source = source;
        copy.dimension = dimension; copy.biomes = new ArrayList<>(biomes); copy.time = time;
        copy.minY = minY; copy.maxY = maxY; copy.minLight = minLight; copy.maxLight = maxLight;
        copy.minGroup = minGroup; copy.maxGroup = maxGroup; copy.maxNearby = maxNearby; copy.globalCap = globalCap;
        copy.despawnDistance = despawnDistance; copy.naturalChance = naturalChance;
        copy.naturalCycleSeconds = naturalCycleSeconds; copy.attemptsPerCycle = attemptsPerCycle;
        copy.minPlayerDistance = minPlayerDistance; copy.maxPlayerDistance = maxPlayerDistance;
        copy.spawnerDimension = spawnerDimension; copy.spawnerX = spawnerX; copy.spawnerY = spawnerY; copy.spawnerZ = spawnerZ;
        copy.spawnerCooldownSeconds = spawnerCooldownSeconds; copy.spawnerRadius = spawnerRadius;
        copy.spawnerActivationRange = spawnerActivationRange;
        return copy.normalize();
    }

    private static String normalizeRegistryId(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return fallback;
        if (!value.contains(":")) value = "minecraft:" + value;
        return value.length() <= 256 ? value : fallback;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
