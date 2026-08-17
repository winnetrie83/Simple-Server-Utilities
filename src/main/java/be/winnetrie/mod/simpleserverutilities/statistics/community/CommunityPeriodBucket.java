package be.winnetrie.mod.simpleserverutilities.statistics.community;

/** Persisted current bucket for a named calendar/community period. */
public final class CommunityPeriodBucket {
    public String key = "";
    public CommunityStatBucket stats = new CommunityStatBucket();

    public void normalize() {
        if (key == null) key = "";
        if (stats == null) stats = new CommunityStatBucket();
        stats.normalize();
    }
}
