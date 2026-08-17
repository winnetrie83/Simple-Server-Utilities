package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-side data-driven ability scheduler shared by normal combat NPCs and bosses. */
final class NpcAbilityController {
    interface DamageFilter { boolean mayDamage(LivingEntity target); }
    interface HealingObserver { void onHeal(LivingEntity healer, LivingEntity healed, double amount); }

    enum RequestStatus { ACTIVE, STARTED, FINISHED, UNAVAILABLE, CHANCE_FAILED }
    record RequestedTickResult(RequestStatus status, boolean ownsMovement) {
        boolean advancePattern() {
            return status == RequestStatus.STARTED
                    || status == RequestStatus.CHANCE_FAILED
                    || status == RequestStatus.UNAVAILABLE;
        }
    }

    private final NpcAbilityLibraryManager library;
    private final Map<UUID, Cast> casts = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new LinkedHashMap<>();
    private final Map<UUID, Long> lastDamageTick = new LinkedHashMap<>();
    private final List<PeriodicEffect> periodicEffects = new ArrayList<>();
    private HealingObserver healingObserver = (healer, healed, amount) -> { };

    NpcAbilityController(NpcAbilityLibraryManager library) {
        this.library = java.util.Objects.requireNonNull(library, "library");
    }

    void setHealingObserver(HealingObserver observer) {
        healingObserver = observer == null ? (healer, healed, amount) -> { } : observer;
    }

    void clear() { casts.clear(); cooldowns.clear(); lastDamageTick.clear(); periodicEffects.clear(); }
    void forget(UUID instanceId) {
        if (instanceId != null) {
            casts.remove(instanceId); cooldowns.remove(instanceId); lastDamageTick.remove(instanceId);
        }
    }
    boolean casting(UUID instanceId) { return instanceId != null && casts.containsKey(instanceId); }
    void noteDamaged(UUID instanceId, long serverTick) { if (instanceId != null) lastDamageTick.put(instanceId, serverTick); }

    /** Persistent bleed/DoT/HoT effects tick independently of the 5-tick relation/targeting cadence. */
    void tickPersistentEffects(long serverTick) {
        Iterator<PeriodicEffect> iterator = periodicEffects.iterator();
        while (iterator.hasNext()) {
            PeriodicEffect effect = iterator.next();
            LivingEntity target = effect.target;
            LivingEntity source = effect.source;
            if (target == null || target.isRemoved() || !target.isAlive() || serverTick > effect.endTick) {
                iterator.remove();
                continue;
            }
            if (serverTick < effect.nextTick) continue;
            effect.nextTick += effect.intervalTicks;
            if (effect.heal) {
                float before = target.getHealth();
                target.heal((float) effect.amount);
                double healed = Math.max(0.0D, target.getHealth() - before);
                if (healed > 0.0D && source != null) healingObserver.onHeal(source, target, healed);
            } else if (source != null && source.level() instanceof ServerLevel level && source.isAlive()) {
                SimpleServerUtilities.NPCS.hurtWithNpcScaling(level, source, target,
                        effect.kind, effect.school, effect.amount);
            }
        }
    }

    boolean tick(UUID instanceId, LivingEntity source, LivingEntity target, NpcDefinition definition,
            NpcBossPhase phase, long serverTick, DamageFilter damageFilter) {
        ActiveTick active = tickActive(instanceId, source, target, definition, phase, serverTick, damageFilter);
        if (active != null) return active.ownsMovement;
        NpcAbilityDefinition selected = choose(instanceId, source, target, definition, phase, serverTick, damageFilter);
        if (selected == null) return false;
        return start(instanceId, source, target, selected, phase, serverTick, damageFilter);
    }

    RequestedTickResult tickRequested(UUID instanceId, LivingEntity source, LivingEntity target,
            NpcDefinition definition, NpcBossPhase phase, long serverTick, String abilityId,
            DamageFilter damageFilter) {
        ActiveTick active = tickActive(instanceId, source, target, definition, phase, serverTick, damageFilter);
        if (active != null) {
            return new RequestedTickResult(active.finished ? RequestStatus.FINISHED : RequestStatus.ACTIVE, active.ownsMovement);
        }
        NpcAbilityAssignment assignment = definition == null ? null : definition.abilityAssignment(abilityId);
        NpcAbilityDefinition ability = assignment == null ? null : library.resolved(assignment.abilityId);
        if (!eligible(instanceId, source, target, definition, assignment, ability, phase, serverTick, damageFilter, false)) {
            return new RequestedTickResult(RequestStatus.UNAVAILABLE, false);
        }
        if (ability.chance <= 0.0D || source.getRandom().nextDouble() > ability.chance) {
            return new RequestedTickResult(RequestStatus.CHANCE_FAILED, false);
        }
        boolean ownsMovement = start(instanceId, source, target, ability, phase, serverTick, damageFilter);
        return new RequestedTickResult(RequestStatus.STARTED, ownsMovement);
    }

