package be.winnetrie.mod.simpleserverutilities.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;

/**
 * Durable fail-closed journal for idempotent Content Core reward/action lists.
 * A PREPARED entry surviving a hard crash is never replayed automatically: this
 * deliberately prefers an administrator-recoverable missed reward over a duplicate.
 */
public final class ContentRewardLedger {
    public static final int CURRENT_SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ENTRIES = 20_000;

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private Path file;
    private boolean writeProtected;

    public synchronized void load(MinecraftServer server) {
        entries.clear();
        writeProtected = false;
        file = StoragePaths.content(StoragePaths.root(server)).resolve("reward_ledger.json");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) return;
            SaveData data = JsonStorage.read(GSON, file, SaveData.class);
            if (data == null) return;
            if (data.schemaVersion > CURRENT_SCHEMA) {
                writeProtected = true;
                throw new IllegalStateException("Content reward ledger uses future schema " + data.schemaVersion + ".");
            }
            if (data.entries != null) {
                for (Map.Entry<String, Entry> raw : data.entries.entrySet()) {
                    if (raw.getValue() == null || raw.getKey() == null || raw.getKey().isBlank()) continue;
                    Entry entry = raw.getValue().normalize(raw.getKey());
                    entries.put(entry.key, entry);
                }
            }
            trim();
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load Content Core reward ledger. Existing data is left untouched.", exception);
        }
    }

    public synchronized Decision begin(String rawKey, String module, String source) {
        String key = normalizeKey(rawKey);
        if (key.isBlank()) return Decision.NEW;
        if (writeProtected) return Decision.STORAGE_FAILED;
        Entry existing = entries.get(key);
        if (existing != null) {
            return existing.state == State.COMMITTED ? Decision.ALREADY_COMMITTED : Decision.RECOVERY_REQUIRED;
        }
        long now = System.currentTimeMillis();
        entries.put(key, new Entry(key, safe(module), safe(source), State.PREPARED, now, now, ""));
        trim();
        if (!writeNow()) {
            entries.remove(key);
            return Decision.STORAGE_FAILED;
        }
        return Decision.NEW;
    }

    public synchronized void commit(String rawKey) {
        update(rawKey, State.COMMITTED, "");
    }

    public synchronized void fail(String rawKey, String error) {
        update(rawKey, State.FAILED, error);
    }

    /** Removes a PREPARED entry after a normal transaction failure whose rollback completed cleanly. */
    public synchronized void abort(String rawKey) {
        String key = normalizeKey(rawKey);
        Entry existing = entries.get(key);
        if (existing == null || existing.state == State.COMMITTED) return;
        entries.remove(key);
        writeNow();
    }

    public synchronized boolean reset(String rawKey) {
        String key = normalizeKey(rawKey);
        if (entries.remove(key) == null) return false;
        return writeNow();
    }

    public synchronized void saveAll() { writeNow(); }
    public synchronized void clear() { entries.clear(); file = null; writeProtected = false; }
    public synchronized int size() { return entries.size(); }

    private void update(String rawKey, State state, String error) {
        String key = normalizeKey(rawKey);
        if (key.isBlank()) return;
        Entry old = entries.get(key);
        long now = System.currentTimeMillis();
        if (old == null) old = new Entry(key, "content", "actions", State.PREPARED, now, now, "");
        entries.put(key, new Entry(key, old.module, old.source, state, old.createdAtEpochMilli, now,
                error == null ? "" : limit(error, 512)));
        writeNow();
    }

    private boolean writeNow() {
        if (file == null || writeProtected) return false;
        try {
            SaveData data = new SaveData();
            data.schemaVersion = CURRENT_SCHEMA;
            data.entries.putAll(entries);
            JsonStorage.write(GSON, file, data);
            return true;
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to persist Content Core reward ledger.", exception);
            return false;
        }
    }

    private void trim() {
        if (entries.size() <= MAX_ENTRIES) return;
        var iterator = entries.entrySet().iterator();
        while (entries.size() > MAX_ENTRIES && iterator.hasNext()) {
            Map.Entry<String, Entry> next = iterator.next();
            if (next.getValue().state == State.COMMITTED) iterator.remove();
        }
    }

    private static String normalizeKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        return key.length() <= 256 ? key : key.substring(0, 256);
    }
    private static String safe(String raw) { return limit(raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT), 96); }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }

    public enum Decision { NEW, ALREADY_COMMITTED, RECOVERY_REQUIRED, STORAGE_FAILED }
    public enum State { PREPARED, COMMITTED, FAILED }

    public static final class Entry {
        String key = "";
        String module = "";
        String source = "";
        State state = State.PREPARED;
        long createdAtEpochMilli;
        long updatedAtEpochMilli;
        String error = "";

        Entry() {}
        Entry(String key, String module, String source, State state, long created, long updated, String error) {
            this.key = key; this.module = module; this.source = source; this.state = state;
            this.createdAtEpochMilli = created; this.updatedAtEpochMilli = updated; this.error = error;
        }
        Entry normalize(String fallbackKey) {
            key = normalizeKey(key == null || key.isBlank() ? fallbackKey : key);
            module = safe(module); source = safe(source); state = state == null ? State.PREPARED : state;
            createdAtEpochMilli = Math.max(0L, createdAtEpochMilli);
            updatedAtEpochMilli = Math.max(createdAtEpochMilli, updatedAtEpochMilli);
            error = limit(error == null ? "" : error, 512);
            return this;
        }
    }

    private static final class SaveData {
        int schemaVersion = CURRENT_SCHEMA;
        Map<String, Entry> entries = new LinkedHashMap<>();
    }
}
