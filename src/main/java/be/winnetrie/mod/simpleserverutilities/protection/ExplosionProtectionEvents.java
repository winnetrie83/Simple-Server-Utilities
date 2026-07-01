package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public class ExplosionProtectionEvents {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();

        event.getAffectedBlocks().removeIf(pos ->
                !ProtectionHelper.canExplosionAffect(level, pos)
        );

        event.getAffectedEntities().removeIf(entity ->
                !ProtectionHelper.canExplosionAffect(level, entity.blockPosition())
        );
    }
}