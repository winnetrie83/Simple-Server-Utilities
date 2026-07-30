package be.winnetrie.mod.simpleserverutilities.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class PermissionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PermissionData data = new PermissionData();
    private PermissionSettings settings = new PermissionSettings();
    private final PermissionResolutionCache resolutionCache = new PermissionResolutionCache();
    private final DirtyJsonRecordStore rankRecordStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore playerRecordStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore dimensionRecordStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore claimContextRecordStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore settingsRecordStore = new DirtyJsonRecordStore();
    private Path rootFolder;
    private Path permissionsFolder;
    private Path settingsFile;
    private Path legacySaveFile;

    public void load(MinecraftServer server) {
        this.rootFolder = StoragePaths.root(server);
        this.permissionsFolder = StoragePaths.permissions(rootFolder);
        this.settingsFile = StoragePaths.permissionSettings(rootFolder);
        this.legacySaveFile = rootFolder.resolve("permissions.json");

        try {
            Files.createDirectories(rootFolder);
            data = new PermissionData();
            settings = new PermissionSettings();
            invalidateResolutionCache();
            rankRecordStore.reset();
            playerRecordStore.reset();
            dimensionRecordStore.reset();
            claimContextRecordStore.reset();
            settingsRecordStore.reset();
            loadSettings();

            if (JsonStorage.hasJsonFiles(permissionsFolder)) {
                discoverSplitStores();
                loadSplitData();
            } else if (Files.exists(legacySaveFile)) {
                loadLegacyData();
                save();
                if (SimpleServerUtilities.STORAGE.flush(java.time.Duration.ofSeconds(10))) {
                    Path archived = JsonStorage.archiveLegacyFile(legacySaveFile);
                    if (archived != null) {
                        SimpleServerUtilities.LOGGER.info("Migrated legacy permission data to split storage. Legacy file archived as: {}", archived);
                    }
                } else {
                    SimpleServerUtilities.LOGGER.error("Permission migration writes did not flush; the legacy file was kept in place.");
                }
            } else {
                data = createDefaultData();
                save();
                SimpleServerUtilities.LOGGER.info("Created default permission data.");
                return;
            }

            if (ensureDefaultDataUpToDate()) {
                save();
            }

            SimpleServerUtilities.LOGGER.info(
                    "Loaded permission data: {} ranks, {} player overrides, {} dimension scopes, {} claim context scopes.",
                    data.getRanks().size(),
                    data.getPlayers().size(),
                    data.getDimensions().size(),
                    data.getPlayerClaimContext().size()
            );
        } catch (Exception e) {
            data = createDefaultData();
            SimpleServerUtilities.LOGGER.error("Failed to load permission data. Using defaults.", e);
        }
    }

    public void save() {
        if (permissionsFolder == null) {
            return;
        }

        invalidateResolutionCache();
        try {
            saveSplitData();
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save permission data.", e);
        }
    }

    public String resolveValue(ServerPlayer player, String key, PermissionContext context) {
        if (key == null || key.isBlank()) {
            return null;
        }

        PermissionResolutionCache.Key cacheKey = PermissionResolutionCache.key(player, key, context);
        PermissionResolutionCache.Lookup cached = resolutionCache.get(cacheKey);
        if (cached.found()) {
            SimpleServerUtilities.PERFORMANCE.recordPermissionCheck(true);
            return cached.value();
        }

        SimpleServerUtilities.PERFORMANCE.recordPermissionCheck(false);
        String resolved = resolveUncached(player, key, context);
        resolutionCache.put(cacheKey, resolved);
        return resolved;
    }

    private String resolveUncached(ServerPlayer player, String key, PermissionContext context) {
        // Personal permissions are always the final player-specific override.
        String playerValue = getPlayerValue(player, key);
        if (playerValue != null) {
            return playerValue;
        }

        // Context rules remain compatible, but can never override a personal value.
        String regionValue = getRegionValue(key, context);
        if (regionValue != null) {
            return regionValue;
        }

        String playerClaimContextValue = getPlayerClaimContextValue(key, context);
        if (playerClaimContextValue != null) {
            return playerClaimContextValue;
        }

        String dimensionValue = getDimensionValue(key, context);
        if (dimensionValue != null) {
            return dimensionValue;
        }

        return getRankValue(player, key);
    }

    public void invalidateResolutionCache() {
        resolutionCache.clear();
        SimpleServerUtilities.PERFORMANCE.recordPermissionCacheInvalidation();
    }

    public int cachedResolutionCount() {
        return resolutionCache.size();
    }

    public PermissionRank getOrCreateRank(String rankName) {
        String normalizedRank = normalizeRankName(rankName);
        return data.getRanks().computeIfAbsent(normalizedRank, ignored -> new PermissionRank(0));
    }

    public PermissionRank getRank(String rankName) {
        return data.getRanks().get(normalizeRankName(rankName));
    }

    public List<String> getRankNames() {
        ArrayList<String> names = new ArrayList<>(data.getRanks().keySet());
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    /** Snapshot used by the paged permission editor. */
    public List<KnownPlayer> getKnownPlayers() {
        ArrayList<KnownPlayer> players = new ArrayList<>();
        for (Map.Entry<String, PlayerPermissionData> entry : data.getPlayers().entrySet()) {
            try {
                UUID playerId = UUID.fromString(entry.getKey());
                PlayerPermissionData playerData = entry.getValue();
                String name = playerData.getLastKnownName();
                if (name.isBlank()) {
                    name = playerId.toString().substring(0, 8);
                }
                players.add(new KnownPlayer(playerId, name, List.copyOf(playerData.getRanks())));
            } catch (IllegalArgumentException ignored) {
                // Invalid legacy entries are skipped here and reported by the normal save path.
            }
        }
        players.sort(Comparator.comparing(KnownPlayer::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(value -> value.playerId().toString()));
        return List.copyOf(players);
    }

    /** Returns direct plus inherited values for one rank. */
    public Map<String, String> getEffectiveRankPermissions(String rankName) {
        if (getRank(rankName) == null) {
            return Map.of();
        }
        return Map.copyOf(getRankPermissionsWithInheritance(rankName, new HashSet<>()));
    }

    /** Returns the merged rank values for an online or offline known player. */
    public Map<String, String> getEffectiveRankPermissions(UUID playerId) {
        PlayerPermissionData playerData = getPlayerData(playerId);
        ArrayList<String> rankNames = new ArrayList<>();
        if (playerData != null) {
            rankNames.addAll(playerData.getRanks());
        }
        if (rankNames.isEmpty()) {
            rankNames.add(settings.getDefaultRank());
        }
        rankNames.sort(Comparator.comparingInt(rankName -> {
            PermissionRank rank = getRank(rankName);
            return rank == null ? 0 : rank.getPriority();
        }));
        Map<String, String> merged = new HashMap<>();
        for (String rankName : rankNames) {
            merged.putAll(getRankPermissionsWithInheritance(rankName, new HashSet<>()));
        }
        return Map.copyOf(merged);
    }

    public record KnownPlayer(UUID playerId, String name, List<String> ranks) {
        public KnownPlayer {
            name = name == null ? "" : name.trim();
            ranks = ranks == null ? List.of() : List.copyOf(ranks);
        }
    }

    public void setRankPermission(String rankName, String key, String value) {
        getOrCreateRank(rankName).setPermission(key, value);
        save();
    }

    public boolean removeRankPermission(String rankName, String key) {
        PermissionRank rank = getRank(rankName);

        if (rank == null) {
            return false;
        }

        boolean existed = rank.getPermissions().containsKey(key);
        rank.removePermission(key);
        save();
        return existed;
    }

    public void setRankPriority(String rankName, int priority) {
        PermissionRank rank = getOrCreateRank(rankName);
        rank.setPriority(priority);
        save();
    }

    public boolean addRankInheritance(String rankName, String inheritedRankName) {
        String normalizedRank = normalizeRankName(rankName);
        String normalizedInheritedRank = normalizeRankName(inheritedRankName);

        if (normalizedRank.equals(normalizedInheritedRank)) {
            return false;
        }

        getOrCreateRank(normalizedRank);
        getOrCreateRank(normalizedInheritedRank);

        if (wouldCreateInheritanceCycle(normalizedRank, normalizedInheritedRank)) {
            return false;
        }

        PermissionRank rank = getOrCreateRank(normalizedRank);

        if (rank.getInherits().contains(normalizedInheritedRank)) {
            return false;
        }

        rank.getInherits().add(normalizedInheritedRank);
        save();
        return true;
    }

    public boolean removeRankInheritance(String rankName, String inheritedRankName) {
        PermissionRank rank = getRank(rankName);

        if (rank == null) {
            return false;
        }

        boolean removed = rank.getInherits().remove(normalizeRankName(inheritedRankName));

        if (removed) {
            save();
        }

        return removed;
    }

    private boolean wouldCreateInheritanceCycle(String rankName, String inheritedRankName) {
        return rankInheritsFrom(inheritedRankName, rankName, new HashSet<>());
    }

    private boolean rankInheritsFrom(String rankName, String targetRankName, Set<String> visited) {
        String normalizedRank = normalizeRankName(rankName);
        String normalizedTargetRank = normalizeRankName(targetRankName);

        if (!visited.add(normalizedRank)) {
            return false;
        }

        PermissionRank rank = getRank(normalizedRank);

        if (rank == null) {
            return false;
        }

        for (String inheritedRankName : rank.getInherits()) {
            String normalizedInheritedRank = normalizeRankName(inheritedRankName);

            if (normalizedInheritedRank.equals(normalizedTargetRank)) {
                return true;
            }

            if (rankInheritsFrom(normalizedInheritedRank, normalizedTargetRank, visited)) {
                return true;
            }
        }

        return false;
    }

    public PlayerPermissionData getOrCreatePlayerData(UUID playerId) {
        PlayerPermissionData playerData = data.getPlayers().computeIfAbsent(playerId.toString(), ignored -> new PlayerPermissionData());
        playerData.setUuid(playerId);
        return playerData;
    }

    public PlayerPermissionData getPlayerData(UUID playerId) {
        return data.getPlayers().get(playerId.toString());
    }

    public void addPlayerRank(UUID playerId, String rankName) {
        getOrCreateRank(rankName);
        getOrCreatePlayerData(playerId).addRank(rankName);
        save();
    }

    public boolean removePlayerRank(UUID playerId, String rankName) {
        PlayerPermissionData playerData = getPlayerData(playerId);

        if (playerData == null) {
            return false;
        }

        boolean existed = playerData.getRanks().contains(normalizeRankName(rankName));
        playerData.removeRank(rankName);
        save();
        return existed;
    }

    public void setPlayerPermission(UUID playerId, String key, String value) {
        getOrCreatePlayerData(playerId).setPermission(key, value);
        save();
    }

    public boolean removePlayerPermission(UUID playerId, String key) {
        PlayerPermissionData playerData = getPlayerData(playerId);

        if (playerData == null) {
            return false;
        }

        boolean existed = playerData.getPermissions().containsKey(key);
        playerData.removePermission(key);
        save();
        return existed;
    }

    public PermissionScope getOrCreateDimensionScope(String dimensionId) {
        return data.getDimensions().computeIfAbsent(dimensionId, ignored -> new PermissionScope());
    }

    public void setDimensionPermission(String dimensionId, String key, String value) {
        getOrCreateDimensionScope(dimensionId).setPermission(key, value);
        save();
    }

    public boolean removeDimensionPermission(String dimensionId, String key) {
        PermissionScope scope = data.getDimensions().get(dimensionId);

        if (scope == null) {
            return false;
        }

        boolean existed = scope.getPermissions().containsKey(key);
        scope.removePermission(key);
        save();
        return existed;
    }

    public PermissionScope getOrCreatePlayerClaimContextScope(String roleName) {
        return data.getPlayerClaimContext().computeIfAbsent(normalizeRoleName(roleName), ignored -> new PermissionScope());
    }

    public void setPlayerClaimContextPermission(String roleName, String key, String value) {
        getOrCreatePlayerClaimContextScope(roleName).setPermission(key, value);
        save();
    }

    public boolean removePlayerClaimContextPermission(String roleName, String key) {
        PermissionScope scope = data.getPlayerClaimContext().get(normalizeRoleName(roleName));

        if (scope == null) {
            return false;
        }

        boolean existed = scope.getPermissions().containsKey(key);
        scope.removePermission(key);
        save();
        return existed;
    }

    public PermissionData getData() {
        return data;
    }

    public PermissionSettings getSettings() {
        return settings;
    }

    public String getDefaultRankName() {
        return settings.getDefaultRank();
    }

    public String getPrimaryRankName(UUID playerId) {
        PlayerPermissionData playerData = getPlayerData(playerId);
        if (playerData == null || playerData.getRanks().isEmpty()) {
            return settings.getDefaultRank();
        }
        return normalizeRankName(playerData.getRanks().get(0));
    }

    public void setDefaultRankName(String rankName) {
        String normalized = normalizeRankName(rankName);
        PermissionRank rank = getOrCreateRank(normalized);
        fillDefaultRank(rank);
        settings.setDefaultRank(normalized);
        save();
    }

    /**
     * Ensures a persistent player permission profile exists and assigns the
     * configured default rank only when the player has no ranks yet.
     */
    public boolean ensurePlayerProfile(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        PlayerPermissionData playerData = getOrCreatePlayerData(player.getUUID());
        boolean changed = false;
        String currentName = player.getName().getString();
        if (!currentName.equals(playerData.getLastKnownName())) {
            playerData.setLastKnownName(currentName);
            changed = true;
        }

        if (settings.isAssignDefaultRankOnFirstJoin() && playerData.getRanks().isEmpty()) {
            playerData.addRank(settings.getDefaultRank());
            changed = true;
        }

        if (changed) {
            save();
        }
        return changed;
    }

    /** Assigns one base rank. Personal permissions remain untouched. */
    public void assignPlayerRank(UUID playerId, String rankName) {
        String normalized = normalizeRankName(rankName);
        getOrCreateRank(normalized);
        PlayerPermissionData playerData = getOrCreatePlayerData(playerId);
        playerData.getRanks().clear();
        playerData.addRank(normalized);
        save();
    }

    public boolean deleteRank(String rankName) {
        String normalized = normalizeRankName(rankName);
        if (normalized.equals(settings.getDefaultRank()) || normalized.equals("admin")) {
            return false;
        }
        if (data.getRanks().remove(normalized) == null) {
            return false;
        }
        for (PlayerPermissionData playerData : data.getPlayers().values()) {
            playerData.removeRank(normalized);
            if (playerData.getRanks().isEmpty()) {
                playerData.addRank(settings.getDefaultRank());
            }
        }
        for (PermissionRank rank : data.getRanks().values()) {
            rank.getInherits().remove(normalized);
        }
        save();
        return true;
    }

    public boolean renameRank(String oldName, String newName) {
        String oldNormalized = normalizeRankName(oldName);
        String newNormalized = normalizeRankName(newName);
        if (oldNormalized.equals(newNormalized) || data.getRanks().containsKey(newNormalized)) {
            return false;
        }
        PermissionRank rank = data.getRanks().remove(oldNormalized);
        if (rank == null) {
            return false;
        }
        data.getRanks().put(newNormalized, rank);
        for (PlayerPermissionData playerData : data.getPlayers().values()) {
            if (playerData.getRanks().remove(oldNormalized)) {
                playerData.addRank(newNormalized);
            }
        }
        for (PermissionRank other : data.getRanks().values()) {
            int index = other.getInherits().indexOf(oldNormalized);
            if (index >= 0) {
                other.getInherits().set(index, newNormalized);
            }
        }
        if (settings.getDefaultRank().equals(oldNormalized)) {
            settings.setDefaultRank(newNormalized);
        }
        save();
        return true;
    }

    public UUID findKnownPlayerId(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        for (Map.Entry<String, PlayerPermissionData> entry : data.getPlayers().entrySet()) {
            if (entry.getValue().getLastKnownName().equalsIgnoreCase(playerName.trim())) {
                try {
                    return UUID.fromString(entry.getKey());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public String resolvePersonalValue(ServerPlayer player, String key) {
        return getPlayerValue(player, key);
    }

    /**
     * Moves former claim-specific player limits into normal personal
     * permissions. Existing personal values win and are never overwritten.
     */
    public int migrateLegacyClaimLimitOverrides() {
        var legacyOverrides = SimpleServerUtilities.PLAYER_CLAIMS.getLegacyLimitOverridesSnapshot();
        int migrated = 0;
        for (Map.Entry<UUID, be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaimLimits> entry
                : legacyOverrides.entrySet()) {
            PlayerPermissionData playerData = getOrCreatePlayerData(entry.getKey());
            var limits = entry.getValue();
            if (limits.hasMaxChunksOverride()
                    && !playerData.getPermissions().containsKey(PermissionKeys.CLAIMS_MAX_CHUNKS)) {
                playerData.setPermission(PermissionKeys.CLAIMS_MAX_CHUNKS, Integer.toString(limits.getMaxChunks()));
                migrated++;
            }
            if (limits.hasMaxClaimGroupsOverride()
                    && !playerData.getPermissions().containsKey(PermissionKeys.CLAIMS_MAX_GROUPS)) {
                playerData.setPermission(PermissionKeys.CLAIMS_MAX_GROUPS, Integer.toString(limits.getMaxClaimGroups()));
                migrated++;
            }
        }
        if (!legacyOverrides.isEmpty()) {
            if (migrated > 0) {
                save();
                if (!SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(10))) {
                    SimpleServerUtilities.LOGGER.error(
                            "Claim-limit permission migration did not flush. Legacy override records were retained."
                    );
                    return migrated;
                }
            }

            SimpleServerUtilities.PLAYER_CLAIMS.clearLegacyLimitOverrides();
            SimpleServerUtilities.LOGGER.info(
                    "Migrated {} legacy claim-limit override(s) to personal permissions and retired {} legacy record(s).",
                    migrated,
                    legacyOverrides.size()
            );
        }
        return migrated;
    }

    private void discoverSplitStores() {
        settingsRecordStore.discoverFile(settingsFile);
        rankRecordStore.discover(StoragePaths.permissionRanks(rootFolder));
        playerRecordStore.discover(StoragePaths.permissionPlayers(rootFolder));
        dimensionRecordStore.discover(StoragePaths.permissionDimensions(rootFolder));
        claimContextRecordStore.discover(StoragePaths.permissionClaimContext(rootFolder));
    }

    private void loadSettings() {
        if (settingsFile == null || !Files.exists(settingsFile)) {
            settings = new PermissionSettings();
            settings.normalize();
            return;
        }
        try {
            PermissionSettings loaded = JsonStorage.read(GSON, settingsFile, PermissionSettings.class);
            settings = loaded == null ? new PermissionSettings() : loaded;
            settings.normalize();
        } catch (Exception e) {
            Path archived = JsonStorage.archiveBrokenFile(settingsFile);
            settings = new PermissionSettings();
            settings.normalize();
            SimpleServerUtilities.LOGGER.error("Failed to load permission settings. Broken file archived as: {}", archived, e);
        }
    }

    private void saveSettings() {
        if (settingsFile == null) {
            return;
        }
        settings.normalize();
        settingsRecordStore.queueJson(GSON, settingsFile, settings);
    }

    private void loadLegacyData() throws IOException {
        try {
            PermissionData loadedData = JsonStorage.read(GSON, legacySaveFile, PermissionData.class);
            data = loadedData == null ? createDefaultData() : loadedData;
        } catch (Exception e) {
            Path archived = JsonStorage.archiveBrokenFile(legacySaveFile);
            data = createDefaultData();
            SimpleServerUtilities.LOGGER.error("Failed to read legacy permission file. Using defaults. Broken file archived as: {}", archived, e);
        }
    }

    private void loadSplitData() throws IOException {
        data = new PermissionData();

        loadRanks();
        loadPlayers();
        loadDimensions();
        loadPlayerClaimContexts();
    }

    private void loadRanks() throws IOException {
        Path folder = StoragePaths.permissionRanks(rootFolder);

        for (Path file : JsonStorage.listJsonFiles(folder)) {
            try {
                PermissionRank rank = JsonStorage.read(GSON, file, PermissionRank.class);

                if (rank == null) {
                    continue;
                }

                data.getRanks().put(normalizeRankName(StoragePaths.fileBaseName(file)), rank);
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load permission rank file. Broken file archived as: {}", archived, e);
            }
        }
    }

    private void loadPlayers() throws IOException {
        Path folder = StoragePaths.permissionPlayers(rootFolder);

        for (Path file : JsonStorage.listJsonFiles(folder)) {
            try {
                PlayerPermissionData playerData = JsonStorage.read(GSON, file, PlayerPermissionData.class);

                if (playerData == null) {
                    continue;
                }

                String playerId = playerData.getUuid().isBlank()
                        ? StoragePaths.fileBaseName(file)
                        : playerData.getUuid();

                UUID uuid = UUID.fromString(playerId);
                playerData.setUuid(uuid);
                data.getPlayers().put(uuid.toString(), playerData);
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load player permission file. Broken file archived as: {}", archived, e);
            }
        }
    }

    private void loadDimensions() throws IOException {
        Path folder = StoragePaths.permissionDimensions(rootFolder);

        for (Path file : JsonStorage.listJsonFiles(folder)) {
            try {
                ScopeSaveData scopeData = JsonStorage.read(GSON, file, ScopeSaveData.class);

                if (scopeData == null) {
                    continue;
                }

                String dimensionId = scopeData.id == null || scopeData.id.isBlank()
                        ? StoragePaths.fileBaseName(file)
                        : scopeData.id;

                data.getDimensions().put(dimensionId, new PermissionScope(scopeData.permissions));
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load dimension permission file. Broken file archived as: {}", archived, e);
            }
        }
    }

    private void loadPlayerClaimContexts() throws IOException {
        Path folder = StoragePaths.permissionClaimContext(rootFolder);

        for (Path file : JsonStorage.listJsonFiles(folder)) {
            try {
                ScopeSaveData scopeData = JsonStorage.read(GSON, file, ScopeSaveData.class);

                if (scopeData == null) {
                    continue;
                }

                String roleName = scopeData.id == null || scopeData.id.isBlank()
                        ? StoragePaths.fileBaseName(file)
                        : scopeData.id;

                data.getPlayerClaimContext().put(normalizeRoleName(roleName), new PermissionScope(scopeData.permissions));
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load player claim context permission file. Broken file archived as: {}", archived, e);
            }
        }
    }

    private void saveSplitData() throws IOException {
        saveSettings();
        saveRanks();
        savePlayers();
        saveDimensions();
        savePlayerClaimContexts();
    }

    private void saveRanks() throws IOException {
        Path folder = StoragePaths.permissionRanks(rootFolder);
        Files.createDirectories(folder);
        Set<Path> keptFiles = new HashSet<>();

        for (Map.Entry<String, PermissionRank> entry : data.getRanks().entrySet()) {
            Path file = StoragePaths.jsonFile(folder, normalizeRankName(entry.getKey()));
            rankRecordStore.queueJson(GSON, file, entry.getValue());
            keptFiles.add(file);
        }

        rankRecordStore.queueDeleteMissing(keptFiles);
    }

    private void savePlayers() throws IOException {
        Path folder = StoragePaths.permissionPlayers(rootFolder);
        Files.createDirectories(folder);
        Set<Path> keptFiles = new HashSet<>();

        for (Map.Entry<String, PlayerPermissionData> entry : data.getPlayers().entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                PlayerPermissionData playerData = entry.getValue();
                playerData.setUuid(uuid);

                Path file = StoragePaths.jsonFile(folder, uuid.toString());
                playerRecordStore.queueJson(GSON, file, playerData);
                keptFiles.add(file);
            } catch (IllegalArgumentException e) {
                SimpleServerUtilities.LOGGER.warn("Skipping player permission entry with invalid UUID: {}", entry.getKey());
            }
        }

        playerRecordStore.queueDeleteMissing(keptFiles);
    }

    private void saveDimensions() throws IOException {
        Path folder = StoragePaths.permissionDimensions(rootFolder);
        Files.createDirectories(folder);
        Set<Path> keptFiles = new HashSet<>();

        for (Map.Entry<String, PermissionScope> entry : data.getDimensions().entrySet()) {
            ScopeSaveData scopeData = new ScopeSaveData(entry.getKey(), entry.getValue().getPermissions());
            Path file = StoragePaths.jsonFile(folder, entry.getKey());
            dimensionRecordStore.queueJson(GSON, file, scopeData);
            keptFiles.add(file);
        }

        dimensionRecordStore.queueDeleteMissing(keptFiles);
    }

    private void savePlayerClaimContexts() throws IOException {
        Path folder = StoragePaths.permissionClaimContext(rootFolder);
        Files.createDirectories(folder);
        Set<Path> keptFiles = new HashSet<>();

        for (Map.Entry<String, PermissionScope> entry : data.getPlayerClaimContext().entrySet()) {
            String roleName = normalizeRoleName(entry.getKey());
            ScopeSaveData scopeData = new ScopeSaveData(roleName, entry.getValue().getPermissions());
            Path file = StoragePaths.jsonFile(folder, roleName);
            claimContextRecordStore.queueJson(GSON, file, scopeData);
            keptFiles.add(file);
        }

        claimContextRecordStore.queueDeleteMissing(keptFiles);
    }

    private String getRegionValue(String key, PermissionContext context) {
        if (context == null || context.getRegion() == null) {
            return null;
        }

        return PermissionValueResolver.getValue(context.getRegion().getPermissionOverrides(), key);
    }

    private String getPlayerClaimContextValue(String key, PermissionContext context) {
        if (context == null || context.getClaimRole() == null) {
            return null;
        }

        PermissionScope scope = data.getPlayerClaimContext().get(normalizeRoleName(context.getClaimRole().name()));

        if (scope == null) {
            return null;
        }

        return scope.getPermission(key);
    }

    private String getDimensionValue(String key, PermissionContext context) {
        if (context == null || context.getDimension() == null) {
            return null;
        }

        PermissionScope scope = data.getDimensions().get(context.getDimension());

        if (scope == null) {
            return null;
        }

        return scope.getPermission(key);
    }

    private String getPlayerValue(ServerPlayer player, String key) {
        if (player == null) {
            return null;
        }

        PlayerPermissionData playerData = getPlayerData(player.getUUID());

        if (playerData == null) {
            return null;
        }

        return PermissionValueResolver.getValue(playerData.getPermissions(), key);
    }

    private String getRankValue(ServerPlayer player, String key) {
        Map<String, String> mergedPermissions = getMergedRankPermissions(player);
        return PermissionValueResolver.getValue(mergedPermissions, key);
    }

    private Map<String, String> getMergedRankPermissions(ServerPlayer player) {
        ArrayList<String> rankNames = new ArrayList<>();

        if (player != null) {
            PlayerPermissionData playerData = getPlayerData(player.getUUID());

            if (playerData != null) {
                rankNames.addAll(playerData.getRanks());
            }
        }

        if (rankNames.isEmpty()) {
            rankNames.add(settings.getDefaultRank());
        }

        rankNames.sort(Comparator.comparingInt(rankName -> {
            PermissionRank rank = getRank(rankName);
            return rank == null ? 0 : rank.getPriority();
        }));

        Map<String, String> mergedPermissions = new HashMap<>();

        for (String rankName : rankNames) {
            mergedPermissions.putAll(getRankPermissionsWithInheritance(rankName, new HashSet<>()));
        }

        return mergedPermissions;
    }

    private Map<String, String> getRankPermissionsWithInheritance(String rankName, Set<String> visited) {
        String normalizedRank = normalizeRankName(rankName);

        if (!visited.add(normalizedRank)) {
            return Map.of();
        }

        PermissionRank rank = getRank(normalizedRank);

        if (rank == null) {
            return Map.of();
        }

        Map<String, String> mergedPermissions = new HashMap<>();

        for (String inheritedRankName : rank.getInherits()) {
            mergedPermissions.putAll(getRankPermissionsWithInheritance(inheritedRankName, visited));
        }

        mergedPermissions.putAll(rank.getPermissions());
        return mergedPermissions;
    }

    private boolean ensureDefaultDataUpToDate() {
        boolean changed = false;

        settings.normalize();
        String defaultRankName = settings.getDefaultRank();
        PermissionRank defaultRank = data.getRanks().get(defaultRankName);

        if (defaultRank == null) {
            data.getRanks().put(defaultRankName, createDefaultRank());
            changed = true;
        } else {
            changed |= fillDefaultRank(defaultRank);
        }

        PermissionRank adminRank = data.getRanks().get("admin");

        if (adminRank == null) {
            data.getRanks().put("admin", createAdminRank());
            changed = true;
        } else {
            changed |= setDefaultPermission(adminRank, "ssu.*", true);
        }

        changed |= ensurePlayerClaimContextScope("owner");
        changed |= ensurePlayerClaimContextScope("co_owner");
        changed |= ensurePlayerClaimContextScope("member");
        changed |= ensurePlayerClaimContextScope("visitor");
        changed |= ensurePlayerClaimContextScope("none");

        return changed;
    }

    private boolean ensurePlayerClaimContextScope(String roleName) {
        if (data.getPlayerClaimContext().containsKey(roleName)) {
            return false;
        }

        data.getPlayerClaimContext().put(roleName, new PermissionScope());
        return true;
    }

    private boolean fillDefaultRank(PermissionRank rank) {
        boolean changed = false;

        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_USE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_SET, true);
        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_DELETE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_TELEPORT, true);
        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_MAX, Config.MAX_PLAYER_HOMES.get());

        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_USE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_TELEPORT, true);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_ADMIN, false);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_SET, false);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_DELETE, false);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_INFO, false);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_MAX, Config.MAX_WARPS.get());

        changed |= setDefaultPermission(rank, PermissionKeys.SPAWN_USE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.SPAWN_ADMIN, false);
        changed |= setDefaultPermission(rank, PermissionKeys.SPAWN_REGION_BYPASS, false);

        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_USE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_CREATE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_DELETE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_TRUST, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_FLAGS, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_MAP, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_VISUALIZE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_TELEPORT, true);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_ADMIN_BYPASS, false);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_MAX_CHUNKS, Config.MAX_PLAYER_CLAIM_CHUNKS.get());
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_MAX_GROUPS, Config.MAX_PLAYER_CLAIM_GROUPS.get());
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_MAX_CHUNKS_PER_GROUP, Config.MAX_PLAYER_CLAIM_CHUNKS_PER_GROUP.get());

        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_USE, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_CREATE, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_DELETE, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_EDIT, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_TELEPORT, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_RENT, true);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_RENT_ADMIN, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_SELECTION, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_VISUALIZE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_ADMIN, false);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_ADMIN_BYPASS, false);
        changed |= setDefaultPermission(rank, PermissionKeys.SSU_RELOAD, false);
        changed |= setDefaultPermission(rank, PermissionKeys.BORDER_CLAIMS_VIEW, true);
        changed |= setDefaultPermission(rank, PermissionKeys.BORDER_REGIONS_VIEW, true);
        changed |= setDefaultPermission(rank, PermissionKeys.VISUALIZATION_ADMIN, false);
        changed |= setDefaultPermission(rank, PermissionKeys.CORE_ADMIN, false);
        changed |= setDefaultPermission(rank, PermissionKeys.SETTINGS_USE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.ADMIN_MENU, false);
        changed |= setDefaultPermission(rank, PermissionKeys.MINIMAP_USE, true);

        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.SPAWN_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.SPAWN_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_ESCAPE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_REGION_BYPASS, false);
        // TELEPORT_REQUIRE_STILL intentionally has no stored default so legacy
        // TELEPORT_CANCEL_ON_MOVE overrides remain effective until explicitly migrated.
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_CANCEL_ON_MOVE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_DELAY_BYPASS, false);
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_COOLDOWN_BYPASS, false);

        changed |= setDefaultPermission(rank, PermissionKeys.PERMISSIONS_ADMIN, false);

        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_ACCESS, true);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_SEND, true);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_SEND_ITEMS, true);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_SEND_MONEY, true);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_MAX_ATTACHMENTS, 1);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_INBOX_SOFT_CAP, 20);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_SENT_LIMIT, 20);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_DAILY_SEND_LIMIT, 20);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_SEND_COOLDOWN, 5);
        changed |= setDefaultPermission(rank, PermissionKeys.MAIL_ADMIN, false);

        return changed;
    }

    private boolean setDefaultPermission(PermissionRank rank, String key, Object value) {
        if (rank.getPermissions().containsKey(key)) {
            return false;
        }

        rank.getPermissions().put(key, String.valueOf(value));
        return true;
    }

    private PermissionData createDefaultData() {
        PermissionData defaultData = new PermissionData();
        defaultData.getRanks().put(settings.getDefaultRank(), createDefaultRank());
        defaultData.getRanks().put("admin", createAdminRank());

        defaultData.getPlayerClaimContext().put("owner", new PermissionScope());
        defaultData.getPlayerClaimContext().put("co_owner", new PermissionScope());
        defaultData.getPlayerClaimContext().put("member", new PermissionScope());
        defaultData.getPlayerClaimContext().put("visitor", new PermissionScope());
        defaultData.getPlayerClaimContext().put("none", new PermissionScope());

        return defaultData;
    }

    private PermissionRank createDefaultRank() {
        PermissionRank rank = new PermissionRank(0);
        fillDefaultRank(rank);
        return rank;
    }

    private PermissionRank createAdminRank() {
        PermissionRank rank = new PermissionRank(1000);
        rank.getPermissions().put("ssu.*", "true");
        return rank;
    }

    private String normalizeRankName(String rankName) {
        if (rankName == null || rankName.isBlank()) {
            return settings == null ? "default" : settings.getDefaultRank();
        }

        return rankName.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "none";
        }

        return roleName.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static class ScopeSaveData {
        private String id = "";
        private Map<String, String> permissions = new HashMap<>();

        public ScopeSaveData() {
            // Required for Gson
        }

        public ScopeSaveData(String id, Map<String, String> permissions) {
            this.id = id == null ? "" : id;
            this.permissions = permissions == null ? new HashMap<>() : permissions;
        }
    }
}
