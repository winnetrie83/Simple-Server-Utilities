package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapChunk;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

public class PlayerClaimManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, PlayerClaim> claims = new HashMap<>();
    private final Map<String, UUID> chunkIndex = new HashMap<>();
    private final Map<UUID, PlayerClaimLimits> limits = new HashMap<>();

    private Path saveFile;

    public void load(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("simpleserverutilities");
        this.saveFile = folder.resolve("player_claims.json");

        claims.clear();
        chunkIndex.clear();
        limits.clear();

        try {
            Files.createDirectories(folder);

            if (!Files.exists(saveFile)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(saveFile)) {
                ClaimSaveData data = GSON.fromJson(reader, ClaimSaveData.class);

                if (data == null) {
                    return;
                }

                if (data.claims != null) {
                    for (PlayerClaim claim : data.claims) {
                        claims.put(claim.getId(), claim);
                    }
                }

                if (data.limits != null) {
                    for (PlayerClaimLimits limit : data.limits) {
                        limits.put(limit.getPlayer(), limit);
                    }
                }

                rebuildChunkIndex();
            }

            SimpleServerUtilities.LOGGER.info("Loaded {} claim groups.", claims.size());
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed to load player claim groups.", e);
        }
    }

    public void save() {
        if (saveFile == null) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(saveFile)) {
            ClaimSaveData data = new ClaimSaveData();
            data.claims = new ArrayList<>(claims.values());
            data.limits = new ArrayList<>(limits.values());

            GSON.toJson(data, writer);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save player claim groups.", e);
        }
    }

    public boolean createClaimGroup(Level level, String name, UUID owner) {
        return createClaimGroupResult(level, name, owner).isSuccess();
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
        return getLimits(player).getMaxChunks();
    }

    public void setMaxChunks(UUID player, int amount) {
        getLimits(player).setMaxChunks(amount);
        save();
    }

    public void addMaxChunks(UUID player, int amount) {
        getLimits(player).addMaxChunks(amount);
        save();
    }

    public int getMaxClaimGroups(UUID player) {
        return getLimits(player).getMaxClaimGroups();
    }

    public void setMaxClaimGroups(UUID player, int amount) {
        getLimits(player).setMaxClaimGroups(amount);
        save();
    }

    public void addMaxClaimGroups(UUID player, int amount) {
        getLimits(player).addMaxClaimGroups(amount);
        save();
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
        return name.toLowerCase();
    }

    private static class ClaimSaveData {
        private ArrayList<PlayerClaim> claims = new ArrayList<>();
        private ArrayList<PlayerClaimLimits> limits = new ArrayList<>();
    }

    public ClaimMapData getMapData(ServerPlayer player, int radius, String selectedClaimGroupName) {
        int safeRadius = Math.max(1, Math.min(radius, 16));

        ChunkPos center = player.chunkPosition();
        ClaimMapData data = new ClaimMapData(
                center.x(),
                center.z(),
                safeRadius,
                selectedClaimGroupName
        );

        PlayerClaim selectedClaim = getClaimGroup(player.getUUID(), selectedClaimGroupName);

        for (int dz = -safeRadius; dz <= safeRadius; dz++) {
            for (int dx = -safeRadius; dx <= safeRadius; dx++) {
                int chunkX = center.x() + dx;
                int chunkZ = center.z() + dz;

                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

                int minX = chunkPos.getMinBlockX();
                int maxX = chunkPos.getMaxBlockX();
                int minZ = chunkPos.getMinBlockZ();
                int maxZ = chunkPos.getMaxBlockZ();

                boolean overlapsRegion = SimpleServerUtilities.REGIONS.overlaps2D(
                        player.level().dimension(),
                        minX,
                        minZ,
                        maxX,
                        maxZ
                );

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

                boolean currentChunk = chunkX == center.x() && chunkZ == center.z();

                boolean canClaim = canClaimFromMap(
                        player,
                        selectedClaim,
                        chunkPos,
                        status
                );

                boolean canUnclaim = claim != null && claim.isOwner(player.getUUID());

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

        if (countClaimChunks(player.getUUID()) >= getMaxChunks(player.getUUID())) {
            return false;
        }

        int maxChunksPerGroup = Config.MAX_PLAYER_CLAIM_CHUNKS_PER_GROUP.get();

        if (maxChunksPerGroup > 0 && selectedClaim.getChunkCount() >= maxChunksPerGroup) {
            return false;
        }

        if (selectedClaim.getChunkCount() == 0) {
            return true;
        }

        return selectedClaim.hasAdjacentChunk(chunkPos.x(), chunkPos.z());
    }
}