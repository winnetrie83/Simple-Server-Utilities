package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared SSU movement layer for schedule, patrol, wander and combat movement.
 *
 * <p>Mob-backed NPCs keep Minecraft's native {@code PathNavigation}. Player-model NPCs
 * are native SSU {@code PathfinderMob}s as of dev3.33 and therefore follow this same path.
 * The controller deliberately does <strong>not</strong> rebuild an unchanged path every SSU
 * behavior tick: native navigation already advances that path every entity tick. Repathing is
 * reserved for a meaningfully moved target, a completed path or genuine stall recovery. This
 * keeps body steering stable instead of making a player NPC constantly second-guess the same
 * route.</p>
 *
 * <p>A generic collision-resolving fallback remains only for unusual non-Mob living shells.
 * Progress sampling detects unreachable/stalled targets and lets the caller recover instead of
 * leaving an NPC pushing against the same obstacle forever.</p>
 */
final class NpcNavigationController {
    private static final double ARRIVAL_DISTANCE_SQR = 1.0D;
    /** Target must drift this far from the last planned destination before a moving path is replaced. */
    private static final double TARGET_REPATH_DISTANCE_SQR = 2.25D; // 1.5 blocks
    /** A large target jump is a genuinely new route and may bypass the normal repath cooldown. */
    private static final double NEW_ROUTE_DISTANCE_SQR = 9.0D; // 3 blocks
    private static final long MIN_REPATH_TICKS = 4L;
    private static final long PROGRESS_SAMPLE_TICKS = 20L;
    private static final int STALLED_SAMPLES_BEFORE_RECOVERY = 3;
    private static final int RECOVERIES_BEFORE_STUCK = 3;
    private static final long STUCK_RETRY_TICKS = 80L;

    private final Map<UUID, State> states = new LinkedHashMap<>();

