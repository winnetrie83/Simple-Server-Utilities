package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

public class PistonProtectionEvents {

    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }

        Direction direction = event.getDirection();

        BlockPos pistonPos = event.getPos();
        BlockPos frontPos = pistonPos.relative(direction);
        BlockPos targetPos = frontPos.relative(direction);

        if (!ProtectionHelper.canPistonMove(level, pistonPos, frontPos)) {
            event.setCanceled(true);
            return;
        }

        if (!ProtectionHelper.canPistonMove(level, frontPos, targetPos)) {
            event.setCanceled(true);
        }
    }
}