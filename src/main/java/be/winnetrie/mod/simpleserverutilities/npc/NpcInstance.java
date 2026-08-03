package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** One placed NPC in a world. Multiple instances may share one reusable definition. */
public final class NpcInstance {
    public static final int SCHEMA_VERSION = 3;

    public int schemaVersion = SCHEMA_VERSION;
    public String id = UUID.randomUUID().toString();
    public String definitionId = "npc";
    public String dimension = "minecraft:overworld";
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public boolean enabled = true;
    /** Schedule belongs to this placement because its targets are world coordinates. */
    public boolean scheduleEnabled;
    public List<NpcScheduleEntry> schedule = new ArrayList<>();
    /** Respawn settings belong to this placement because their anchor is world-specific. */
    public boolean respawnEnabled;
    public int respawnDelaySeconds = 30;
    public String respawnDimension = "";
    public double respawnX;
    public double respawnY;
    public double respawnZ;
    public float respawnYaw;
    public float respawnPitch;
    /** Durable runtime state so a dead NPC is not immediately recreated by reconciliation. */
    public boolean dead;
    /** Wall-clock deadline persists correctly across server restarts; zero means no automatic respawn. */
    public long respawnAtEpochMillis;

    /** UUID of the vanilla entity shell; used to rebind after saves and restarts. */
    public String runtimeEntityId = "";

    public NpcInstance normalize() {
        int loadedSchema = schemaVersion;
        schemaVersion = SCHEMA_VERSION;
        try {
            id = UUID.fromString(id == null ? "" : id).toString();
        } catch (IllegalArgumentException exception) {
            id = UUID.randomUUID().toString();
        }
        definitionId = NpcDefinition.sanitizeId(definitionId);
        dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : limit(dimension.trim(), 256);
        x = finite(x, 0.0D); y = finite(y, 64.0D); z = finite(z, 0.0D);
        yaw = Float.isFinite(yaw) ? yaw : 0.0F;
        pitch = Float.isFinite(pitch) ? Math.max(-90.0F, Math.min(90.0F, pitch)) : 0.0F;
        if (loadedSchema < 3) {
            respawnDimension = dimension;
            respawnX = x; respawnY = y; respawnZ = z;
            respawnYaw = yaw; respawnPitch = pitch;
        }
        respawnDelaySeconds = Math.max(0, Math.min(86_400, respawnDelaySeconds));
        respawnDimension = respawnDimension == null || respawnDimension.isBlank()
                ? dimension : limit(respawnDimension.trim(), 256);
        respawnX = finite(respawnX, x); respawnY = finite(respawnY, y); respawnZ = finite(respawnZ, z);
        respawnYaw = Float.isFinite(respawnYaw) ? respawnYaw : yaw;
        respawnPitch = Float.isFinite(respawnPitch)
                ? Math.max(-90.0F, Math.min(90.0F, respawnPitch)) : pitch;
        respawnAtEpochMillis = Math.max(0L, respawnAtEpochMillis);
        if (schedule == null) schedule = new ArrayList<>();
        List<NpcScheduleEntry> normalizedSchedule = new ArrayList<>();
        for (NpcScheduleEntry entry : schedule) {
            if (entry == null) continue;
            normalizedSchedule.add(entry.normalize());
            if (normalizedSchedule.size() >= 16) break;
        }
        normalizedSchedule.sort(Comparator.comparingInt(entry -> entry.minuteOfDay));
        schedule = normalizedSchedule;
        try {
            runtimeEntityId = runtimeEntityId == null || runtimeEntityId.isBlank()
                    ? "" : UUID.fromString(runtimeEntityId).toString();
        } catch (IllegalArgumentException exception) {
            runtimeEntityId = "";
        }
        return this;
    }

    public UUID uuid() {
        return UUID.fromString(id);
    }

    public UUID runtimeUuid() {
        try {
            return runtimeEntityId == null || runtimeEntityId.isBlank() ? null : UUID.fromString(runtimeEntityId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public NpcInstance copyAt(String targetDimension, double targetX, double targetY, double targetZ, float targetYaw, float targetPitch) {
        NpcInstance copy = new NpcInstance();
        copy.definitionId = definitionId;
        copy.dimension = targetDimension;
        copy.x = targetX; copy.y = targetY; copy.z = targetZ;
        copy.yaw = targetYaw; copy.pitch = targetPitch;
        copy.enabled = enabled;
        copy.scheduleEnabled = scheduleEnabled;
        copy.respawnEnabled = respawnEnabled;
        copy.respawnDelaySeconds = respawnDelaySeconds;
        copy.respawnDimension = targetDimension;
        copy.respawnX = targetX + (respawnX - x);
        copy.respawnY = targetY + (respawnY - y);
        copy.respawnZ = targetZ + (respawnZ - z);
        copy.respawnYaw = respawnYaw;
        copy.respawnPitch = respawnPitch;
        copy.dead = false;
        copy.respawnAtEpochMillis = 0L;
        copy.schedule = new ArrayList<>();
        double offsetX = targetX - x;
        double offsetY = targetY - y;
        double offsetZ = targetZ - z;
        for (NpcScheduleEntry entry : schedule) {
            NpcScheduleEntry shifted = entry.copy();
            shifted.x += offsetX;
            shifted.y += offsetY;
            shifted.z += offsetZ;
            copy.schedule.add(shifted);
        }
        return copy.normalize();
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
