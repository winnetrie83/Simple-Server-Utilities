package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Encounter-local threat tables for SSU NPCs. Values are deliberately not persisted: logging out,
 * despawning or resetting an encounter starts a clean aggro table.
 */
final class NpcThreatController {
    interface TargetFilter { boolean mayTarget(LivingEntity target); }

    private final Map<UUID, Map<UUID, ThreatEntry>> tables = new LinkedHashMap<>();
    private final Map<UUID, ForcedTarget> forcedTargets = new LinkedHashMap<>();

    void clear() { tables.clear(); forcedTargets.clear(); }
    boolean hasThreat(UUID instanceId) {
        Map<UUID, ThreatEntry> table = instanceId == null ? null : tables.get(instanceId);
        return table != null && !table.isEmpty();
    }
    boolean contains(UUID instanceId, UUID targetId) {
        Map<UUID, ThreatEntry> table = instanceId == null ? null : tables.get(instanceId);
        return table != null && targetId != null && table.containsKey(targetId);
    }
    void forget(UUID instanceId) {
        if (instanceId == null) return;
        tables.remove(instanceId);
        forcedTargets.remove(instanceId);
    }

    void add(UUID instanceId, LivingEntity target, double amount, long serverTick) {
        if (instanceId == null || target == null || !(amount > 0.0D) || !Double.isFinite(amount)) return;
        Map<UUID, ThreatEntry> table = tables.computeIfAbsent(instanceId, ignored -> new LinkedHashMap<>());
        ThreatEntry previous = table.get(target.getUUID());
        double value = (previous == null ? 0.0D : previous.value) + Math.min(1_000_000_000.0D, amount);
        table.put(target.getUUID(), new ThreatEntry(Math.min(1_000_000_000.0D, value), serverTick));
    }

    void ensure(UUID instanceId, LivingEntity target, double minimum, long serverTick) {
        if (instanceId == null || target == null || !(minimum > 0.0D)) return;
        Map<UUID, ThreatEntry> table = tables.computeIfAbsent(instanceId, ignored -> new LinkedHashMap<>());
        ThreatEntry previous = table.get(target.getUUID());
        if (previous == null || previous.value < minimum) table.put(target.getUUID(), new ThreatEntry(minimum, serverTick));
    }

    void taunt(UUID instanceId, LivingEntity target, double bonusThreat, long serverTick, long durationTicks) {
        if (instanceId == null || target == null) return;
        double highest = 0.0D;
        Map<UUID, ThreatEntry> table = tables.get(instanceId);
        if (table != null) for (ThreatEntry entry : table.values()) highest = Math.max(highest, entry.value);
        ensure(instanceId, target, highest + Math.max(1.0D, bonusThreat), serverTick);
        forcedTargets.put(instanceId, new ForcedTarget(target.getUUID(), serverTick + Math.max(1L, durationTicks)));
    }

    LivingEntity select(UUID instanceId, LivingEntity source, NpcDefinition definition, long serverTick,
            UUID currentTargetId, TargetFilter filter) {
        if (instanceId == null || source == null || definition == null
                || !(source.level() instanceof ServerLevel level)) return null;

        // Forced targets are a general encounter primitive (for example boss Fixate), not only a
        // threat-table feature. Resolve them before the threat-enabled gate so a scripted fixate
        // remains authoritative even on bosses that do not otherwise use threat/aggro tables.
        ForcedTarget forced = forcedTargets.get(instanceId);
        if (forced != null) {
            if (forced.expiresTick >= serverTick) {
                LivingEntity target = living(level, forced.targetId);
                if (validForced(source, target, definition, filter)) return target;
            }
            forcedTargets.remove(instanceId);
        }

        if (!definition.threatEnabled) return null;
        Map<UUID, ThreatEntry> table = tables.get(instanceId);
        if (table == null || table.isEmpty()) return null;

        LivingEntity best = null;
        double bestThreat = 0.0D;
        LivingEntity current = null;
        double currentThreat = 0.0D;
        Iterator<Map.Entry<UUID, ThreatEntry>> iterator = table.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ThreatEntry> row = iterator.next();
            LivingEntity candidate = living(level, row.getKey());
            if (!valid(source, candidate, definition, filter)) { iterator.remove(); continue; }
            ThreatEntry entry = row.getValue();
            double elapsedSeconds = Math.max(0L, serverTick - entry.updatedTick) / 20.0D;
            double value = entry.value - definition.threatDecayPerSecond * elapsedSeconds;
            if (!(value > 0.0001D)) { iterator.remove(); continue; }
            // Rebase occasionally so very old entries do not keep repeatedly applying the same elapsed decay.
            if (serverTick - entry.updatedTick >= 20L) {
                entry = new ThreatEntry(value, serverTick);
                row.setValue(entry);
            }
            if (row.getKey().equals(currentTargetId)) { current = candidate; currentThreat = value; }
            if (value > bestThreat) { bestThreat = value; best = candidate; }
        }
        if (table.isEmpty()) tables.remove(instanceId);
        if (best == null) return null;
        if (current != null && current != best && currentThreat > 0.0D
                && bestThreat < currentThreat * definition.threatSwitchRatio) return current;
        return best;
    }

    private static LivingEntity living(ServerLevel level, UUID id) {
        Entity entity = id == null ? null : level.getEntity(id);
        return entity instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }

    private static boolean valid(LivingEntity source, LivingEntity target, NpcDefinition definition, TargetFilter filter) {
        if (!baseValid(source, target, filter)) return false;
        double range = definition.threatRange;
        return source.distanceToSqr(target) <= range * range;
    }

    private static boolean validForced(LivingEntity source, LivingEntity target, NpcDefinition definition, TargetFilter filter) {
        if (!baseValid(source, target, filter)) return false;
        // Boss fixates are encounter mechanics and may legitimately span farther than the normal
        // threat radius. Keep them bounded by the largest configured combat/boss visibility range.
        double follow = definition.followRange > 0.0D ? definition.followRange : 16.0D;
        double range = Math.max(definition.threatRange, follow);
        if (definition.bossEnabled) range = Math.max(range, definition.bossBarRange);
        return source.distanceToSqr(target) <= range * range;
    }

    private static boolean baseValid(LivingEntity source, LivingEntity target, TargetFilter filter) {
        if (target == null || target == source || target.level() != source.level()) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return filter == null || filter.mayTarget(target);
    }

    private record ThreatEntry(double value, long updatedTick) { }
    private record ForcedTarget(UUID targetId, long expiresTick) { }
}
