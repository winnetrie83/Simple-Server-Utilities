package be.winnetrie.mod.simpleserverutilities.region;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private boolean borderVisible = false;

    private final Set<UUID> managers = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();

    private final RegionSettings settings = new RegionSettings();
    private final RegionRentData rentData = new RegionRentData();
    private final RegionResetSettings resetSettings = new RegionResetSettings();

    /**
     * Optional permission overrides for this specific region.
     * These are resolved only for the effective region, so nested regions keep priority.
     * Values are stored as strings so the same map can hold booleans, integers and later enums.
     */
    private final Map<String, String> permissionOverrides = new HashMap<>();

    private BlockPos spawnPos;
    private float spawnYaw;
    private float spawnPitch;

    private String welcomeMessage = "";
    private String leaveMessage = "";

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

    public boolean isManager(UUID uuid) {
        return managers.contains(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean hasAccess(UUID uuid) {
        return isManager(uuid) || isMember(uuid);
    }

    public void addManager(UUID uuid) {
        managers.add(uuid);
    }

    public void removeManager(UUID uuid) {
        managers.remove(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public Set<UUID> getManagers() {
        return managers;
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

    public RegionResetSettings getResetSettings() {
        return resetSettings;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage == null ? "" : welcomeMessage;
    }

    public String getLeaveMessage() {
        return leaveMessage;
    }

    public void setLeaveMessage(String leaveMessage) {
        this.leaveMessage = leaveMessage == null ? "" : leaveMessage;
    }

    public Map<String, String> getPermissionOverrides() {
        return permissionOverrides;
    }

    public String getPermissionOverride(String key) {
        return key == null ? null : permissionOverrides.get(key.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public void setPermissionOverride(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }

        String normalizedKey = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (value == null || value.isBlank()) {
            permissionOverrides.remove(normalizedKey);
            return;
        }

        permissionOverrides.put(normalizedKey, value.trim());
    }

    public void removePermissionOverride(String key) {
        if (key != null) permissionOverrides.remove(key.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public BlockPos getSpawnPos() {
        return spawnPos;
    }

    public void setSpawn(BlockPos spawnPos, float spawnYaw, float spawnPitch) {
        this.spawnPos = spawnPos.immutable();
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
    }

    public void clearSpawn() {
        this.spawnPos = null;
        this.spawnYaw = 0.0F;
        this.spawnPitch = 0.0F;
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

    /**
     * Server-owned switch for this region's border eligibility. The player
     * preference is applied separately and can only hide an eligible border.
     */
    public boolean isBorderVisible() {
        return borderVisible;
    }

    public void setBorderVisible(boolean borderVisible) {
        this.borderVisible = borderVisible;
    }

    public long getVolume() {
        return (long) (maxX - minX + 1)
                * (maxY - minY + 1)
                * (maxZ - minZ + 1);
    }
}