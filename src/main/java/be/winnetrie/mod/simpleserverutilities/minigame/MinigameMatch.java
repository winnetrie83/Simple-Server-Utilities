package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/** Server-only live match state. Definitions remain immutable while a match runs. */
public final class MinigameMatch {
    public final UUID id;
    public final String minigameId;
    public final String arenaId;
    public final Map<UUID, Integer> teams = new LinkedHashMap<>();
    public final Map<UUID, Long> scores = new LinkedHashMap<>();
    public final Set<UUID> eliminated = new LinkedHashSet<>();
    /** Capture the Flag: flag team -> current enemy carrier. */
    public final Map<Integer, UUID> flagCarriers = new LinkedHashMap<>();
    /** Capture the Flag team scores. */
    public final Map<Integer, Integer> ctfScores = new LinkedHashMap<>();
    /** Capture the Flag: flag team -> physical dropped-banner location. */
    public final Map<Integer, MinigameLocation> ctfDroppedFlags = new LinkedHashMap<>();
    /** Carriers already crouching; prevents an immediate re-drop until they release sneak once. */
    public final Set<UUID> ctfCarrierSneakLatch = new LinkedHashSet<>();
    /** Player -> active stationary enemy-flag take cast. */
    public final Map<UUID, CtfCast> ctfCasts = new LinkedHashMap<>();
    /** Carrier player -> temporary head equipment replaced by the captain-style banner. */
    public final Map<UUID, ItemStack> ctfPreviousHeadItems = new LinkedHashMap<>();
    /** Original scoreboard team name while a temporary colored carrier glow is active. */
    public final Map<UUID, String> ctfPreviousScoreboardTeams = new LinkedHashMap<>();
    /** Original glowing state while a temporary colored carrier glow is active. */
    public final Map<UUID, Boolean> ctfPreviousGlowing = new LinkedHashMap<>();
    /** Per-carrier temporary scoreboard team used for the configured glow color. */
    public final Map<UUID, String> ctfGlowTeams = new LinkedHashMap<>();
    public boolean ctfInitialized;
    /** Domination node ID -> owning team (0 neutral). */
    public final Map<String, Integer> dominationOwners = new LinkedHashMap<>();
    /** Legacy proximity-capture progress, cleared when a match starts. */
    public final Map<String, Integer> dominationProgress = new LinkedHashMap<>();
    /** Player -> active right-click capture cast. */
    public final Map<UUID, DominationCast> dominationCasts = new LinkedHashMap<>();
    /** Node ID -> delayed ownership transfer after a completed cast. */
    public final Map<String, DominationClaim> dominationClaims = new LinkedHashMap<>();
    public final Map<Integer, Integer> dominationScores = new LinkedHashMap<>();
    public boolean dominationInitialized;
    public long dominationLastScoreTick;
    public final Map<UUID, MinigameLocation> returnLocations = new LinkedHashMap<>();
    public final Map<UUID, MinigamePlayerState> playerStates = new LinkedHashMap<>();
    public final java.util.List<UUID> joinOrder = new java.util.ArrayList<>();
    /** Per-team cursor used to cycle through optional team spawns from a randomized start/order. */
    public final Map<Integer, Integer> teamSpawnCursors = new LinkedHashMap<>();
    /** Players whose original state has already been restored before rewards are delivered. */
    public final Set<UUID> restoredStates = new LinkedHashSet<>();
    public boolean rewardsDelivered;
    /** The restored post-reward state is verified on disk before final return/cleanup. */
    public boolean postRewardRecoveryDurable;
    /** Forced administrator test matches never grant configured rewards. */
    public boolean rewardsEnabled = true;
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

    public record CtfCast(int flagTeam, int carrierTeam, long startedTick, long completesTick,
                          double startX, double startY, double startZ) {
    }

    public record DominationCast(String pointId, int team, long startedTick, long completesTick,
                                 double startX, double startY, double startZ) {
    }

    public record DominationClaim(int previousOwner, int claimingTeam, long startedTick, long completesTick) {
    }
}

