package be.winnetrie.mod.simpleserverutilities.warp;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Once-per-minute rented-warp renewal processing. */
public final class WarpRentalEvents {
    private static final long INTERVAL_TICKS = 20L * 60L;
    private static long nextTick;
    private WarpRentalEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.ENABLE_WARPS.get()) { nextTick = 0L; return; }
        long tick = event.getServer().getTickCount();
        if (tick + INTERVAL_TICKS < nextTick) nextTick = 0L;
        if (tick < nextTick) return;
        nextTick = tick + INTERVAL_TICKS;
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try { SimpleServerUtilities.WARPS.maintenanceTick(); }
        finally { SimpleServerUtilities.PERFORMANCE.stopTimer("warp_rental_maintenance", timer); }
    }
}
