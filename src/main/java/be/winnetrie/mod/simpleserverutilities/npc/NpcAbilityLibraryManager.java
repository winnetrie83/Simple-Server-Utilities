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
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;

/** Server-wide reusable NPC ability catalogue. NPC templates only keep assignments/references. */
public final class NpcAbilityLibraryManager {
    public static final int STORAGE_SCHEMA = 1;
    public static final int MAX_ABILITIES = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, NpcAbilityDefinition> abilities = new LinkedHashMap<>();
    private final DirtyJsonRecordStore store = new DirtyJsonRecordStore();
    private Path folder;

    public synchronized void load(MinecraftServer server) {
        abilities.clear();
        store.reset();
        folder = StoragePaths.npcAbilities(StoragePaths.root(server));
        try {
            Files.createDirectories(folder);
            store.discover(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    NpcAbilityDefinition value = JsonStorage.read(GSON, file, NpcAbilityDefinition.class);
                    if (value == null) continue;
                    value.normalize();
                    value.phaseId = ""; // phase gating belongs to the NPC assignment, not the shared ability.
                    if (!abilities.containsKey(value.id) && abilities.size() < MAX_ABILITIES) abilities.put(value.id, value);
                } catch (Exception exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load NPC ability; archived as {}.", archived, exception);
                }
            }
            saveAll();
            SimpleServerUtilities.LOGGER.info("Loaded {} shared SSU NPC abilities.", abilities.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load shared SSU NPC abilities.", exception);
        }
    }

    public synchronized Collection<NpcAbilityDefinition> definitions() {
        ArrayList<NpcAbilityDefinition> result = new ArrayList<>();
        abilities.values().stream().sorted(Comparator.comparing(a -> a.id)).forEach(a -> result.add(a.copy()));
        return List.copyOf(result);
    }

    public synchronized NpcAbilityDefinition get(String rawId) {
        String id = sanitize(rawId);
        NpcAbilityDefinition value = abilities.get(id);
        return value == null ? null : value.copy();
    }

    /** Runtime-only resolved instance. Callers must never mutate it. */
    synchronized NpcAbilityDefinition resolved(String rawId) { return abilities.get(sanitize(rawId)); }

    public synchronized boolean save(String rawOriginalId, NpcAbilityDefinition value) {
        if (value == null) return false;
        value = value.copy();
        value.phaseId = "";
        value.normalize();
        String originalId = sanitize(rawOriginalId);
        if (originalId.isBlank()) originalId = value.id;
        if (!originalId.equals(value.id) && abilities.containsKey(value.id)) return false;
        if (!abilities.containsKey(originalId) && !abilities.containsKey(value.id) && abilities.size() >= MAX_ABILITIES) return false;
        if (!originalId.equals(value.id)) abilities.remove(originalId);
        abilities.put(value.id, value);
        saveAll();
        return true;
    }

    public synchronized boolean delete(String rawId) {
        String id = sanitize(rawId);
        if (id.isBlank() || abilities.remove(id) == null) return false;
        saveAll();
        return true;
    }

    /** Imports schema<=18 per-NPC ability copies into the shared library without accidental cross-NPC sharing. */
    public synchronized Map<String, String> importLegacy(String npcId, List<NpcAbilityDefinition> legacy) {
        LinkedHashMap<String, String> mapping = new LinkedHashMap<>();
        if (legacy == null || legacy.isEmpty()) return mapping;
        String owner = sanitize(npcId);
        int ordinal = 1;
        for (NpcAbilityDefinition raw : legacy) {
            if (raw == null || abilities.size() >= MAX_ABILITIES) continue;
            NpcAbilityDefinition value = raw.copy();
            applySmartLegacyDefaults(value);
            String oldId = value.id;
            String base = sanitize(oldId);
            String candidate = base;
            if (candidate.isBlank() || abilities.containsKey(candidate)) candidate = sanitize(owner + "." + (base.isBlank() ? "ability_" + ordinal : base));
            int suffix = 2;
            String stem = candidate;
            while (abilities.containsKey(candidate)) candidate = truncate(stem, Math.max(1, 60 - Integer.toString(suffix).length())) + "_" + suffix++;
            value.id = candidate;
            value.phaseId = "";
            value.normalize();
            abilities.put(value.id, value);
            mapping.put(oldId, value.id);
            ordinal++;
        }
        saveAll();
        return mapping;
    }

    /** Applies safe AI ownership defaults when migrating embedded dev3.39 abilities into the shared library. */
    private static void applySmartLegacyDefaults(NpcAbilityDefinition ability) {
        if (ability == null) return;
        switch (ability.abilityType()) {
            case THUNDERCLAP -> { ability.requiresStationary = true; ability.minTargets = Math.max(1, ability.minTargets); }
            case ARCANE_MISSILES, SLASH, ARROW_VOLLEY, FIREBALL, ICE_BALL, WEAPON_RANGED, SELF_HEAL -> ability.requiresStationary = true;
            default -> { }
        }
        ability.normalize();
    }

    public synchronized void saveAll() {
        if (folder == null) return;
        Set<Path> keep = new LinkedHashSet<>();
        for (NpcAbilityDefinition value : abilities.values()) {
            value.phaseId = "";
            value.normalize();
            Path file = StoragePaths.jsonFile(folder, value.id);
            keep.add(file.toAbsolutePath().normalize());
            store.queueJson(GSON, file, value);
        }
        store.queueDeleteMissing(keep);
    }

    public synchronized void clear() {
        abilities.clear();
        folder = null;
        store.reset();
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
        return truncate(value, 64);
    }
    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