    boolean triggerScripted(UUID instanceId, LivingEntity source, LivingEntity target, NpcDefinition definition,
            NpcBossPhase phase, long serverTick, String abilityId, DamageFilter damageFilter) {
        if (instanceId == null || source == null || definition == null || abilityId == null || abilityId.isBlank()) return false;
        if (casts.containsKey(instanceId)) return false;
        NpcAbilityAssignment assignment = definition.abilityAssignment(abilityId);
        NpcAbilityDefinition ability = assignment == null ? null : library.resolved(assignment.abilityId);
        if (!eligible(instanceId, source, target, definition, assignment, ability, phase, serverTick, damageFilter, true)) return false;
        return start(instanceId, source, target, ability, phase, serverTick, damageFilter);
    }

    void resetCooldowns(UUID instanceId) {
        if (instanceId == null) return;
        casts.remove(instanceId);
        cooldowns.remove(instanceId);
        lastDamageTick.remove(instanceId);
    }

    private ActiveTick tickActive(UUID instanceId, LivingEntity source, LivingEntity target,
            NpcDefinition definition, NpcBossPhase phase, long serverTick, DamageFilter damageFilter) {
        if (instanceId == null || source == null || definition == null || !(source.level() instanceof ServerLevel level)) return null;
        Cast active = casts.get(instanceId);
        if (active == null) return null;
        NpcAbilityAssignment assignment = definition.abilityAssignment(active.abilityId);
        NpcAbilityDefinition ability = assignment == null ? null : library.resolved(assignment.abilityId);
        if (ability == null || !ability.enabled || !definition.attackKindEnabled(ability.attackKind())
                || !source.isAlive() || source.isRemoved()) {
            cancel(instanceId, source);
            return new ActiveTick(false, true);
        }
        if (assignment != null && !assignment.phaseId.isBlank() && (phase == null || !assignment.phaseId.equals(phase.id))) {
            cancel(instanceId, source);
            return new ActiveTick(false, true);
        }
        if (requiresTarget(ability) && (target == null || !target.isAlive() || target.isRemoved())) {
            cancel(instanceId, source);
            return new ActiveTick(false, true);
        }

        if (ability.requiresStationary) {
            holdStationary(source, target);
        }

        if (ability.channeling) {
            if (ability.interruptOnDamage && lastDamageTick.getOrDefault(instanceId, Long.MIN_VALUE) >= active.startTick) {
                interrupt(level, instanceId, source);
                return new ActiveTick(false, true);
            }
            if (ability.interruptOnMove && serverTick > active.startTick + 1L
                    && source.position().distanceToSqr(active.startPosition) > 0.0625D) {
                interrupt(level, instanceId, source);
                return new ActiveTick(false, true);
            }
        }

        if (ability.abilityType() == NpcAbilityType.CHARGE && target != null) {
            if (active.pulsesDone == 0) {
                // Refresh the configurable stun while the NPC is actively charging so the target
                // cannot simply walk out halfway through a longer charge.
                if (ability.stunTicks > 0) applyStun(target, Math.max(ability.stunTicks, 6));
                tickCharge(level, source, target, ability);
                if (source.distanceToSqr(target) <= 5.0D) {
                    executePulse(level, source, target, definition, ability, phase, damageFilter, 0);
                    active.pulsesDone = 1;
                    active.lastPulseTick = serverTick;
                    if (source instanceof Mob mob) mob.getNavigation().stop();
                } else if (serverTick >= active.firstPulseTick) {
                    // The pathing-aware charge timed out before contact; end cleanly rather than
                    // chasing forever under charge ownership.
                    casts.remove(instanceId);
                    return new ActiveTick(false, true);
                }
            }
        } else {
            telegraph(level, source, ability, serverTick, active.firstPulseTick);
            while (active.pulsesDone < ability.hitCount
                    && serverTick >= active.firstPulseTick + (long) active.pulsesDone * ability.pulseIntervalTicks) {
                executePulse(level, source, target, definition, ability, phase, damageFilter, active.pulsesDone);
                active.pulsesDone++;
            }
        }

        long lastPulseTick = active.firstPulseTick + (long) Math.max(0, ability.hitCount - 1) * ability.pulseIntervalTicks;
        long finishTick = lastPulseTick + ability.recoveryTicks;
        if (ability.abilityType() == NpcAbilityType.CHARGE) {
            if (active.pulsesDone == 0) return new ActiveTick(true, false);
            finishTick = active.lastPulseTick + ability.recoveryTicks;
        }
        if (serverTick >= finishTick) {
            casts.remove(instanceId);
            return new ActiveTick(false, true);
        }
        return new ActiveTick(ownsMovement(ability), false);
    }

