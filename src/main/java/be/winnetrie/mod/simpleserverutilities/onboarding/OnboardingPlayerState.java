package be.winnetrie.mod.simpleserverutilities.onboarding;

/** One player's durable first-join progress. */
public final class OnboardingPlayerState {
    public static final int SCHEMA_VERSION = 1;
    public int schemaVersion = SCHEMA_VERSION;
    public String lastKnownName = "";
    public long firstSeenAt;
    public long lastSeenAt;
    public boolean rulesAccepted;
    public boolean completed;
    public int introductionPage;
    public long completedAt;

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        lastKnownName = lastKnownName == null ? "" : lastKnownName.trim();
        firstSeenAt = Math.max(0L, firstSeenAt);
        lastSeenAt = Math.max(firstSeenAt, lastSeenAt);
        introductionPage = Math.max(0, introductionPage);
        completedAt = Math.max(0L, completedAt);
        if (completed) rulesAccepted = true;
    }
}
