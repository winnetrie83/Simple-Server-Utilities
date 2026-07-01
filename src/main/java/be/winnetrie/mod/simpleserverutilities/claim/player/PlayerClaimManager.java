package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;
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
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return false;
        }

        if (getClaimGroup(owner, name) != null) {
            return false;
        }

        if (countClaimGroups(owner) >= getMaxClaimGroups(owner)) {
            return false;
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
        return true;
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
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return false;
        }

        PlayerClaim claim = getClaimGroup(owner, claimName);

        if (claim == null) {
            return false;
        }

        if (!claim.getDimension().equals(getDimensionId(level))) {
            return false;
        }

        String key = createKey(level, chunkPos);

        if (chunkIndex.containsKey(key)) {
            return false;
        }

        if (claim.getChunkCount() > 0 && !claim.hasAdjacentChunk(chunkPos.x(), chunkPos.z())) {
            return false;
        }

        

        if (countClaimChunks(owner) >= getMaxChunks(owner)) {
            return false;
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
            return false;
        }

        long now = System.currentTimeMillis();

        if (!claim.addChunk(chunkPos.x(), chunkPos.z(), now)) {
            return false;
        }

        chunkIndex.put(key, claim.getId());
        save();
        return true;
    }

    public boolean unclaim(Level level, ChunkPos chunkPos, UUID playerUuid, boolean adminBypass) {
        String key = createKey(level, chunkPos);
        UUID claimId = chunkIndex.get(key);

        if (claimId == null) {
            return false;
        }

        PlayerClaim claim = claims.get(claimId);

        if (claim == null) {
            chunkIndex.remove(key);
            return false;
        }

        if (!adminBypass && !claim.isOwner(playerUuid)) {
            return false;
        }

        if (!claim.removeChunk(chunkPos.x(), chunkPos.z(), System.currentTimeMillis())) {
            return false;
        }

        chunkIndex.remove(key);
        save();
        return true;
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

    private PlayerClaimLimits getLimits(UUID player) {
        return limits.computeIfAbsent(player, uuid -> new PlayerClaimLimits(
                uuid,
                Config.MAX_PLAYER_CLAIMS.get(),
                1
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
}