    private boolean start(UUID instanceId, LivingEntity source, LivingEntity target,
            NpcAbilityDefinition selected, NpcBossPhase phase, long serverTick, DamageFilter damageFilter) {
        if (!(source.level() instanceof ServerLevel level)) return false;
        long firstPulse = serverTick + selected.windupTicks;
        if (selected.abilityType() == NpcAbilityType.CHARGE) {
            // Give the charge enough travel time to actually reach a medium-range target.
            firstPulse = serverTick + Math.max(selected.windupTicks, Math.min(40, (int) Math.ceil(selected.maxRange * 1.4D)));
        }
        if (selected.requiresStationary) holdStationary(source, target);
        Cast cast = new Cast(selected.id, serverTick, firstPulse, source.position());
        casts.put(instanceId, cast);
        NpcAnimationBridge.trigger(source, NpcAnimationState.CAST,
                firstPulse + (long) Math.max(0, selected.hitCount - 1) * selected.pulseIntervalTicks + selected.recoveryTicks);
        long scaledCooldown = Math.max(4L, Math.round(selected.cooldownTicks * (phase == null ? 1.0D : phase.cooldownMultiplier)));
        cooldowns.computeIfAbsent(instanceId, ignored -> new LinkedHashMap<>()).put(selected.id, serverTick + scaledCooldown);

        if (selected.abilityType() == NpcAbilityType.CHARGE && target != null) {
            applyStun(target, selected.stunTicks);
            tickCharge(level, source, target, selected);
        }
        telegraph(level, source, selected, serverTick, firstPulse);
        if (selected.windupTicks == 0 && selected.abilityType() != NpcAbilityType.CHARGE) {
            executePulse(level, source, target, null, selected, phase, damageFilter, 0);
            cast.pulsesDone = 1;
        }
        return ownsMovement(selected);
    }

