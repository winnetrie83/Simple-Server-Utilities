package be.winnetrie.mod.simpleserverutilities.dungeon;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-only runtime state for one dungeon party. */
public final class DungeonRun {
    public final UUID id;
    public final String dungeonId;
    public final String arenaId;
    public final Set<UUID> participants = new LinkedHashSet<>();
    public final Set<UUID> eliminated = new LinkedHashSet<>();
    public final Map<UUID, Integer> remainingLives = new LinkedHashMap<>();
    public final Map<UUID, DungeonLocation> returnLocations = new LinkedHashMap<>();
    public DungeonRunState state = DungeonRunState.COUNTDOWN;
    public long stateStartedTick;
    public long stageStartedTick;
    public int stageIndex;
    public long stageProgress;
    public String activeCheckpointId = "";
    public boolean successful;
    public String finishReason = "";
    public long lastAnnouncementSecond = Long.MIN_VALUE;

    public DungeonRun(UUID id, String dungeonId, String arenaId, long tick) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.dungeonId = dungeonId;
        this.arenaId = arenaId;
        this.stateStartedTick = tick;
        this.stageStartedTick = tick;
    }

    public int lives(UUID player) { return remainingLives.getOrDefault(player, 0); }
    public boolean active(UUID player) { return participants.contains(player) && !eliminated.contains(player); }
}
