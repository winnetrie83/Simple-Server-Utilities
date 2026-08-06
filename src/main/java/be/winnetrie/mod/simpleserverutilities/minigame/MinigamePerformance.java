package be.winnetrie.mod.simpleserverutilities.minigame;

/** Mutable server-only per-player statistics for one match. */
public final class MinigamePerformance {
    public long kills;
    public long deaths;
    public long assists;
    /** Damage and healing are stored in hundredths of one health point. */
    public long damageDealt;
    public long damageTaken;
    public long healingDone;
    public long captures;
    public long defenses;
    public long objectiveTicks;
    public long boostsCollected;
    public long abilitiesUsed;

    public long objectiveSeconds() {
        return Math.max(0L, objectiveTicks / 20L);
    }

    public long contributionScore() {
        long score = 0L;
        score = saturatingAdd(score, kills * 100L);
        score = saturatingAdd(score, assists * 50L);
        score = saturatingAdd(score, captures * 150L);
        score = saturatingAdd(score, defenses * 100L);
        score = saturatingAdd(score, Math.min(10_000L, healingDone / 25L));
        score = saturatingAdd(score, Math.min(10_000L, damageDealt / 50L));
        score = saturatingAdd(score, objectiveSeconds() * 2L);
        return Math.max(0L, score);
    }

    public MinigamePerformance copy() {
        MinigamePerformance value = new MinigamePerformance();
        value.kills = kills;
        value.deaths = deaths;
        value.assists = assists;
        value.damageDealt = damageDealt;
        value.damageTaken = damageTaken;
        value.healingDone = healingDone;
        value.captures = captures;
        value.defenses = defenses;
        value.objectiveTicks = objectiveTicks;
        value.boostsCollected = boostsCollected;
        value.abilitiesUsed = abilitiesUsed;
        return value;
    }

    public static long saturatingAdd(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException ignored) { return right >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE; }
    }
}