    MoveResult move(UUID instanceId, LivingEntity entity, NpcDefinition definition,
            double targetX, double targetY, double targetZ, double speedMultiplier,
            long serverTick, long repathIntervalTicks) {
        if (instanceId == null || entity == null || definition == null) return MoveResult.STUCK;
        Vec3 target = new Vec3(targetX, targetY, targetZ);
        double distanceSqr = entity.distanceToSqr(targetX, targetY, targetZ);
        if (distanceSqr <= ARRIVAL_DISTANCE_SQR) {
            stop(instanceId, entity);
            return MoveResult.ARRIVED;
        }

        State state = states.computeIfAbsent(instanceId, ignored -> new State());
        state.updateDesiredTarget(target, entity.position(), distanceSqr, serverTick);
        if (serverTick < state.retryAfterTick) {
            stopEntity(entity);
            return MoveResult.STUCK;
        }

        boolean stalled = sampleProgress(state, entity.position(), distanceSqr, serverTick);
        if (stalled) {
            state.recoveries++;
            state.stalledSamples = 0;
            state.nextPathTick = 0L;
            state.steeringSign = -state.steeringSign;
            if (state.recoveries >= RECOVERIES_BEFORE_STUCK) {
                state.retryAfterTick = serverTick + STUCK_RETRY_TICKS;
                state.recoveries = 0;
                stopEntity(entity);
                return MoveResult.STUCK;
            }
        }

        double safeSpeedMultiplier = finiteClamp(speedMultiplier, 0.05D, 4.0D, 1.0D);
        NpcLocomotionProfile locomotion = NpcLocomotionProfile.resolve(entity);

        // Explicit Can fly is an admin override for otherwise-grounded shells. Native flying mobs
        // (Vex, Ghast, Bee, Allay, etc.) must keep their own MoveControl/PathNavigation instead of
        // being reduced to the old one-size-fits-all delta-motion steering.
        boolean manualFlightOverride = definition.canFly && !locomotion.nativeFlying();
        boolean manualSwimOverride = definition.canSwim && entity.isInWater() && !locomotion.waterNative();
        if (manualFlightOverride || manualSwimOverride) {
            double speed = Math.min(0.8D, Math.max(0.04D, 0.18D * safeSpeedMultiplier));
            entity.setNoGravity(true);
            Vec3 difference = target.subtract(entity.position());
            if (difference.lengthSqr() > 0.0001D) {
                // Preserve a little existing velocity so manually flying visual shells turn instead
                // of snapping their motion vector at each 5 Hz SSU behavior update.
                Vec3 desired = difference.normalize().scale(speed);
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.35D).add(desired.scale(0.65D)));
            }
            return stalled ? MoveResult.RECOVERING : MoveResult.MOVING;
        }

        entity.setNoGravity(!definition.affectedByGravity || locomotion.nativeFlying());
        if (entity instanceof Mob mob) {
            long repathDelay = Math.max(MIN_REPATH_TICKS, Math.max(1L, repathIntervalTicks));
            boolean noPlannedPath = state.plannedTarget == null;
            boolean targetDrifted = state.plannedTarget != null
                    && state.plannedTarget.distanceToSqr(target) >= TARGET_REPATH_DISTANCE_SQR;
            boolean cooldownReady = serverTick >= state.nextPathTick;

            if (locomotion.directMoveControl()) {
                // Vex/Ghast/Phantom/Bat-style mobs are steered by specialised vanilla MoveControls.
                // Keep feeding that controller the SSU destination instead of pretending they are a
                // walking PathfinderMob. This preserves their native free-flight character.
                mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, safeSpeedMultiplier);
                state.plannedTarget = target;
                state.nextPathTick = serverTick + repathDelay;
                return stalled ? MoveResult.RECOVERING : MoveResult.MOVING;
            }

            var nativeNavigation = mob.getNavigation();
            boolean pathFinished = nativeNavigation.isDone();

            // The first path is immediate. Afterwards, let vanilla advance its existing path every
            // entity tick. Only replace it when the destination has really moved, the path ended,
            // or stall recovery explicitly requested a fresh route. Because this calls the shell's
            // own navigation + MoveControl, Slimes keep hopping, water mobs swim and normal ground
            // mobs walk instead of sharing the Player NPC's physical steering implementation.
            if (noPlannedPath || stalled || (cooldownReady && (pathFinished || targetDrifted))) {
                boolean accepted = nativeNavigation.moveTo(targetX, targetY, targetZ, safeSpeedMultiplier);
                state.plannedTarget = target;
                state.nextPathTick = serverTick + repathDelay;
                if (!accepted && (locomotion.nativeFlying() || locomotion.waterNative())) {
                    // Some specialised mobs expose a navigation object but normally drive movement
                    // straight through MoveControl. If path construction rejects a perfectly valid
                    // air/water target, fall back to that native controller rather than raw velocity.
                    mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, safeSpeedMultiplier);
                    state.nativeControlFallback = true;
                } else {
                    state.nativeControlFallback = false;
                }
            } else if (state.nativeControlFallback) {
                // Refresh a direct native controller at the SSU behavior cadence without rebuilding
                // a path. This is stable for flying/swimming MoveControls and keeps target ownership.
                mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, safeSpeedMultiplier);
            }
            return stalled ? MoveResult.RECOVERING : MoveResult.MOVING;
        }

        // Some modded/vanilla LivingEntity shells are not Mobs and expose no PathNavigation.
        // Keep their fallback physical: Entity.move resolves collision instead of phasing through blocks.
        Vec3 difference = target.subtract(entity.position());
        Vec3 horizontal = new Vec3(difference.x, 0.0D, difference.z);
        if (horizontal.lengthSqr() < 0.0001D) return MoveResult.MOVING;
        Vec3 forward = horizontal.normalize();
        double step = Math.min(Math.max(0.035D, 0.20D * safeSpeedMultiplier), horizontal.length());
        Vec3 motion = forward.scale(step);
        if (stalled || state.stalledSamples >= 2) {
            Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).scale(step * 0.85D * state.steeringSign);
            motion = forward.scale(step * 0.45D).add(side);
        }
        entity.move(MoverType.SELF, motion);
        if (entity.onGround() && targetY - entity.getY() > 0.6D) {
            Vec3 velocity = entity.getDeltaMovement();
            entity.setDeltaMovement(velocity.x, Math.max(velocity.y, 0.36D), velocity.z);
        }
        return stalled ? MoveResult.RECOVERING : MoveResult.MOVING;
    }

    void stop(UUID instanceId, LivingEntity entity) {
        if (instanceId != null) states.remove(instanceId);
        stopEntity(entity);
    }

    void forget(UUID instanceId) {
        if (instanceId != null) states.remove(instanceId);
    }

    /**
     * Starts a genuinely different route without killing the entity's current momentum. Clearing
     * the native path matters for patrol waypoint transitions: an already-completed path must not
     * remain the active navigation object while SSU switches its logical waypoint index.
     */
    void beginNewRoute(UUID instanceId, LivingEntity entity) {
        if (instanceId != null) states.remove(instanceId);
        if (entity instanceof Mob mob) mob.getNavigation().stop();
    }

    void clear() {
        states.clear();
    }

    private static boolean sampleProgress(State state, Vec3 position, double distanceSqr, long serverTick) {
        if (serverTick - state.lastSampleTick < PROGRESS_SAMPLE_TICKS) return false;
        double movedSqr = position.distanceToSqr(state.lastPosition);
        boolean meaningfulProgress = distanceSqr + 0.05D < state.lastDistanceSqr;
        if (movedSqr < 0.01D && !meaningfulProgress) state.stalledSamples++;
        else {
            state.stalledSamples = 0;
            if (meaningfulProgress) state.recoveries = 0;
        }
        state.lastPosition = position;
        state.lastDistanceSqr = distanceSqr;
        state.lastSampleTick = serverTick;
        return state.stalledSamples >= STALLED_SAMPLES_BEFORE_RECOVERY;
    }

    private static void stopEntity(LivingEntity entity) {
        if (entity == null) return;
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            // Specialised flying/swimming MoveControls may keep an old wanted position even after
            // PathNavigation stops. Put the native controller in WAIT as well when SSU deliberately
            // stops at a waypoint/idle state, otherwise a Vex/Ghast-style shell can keep drifting.
            mob.getMoveControl().setWait();
        }
        entity.setDeltaMovement(Vec3.ZERO);
    }

    private static double finiteClamp(double value, double minimum, double maximum, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    enum MoveResult {
        MOVING,
        ARRIVED,
        RECOVERING,
        STUCK
    }

    private static final class State {
        Vec3 desiredTarget;
        Vec3 plannedTarget;
        Vec3 lastPosition = Vec3.ZERO;
        double lastDistanceSqr = Double.POSITIVE_INFINITY;
        long lastSampleTick;
        long nextPathTick;
        long retryAfterTick;
        int stalledSamples;
        int recoveries;
        int steeringSign = 1;
        boolean nativeControlFallback;

        void updateDesiredTarget(Vec3 nextTarget, Vec3 currentPosition, double distanceSqr, long serverTick) {
            if (desiredTarget == null) {
                resetForTarget(nextTarget, currentPosition, distanceSqr, serverTick);
                return;
            }
            double changedSqr = desiredTarget.distanceToSqr(nextTarget);
            desiredTarget = nextTarget;
            if (changedSqr < NEW_ROUTE_DISTANCE_SQR) return;

            // A schedule slot/waypoint jump is a new route. Reset only stall bookkeeping and allow
            // an immediate fresh path; small combat-target motion intentionally keeps the current
            // route so steering remains stable.
            lastPosition = currentPosition;
            lastDistanceSqr = distanceSqr;
            lastSampleTick = serverTick;
            nextPathTick = 0L;
            retryAfterTick = 0L;
            stalledSamples = 0;
            recoveries = 0;
            nativeControlFallback = false;
        }

        void resetForTarget(Vec3 nextTarget, Vec3 currentPosition, double distanceSqr, long serverTick) {
            desiredTarget = nextTarget;
            plannedTarget = null;
            lastPosition = currentPosition;
            lastDistanceSqr = distanceSqr;
            lastSampleTick = serverTick;
            nextPathTick = 0L;
            retryAfterTick = 0L;
            stalledSamples = 0;
            recoveries = 0;
            steeringSign = 1;
            nativeControlFallback = false;
        }
    }
}
