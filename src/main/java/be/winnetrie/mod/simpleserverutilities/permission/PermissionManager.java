package be.winnetrie.mod.simpleserverutilities.permission;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public class PermissionManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PermissionData data = new PermissionData();
    private Path saveFile;

    public void load(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("simpleserverutilities");
        this.saveFile = folder.resolve("permissions.json");

        try {
            Files.createDirectories(folder);

            if (!Files.exists(saveFile)) {
                data = createDefaultData();
                save();
                SimpleServerUtilities.LOGGER.info("Created default permission data.");
                return;
            }

            try (Reader reader = Files.newBufferedReader(saveFile)) {
                PermissionData loadedData = GSON.fromJson(reader, PermissionData.class);
                data = loadedData == null ? createDefaultData() : loadedData;
            }

            if (ensureDefaultDataUpToDate()) {
                save();
            }

            SimpleServerUtilities.LOGGER.info("Loaded permission data: {} ranks, {} player overrides.",
                    data.getRanks().size(),
                    data.getPlayers().size());
        } catch (Exception e) {
            data = createDefaultData();
            SimpleServerUtilities.LOGGER.error("Failed to load permission data. Using defaults.", e);
        }
    }

    public void save() {
        if (saveFile == null) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(saveFile)) {
            GSON.toJson(data, writer);
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
        return data.getPlayers().computeIfAbsent(playerId.toString(), ignored -> new PlayerPermissionData());
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
        changed |= setDefaultPermission(rank, PermissionKeys.REGIONS_ADMIN, false);

        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.HOMES_TELEPORT_COOLDOWN, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_TELEPORT_DELAY, 0);
        changed |= setDefaultPermission(rank, PermissionKeys.WARPS_TELEPORT_COOLDOWN, 0);
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

        return rankName.trim().toLowerCase();
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "none";
        }

        return roleName.trim().toLowerCase();
    }
}
