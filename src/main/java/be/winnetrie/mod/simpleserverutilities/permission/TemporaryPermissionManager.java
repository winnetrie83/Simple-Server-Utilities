package be.winnetrie.mod.simpleserverutilities.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;

/** Persistent overlay for expiring permission rewards without mutating a player's base permissions. */
public final class TemporaryPermissionManager {
    public static final int CURRENT_SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Grant> grants = new LinkedHashMap<>();
    private Path file;
    private boolean writeProtected;

    public synchronized void load(MinecraftServer server) {
        file = StoragePaths.content(StoragePaths.root(server)).resolve("temporary_permissions.json");
        grants.clear(); writeProtected = false;
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                SaveData data = JsonStorage.read(GSON, file, SaveData.class);
                if (data != null) {
                    if (data.schemaVersion > CURRENT_SCHEMA) { writeProtected = true; throw new IllegalStateException("Temporary permissions use a future schema."); }
                    if (data.grants != null) for (Grant grant : data.grants) {
                        if (grant == null) continue;
                        try { grant.normalize(); grants.put(grant.key(), grant); }
                        catch (RuntimeException exception) { SimpleServerUtilities.LOGGER.warn("Ignoring invalid temporary permission grant.", exception); }
                    }
                }
            }
            purgeExpired(true);
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load temporary permission grants.", exception);
        }
    }

    public synchronized boolean grant(UUID playerId, String permission, String value, long durationSeconds, String sourceKey) {
        if (playerId == null) throw new IllegalArgumentException("Player is required.");
        if (writeProtected) throw new IllegalStateException("Temporary permission storage uses a newer schema and is write-protected.");
        if (durationSeconds <= 0L) throw new IllegalArgumentException("Temporary permission duration must be positive.");
        long now = System.currentTimeMillis();
        long durationMillis = durationSeconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : durationSeconds * 1000L;
        long expires = durationMillis == Long.MAX_VALUE || now > Long.MAX_VALUE - durationMillis ? Long.MAX_VALUE : now + durationMillis;
        Grant grant = new Grant(playerId.toString(), normalizePermission(permission), normalizeValue(value), normalizeSource(sourceKey), now, expires);
        String key = grant.key();
        Grant existing = grants.get(key);
        if (existing != null && existing.expiresAtEpochMilli >= expires && existing.value.equals(grant.value)) return false;
        grants.put(key, grant);
        if (!saveNow()) {
            if (existing == null) grants.remove(key); else grants.put(key, existing);
            throw new IllegalStateException("Temporary permission grant could not be persisted safely.");
        }
        return true;
    }

    public synchronized boolean revoke(UUID playerId, String permission, String sourceKey) {
        if (playerId == null || writeProtected) return false;
        String key = playerId + "|" + normalizePermission(permission) + "|" + normalizeSource(sourceKey);
        Grant removedGrant = grants.remove(key);
        if (removedGrant == null) return false;
        if (!saveNow()) {
            grants.put(key, removedGrant);
            throw new IllegalStateException("Temporary permission revoke could not be persisted safely.");
        }
        return true;
    }

    /** Returns null when no active temporary overlay exists. Newest matching grant wins. */
    public synchronized String resolve(UUID playerId, String permission) {
        if (playerId == null) return null;
        purgeExpired(true);
        String normalized = normalizePermission(permission);
        Grant best = null;
        for (Grant grant : grants.values()) {
            if (!grant.playerUuid.equals(playerId.toString()) || !grant.permission.equals(normalized)) continue;
            if (best == null || grant.grantedAtEpochMilli > best.grantedAtEpochMilli) best = grant;
        }
        return best == null ? null : best.value;
    }

    public synchronized void tick(long tick) { if (tick % 200L == 0L) purgeExpired(true); }
    public synchronized void saveAll() { saveNow(); }
    public synchronized void clear() { grants.clear(); file = null; writeProtected = false; }
    public synchronized List<Grant> grants(UUID playerId) {
        if (playerId == null) return List.of();
        purgeExpired(true);
        ArrayList<Grant> result = new ArrayList<>();
        for (Grant grant : grants.values()) if (grant.playerUuid.equals(playerId.toString())) result.add(grant);
        return List.copyOf(result);
    }

    private void purgeExpired(boolean save) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<String, Grant>> iterator = grants.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAtEpochMilli <= now) { iterator.remove(); changed = true; }
        }
        if (changed && save) saveNow();
    }

    private boolean saveNow() {
        if (file == null || writeProtected) return false;
        try {
            SaveData data = new SaveData();
            data.grants.addAll(grants.values());
            JsonStorage.write(GSON, file, data);
            return true;
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to persist temporary permission grants.", exception);
            return false;
        }
    }

    private static String normalizePermission(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank() || value.length() > 128 || !value.matches("[a-z0-9_.:-]+")) throw new IllegalArgumentException("Invalid permission key.");
        return value;
    }
    private static String normalizeValue(String raw) {
        String value = raw == null || raw.isBlank() ? "true" : raw.trim();
        return value.length() <= 128 ? value : value.substring(0, 128);
    }
    private static String normalizeSource(String raw) {
        String value = raw == null || raw.isBlank() ? "content" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    public static final class Grant {
        String playerUuid = "";
        String permission = "";
        String value = "true";
        String sourceKey = "content";
        long grantedAtEpochMilli;
        long expiresAtEpochMilli;
        Grant() {}
        Grant(String playerUuid, String permission, String value, String sourceKey, long granted, long expires) {
            this.playerUuid = playerUuid; this.permission = permission; this.value = value; this.sourceKey = sourceKey;
            this.grantedAtEpochMilli = granted; this.expiresAtEpochMilli = expires;
        }
        void normalize() {
            UUID.fromString(playerUuid); permission = normalizePermission(permission); value = normalizeValue(value); sourceKey = normalizeSource(sourceKey);
            if (expiresAtEpochMilli <= grantedAtEpochMilli) throw new IllegalArgumentException("Temporary permission expiration must be after grant time.");
        }
        String key() { return playerUuid + "|" + permission + "|" + sourceKey; }
        public String permission() { return permission; }
        public String value() { return value; }
        public long expiresAtEpochMilli() { return expiresAtEpochMilli; }
        public String sourceKey() { return sourceKey; }
    }
    private static final class SaveData {
        int schemaVersion = CURRENT_SCHEMA;
        List<Grant> grants = new ArrayList<>();
    }
}
