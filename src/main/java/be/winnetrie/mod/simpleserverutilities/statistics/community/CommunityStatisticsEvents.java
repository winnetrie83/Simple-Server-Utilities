package be.winnetrie.mod.simpleserverutilities.statistics.community;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Period rollover, snapshot and persistence tick for community statistics. */
public final class CommunityStatisticsEvents {
    private CommunityStatisticsEvents() { }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!SimpleServerUtilities.CORE.modules().isActive("community_statistics")) return;
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try { SimpleServerUtilities.COMMUNITY_STATISTICS.tick(event.getServer()); }
        finally { SimpleServerUtilities.PERFORMANCE.stopTimer("community_statistics", timer); }
    }
}
