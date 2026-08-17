package be.winnetrie.mod.simpleserverutilities.npc;

/** One persistent patrol waypoint belonging to an NPC placement. */
public final class NpcPatrolPoint {
    public double x;
    public double y = 64.0D;
    public double z;
    public float yaw;
    /** Pause after reaching this point. */
    public int pauseSeconds = 0;

    public NpcPatrolPoint normalize() {
        x = finite(x, 0.0D);
        y = finite(y, 64.0D);
        z = finite(z, 0.0D);
        yaw = Float.isFinite(yaw) ? yaw : 0.0F;
        pauseSeconds = Math.max(0, Math.min(300, pauseSeconds));
        return this;
    }

    public NpcPatrolPoint copy() {
        NpcPatrolPoint copy = new NpcPatrolPoint();
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.yaw = yaw;
        copy.pauseSeconds = pauseSeconds;
        return copy;
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