    private NpcAbilityDefinition choose(UUID instanceId, LivingEntity source, LivingEntity target,
            NpcDefinition definition, NpcBossPhase phase, long serverTick, DamageFilter damageFilter) {
        if (definition.abilityAssignments == null || definition.abilityAssignments.isEmpty()) return null;
        List<NpcAbilityDefinition> candidates = new ArrayList<>();
        for (NpcAbilityAssignment assignment : definition.abilityAssignments) {
            if (assignment == null || !assignment.configured()) continue;
            NpcAbilityDefinition raw = library.resolved(assignment.abilityId);
            if (!eligible(instanceId, source, target, definition, assignment, raw, phase, serverTick, damageFilter, false)) continue;
            if (raw.chance <= 0.0D || source.getRandom().nextDouble() > raw.chance) continue;
            candidates.add(raw);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(source.getRandom().nextInt(candidates.size()));
    }

    private boolean eligible(UUID instanceId, LivingEntity source, LivingEntity target, NpcDefinition definition,
            NpcAbilityAssignment assignment, NpcAbilityDefinition ability, NpcBossPhase phase, long serverTick,
            DamageFilter damageFilter, boolean ignoreCooldown) {
        if (instanceId == null || source == null || definition == null || assignment == null || ability == null || !ability.enabled) return false;
        if (!definition.attackKindEnabled(ability.attackKind())) return false;
        String phaseId = phase == null ? "" : phase.id;
        if (!assignment.phaseId.isBlank() && !assignment.phaseId.equals(phaseId)) return false;
        Map<String, Long> ready = cooldowns.get(instanceId);
        if (!ignoreCooldown && ready != null && ready.getOrDefault(ability.id, 0L) > serverTick) return false;
        NpcAbilityType type = ability.abilityType();
        if (requiresTarget(ability) && (target == null || !target.isAlive() || target.isRemoved())) return false;
        if (type == NpcAbilityType.SELF_HEAL && source.getHealth() >= source.getMaxHealth() - 0.5F) return false;
        // Self-centered AoE abilities reason about enemies inside their actual radius/shape below;
        // they must not fire merely because a distant combat target exists, nor be blocked by that
        // distant target when another valid enemy is already standing next to the caster.
        if (requiresTarget(ability) && target != null && ability.abilityShape() != NpcAbilityShape.AROUND_SELF) {
            double distance = Math.sqrt(source.distanceToSqr(target));
            if (distance < ability.minRange || distance > ability.maxRange) return false;
        }
        if ((type == NpcAbilityType.WEAPON_RANGED || ability.attackKind() == NpcAttackKind.RANGED)
                && ability.damageUsesEquipment && !NpcCombatEquipment.hasRangedWeapon(source)) return false;
        if (ability.minTargets > 0) {
            if (!(source.level() instanceof ServerLevel level)) return false;
            int targets = countTargets(level, source, target, ability, damageFilter);
            if (targets < ability.minTargets) return false;
        }
        return true;
    }

    private static int countTargets(ServerLevel level, LivingEntity source, LivingEntity primary,
            NpcAbilityDefinition ability, DamageFilter filter) {
        if (ability == null || filter == null) return 0;
        return targets(level, source, primary, ability, filter).size();
    }

    private void executePulse(ServerLevel level, LivingEntity source, LivingEntity primaryTarget,
            NpcDefinition definition, NpcAbilityDefinition ability, NpcBossPhase phase,
            DamageFilter damageFilter, int pulseIndex) {
        double phaseDamage = phase == null ? 1.0D : phase.abilityDamageMultiplier;
        NpcAbilityType type = ability.abilityType();

        if (type == NpcAbilityType.SELF_HEAL) {
            heal(level, source, source, ability.healAmount);
            if (ability.hotAmount > 0.0D && ability.hotDurationTicks > 0) {
                schedulePeriodic(source, source, ability.hotAmount, ability.hotDurationTicks, ability.hotIntervalTicks,
                        NpcAttackKind.MAGIC, NpcDamageSchool.ARCANE, true);
            }
            particles(level, source, ability, primaryTarget, pulseIndex);
            return;
        }
        if (type == NpcAbilityType.LEAP && primaryTarget != null && pulseIndex == 0) {
            Vec3 direction = primaryTarget.position().subtract(source.position());
            Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
            if (horizontal.lengthSqr() > 1.0E-6D) {
                horizontal = horizontal.normalize().scale(Math.max(0.35D, Math.min(1.8D, ability.knockback + 0.45D)));
                source.setDeltaMovement(horizontal.x, Math.max(0.36D, Math.min(0.9D, 0.36D + ability.radius * 0.04D)), horizontal.z);
            }
            particles(level, source, ability, primaryTarget, pulseIndex);
            return;
        }

        if (ability.healAmount > 0.0D) heal(level, source, source, ability.healAmount);
        if (ability.hotAmount > 0.0D && ability.hotDurationTicks > 0) {
            schedulePeriodic(source, source, ability.hotAmount, ability.hotDurationTicks, ability.hotIntervalTicks,
                    NpcAttackKind.MAGIC, NpcDamageSchool.ARCANE, true);
        }

        for (LivingEntity target : targets(level, source, primaryTarget, ability, damageFilter)) {
            if (target == source || !target.isAlive() || target.isRemoved()) continue;
            if (ability.damageUsesEquipment && ability.damage > 0.0D) {
                SimpleServerUtilities.NPCS.performEquipmentAbilityHit(level, source, target,
                        ability.attackKind(), ability.damageSchool(), ability.damage * phaseDamage);
            } else if (ability.damage > 0.0D) {
                SimpleServerUtilities.NPCS.hurtWithNpcScaling(level, source, target,
                        ability.attackKind(), ability.damageSchool(), ability.damage * phaseDamage);
            }
            if (ability.knockback > 0.0D) pushAway(source, target, ability.knockback,
                    type == NpcAbilityType.THUNDERCLAP ? 0.22D : 0.08D);
            if (ability.stunTicks > 0 && type != NpcAbilityType.CHARGE) applyStun(target, ability.stunTicks);
            if (ability.slowTicks > 0) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    ability.slowTicks, ability.slowAmplifier, false, true, true), source);
            applyConfiguredEffect(target, source, ability);
            if (ability.bleedDamage > 0.0D && ability.bleedDurationTicks > 0) {
                schedulePeriodic(source, target, ability.bleedDamage, ability.bleedDurationTicks, ability.bleedIntervalTicks,
                        NpcAttackKind.MELEE, NpcDamageSchool.PHYSICAL, false);
            }
            if (ability.dotDamage > 0.0D && ability.dotDurationTicks > 0) {
                schedulePeriodic(source, target, ability.dotDamage, ability.dotDurationTicks, ability.dotIntervalTicks,
                        ability.attackKind(), ability.damageSchool(), false);
            }
            if (ability.damageSchool() == NpcDamageSchool.FIRE) target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 60));
        }
        if (type == NpcAbilityType.THUNDERCLAP && pulseIndex == 0) playSound(level, source, "minecraft:entity.lightning_bolt.thunder", 1.25F, 1.05F);
        particles(level, source, ability, primaryTarget, pulseIndex);
    }

    private static List<LivingEntity> targets(ServerLevel level, LivingEntity source, LivingEntity primary,
            NpcAbilityDefinition ability, DamageFilter filter) {
        List<LivingEntity> out = new ArrayList<>();
        switch (ability.abilityShape()) {
            case SINGLE -> {
                if (primary != null && filter.mayDamage(primary)) out.add(primary);
            }
            case AROUND_SELF -> collectArea(level, source, source.position(), ability.radius, filter, out);
            case AROUND_TARGET -> {
                Vec3 center = primary == null ? source.position() : primary.position();
                collectArea(level, source, center, ability.radius, filter, out);
            }
            case CONE -> {
                double radius = ability.radius;
                // Eligibility must not depend on a stale body rotation from the previous AI state.
                // Aim the selection cone at the current combat target when available; the look
                // controller will visually turn the NPC during the ability preparation itself.
                Vec3 look = primary != null
                        ? primary.getBoundingBox().getCenter().subtract(source.getBoundingBox().getCenter())
                        : source.getLookAngle();
                Vec3 flatLook = new Vec3(look.x, 0.0D, look.z);
                if (flatLook.lengthSqr() < 1.0E-6D) flatLook = new Vec3(0, 0, 1);
                flatLook = flatLook.normalize();
                double minDot = Math.cos(Math.toRadians(ability.coneAngleDegrees * 0.5D));
                for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, source.getBoundingBox().inflate(radius))) {
                    if (candidate == source || !filter.mayDamage(candidate)) continue;
                    Vec3 delta = candidate.position().subtract(source.position());
                    Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
                    if (flat.lengthSqr() > radius * radius || flat.lengthSqr() < 1.0E-6D) continue;
                    if (flat.normalize().dot(flatLook) >= minDot) out.add(candidate);
                }
            }
        }
        return out;
    }

    private static void applyConfiguredEffect(LivingEntity target, LivingEntity source, NpcAbilityDefinition ability) {
        if (target == null || ability == null || ability.debuffEffect == null || ability.debuffEffect.isBlank()
                || ability.debuffDurationTicks <= 0) return;
        try {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(Identifier.parse(ability.debuffEffect)).orElse(null);
            if (effect != null) {
                target.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                        ability.debuffDurationTicks, ability.debuffAmplifier, false, true, true), source);
            }
        } catch (RuntimeException ignored) {
            // Invalid/missing modded effect IDs are rejected by normalization or ignored safely at runtime.
        }
    }

    private static void collectArea(ServerLevel level, LivingEntity source, Vec3 center, double radius,
            DamageFilter filter, List<LivingEntity> out) {
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        double radiusSqr = radius * radius;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == source || !candidate.isAlive() || candidate.isRemoved()
                    || candidate.position().distanceToSqr(center) > radiusSqr || !filter.mayDamage(candidate)) continue;
            out.add(candidate);
        }
    }

    private void schedulePeriodic(LivingEntity source, LivingEntity target, double amount, int duration, int interval,
            NpcAttackKind kind, NpcDamageSchool school, boolean heal) {
        if (!(amount > 0.0D) || duration <= 0 || target == null) return;
        long now = source.level().getServer() == null ? 0L : source.level().getServer().getTickCount();
        periodicEffects.add(new PeriodicEffect(source, target, amount, Math.max(1, interval), now + Math.max(1, interval),
                now + duration, kind, school, heal));
    }

    private void heal(ServerLevel level, LivingEntity healer, LivingEntity target, double amount) {
        if (!(amount > 0.0D)) return;
        float before = target.getHealth();
        target.heal((float) amount);
        double healed = Math.max(0.0D, target.getHealth() - before);
        if (healed > 0.0D) healingObserver.onHeal(healer, target, healed);
    }


    private static boolean requiresTarget(NpcAbilityDefinition ability) {
        if (ability == null) return false;
        if (ability.abilityShape() == NpcAbilityShape.AROUND_SELF) return false;
        return ability.abilityType().targetRequired();
    }

    private static boolean ownsMovement(NpcAbilityDefinition ability) {
        return ability.abilityType().locksMovement() || ability.requiresStationary;
    }

    private static void holdStationary(LivingEntity source, LivingEntity target) {
        if (source instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.getMoveControl().setWait();
            if (target != null) mob.getLookControl().setLookAt(target, 45.0F, 45.0F);
        }
        Vec3 velocity = source.getDeltaMovement();
        source.setDeltaMovement(0.0D, velocity.y, 0.0D);
    }

    private static void tickCharge(ServerLevel level, LivingEntity source, LivingEntity target, NpcAbilityDefinition ability) {
        if (source instanceof Mob mob) {
            mob.getNavigation().moveTo(target, Math.max(0.2D, ability.chargeSpeed));
            mob.getLookControl().setLookAt(target, 45.0F, 45.0F);
        } else {
            Vec3 delta = target.position().subtract(source.position());
            if (delta.lengthSqr() > 1.0E-6D) source.setDeltaMovement(delta.normalize().scale(Math.min(1.4D, ability.chargeSpeed * 0.2D)));
        }
        level.sendParticles(ParticleTypes.CLOUD, source.getX(), source.getY() + 0.1D, source.getZ(), 3,
                0.18D, 0.05D, 0.18D, 0.01D);
    }

    private static void applyStun(LivingEntity target, int ticks) {
        if (target == null || ticks <= 0) return;
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 10, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 10, false, true, true));
        target.setDeltaMovement(Vec3.ZERO);
        if (target instanceof Mob mob) mob.getNavigation().stop();
    }

    private static void pushAway(LivingEntity source, LivingEntity target, double strength, double upward) {
        if (!(strength > 0.0D)) return;
        Vec3 away = target.position().subtract(source.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 1.0E-6D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        horizontal = horizontal.normalize().scale(Math.min(4.0D, strength));
        Vec3 current = target.getDeltaMovement();
        target.setDeltaMovement(horizontal.x, Math.max(current.y, upward), horizontal.z);
    }

    private static void particles(ServerLevel level, LivingEntity source, NpcAbilityDefinition ability,
            LivingEntity target, int pulse) {
        NpcAbilityType type = ability.abilityType();
        if (type == NpcAbilityType.ARCANE_MISSILES || ability.damageSchool() == NpcDamageSchool.ARCANE) {
            arcaneMissiles(level, source, target, ability, pulse);
        } else if (type == NpcAbilityType.FIREBALL || ability.damageSchool() == NpcDamageSchool.FIRE) {
            beam(level, source, target, ParticleTypes.FLAME);
        } else if (type == NpcAbilityType.ICE_BALL || ability.damageSchool() == NpcDamageSchool.ICE) {
            beam(level, source, target, ParticleTypes.SNOWFLAKE);
        } else if (type == NpcAbilityType.SLASH || type == NpcAbilityType.MORTAL_STRIKE || type == NpcAbilityType.BLADESTORM) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, source.getX(), source.getY() + source.getBbHeight() * 0.55D,
                    source.getZ(), Math.max(1, type == NpcAbilityType.BLADESTORM ? 4 : 1), 0.5D, 0.2D, 0.5D, 0.0D);
        } else if (type == NpcAbilityType.ARROW_VOLLEY) {
            Vec3 center = target == null ? source.position() : target.position();
            level.sendParticles(ParticleTypes.CRIT, center.x, center.y + 2.0D, center.z, 12, ability.radius * 0.5D, 1.0D,
                    ability.radius * 0.5D, 0.08D);
        } else if (type == NpcAbilityType.THUNDERCLAP || type == NpcAbilityType.SHOCKWAVE) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, source.getX(), source.getY() + 0.2D, source.getZ(),
                    Math.max(12, (int) Math.round(ability.radius * 8.0D)), ability.radius * 0.6D, 0.2D, ability.radius * 0.6D, 0.08D);
        } else {
            level.sendParticles(ParticleTypes.FIREWORK, source.getX(), source.getY() + source.getBbHeight() * 0.55D,
                    source.getZ(), 8, 0.35D, 0.35D, 0.35D, 0.04D);
        }
    }

    private static void arcaneMissiles(ServerLevel level, LivingEntity source, LivingEntity target,
            NpcAbilityDefinition ability, int pulse) {
        if (target == null) return;
        Vec3 start = source.getEyePosition().add(source.getLookAngle().scale(0.18D));
        Vec3 end = target.getBoundingBox().getCenter().add(0.0D, target.getBbHeight() * 0.08D, 0.0D);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0E-6D) return;

        Vec3 forward = delta.scale(1.0D / length);
        Vec3 axisSeed = Math.abs(forward.y) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = forward.cross(axisSeed);
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize();
        Vec3 normal = side.cross(forward);
        if (normal.lengthSqr() < 1.0E-6D) normal = new Vec3(0.0D, 1.0D, 0.0D);
        normal = normal.normalize();

        DustParticleOptions beamCore = new DustParticleOptions(0xA95CFF, 1.20F);
        DustParticleOptions beamGlow = new DustParticleOptions(0xE7C9FF, 0.85F);
        DustParticleOptions orbCore = new DustParticleOptions(0x7B2CFF, 1.65F);
        DustParticleOptions orbGlow = new DustParticleOptions(0xF5E9FF, 0.95F);
        DustParticleOptions impactDust = new DustParticleOptions(0xC78BFF, 1.10F);

        int steps = Math.max(20, Math.min(96, (int) Math.ceil(length * 9.0D)));
        double amplitude = Math.min(0.42D, Math.max(0.14D, 0.08D + length * 0.016D));
        double ribbonCycles = Math.max(1.60D, Math.min(4.20D, 1.20D + length * 0.18D));
        double phase = pulse * (Math.PI * 0.72D);

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            Vec3 point = start.add(delta.scale(t));
            double angle = t * ribbonCycles * Math.PI * 2.0D + phase;
            double sideways = Math.sin(angle) * amplitude;
            double vertical = Math.cos(angle) * amplitude * 0.58D;

            Vec3 ribbonA = point.add(side.scale(sideways)).add(normal.scale(vertical));
            Vec3 ribbonB = point.add(side.scale(-sideways)).add(normal.scale(-vertical));

            level.sendParticles(beamCore, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            level.sendParticles(beamGlow, ribbonA.x, ribbonA.y, ribbonA.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
            level.sendParticles(beamGlow, ribbonB.x, ribbonB.y, ribbonB.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
            if ((step & 3) == 0) {
                level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z,
                        1, 0.03D, 0.03D, 0.03D, 0.0D);
            }
        }

        int orbs = 3;
        for (int index = 0; index < orbs; index++) {
            double orbT = 0.18D + 0.22D * index + (pulse % 3) * 0.045D;
            if (orbT > 0.92D) orbT -= 0.58D;
            double angle = orbT * ribbonCycles * Math.PI * 2.0D + phase;
            Vec3 center = start.add(delta.scale(orbT))
                    .add(side.scale(Math.sin(angle) * amplitude * 1.10D))
                    .add(normal.scale(Math.cos(angle) * amplitude * 0.72D));
            level.sendParticles(orbCore, center.x, center.y, center.z, 6, 0.035D, 0.035D, 0.035D, 0.01D);
            level.sendParticles(orbGlow, center.x, center.y, center.z, 2, 0.09D, 0.09D, 0.09D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }

        level.sendParticles(impactDust, end.x, end.y, end.z, 16, 0.18D, 0.18D, 0.18D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 5, 0.12D, 0.12D, 0.12D, 0.01D);
        level.sendParticles(ParticleTypes.WITCH, end.x, end.y, end.z, 8, 0.16D, 0.16D, 0.16D, 0.0D);
        playSound(level, source, "minecraft:block.amethyst_block.resonate", 0.75F, 1.35F + pulse * 0.05F);
    }

    private static <T extends net.minecraft.core.particles.ParticleOptions> void beam(ServerLevel level,
            LivingEntity source, LivingEntity target, T particle) {
        if (target == null) return;
        Vec3 start = source.getEyePosition();
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 delta = end.subtract(start);
        int steps = Math.max(4, Math.min(32, (int) Math.ceil(delta.length() * 2.0D)));
        for (int index = 1; index <= steps; index++) {
            Vec3 point = start.add(delta.scale(index / (double) steps));
            level.sendParticles(particle, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    private static void telegraph(ServerLevel level, LivingEntity source, NpcAbilityDefinition ability,
            long serverTick, long executeTick) {
        if (serverTick > executeTick || (serverTick & 1L) != 0L) return;
        if (ability.abilityType() == NpcAbilityType.ARCANE_MISSILES || ability.damageSchool() == NpcDamageSchool.ARCANE) {
            arcaneTelegraph(level, source, serverTick, executeTick);
            return;
        }
        double radius = switch (ability.abilityShape()) {
            case AROUND_SELF, AROUND_TARGET, CONE -> ability.radius;
            case SINGLE -> 0.55D;
        };
        level.sendParticles(ParticleTypes.END_ROD, source.getX(), source.getY() + 0.15D, source.getZ(),
                Math.max(2, (int) Math.round(radius * 3.0D)), radius * 0.55D, 0.08D, radius * 0.55D, 0.01D);
    }

    private static void arcaneTelegraph(ServerLevel level, LivingEntity source, long serverTick, long executeTick) {
        double remaining = Math.max(0.0D, executeTick - serverTick);
        double charge = 1.0D - Math.min(1.0D, remaining / 16.0D);
        double centerY = source.getY() + source.getBbHeight() * (0.58D + charge * 0.08D);
        DustParticleOptions core = new DustParticleOptions(0x9C4DFF, 1.0F);
        DustParticleOptions glow = new DustParticleOptions(0xEED8FF, 0.75F);
        double radius = 0.28D + charge * 0.16D;
        int points = 8;
        for (int index = 0; index < points; index++) {
            double angle = (serverTick * 0.32D) + (Math.PI * 2.0D * index / points);
            double x = source.getX() + Math.cos(angle) * radius;
            double z = source.getZ() + Math.sin(angle) * radius;
            level.sendParticles(core, x, centerY, z, 1, 0.01D, 0.03D, 0.01D, 0.0D);
            if ((index & 1) == 0) {
                level.sendParticles(glow, x, centerY + 0.08D, z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.WITCH, source.getX(), centerY, source.getZ(), 3,
                0.10D + charge * 0.08D, 0.12D, 0.10D + charge * 0.08D, 0.0D);
    }

    private static void playSound(ServerLevel level, LivingEntity source, String soundId, float volume, float pitch) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(soundId)).orElse(null);
        if (sound == null) return;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(source) > 48.0D * 48.0D) continue;
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                    SoundSource.HOSTILE, source.getX(), source.getY(), source.getZ(), volume, pitch,
                    level.getServer().getTickCount() ^ source.getUUID().getLeastSignificantBits()));
        }
    }

    private void interrupt(ServerLevel level, UUID instanceId, LivingEntity source) {
        casts.remove(instanceId);
        castsStop(source);
        level.sendParticles(ParticleTypes.SMOKE, source.getX(), source.getY() + source.getBbHeight() * 0.6D, source.getZ(),
                8, 0.25D, 0.35D, 0.25D, 0.02D);
    }

    private void cancel(UUID instanceId, LivingEntity source) {
        casts.remove(instanceId);
        castsStop(source);
    }

    private static void castsStop(LivingEntity source) {
        if (source instanceof Mob mob) mob.getNavigation().stop();
    }

    private record ActiveTick(boolean ownsMovement, boolean finished) { }

    private static final class Cast {
        final String abilityId;
        final long startTick;
        final long firstPulseTick;
        final Vec3 startPosition;
        int pulsesDone;
        long lastPulseTick = -1L;
        Cast(String abilityId, long startTick, long firstPulseTick, Vec3 startPosition) {
            this.abilityId = abilityId;
            this.startTick = startTick;
            this.firstPulseTick = firstPulseTick;
            this.startPosition = startPosition;
        }
    }

    private static final class PeriodicEffect {
        final LivingEntity source;
        final LivingEntity target;
        final double amount;
        final int intervalTicks;
        long nextTick;
        final long endTick;
        final NpcAttackKind kind;
        final NpcDamageSchool school;
        final boolean heal;
        PeriodicEffect(LivingEntity source, LivingEntity target, double amount, int intervalTicks, long nextTick,
                long endTick, NpcAttackKind kind, NpcDamageSchool school, boolean heal) {
            this.source = source; this.target = target; this.amount = amount; this.intervalTicks = intervalTicks;
            this.nextTick = nextTick; this.endTick = endTick; this.kind = kind; this.school = school; this.heal = heal;
        }
    }
}
