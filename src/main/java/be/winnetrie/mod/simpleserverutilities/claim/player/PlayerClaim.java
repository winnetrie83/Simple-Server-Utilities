package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerClaim {

    private UUID id;
    private String name;
    private String dimension;
    private UUID owner;

    private long createdAt;
    private long lastChunkChangeAt;

    private String welcomeMessage = "";

    private Set<ClaimChunk> chunks = new HashSet<>();
    private Set<UUID> trustedPlayers = new HashSet<>();
    private ClaimSettings settings = new ClaimSettings();

    public PlayerClaim() {
        // Required for Gson
    }

    public PlayerClaim(String name, String dimension, UUID owner, long timestamp) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.dimension = dimension;
        this.owner = owner;
        this.createdAt = timestamp;
        this.lastChunkChangeAt = timestamp;
    }

    private void ensureDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (chunks == null) {
            chunks = new HashSet<>();
        }

        if (trustedPlayers == null) {
            trustedPlayers = new HashSet<>();
        }

        if (settings == null) {
            settings = new ClaimSettings();
        }

        if (welcomeMessage == null) {
            welcomeMessage = "";
        }
    }

    public UUID getId() {
        ensureDefaults();
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDimension() {
        return dimension;
    }

    public UUID getOwner() {
        return owner;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastChunkChangeAt() {
        return lastChunkChangeAt;
    }

    public String getWelcomeMessage() {
        ensureDefaults();
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage == null ? "" : welcomeMessage;
    }

    public Set<ClaimChunk> getChunks() {
        ensureDefaults();
        return chunks;
    }

    public int getChunkCount() {
        return getChunks().size();
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return getChunks().contains(new ClaimChunk(chunkX, chunkZ));
    }

    public boolean addChunk(int chunkX, int chunkZ, long timestamp) {
        boolean added = getChunks().add(new ClaimChunk(chunkX, chunkZ));

        if (added) {
            if (createdAt <= 0) {
                createdAt = timestamp;
            }

            lastChunkChangeAt = timestamp;
        }

        return added;
    }

    public boolean removeChunk(int chunkX, int chunkZ, long timestamp) {
        boolean removed = getChunks().remove(new ClaimChunk(chunkX, chunkZ));

        if (removed) {
            lastChunkChangeAt = timestamp;
        }

        return removed;
    }

    public Set<UUID> getTrustedPlayers() {
        ensureDefaults();
        return trustedPlayers;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return getTrustedPlayers().contains(uuid);
    }

    public boolean canBuild(UUID uuid) {
        return isOwner(uuid) || isTrusted(uuid);
    }

    public void trust(UUID uuid) {
        getTrustedPlayers().add(uuid);
    }

    public void untrust(UUID uuid) {
        getTrustedPlayers().remove(uuid);
    }

    public ClaimSettings getSettings() {
        ensureDefaults();
        return settings;
    }

    public String getDisplayName() {
        return name == null ? "unnamed" : name;
    }

    public boolean hasAdjacentChunk(int chunkX, int chunkZ) {
        return hasChunk(chunkX + 1, chunkZ)
                || hasChunk(chunkX - 1, chunkZ)
                || hasChunk(chunkX, chunkZ + 1)
                || hasChunk(chunkX, chunkZ - 1);
    }
}