package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final Map<String, PlayerClaim> claims = new HashMap<>();
    private Path saveFile;

    public void load(MinecraftServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("simpleserverutilities");
        this.saveFile = folder.resolve("player_claims.json");

        claims.clear();

        try {
            Files.createDirectories(folder);

            if (!Files.exists(saveFile)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(saveFile)) {
                PlayerClaim[] loadedClaims = GSON.fromJson(reader, PlayerClaim[].class);

                if (loadedClaims != null) {
                    for (PlayerClaim claim : loadedClaims) {
                        claims.put(claim.getKey(), claim);
                    }
                }
            }

            SimpleServerUtilities.LOGGER.info("Loaded {} player claims.", claims.size());
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to load player claims.", e);
        }
    }

    public void save() {
        if (saveFile == null) {
            return;
        }

        try (Writer writer = Files.newBufferedWriter(saveFile)) {
            GSON.toJson(claims.values(), writer);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to save player claims.", e);
        }
    }

    public boolean claim(Level level, ChunkPos chunkPos, UUID owner) {
        if (!Config.ENABLE_PLAYER_CLAIMS.get()) {
            return false;
        }

        String key = createKey(level, chunkPos);

        if (claims.containsKey(key)) {
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

        PlayerClaim claim = new PlayerClaim(
            getDimensionId(level), chunkPos.x(), chunkPos.z(), owner);

        claims.put(key, claim);
        save();
        return true;
    }

    public boolean unclaim(Level level, ChunkPos chunkPos, UUID playerUuid, boolean adminBypass) {
        String key = createKey(level, chunkPos);
        PlayerClaim claim = claims.get(key);

        if (claim == null) {
            return false;
        }

        if (!adminBypass && !claim.isOwner(playerUuid)) {
            return false;
        }

        claims.remove(key);
        save();
        return true;
    }

    public PlayerClaim getClaim(Level level, ChunkPos chunkPos) {
        return claims.get(createKey(level, chunkPos));
    }

    public boolean isClaimed(Level level, ChunkPos chunkPos) {
        return getClaim(level, chunkPos) != null;
    }

    public int countClaims(UUID owner) {
        int count = 0;

        for (PlayerClaim claim : claims.values()) {
            if (claim.isOwner(owner)) {
                count++;
            }
        }

        return count;
    }

    public Collection<PlayerClaim> getClaims() {
        return claims.values();
    }

    private String createKey(Level level, ChunkPos chunkPos) {
        return getDimensionId(level) + ":" + chunkPos.x() + "," + chunkPos.z();
    }

    private String getDimensionId(Level level) {
        return level.dimension().identifier().toString();
    }
}