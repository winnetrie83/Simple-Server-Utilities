package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Enforces the existing allowRedstone claim and region setting. The event is
 * only cancelled for updates emitted by a redstone signal source or redstone
 * wire; unrelated neighbour/physics updates are left untouched.
 */
public final class RedstoneProtectionEvents {

    private RedstoneProtectionEvents() {
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }

        BlockState sourceState = event.getState();
        if (!isRedstoneComponent(sourceState) && !event.getForceRedstoneUpdate()) {
            return;
        }

        BlockPos sourcePos = event.getPos();
        for (Direction direction : event.getNotifiedSides()) {
            if (!ProtectionHelper.canRedstoneAffect(level, sourcePos, sourcePos.relative(direction))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static boolean isRedstoneComponent(BlockState state) {
        return state.isSignalSource() || state.getBlock() instanceof RedStoneWireBlock;
    }
}
