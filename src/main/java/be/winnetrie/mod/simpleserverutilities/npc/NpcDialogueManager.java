package be.winnetrie.mod.simpleserverutilities.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;

/** Persistent dialogue definition library shared by linked NPC templates. */
public final class NpcDialogueManager {
    public static final int MAX_DIALOGUES = 512;
    public static final int MAX_SERIALIZED_CHARACTERS = 65_535;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, NpcDialogueDefinition> dialogues = new LinkedHashMap<>();
    private final DirtyJsonRecordStore store = new DirtyJsonRecordStore();
    private Path folder;

    public synchronized void load(MinecraftServer server) {
        clear();
        folder = StoragePaths.npcDialogues(StoragePaths.root(server));
        try {
            Files.createDirectories(folder);
            store.discover(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    NpcDialogueDefinition value = JsonStorage.read(GSON, file, NpcDialogueDefinition.class);
                    if (value == null) continue;
                    value.normalize();
                    if (GSON.toJson(value).length() > MAX_SERIALIZED_CHARACTERS) {
                        throw new IllegalArgumentException("Dialogue exceeds " + MAX_SERIALIZED_CHARACTERS + " serialized characters.");
                    }
                    if (dialogues.putIfAbsent(value.id, value) != null) {
                        throw new IllegalArgumentException("Duplicate dialogue ID across files: " + value.id);
                    }
                } catch (Exception exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load NPC dialogue; archived as {}.", archived, exception);
                }
            }
            saveAll();
            SimpleServerUtilities.LOGGER.info("Loaded {} SSU NPC dialogues.", dialogues.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU NPC dialogues.", exception);
        }
    }

    public synchronized Collection<NpcDialogueDefinition> all() {
        ArrayList<NpcDialogueDefinition> result = new ArrayList<>(dialogues.values());
        result.sort(Comparator.comparing(value -> value.id));
        return java.util.List.copyOf(result);
    }

    public synchronized NpcDialogueDefinition get(String rawId) {
        return dialogues.get(be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(rawId));
    }

    public synchronized boolean save(NpcDialogueDefinition value) {
        if (value == null) return false;
        value.normalize();
        String serialized = GSON.toJson(value);
        if (serialized.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Dialogue exceeds " + MAX_SERIALIZED_CHARACTERS + " serialized characters.");
        }
        if (!dialogues.containsKey(value.id) && dialogues.size() >= MAX_DIALOGUES) return false;
        dialogues.put(value.id, value);
        saveAll();
        return true;
    }

    public synchronized NpcDialogueDefinition ensureSimple(String rawId, String npcName, String text) {
        String id = NpcDialogueDefinition.requireId(rawId, "Dialogue ID");
        NpcDialogueDefinition current = dialogues.get(id);
        if (current != null) return current;
        if (dialogues.size() >= MAX_DIALOGUES) return null;
        NpcDialogueDefinition created = NpcDialogueDefinition.simple(id, npcName, text);
        dialogues.put(id, created);
        saveAll();
        return created;
    }

    public synchronized boolean delete(String rawId, boolean referenced) {
        String id = be.winnetrie.mod.simpleserverutilities.content.ContentId.normalize(rawId);
        if (referenced || dialogues.remove(id) == null) return false;
        saveAll();
        return true;
    }

    public synchronized String toJson(NpcDialogueDefinition value) {
        String json = GSON.toJson(value == null ? new NpcDialogueDefinition().normalize() : value);
        if (json.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Dialogue exceeds " + MAX_SERIALIZED_CHARACTERS + " serialized characters.");
        }
        return json;
    }

    public synchronized NpcDialogueDefinition fromJson(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("Dialogue data is empty.");
        if (json.length() > MAX_SERIALIZED_CHARACTERS) throw new IllegalArgumentException("Dialogue data exceeds " + MAX_SERIALIZED_CHARACTERS + " characters.");
        NpcDialogueDefinition value = GSON.fromJson(json, NpcDialogueDefinition.class);
        if (value == null) throw new IllegalArgumentException("Dialogue data is invalid.");
        return value.normalize();
    }

    public synchronized void saveAll() {
        if (folder == null) return;
        Set<Path> files = new LinkedHashSet<>();
        for (NpcDialogueDefinition value : dialogues.values()) {
            value.normalize();
            Path file = StoragePaths.jsonFile(folder, value.id);
            files.add(file.toAbsolutePath().normalize());
            store.queueJson(GSON, file, value);
        }
        store.queueDeleteMissing(files);
    }

    public synchronized void clear() {
        dialogues.clear();
        store.reset();
        folder = null;
    }
}
