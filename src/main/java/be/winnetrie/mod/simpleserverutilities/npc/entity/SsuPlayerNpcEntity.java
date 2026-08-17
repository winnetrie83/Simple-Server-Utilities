package be.winnetrie.mod.simpleserverutilities.npc.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Native physical runtime for SSU player-model NPCs.
 *
 * <p>The entity intentionally owns no vanilla goals. SSU's NPC controllers remain the
 * authoritative behavior layer, while extending {@link PathfinderMob} gives player-model
 * NPCs Minecraft's normal mob physics and {@code PathNavigation} implementation.</p>
 */
public final class SsuPlayerNpcEntity extends PathfinderMob {
    public SsuPlayerNpcEntity(EntityType<? extends SsuPlayerNpcEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // SSU owns schedules, patrols, wandering, reactions and combat targeting.
        // Keeping the vanilla goal selectors empty prevents competing AI controllers.
    }
}
