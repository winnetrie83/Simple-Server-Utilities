package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapBatchResult;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapChunk;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapOperation;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimShapeValidator;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapData;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.policy.ClaimPolicy;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class PlayerClaimManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, PlayerClaim> claims = new HashMap<>();
    private final Map<String, UUID> chunkIndex = new HashMap<>();
    private final Map<UUID, PlayerClaimLimits> limits = new HashMap<>();
    private final DirtyJsonRecordStore claimRecordStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore limitRecordStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore indexRecordStore = new DirtyJsonRecordStore();

    private Path rootFolder;
    private Path claimsFolder;
    private Path limitsFolder;
    private Path legacySaveFile;

    public void load(MinecraftServer server) {
        this.rootFolder = StoragePaths.root(server);
        this.claimsFolder = StoragePaths.playerClaimEntries(rootFolder);
        this.limitsFolder = StoragePaths.playerClaimLimits(rootFolder);
        this.legacySaveFile = rootFolder.resolve("player_claims.json");

        claims.clear();
        chunkIndex.clear();
        limits.clear();
        claimRecordStore.reset();
        limitRecordStore.reset();
        indexRecordStore.reset();

        try {
            Files.createDirectories(rootFolder);
            claimRecordStore.discover(claimsFolder);
            limitRecordStore.discover(limitsFolder);

            if (JsonStorage.hasJsonFiles(claimsFolder) || JsonStorage.hasJsonFiles(limitsFolder)) {
                loadSplitClaims();
            } else if (Files.exists(legacySaveFile)) {
                loadLegacyClaims();
                save();
                if (SimpleServerUtilities.STORAGE.flush(java.time.Duration.ofSeconds(10))) {
                    Path archived = JsonStorage.archiveLegacyFile(legacySaveFile);
                    if (archived != null) {
                        SimpleServerUtilities.LOGGER.info(
                                "Migrated legacy player claims to split storage. Legacy file archived as: {}",
                                archived
                        );
                    }
                } else {
                    SimpleServerUtilities.LOGGER.error(
                            "Player claim migration writes did not flush successfully; the legacy file was kept in place."
                    );
                }
            } else {
                Files.createDirectories(claimsFolder);
                Files.createDirectories(limitsFolder);
                save();
            }

            rebuildChunkIndex();
            SimpleServerUtilities.LOGGER.info("Loaded {} claim groups and {} player claim limit overrides.", claims.size(), limits.size());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load player claim groups.", e);
        }
    }

    public void save() {
        if (rootFolder == null) {
            return;
        }

        try {
            saveSplitClaims();
            SimpleServerUtilities.BORDER_VISUALIZATIONS.markClaimsChanged();
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save player claim groups.", e);
        }
    }

    public boolean createClaimGroup(Level level, String name, UUID owner) {
        return createClaimGroupResult(level, name, owner).isSuccess();
    }

    public boolean createClaimGroup(ServerPlayer player, String name) {
        return createClaimGroupResult(player, name).isSuccess();
    }

    public ClaimOperationResult createClaimGroupResult(ServerPlayer player, String name) {
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.PLAYER_CLAIMS_DISABLED,
                    ""
            );
        }

        UUID owner = player.getUUID();

        if (getClaimGroup(owner, name) != null) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_ALREADY_EXISTS,
                    name
            );
        }

        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        int maxGroups = ClaimPolicy.getMaxClaimGroups(player, context);

        if (countClaimGroups(owner) >= maxGroups) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_LIMIT_REACHED,
                    "max groups: " + maxGroups
            );
        }

        long now = System.currentTimeMillis();

        PlayerClaim claim = new PlayerClaim(
                name,
                getDimensionId(player.level()),
                owner,
                now
        );

        claims.put(claim.getId(), claim);
        save();

        return ClaimOperationResult.success();
    }

    public ClaimOperationResult createClaimGroupResult(Level level, String name, UUID owner) {
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.PLAYER_CLAIMS_DISABLED,
                    ""
            );
        }

        if (getClaimGroup(owner, name) != null) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_ALREADY_EXISTS,
                    name
            );
        }

        if (countClaimGroups(owner) >= getMaxClaimGroups(owner)) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_LIMIT_REACHED,
                    "max groups: " + getMaxClaimGroups(owner)
            );
        }

        long now = System.currentTimeMillis();

        PlayerClaim claim = new PlayerClaim(
                name,
                getDimensionId(level),
                owner,
                now
        );

        claims.put(claim.getId(), claim);
        save();

        return ClaimOperationResult.success();
    }

    public boolean deleteClaimGroup(UUID owner, String name, boolean adminBypass) {
        PlayerClaim claim = getClaimGroup(owner, name);

        if (claim == null) {
            return false;
        }

        if (!adminBypass && !claim.isOwner(owner)) {
            return false;
        }

        for (ClaimChunk chunk : claim.getChunks()) {
            chunkIndex.remove(createKey(claim.getDimension(), chunk.getX(), chunk.getZ()));
        }

        claims.remove(claim.getId());
        save();
        return true;
    }

    public boolean claimChunk(Level level, ChunkPos chunkPos, UUID owner, String claimName) {
        return claimChunkResult(level, chunkPos, owner, claimName).isSuccess();
    }

    public boolean claimChunk(ServerPlayer player, ChunkPos chunkPos, String claimName) {
        return claimChunkResult(player, chunkPos, claimName).isSuccess();
    }

    public ClaimOperationResult claimChunkResult(ServerPlayer player, ChunkPos chunkPos, String claimName) {
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.PLAYER_CLAIMS_DISABLED,
                    ""
            );
        }

        Level level = player.level();
        UUID owner = player.getUUID();
        PlayerClaim claim = getClaimGroup(owner, claimName);

        if (claim == null) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_NOT_FOUND,
                    claimName
            );
        }

        if (!claim.getDimension().equals(getDimensionId(level))) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.WRONG_DIMENSION,
                    "claim dimension: " + claim.getDimension() + ", current dimension: " + getDimensionId(level)
            );
        }

        String key = createKey(level, chunkPos);

        if (chunkIndex.containsKey(key)) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_ALREADY_CLAIMED,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        if (claim.getChunkCount() > 0 && !claim.hasAdjacentChunk(chunkPos.x(), chunkPos.z())) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_NOT_ADJACENT,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        int maxChunks = ClaimPolicy.getMaxClaimChunks(player, context);

        if (countClaimChunks(owner) >= maxChunks) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_LIMIT_REACHED,
                    "max chunks: " + maxChunks
            );
        }

        int maxChunksPerGroup = ClaimPolicy.getMaxChunksPerClaim(player, context);

        if (maxChunksPerGroup > 0 && claim.getChunkCount() >= maxChunksPerGroup) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_CHUNK_LIMIT_REACHED,
                    "max chunks in this claim: " + maxChunksPerGroup
            );
        }

        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();

        if (SimpleServerUtilities.REGIONS.overlaps2D(
                level.dimension(),
                minX,
                minZ,
                maxX,
                maxZ
        )) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_OVERLAPS_REGION,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        long now = System.currentTimeMillis();

        if (!claim.addChunk(chunkPos.x(), chunkPos.z(), now)) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_ALREADY_CLAIMED,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        chunkIndex.put(key, claim.getId());
        save();

        return ClaimOperationResult.success();
    }

    public ClaimOperationResult claimChunkResult(Level level, ChunkPos chunkPos, UUID owner, String claimName) {
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.PLAYER_CLAIMS_DISABLED,
                    ""
            );
        }

        PlayerClaim claim = getClaimGroup(owner, claimName);

        if (claim == null) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_NOT_FOUND,
                    claimName
            );
        }

        if (!claim.getDimension().equals(getDimensionId(level))) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.WRONG_DIMENSION,
                    "claim dimension: " + claim.getDimension() + ", current dimension: " + getDimensionId(level)
            );
        }

        String key = createKey(level, chunkPos);

        if (chunkIndex.containsKey(key)) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_ALREADY_CLAIMED,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        if (claim.getChunkCount() > 0 && !claim.hasAdjacentChunk(chunkPos.x(), chunkPos.z())) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_NOT_ADJACENT,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        if (countClaimChunks(owner) >= getMaxChunks(owner)) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_LIMIT_REACHED,
                    "max chunks: " + getMaxChunks(owner)
            );
        }

        int maxChunksPerGroup = Config.MAX_PLAYER_CLAIM_CHUNKS_PER_GROUP.get();

        if (maxChunksPerGroup > 0 && claim.getChunkCount() >= maxChunksPerGroup) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CLAIM_GROUP_CHUNK_LIMIT_REACHED,
                    "max chunks in this claim: " + maxChunksPerGroup
            );
        }

        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();

        if (SimpleServerUtilities.REGIONS.overlaps2D(
                level.dimension(),
                minX,
                minZ,
                maxX,
                maxZ
        )) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_OVERLAPS_REGION,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        long now = System.currentTimeMillis();

        if (!claim.addChunk(chunkPos.x(), chunkPos.z(), now)) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_ALREADY_CLAIMED,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        chunkIndex.put(key, claim.getId());
        save();

        return ClaimOperationResult.success();
    }

    public ClaimMapBatchResult applyMapOperation(
            ServerPlayer player,
            ClaimMapOperation operation,
            String rawClaimName,
            Collection<ChunkPos> requestedChunks
    ) {
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                    ClaimOperationResult.Type.PLAYER_CLAIMS_DISABLED,
                    ""
            ));
        }

        if (!ClaimPolicy.canUseMap(player)) {
            return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                    ClaimOperationResult.Type.INVALID_SELECTION,
                    "You do not have permission to use the interactive claim map."
            ));
        }

        String claimName = rawClaimName == null ? "" : rawClaimName.trim();
        if (!isValidClaimName(claimName)) {
            return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                    ClaimOperationResult.Type.INVALID_CLAIM_NAME,
                    "Use 1-32 letters, numbers, underscores or hyphens."
            ));
        }

        LinkedHashSet<ChunkPos> chunks = new LinkedHashSet<>();
        if (requestedChunks != null) {
            chunks.addAll(requestedChunks);
        }

        if (chunks.isEmpty()) {
            return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                    ClaimOperationResult.Type.EMPTY_SELECTION,
                    "Select at least one chunk."
            ));
        }

        if (chunks.size() > 256) {
            return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                    ClaimOperationResult.Type.INVALID_SELECTION,
                    "A single map operation can change at most 256 chunks."
            ));
        }

        UUID owner = player.getUUID();
        String dimension = getDimensionId(player.level());
        PlayerClaim claim = getClaimGroup(owner, claimName);

        if (operation == ClaimMapOperation.CREATE) {
            if (claim != null) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.CLAIM_GROUP_ALREADY_EXISTS,
                        claimName
                ));
            }
        } else {
            if (claim == null) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.CLAIM_GROUP_NOT_FOUND,
                        claimName
                ));
            }
            if (!claim.getDimension().equals(dimension)) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.WRONG_DIMENSION,
                        "claim dimension: " + claim.getDimension() + ", current dimension: " + dimension
                ));
            }
            if (!claim.isOwner(owner)) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.NOT_OWNER,
                        claim.getDisplayName()
                ));
            }
        }

        if (operation == ClaimMapOperation.CREATE || operation == ClaimMapOperation.ADD) {
            int maxChunks = Integer.MAX_VALUE;
            int maxGroups = Integer.MAX_VALUE;
            int maxPerClaim = 0;

            for (ChunkPos chunkPos : chunks) {
                PermissionContext targetContext = PermissionContext.at(player, mapChunkCenter(player, chunkPos));
                if (!ClaimPolicy.canCreateClaim(player, targetContext)) {
                    return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                            ClaimOperationResult.Type.INVALID_SELECTION,
                            "You do not have permission to claim chunk " + chunkPos.x() + ", " + chunkPos.z() + "."
                    ));
                }

                maxChunks = Math.min(maxChunks, ClaimPolicy.getMaxClaimChunks(player, targetContext));
                maxGroups = Math.min(maxGroups, ClaimPolicy.getMaxClaimGroups(player, targetContext));
                maxPerClaim = mostRestrictiveOptionalLimit(
                        maxPerClaim,
                        ClaimPolicy.getMaxChunksPerClaim(player, targetContext)
                );

                if (chunkIndex.containsKey(createKey(player.level(), chunkPos))) {
                    return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                            ClaimOperationResult.Type.CHUNK_ALREADY_CLAIMED,
                            "chunk " + chunkPos.x() + ", " + chunkPos.z()
                    ));
                }
                if (overlapsRegion(player.level(), chunkPos)) {
                    return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                            ClaimOperationResult.Type.CHUNK_OVERLAPS_REGION,
                            "chunk " + chunkPos.x() + ", " + chunkPos.z()
                    ));
                }
            }

            if (operation == ClaimMapOperation.CREATE && countClaimGroups(owner) >= maxGroups) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.CLAIM_GROUP_LIMIT_REACHED,
                        "max groups: " + maxGroups
                ));
            }

            if (countClaimChunks(owner) + chunks.size() > maxChunks) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.CHUNK_LIMIT_REACHED,
                        "max chunks: " + maxChunks
                ));
            }

            int existingSize = claim == null ? 0 : claim.getChunkCount();
            if (maxPerClaim > 0 && existingSize + chunks.size() > maxPerClaim) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.CLAIM_GROUP_CHUNK_LIMIT_REACHED,
                        "max chunks in this claim: " + maxPerClaim
                ));
            }

            Set<ClaimChunk> finalShape = claim == null
                    ? new HashSet<>()
                    : new HashSet<>(claim.getChunks());
            for (ChunkPos chunkPos : chunks) {
                finalShape.add(new ClaimChunk(chunkPos.x(), chunkPos.z()));
            }
            if (!ClaimShapeValidator.isConnected(finalShape)) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.CHUNK_NOT_ADJACENT,
                        "The selected chunks must form one connected claim."
                ));
            }

            long now = System.currentTimeMillis();
            if (operation == ClaimMapOperation.CREATE) {
                claim = new PlayerClaim(claimName, dimension, owner, now);
                claims.put(claim.getId(), claim);
            }
            for (ChunkPos chunkPos : chunks) {
                claim.addChunk(chunkPos.x(), chunkPos.z(), now);
                chunkIndex.put(createKey(dimension, chunkPos.x(), chunkPos.z()), claim.getId());
            }
            save();
            return ClaimMapBatchResult.success(chunks.size());
        }

        for (ChunkPos chunkPos : chunks) {
            UUID indexedClaim = chunkIndex.get(createKey(player.level(), chunkPos));
            if (indexedClaim == null || !indexedClaim.equals(claim.getId())) {
                return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                        ClaimOperationResult.Type.CHUNK_NOT_CLAIMED,
                        "chunk " + chunkPos.x() + ", " + chunkPos.z() + " is not part of " + claimName
                ));
            }
        }

        Set<ClaimChunk> remaining = new HashSet<>(claim.getChunks());
        for (ChunkPos chunkPos : chunks) {
            remaining.remove(new ClaimChunk(chunkPos.x(), chunkPos.z()));
        }
        if (!ClaimShapeValidator.isConnected(remaining)) {
            return ClaimMapBatchResult.failure(ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_REMOVAL_DISCONNECTS_CLAIM,
                    "The remaining chunks would no longer be connected."
            ));
        }

        long now = System.currentTimeMillis();
        for (ChunkPos chunkPos : chunks) {
            claim.removeChunk(chunkPos.x(), chunkPos.z(), now);
            chunkIndex.remove(createKey(dimension, chunkPos.x(), chunkPos.z()));
        }
        save();
        return ClaimMapBatchResult.success(chunks.size());
    }

    private BlockPos mapChunkCenter(ServerPlayer player, ChunkPos chunkPos) {
        return new BlockPos(
                chunkPos.getMinBlockX() + 8,
                player.blockPosition().getY(),
                chunkPos.getMinBlockZ() + 8
        );
    }

    private int mostRestrictiveOptionalLimit(int current, int candidate) {
        if (candidate <= 0) {
            return current;
        }
        if (current <= 0) {
            return candidate;
        }
        return Math.min(current, candidate);
    }

    private boolean overlapsRegion(Level level, ChunkPos chunkPos) {
        return SimpleServerUtilities.REGIONS.overlaps2D(
                level.dimension(),
                chunkPos.getMinBlockX(),
                chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(),
                chunkPos.getMaxBlockZ()
        );
    }

    private boolean isValidClaimName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    public boolean unclaim(Level level, ChunkPos chunkPos, UUID playerUuid, boolean adminBypass) {
        return unclaimResult(level, chunkPos, playerUuid, adminBypass).isSuccess();
    }

    public ClaimOperationResult unclaimResult(Level level, ChunkPos chunkPos, UUID playerUuid, boolean adminBypass) {
        String key = createKey(level, chunkPos);
        UUID claimId = chunkIndex.get(key);

        if (claimId == null) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_NOT_CLAIMED,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        PlayerClaim claim = claims.get(claimId);

        if (claim == null) {
            chunkIndex.remove(key);

            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_NOT_CLAIMED,
                    "claim group missing for chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        if (!adminBypass && !claim.isOwner(playerUuid)) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.NOT_OWNER,
                    claim.getDisplayName()
            );
        }

        if (wouldDisconnectClaim(claim, chunkPos.x(), chunkPos.z())) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_REMOVAL_DISCONNECTS_CLAIM,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        if (!claim.removeChunk(chunkPos.x(), chunkPos.z(), System.currentTimeMillis())) {
            return ClaimOperationResult.fail(
                    ClaimOperationResult.Type.CHUNK_NOT_CLAIMED,
                    "chunk " + chunkPos.x() + ", " + chunkPos.z()
            );
        }

        chunkIndex.remove(key);
        save();

        return ClaimOperationResult.success();
    }

    public PlayerClaim getClaim(Level level, ChunkPos chunkPos) {
        UUID claimId = chunkIndex.get(createKey(level, chunkPos));

        if (claimId == null) {
            return null;
        }

        return claims.get(claimId);
    }

    public PlayerClaim getClaimGroup(UUID owner, String name) {
        String normalizedName = normalizeName(name);

        for (PlayerClaim claim : claims.values()) {
            if (!claim.isOwner(owner)) {
                continue;
            }

            if (normalizeName(claim.getName()).equals(normalizedName)) {
                return claim;
            }
        }

        return null;
    }

    public Collection<PlayerClaim> getClaims() {
        return claims.values();
    }

    public int countClaimGroups(UUID owner) {
        int count = 0;

        for (PlayerClaim claim : claims.values()) {
            if (claim.isOwner(owner)) {
                count++;
            }
        }

        return count;
    }

    public int countClaimChunks(UUID owner) {
        int count = 0;

        for (PlayerClaim claim : claims.values()) {
            if (claim.isOwner(owner)) {
                count += claim.getChunkCount();
            }
        }

        return count;
    }

    public int getMaxChunks(UUID player) {
        PlayerClaimLimits limit = limits.get(player);
        return limit != null && limit.hasMaxChunksOverride()
                ? limit.getMaxChunks()
                : Config.MAX_PLAYER_CLAIM_CHUNKS.get();
    }

    public void setMaxChunks(UUID player, int amount) {
        getLimits(player).setMaxChunks(amount);
        save();
    }

    public void addMaxChunks(UUID player, int amount) {
        getLimits(player).addMaxChunks(amount, Config.MAX_PLAYER_CLAIM_CHUNKS.get());
        save();
    }

    public void clearMaxChunksOverride(UUID player) {
        PlayerClaimLimits limit = limits.get(player);
        if (limit == null) {
            return;
        }

        limit.clearMaxChunksOverride(Config.MAX_PLAYER_CLAIM_CHUNKS.get());
        removeEmptyLimitRecord(player, limit);
        save();
    }

    public int getMaxClaimGroups(UUID player) {
        PlayerClaimLimits limit = limits.get(player);
        return limit != null && limit.hasMaxClaimGroupsOverride()
                ? limit.getMaxClaimGroups()
                : Config.MAX_PLAYER_CLAIM_GROUPS.get();
    }

    public void setMaxClaimGroups(UUID player, int amount) {
        getLimits(player).setMaxClaimGroups(amount);
        save();
    }

    public void addMaxClaimGroups(UUID player, int amount) {
        getLimits(player).addMaxClaimGroups(amount, Config.MAX_PLAYER_CLAIM_GROUPS.get());
        save();
    }

    public void clearMaxClaimGroupsOverride(UUID player) {
        PlayerClaimLimits limit = limits.get(player);
        if (limit == null) {
            return;
        }

        limit.clearMaxClaimGroupsOverride(Config.MAX_PLAYER_CLAIM_GROUPS.get());
        removeEmptyLimitRecord(player, limit);
        save();
    }

    public OptionalInt getMaxChunksOverride(UUID player) {
        PlayerClaimLimits limit = limits.get(player);
        return limit != null && limit.hasMaxChunksOverride()
                ? OptionalInt.of(limit.getMaxChunks())
                : OptionalInt.empty();
    }

    public OptionalInt getMaxClaimGroupsOverride(UUID player) {
        PlayerClaimLimits limit = limits.get(player);
        return limit != null && limit.hasMaxClaimGroupsOverride()
                ? OptionalInt.of(limit.getMaxClaimGroups())
                : OptionalInt.empty();
    }

    /** Snapshot used once by the permission migration in 1.3.0-dev1. */
    public Map<UUID, PlayerClaimLimits> getLegacyLimitOverridesSnapshot() {
        Map<UUID, PlayerClaimLimits> snapshot = new HashMap<>();
        for (Map.Entry<UUID, PlayerClaimLimits> entry : limits.entrySet()) {
            if (entry.getValue() != null && entry.getValue().hasAnyOverride()) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(snapshot);
    }

    /** Removes the old claim-specific override storage after successful migration. */
    public void clearLegacyLimitOverrides() {
        if (limits.isEmpty()) {
            return;
        }
        limits.clear();
        save();
    }

    private void loadSplitClaims() throws IOException {
        Files.createDirectories(claimsFolder);
        Files.createDirectories(limitsFolder);

        for (Path file : JsonStorage.listJsonFiles(claimsFolder)) {
            try {
                PlayerClaim claim = JsonStorage.read(GSON, file, PlayerClaim.class);

                if (claim == null || claim.getOwner() == null) {
                    continue;
                }

                claims.put(claim.getId(), claim);
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load player claim file. Broken file archived as: {}", archived, e);
            }
        }

        for (Path file : JsonStorage.listJsonFiles(limitsFolder)) {
            try {
                PlayerClaimLimits limit = JsonStorage.read(GSON, file, PlayerClaimLimits.class);

                if (limit == null || limit.getPlayer() == null) {
                    continue;
                }

                limit.migrateLegacyOverrideState(
                        Config.MAX_PLAYER_CLAIM_CHUNKS.get(),
                        Config.MAX_PLAYER_CLAIM_GROUPS.get()
                );
                if (limit.hasAnyOverride()) {
                    limits.put(limit.getPlayer(), limit);
                }
            } catch (Exception e) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load player claim limit file. Broken file archived as: {}", archived, e);
            }
        }
    }

    private void loadLegacyClaims() {
        try {
            ClaimSaveData data = JsonStorage.read(GSON, legacySaveFile, ClaimSaveData.class);

            if (data == null) {
                return;
            }

            if (data.claims != null) {
                for (PlayerClaim claim : data.claims) {
                    if (claim == null || claim.getOwner() == null) {
                        continue;
                    }

                    claims.put(claim.getId(), claim);
                }
            }

            if (data.limits != null) {
                for (PlayerClaimLimits limit : data.limits) {
                    if (limit == null || limit.getPlayer() == null) {
                        continue;
                    }

                    limit.migrateLegacyOverrideState(
                        Config.MAX_PLAYER_CLAIM_CHUNKS.get(),
                        Config.MAX_PLAYER_CLAIM_GROUPS.get()
                );
                if (limit.hasAnyOverride()) {
                    limits.put(limit.getPlayer(), limit);
                }
                }
            }
        } catch (Exception e) {
            Path archived = JsonStorage.archiveBrokenFile(legacySaveFile);
            SimpleServerUtilities.LOGGER.error("Failed to read legacy player claims file. Broken file archived as: {}", archived, e);
        }
    }

    private void saveSplitClaims() throws IOException {
        Files.createDirectories(claimsFolder);
        Files.createDirectories(limitsFolder);

        Set<Path> keptClaimFiles = new HashSet<>();

        for (PlayerClaim claim : claims.values()) {
            Path file = StoragePaths.jsonFile(claimsFolder, createClaimFileName(claim));
            claimRecordStore.queueJson(GSON, file, claim);
            keptClaimFiles.add(file);
        }

        claimRecordStore.queueDeleteMissing(keptClaimFiles);

        Set<Path> keptLimitFiles = new HashSet<>();

        for (PlayerClaimLimits limit : limits.values()) {
            if (limit.getPlayer() == null) {
                continue;
            }

            Path file = StoragePaths.jsonFile(limitsFolder, limit.getPlayer().toString());
            limitRecordStore.queueJson(GSON, file, limit);
            keptLimitFiles.add(file);
        }

        limitRecordStore.queueDeleteMissing(keptLimitFiles);
        savePlayerIndex();
    }

    private void savePlayerIndex() throws IOException {
        ClaimPlayerIndex index = new ClaimPlayerIndex();
        Map<UUID, ClaimPlayerIndexEntry> entriesByOwner = new HashMap<>();

        for (PlayerClaim claim : claims.values()) {
            ClaimPlayerIndexEntry entry = entriesByOwner.computeIfAbsent(claim.getOwner(), owner -> new ClaimPlayerIndexEntry(owner.toString()));
            entry.claims.add(new ClaimPlayerIndexClaim(
                    claim.getId().toString(),
                    claim.getDisplayName(),
                    claim.getDimension(),
                    claim.getChunkCount()
            ));
        }

        index.players.addAll(entriesByOwner.values());
        index.players.sort(Comparator.comparing((ClaimPlayerIndexEntry entry) -> entry.player));

        for (ClaimPlayerIndexEntry entry : index.players) {
            entry.claims.sort(Comparator.comparing((ClaimPlayerIndexClaim claim) -> claim.name, String::compareToIgnoreCase));
        }

        indexRecordStore.queueJson(
                GSON,
                rootFolder.resolve("player_claims").resolve("player_index.json"),
                index
        );
    }

    private String createClaimFileName(PlayerClaim claim) {
        String base = StoragePaths.sanitizeFileName(claim.getOwner() + "_" + claim.getDisplayName());

        if (base.length() > 80) {
            base = base.substring(0, 80);
        }

        return base + "_" + claim.getId();
    }

    private boolean wouldDisconnectClaim(PlayerClaim claim, int removedChunkX, int removedChunkZ) {
        Set<ClaimChunk> remainingChunks = new HashSet<>();

        for (ClaimChunk chunk : claim.getChunks()) {
            if (chunk.getX() == removedChunkX && chunk.getZ() == removedChunkZ) {
                continue;
            }

            remainingChunks.add(chunk);
        }

        if (remainingChunks.size() <= 1) {
            return false;
        }

        Set<ClaimChunk> visited = new HashSet<>();
        Queue<ClaimChunk> queue = new ArrayDeque<>();

        ClaimChunk start = remainingChunks.iterator().next();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            ClaimChunk current = queue.poll();

            addConnectedNeighbor(remainingChunks, visited, queue, current.getX() + 1, current.getZ());
            addConnectedNeighbor(remainingChunks, visited, queue, current.getX() - 1, current.getZ());
            addConnectedNeighbor(remainingChunks, visited, queue, current.getX(), current.getZ() + 1);
            addConnectedNeighbor(remainingChunks, visited, queue, current.getX(), current.getZ() - 1);
        }

        return visited.size() != remainingChunks.size();
    }

    private void addConnectedNeighbor(
            Set<ClaimChunk> remainingChunks,
            Set<ClaimChunk> visited,
            Queue<ClaimChunk> queue,
            int chunkX,
            int chunkZ
    ) {
        ClaimChunk neighbor = new ClaimChunk(chunkX, chunkZ);

        if (!remainingChunks.contains(neighbor)) {
            return;
        }

        if (!visited.add(neighbor)) {
            return;
        }

        queue.add(neighbor);
    }

    private void removeEmptyLimitRecord(UUID player, PlayerClaimLimits limit) {
        if (!limit.hasAnyOverride()) {
            limits.remove(player);
        }
    }

    private PlayerClaimLimits getLimits(UUID player) {
        return limits.computeIfAbsent(player, uuid -> new PlayerClaimLimits(
                uuid,
                Config.MAX_PLAYER_CLAIM_CHUNKS.get(),
                Config.MAX_PLAYER_CLAIM_GROUPS.get()
        ));
    }

    private void rebuildChunkIndex() {
        chunkIndex.clear();

        for (PlayerClaim claim : claims.values()) {
            for (ClaimChunk chunk : claim.getChunks()) {
                chunkIndex.put(
                        createKey(claim.getDimension(), chunk.getX(), chunk.getZ()),
                        claim.getId()
                );
            }
        }
    }

    private String createKey(Level level, ChunkPos chunkPos) {
        return createKey(getDimensionId(level), chunkPos.x(), chunkPos.z());
    }

    private String createKey(String dimension, int chunkX, int chunkZ) {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    private String getDimensionId(Level level) {
        return level.dimension().identifier().toString();
    }

    private String normalizeName(String name) {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private static class ClaimSaveData {
        private ArrayList<PlayerClaim> claims = new ArrayList<>();
        private ArrayList<PlayerClaimLimits> limits = new ArrayList<>();
    }

    private static class ClaimPlayerIndex {
        private int schemaVersion = 1;
        private ArrayList<ClaimPlayerIndexEntry> players = new ArrayList<>();
    }

    private static class ClaimPlayerIndexEntry {
        private String player;
        private ArrayList<ClaimPlayerIndexClaim> claims = new ArrayList<>();

        public ClaimPlayerIndexEntry(String player) {
            this.player = player;
        }
    }

    private static class ClaimPlayerIndexClaim {
        private String id;
        private String name;
        private String dimension;
        private int chunks;

        public ClaimPlayerIndexClaim(String id, String name, String dimension, int chunks) {
            this.id = id;
            this.name = name;
            this.dimension = dimension;
            this.chunks = chunks;
        }
    }

    public ClaimMapData getMapData(ServerPlayer player, int radius, String selectedClaimGroupName) {
        return getMapData(
                player,
                player.chunkPosition().x(),
                player.chunkPosition().z(),
                radius,
                selectedClaimGroupName,
                "",
                false
        );
    }

    public ClaimMapData getMapData(
            ServerPlayer player,
            int requestedCenterChunkX,
            int requestedCenterChunkZ,
            int radius,
            String selectedClaimGroupName,
            String notice,
            boolean error
    ) {
        int safeRadius = Math.max(2, Math.min(radius, 12));
        ChunkPos playerChunk = player.chunkPosition();
        int centerChunkX = clampMapCenter(requestedCenterChunkX, playerChunk.x());
        int centerChunkZ = clampMapCenter(requestedCenterChunkZ, playerChunk.z());

        List<String> ownedClaims = claims.values().stream()
                .filter(claim -> claim.isOwner(player.getUUID()))
                .filter(claim -> claim.getDimension().equals(getDimensionId(player.level())))
                .sorted(Comparator.comparing(PlayerClaim::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(PlayerClaim::getDisplayName)
                .limit(256)
                .toList();

        String selectedName = selectedClaimGroupName == null ? "" : selectedClaimGroupName;
        PlayerClaim selectedClaim = getClaimGroup(player.getUUID(), selectedName);
        if (selectedClaim == null || !selectedClaim.getDimension().equals(getDimensionId(player.level()))) {
            selectedClaim = null;
            selectedName = ownedClaims.isEmpty() ? "" : ownedClaims.getFirst();
            if (!selectedName.isEmpty()) {
                selectedClaim = getClaimGroup(player.getUUID(), selectedName);
            }
        }

        PermissionContext context = PermissionContext.at(
                player,
                new BlockPos(centerChunkX * 16 + 8, player.blockPosition().getY(), centerChunkZ * 16 + 8)
        );
        ClaimMapData data = new ClaimMapData(
                centerChunkX,
                centerChunkZ,
                safeRadius,
                selectedName,
                ownedClaims,
                countClaimChunks(player.getUUID()),
                ClaimPolicy.getMaxClaimChunks(player, context),
                countClaimGroups(player.getUUID()),
                ClaimPolicy.getMaxClaimGroups(player, context),
                selectedClaim == null ? 0 : selectedClaim.getChunkCount(),
                ClaimPolicy.getMaxChunksPerClaim(player, context),
                ClaimPolicy.canCreateClaim(player, context),
                notice,
                error
        );

        for (int dz = -safeRadius; dz <= safeRadius; dz++) {
            for (int dx = -safeRadius; dx <= safeRadius; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

                boolean overlapsRegion = overlapsRegion(player.level(), chunkPos);
                PlayerClaim claim = getClaim(player.level(), chunkPos);

                ClaimChunkStatus status;
                String claimName = "";
                UUID owner = null;

                if (overlapsRegion) {
                    status = ClaimChunkStatus.REGION;
                } else if (claim == null) {
                    status = ClaimChunkStatus.WILDERNESS;
                } else {
                    claimName = claim.getDisplayName();
                    owner = claim.getOwner();
                    if (claim.isOwner(player.getUUID())) {
                        status = ClaimChunkStatus.OWNED_BY_SELF;
                    } else if (claim.isTrusted(player.getUUID())) {
                        status = ClaimChunkStatus.OWNED_BY_TRUSTED;
                    } else {
                        status = ClaimChunkStatus.OWNED_BY_OTHER;
                    }
                }

                boolean currentChunk = chunkX == playerChunk.x() && chunkZ == playerChunk.z();
                boolean selectedClaimChunk = selectedClaim != null && selectedClaim.hasChunk(chunkX, chunkZ);
                boolean canClaim = canClaimFromMap(player, selectedClaim, chunkPos, status);
                boolean canUnclaim = selectedClaimChunk && selectedClaim.isOwner(player.getUUID());

                data.addChunk(new ClaimMapChunk(
                        chunkX,
                        chunkZ,
                        status,
                        claimName,
                        owner,
                        currentChunk,
                        canClaim,
                        canUnclaim
                ));
            }
        }

        return data;
    }

    private int clampMapCenter(int requested, int playerCenter) {
        return Math.max(playerCenter - 128, Math.min(playerCenter + 128, requested));
    }

    private boolean canClaimFromMap(ServerPlayer player, PlayerClaim selectedClaim, ChunkPos chunkPos, ClaimChunkStatus status) {
        if (selectedClaim == null) {
            return false;
        }

        if (status != ClaimChunkStatus.WILDERNESS) {
            return false;
        }

        if (!selectedClaim.getDimension().equals(getDimensionId(player.level()))) {
            return false;
        }

        PermissionContext context = PermissionContext.at(player, mapChunkCenter(player, chunkPos));

        if (!ClaimPolicy.canCreateClaim(player, context)) {
            return false;
        }

        if (countClaimChunks(player.getUUID()) >= ClaimPolicy.getMaxClaimChunks(player, context)) {
            return false;
        }

        int maxChunksPerGroup = ClaimPolicy.getMaxChunksPerClaim(player, context);

        if (maxChunksPerGroup > 0 && selectedClaim.getChunkCount() >= maxChunksPerGroup) {
            return false;
        }

        if (selectedClaim.getChunkCount() == 0) {
            return true;
        }

        return selectedClaim.hasAdjacentChunk(chunkPos.x(), chunkPos.z());
    }
}
