package be.winnetrie.mod.simpleserverutilities.core.job;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SsuJobEvents {

    private SsuJobEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SimpleServerUtilities.JOBS.tick(event.getServer());
    }
}
