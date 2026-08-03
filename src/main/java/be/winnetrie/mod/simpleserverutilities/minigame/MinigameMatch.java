package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-only live match state. Definitions remain immutable while a match runs. */
public final class MinigameMatch {
    public final UUID id;
    public final String minigameId;
    public final String arenaId;
    public final Map<UUID, Integer> teams = new LinkedHashMap<>();
    public final Map<UUID, Long> scores = new LinkedHashMap<>();
    public final Set<UUID> eliminated = new LinkedHashSet<>();
    public final Map<UUID, MinigameLocation> returnLocations = new LinkedHashMap<>();
    public MinigameMatchState state = MinigameMatchState.COUNTDOWN;
    public long stateStartedTick;
    public long startedEpochMilli = System.currentTimeMillis();
    public long lastAnnouncementSecond = Long.MIN_VALUE;
    public Set<Integer> winningTeams = Set.of();
    public String finishReason = "";

    public MinigameMatch(UUID id, String minigameId, String arenaId, long tick) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.minigameId = minigameId;
        this.arenaId = arenaId;
        this.stateStartedTick = tick;
    }

    public int team(UUID player) { return teams.getOrDefault(player, 0); }
    public long score(UUID player) { return scores.getOrDefault(player, 0L); }
    public boolean active(UUID player) { return teams.containsKey(player) && !eliminated.contains(player); }
}
