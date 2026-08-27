package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Protects both the local explosion flag and SSU protection boundaries.
 * An explosion may only affect blocks/entities in the same protection area as
 * its centre; the area's normal allowExplosions setting is then applied.
 */
public class ExplosionProtectionEvents {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        Level level = event.getLevel();
        BlockPos source = BlockPos.containing(event.getExplosion().center());

        event.getAffectedBlocks().removeIf(pos ->
                !ProtectionBoundary.canCross(level, source, pos)
                        || !ProtectionHelper.canExplosionAffect(level, pos)
        );

        event.getAffectedEntities().removeIf(entity -> {
            BlockPos target = entity.blockPosition();
            return !ProtectionBoundary.canCross(level, source, target)
                    || !ProtectionHelper.canExplosionAffect(level, target);
        });
    }
}
