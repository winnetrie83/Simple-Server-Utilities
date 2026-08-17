package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.LivingEntity;

/**
 * Semantic animation-state bridge retained for SSU's own future rendering work. The current
 * dev3.32 Entity and Player modes use Minecraft's native model animations.
 */
public final class NpcAnimationBridge {
    private record Override(NpcAnimationState state, long untilTick) { }
    private static final Map<UUID, Override> OVERRIDES = new ConcurrentHashMap<>();

    private NpcAnimationBridge() {}

    public static void trigger(LivingEntity entity, NpcAnimationState state, long untilTick) {
        if (entity == null || state == null) return;
        OVERRIDES.put(entity.getUUID(), new Override(state, Math.max(entity.tickCount + 1L, untilTick)));
    }

    public static NpcAnimationState state(LivingEntity entity, long currentTick) {
        if (entity == null) return NpcAnimationState.IDLE;
        if (!entity.isAlive() || entity.isDeadOrDying()) return NpcAnimationState.DEATH;
        if (entity.hurtTime > 0) return NpcAnimationState.HURT;
        Override override = OVERRIDES.get(entity.getUUID());
        if (override != null) {
            if (currentTick <= override.untilTick) return override.state;
            OVERRIDES.remove(entity.getUUID(), override);
        }
        return entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-5D
                ? NpcAnimationState.WALK : NpcAnimationState.IDLE;
    }

    public static String animationName(NpcDefinition definition, NpcAnimationState state) {
        if (definition == null) return "idle";
        return switch (state == null ? NpcAnimationState.IDLE : state) {
            case IDLE -> definition.idleAnimation;
            case WALK -> definition.walkAnimation;
            case ATTACK -> definition.attackAnimation;
            case CAST -> definition.castAnimation;
            case HURT -> definition.hurtAnimation;
            case DEATH -> definition.deathAnimation;
        };
    }

    public static void forget(UUID runtimeEntityId) {
        if (runtimeEntityId != null) OVERRIDES.remove(runtimeEntityId);
    }

    public static void clear() {
        OVERRIDES.clear();
    }
}
