package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
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
    /** Preferred queue role copied into the match for diagnostics and UI. */
    public final Map<UUID, MinigameRole> preferredRoles = new LinkedHashMap<>();
    /** Server-assigned role after per-team min/max composition. */
    public final Map<UUID, MinigameRole> roles = new LinkedHashMap<>();
    /** Player -> ability ID -> next server tick at which it may be used. */
    public final Map<UUID, Map<String, Long>> roleCooldowns = new LinkedHashMap<>();
    public final Map<UUID, Long> scores = new LinkedHashMap<>();
    public final Set<UUID> eliminated = new LinkedHashSet<>();
    /** CTF/Domination players temporarily spectating until their delayed respawn. */
    public final Map<UUID, PendingRespawn> pendingRespawns = new LinkedHashMap<>();
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
    /** Spleef: next tick at which each player may fire the infinite projectile. */
    public final Map<UUID, Long> spleefStandardProjectileCooldowns = new LinkedHashMap<>();
    public boolean spleefStandardProjectileUnlocked;
    public boolean spleefBurstScheduleStarted;
    public long spleefNextBurstGrantTick;

    /** Shared CTF/Domination boost runtime. */
    public final Map<UUID, ActiveBoost> activeBoosts = new LinkedHashMap<>();
    public final List<MinigameLocation> boostCandidateLocations = new ArrayList<>();
    public final List<Long> boostSpawnSchedule = new ArrayList<>();
    public final Map<UUID, Double> boostOriginalArmorBase = new LinkedHashMap<>();
    public final Map<UUID, Long> boostArmorExpires = new LinkedHashMap<>();
    /** Regeneration boost timing is handled server-side so hunger healing can stay disabled. */
    public final Map<UUID, Long> boostRegenerationExpires = new LinkedHashMap<>();
    public final Map<UUID, Long> boostRegenerationNextHeal = new LinkedHashMap<>();
    public boolean boostsInitialized;
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
    public MinigameRole role(UUID player) { return roles.getOrDefault(player, MinigameRole.DPS); }
    public long score(UUID player) { return scores.getOrDefault(player, 0L); }
    public boolean active(UUID player) {
        return teams.containsKey(player) && !eliminated.contains(player) && !pendingRespawns.containsKey(player);
    }

    public static final class PendingRespawn {
        public final MinigameLocation destination;
        public final long completesTick;
        public long lastDisplayedSecond = Long.MIN_VALUE;

        public PendingRespawn(MinigameLocation destination, long completesTick) {
            this.destination = destination == null ? new MinigameLocation() : destination.copy();
            this.completesTick = completesTick;
        }
    }

    public record CtfCast(int flagTeam, int carrierTeam, long startedTick, long completesTick,
                          double startX, double startY, double startZ) {
    }

    public record DominationCast(String pointId, int team, long startedTick, long completesTick,
                                 double startX, double startY, double startZ) {
    }

    public record DominationClaim(int previousOwner, int claimingTeam, long startedTick, long completesTick) {
    }

    public static final class ActiveBoost {
        public final UUID id;
        public final MinigameBoostType type;
        public final MinigameLocation location;
        public UUID entityId;

        public ActiveBoost(UUID id, MinigameBoostType type, MinigameLocation location, UUID entityId) {
            this.id = id == null ? UUID.randomUUID() : id;
            this.type = type == null ? MinigameBoostType.SPEED : type;
            this.location = location == null ? new MinigameLocation() : location;
            this.entityId = entityId;
        }
    }
}

