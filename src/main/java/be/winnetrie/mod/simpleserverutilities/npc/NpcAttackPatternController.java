package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;

/** Runtime cursor for ordered combat patterns. Pattern state is encounter-local and never persisted. */
final class NpcAttackPatternController {
    private final Map<UUID, Integer> cursors = new LinkedHashMap<>();

    void clear() { cursors.clear(); }
    void forget(UUID instanceId) { if (instanceId != null) cursors.remove(instanceId); }
    void reset(UUID instanceId) { if (instanceId != null) cursors.put(instanceId, 0); }

    Selection select(UUID instanceId, NpcDefinition definition, NpcBossPhase phase,
            LivingEntity source, LivingEntity target) {
        if (instanceId == null || definition == null || source == null || target == null
                || !definition.attackPatternEnabled || definition.attackPattern == null
                || definition.attackPattern.isEmpty()) return null;
        List<NpcAttackPatternStep> steps = definition.attackPattern;
        int size = steps.size();
        int start = Math.floorMod(cursors.getOrDefault(instanceId, 0), size);
        double distance = Math.sqrt(source.distanceToSqr(target));
        double healthPercent = source.getMaxHealth() <= 0.0F ? 100.0D
                : source.getHealth() * 100.0D / source.getMaxHealth();
        for (int offset = 0; offset < size; offset++) {
            int index = (start + offset) % size;
            NpcAttackPatternStep step = steps.get(index);
            if (step == null || !step.matches(phase, distance, healthPercent)) continue;
            cursors.put(instanceId, index);
            return new Selection(index, step);
        }
        return null;
    }

    void advance(UUID instanceId, NpcDefinition definition, int completedIndex) {
        if (instanceId == null || definition == null || definition.attackPattern == null
                || definition.attackPattern.isEmpty()) return;
        cursors.put(instanceId, (completedIndex + 1) % definition.attackPattern.size());
    }

    record Selection(int index, NpcAttackPatternStep step) { }
}
