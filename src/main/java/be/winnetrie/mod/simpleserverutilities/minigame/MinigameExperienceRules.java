package be.winnetrie.mod.simpleserverutilities.minigame;

/**
 * Shared match-polish settings used by every concrete minigame type.
 * All values are bounded here so old definitions migrate safely.
 */
public final class MinigameExperienceRules {
    public boolean resultsScreenEnabled = true;
    public boolean killFeedEnabled = true;

    public boolean rejoinEnabled = true;
    public int rejoinGraceSeconds = 90;

    public boolean afkDetectionEnabled = true;
    public int afkTimeoutSeconds = 120;
    public int afkWarningSeconds = 20;

    public boolean overtimeEnabled = true;
    public int overtimeSeconds = 60;

    public boolean postGameVotingEnabled = true;
    public int postGameVoteSeconds = 12;

    public boolean spectatorToolsEnabled = true;
    public boolean matchSummaryMailEnabled = true;

    public boolean performanceBalancingEnabled = true;
    /** 0 = player count only, 1 = rating dominates team assignment. */
    public double performanceBalanceWeight = 0.35D;

    public boolean progressionEnabled = true;
    public int participationExperience = 25;
    public int winnerExperience = 50;
    public int objectiveExperienceCap = 40;

    /** Shared weekly cosmetic challenge rewards for this minigame definition. */
    public boolean weeklyChallengesEnabled = true;
    public int weeklyMatchesRequired = 5;
    public int weeklyMatchesExperience = 25;
    public int weeklyWinsRequired = 2;
    public int weeklyWinsExperience = 35;
    public long weeklyContributionRequired = 2_500L;
    public int weeklyContributionExperience = 40;

    public void normalize() {
        // The results screen is also the bounded post-game voting surface.
        if (postGameVotingEnabled) resultsScreenEnabled = true;
        rejoinGraceSeconds = clamp(rejoinGraceSeconds, 10, 900);
        afkTimeoutSeconds = clamp(afkTimeoutSeconds, 30, 3_600);
        afkWarningSeconds = clamp(afkWarningSeconds, 5, Math.max(5, afkTimeoutSeconds - 5));
        overtimeSeconds = clamp(overtimeSeconds, 5, 600);
        postGameVoteSeconds = clamp(postGameVoteSeconds, 5, 120);
        if (!Double.isFinite(performanceBalanceWeight)) performanceBalanceWeight = 0.35D;
        performanceBalanceWeight = Math.max(0.0D, Math.min(1.0D, performanceBalanceWeight));
        participationExperience = clamp(participationExperience, 0, 100_000);
        winnerExperience = clamp(winnerExperience, 0, 100_000);
        objectiveExperienceCap = clamp(objectiveExperienceCap, 0, 100_000);
        weeklyMatchesRequired = clamp(weeklyMatchesRequired, 1, 10_000);
        weeklyMatchesExperience = clamp(weeklyMatchesExperience, 0, 100_000);
        weeklyWinsRequired = clamp(weeklyWinsRequired, 1, 10_000);
        weeklyWinsExperience = clamp(weeklyWinsExperience, 0, 100_000);
        weeklyContributionRequired = clamp(weeklyContributionRequired, 1L, 1_000_000_000L);
        weeklyContributionExperience = clamp(weeklyContributionExperience, 0, 100_000);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
