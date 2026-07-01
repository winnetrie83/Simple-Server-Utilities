package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class FireProtectionEvents {

    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!event.getNewState().is(Blocks.FIRE)) {
            return;
        }

        if (ProtectionHelper.canFireAffect(event.getLevel(), event.getPos())) {
            return;
        }

        // Important:
        // In this NeoForge version, cancelling FluidPlaceBlockEvent is not enough.
        // The hook returns event.getNewState(), so we must replace the fire state.
        event.setNewState(event.getOriginalState());
    }
}