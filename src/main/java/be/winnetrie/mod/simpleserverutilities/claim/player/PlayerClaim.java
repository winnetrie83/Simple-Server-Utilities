package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerClaim {

    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final UUID owner;
    private final Set<UUID> trustedPlayers = new HashSet<>();
    private ClaimSettings settings = new ClaimSettings();

    public PlayerClaim(String dimension, int chunkX, int chunkZ, UUID owner) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.owner = owner;
    }

    public String getDimension() {
        return dimension;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getTrustedPlayers() {
        return trustedPlayers;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return trustedPlayers.contains(uuid);
    }

    public boolean canBuild(UUID uuid) {
        return isOwner(uuid) || isTrusted(uuid);
    }

    public void trust(UUID uuid) {
        trustedPlayers.add(uuid);
    }

    public void untrust(UUID uuid) {
        trustedPlayers.remove(uuid);
    }

    public String getKey() {
        return dimension + ":" + chunkX + "," + chunkZ;
    }

    public ClaimSettings getSettings() {
        if (settings == null) {
            settings = new ClaimSettings();
        }

        return settings;
    }
}