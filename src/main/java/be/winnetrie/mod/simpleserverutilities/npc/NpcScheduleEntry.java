package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Locale;

/** One time-of-day target and simulated activity for a placed NPC. */
public final class NpcScheduleEntry {
    public static final String MOVEMENT_WALK = "walk";
    public static final String MOVEMENT_TELEPORT = "teleport";
    public static final String ACTIVITY_IDLE = "idle";
    public static final String ACTIVITY_LOOK_AROUND = "look_around";
    public static final String ACTIVITY_CHOP_TREE = "chop_tree";
    public static final String ACTIVITY_WORK = "work";
    public static final String ACTIVITY_GUARD = "guard";

    /** Minecraft clock time as real clock minutes, 0..1439. */
    public int minuteOfDay;
    public double x;
    public double y = 64.0D;
    public double z;
    public float yaw;
    /** walk or teleport. Flying/swimming movement is derived from the NPC movement settings. */
    public String movement = MOVEMENT_WALK;
    /** Activity performed after arrival. See {@link NpcScheduleActivity}. */
    public String activity = ACTIVITY_IDLE;
    /** Pathing speed multiplier. */
    public double speed = 1.0D;

    public NpcScheduleEntry normalize() {
        minuteOfDay = Math.max(0, Math.min(1_439, minuteOfDay));
        x = finite(x, 0.0D);
        y = finite(y, 64.0D);
        z = finite(z, 0.0D);
        yaw = Float.isFinite(yaw) ? yaw : 0.0F;
        movement = normalizedMovement(movement);
        activity = normalizedActivity(activity);
        speed = Double.isFinite(speed) ? Math.max(0.05D, Math.min(4.0D, speed)) : 1.0D;
        return this;
    }

    public NpcScheduleEntry copy() {
        NpcScheduleEntry copy = new NpcScheduleEntry();
        copy.minuteOfDay = minuteOfDay;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.yaw = yaw;
        copy.movement = movement;
        copy.activity = activity;
        copy.speed = speed;
        return copy;
    }

    public String clockText() {
        return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    public static String normalizedMovement(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return MOVEMENT_TELEPORT.equals(value) ? MOVEMENT_TELEPORT : MOVEMENT_WALK;
    }

    public static String normalizedActivity(String raw) {
        return NpcScheduleActivity.parse(raw).id();
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
