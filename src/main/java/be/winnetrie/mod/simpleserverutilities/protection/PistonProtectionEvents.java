package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * Validates the complete piston structure, including slime/honey branches and
 * blocks destroyed by the piston. The previous implementation only checked the
 * two blocks directly in front of the piston.
 */
public final class PistonProtectionEvents {

    private PistonProtectionEvents() {
    }

    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }

        if (!ProtectionHelper.canPistonMove(level, event.getPos(), event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }

        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }

        for (BlockPos sourcePos : resolver.getToPush()) {
            BlockPos targetPos = sourcePos.relative(resolver.getPushDirection());
            if (!ProtectionHelper.canPistonMove(level, sourcePos, targetPos)) {
                event.setCanceled(true);
                return;
            }
        }

        for (BlockPos destroyedPos : resolver.getToDestroy()) {
            if (!ProtectionHelper.canPistonMove(level, destroyedPos, destroyedPos)) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
