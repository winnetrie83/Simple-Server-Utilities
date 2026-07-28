package be.winnetrie.mod.simpleserverutilities.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class PermissionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PermissionData data = new PermissionData();
    private Path rootFolder;
    private Path permissionsFolder;
    private Path legacySaveFile;

    public void load(MinecraftServer server) {
        this.rootFolder = StoragePaths.root(server);
        this.permissionsFolder = StoragePaths.permissions(rootFolder);
        this.legacySaveFile = rootFolder.resolve("permissions.json");

        try {
            Files.createDirectories(rootFolder);
            data = new PermissionData();

            if (JsonStorage.hasJsonFiles(permissionsFolder)) {
                loadSplitData();
            } else if (Files.exists(legacySaveFile)) {
                loadLegacyData();
                save();
                Path archived = JsonStorage.archiveLegacyFile(legacySaveFile);

                if (archived != null) {
                    SimpleServerUtilities.LOGGER.info("Migrated legacy permission data to split storage. Legacy file archived as: {}", archived);
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

        String playerValue = getPlayerValue(player, key);
        if (playerValue != null) {
            return playerValue;
        }

        return getRankValue(player, key);
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
            JsonStorage.write(GSON, file, entry.getValue());
            keptFiles.add(file);
        }

        JsonStorage.deleteStaleJsonFiles(folder, keptFiles);
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
                JsonStorage.write(GSON, file, playerData);
                keptFiles.add(file);
            } catch (IllegalArgumentException e) {
                SimpleServerUtilities.LOGGER.warn("Skipping player permission entry with invalid UUID: {}", entry.getKey());
            }
        }

        JsonStorage.deleteStaleJsonFiles(folder, keptFiles);
    }

    private void saveDimensions() throws IOException {
        Path folder = StoragePaths.permissionDimensions(rootFolder);
        Files.createDirectories(folder);
        Set<Path> keptFiles = new HashSet<>();

        for (Map.Entry<String, PermissionScope> entry : data.getDimensions().entrySet()) {
            ScopeSaveData scopeData = new ScopeSaveData(entry.getKey(), entry.getValue().getPermissions());
            Path file = StoragePaths.jsonFile(folder, entry.getKey());
            JsonStorage.write(GSON, file, scopeData);
            keptFiles.add(file);
        }

        JsonStorage.deleteStaleJsonFiles(folder, keptFiles);
    }

    private void savePlayerClaimContexts() throws IOException {
        Path folder = StoragePaths.permissionClaimContext(rootFolder);
        Files.createDirectories(folder);
        Set<Path> keptFiles = new HashSet<>();

        for (Map.Entry<String, PermissionScope> entry : data.getPlayerClaimContext().entrySet()) {
            String roleName = normalizeRoleName(entry.getKey());
            ScopeSaveData scopeData = new ScopeSaveData(roleName, entry.getValue().getPermissions());
            Path file = StoragePaths.jsonFile(folder, roleName);
            JsonStorage.write(GSON, file, scopeData);
            keptFiles.add(file);
        }

        JsonStorage.deleteStaleJsonFiles(folder, keptFiles);
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
            rankNames.add("default");
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

        PermissionRank defaultRank = data.getRanks().get("default");

        if (defaultRank == null) {
            data.getRanks().put("default", createDefaultRank());
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

        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.CLAIMS_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_CANCEL_ON_MOVE, true);
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_DELAY_BYPASS, false);
        changed |= setDefaultPermission(rank, PermissionKeys.TELEPORT_COOLDOWN_BYPASS, false);

        changed |= setDefaultPermission(rank, PermissionKeys.PERMISSIONS_ADMIN, false);

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
        defaultData.getRanks().put("default", createDefaultRank());
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
            return "default";
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
