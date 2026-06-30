package be.winnetrie.mod.simpleserverutilities.region;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class Region {

    private final String name;
    private final ResourceKey<Level> dimension;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private int priority = 0;

    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();

    private final RegionSettings settings = new RegionSettings();
    private final RegionRentData rentData = new RegionRentData();

    private BlockPos spawnPos;
    private float spawnYaw;
    private float spawnPitch;

    public Region(String name, ResourceKey<Level> dimension, BlockPos point1, BlockPos point2) {
        this.name = name;
        this.dimension = dimension;

        this.minX = Math.min(point1.getX(), point2.getX());
        this.minY = Math.min(point1.getY(), point2.getY());
        this.minZ = Math.min(point1.getZ(), point2.getZ());

        this.maxX = Math.max(point1.getX(), point2.getX());
        this.maxY = Math.max(point1.getY(), point2.getY());
        this.maxZ = Math.max(point1.getZ(), point2.getZ());
    }

    public String getName() {
        return name;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
        if (!this.dimension.equals(dimension)) {
            return false;
        }

        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public boolean isOwner(UUID uuid) {
        return owners.contains(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean hasAccess(UUID uuid) {
        return isOwner(uuid) || isMember(uuid);
    }

    public void addOwner(UUID uuid) {
        owners.add(uuid);
    }

    public void removeOwner(UUID uuid) {
        owners.remove(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public Set<UUID> getOwners() {
        return owners;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public RegionSettings getSettings() {
        return settings;
    }

    public RegionRentData getRentData() {
        return rentData;
    }

    public BlockPos getSpawnPos() {
        return spawnPos;
    }

    public void setSpawn(BlockPos spawnPos, float spawnYaw, float spawnPitch) {
        this.spawnPos = spawnPos.immutable();
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public String getBoundsText() {
        return "(" + minX + ", " + minY + ", " + minZ + ") -> ("
                + maxX + ", " + maxY + ", " + maxZ + ")";
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public long getVolume() {
        return (long) (maxX - minX + 1)
                * (maxY - minY + 1)
                * (maxZ - minZ + 1);
    }
}