package be.winnetrie.mod.simpleserverutilities.claim.tax;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded once-per-minute recurring claim-tax processing. */
public final class PlayerClaimTaxEvents {
    private static final long INTERVAL_TICKS = 20L * 60L;
    private static long nextTick;
    private PlayerClaimTaxEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        if (tick + INTERVAL_TICKS < nextTick) nextTick = 0L;
        if (tick < nextTick) return;
        nextTick = tick + INTERVAL_TICKS;
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try { SimpleServerUtilities.CLAIM_TAX.maintenanceTick(); }
        finally { SimpleServerUtilities.PERFORMANCE.stopTimer("claim_tax_maintenance", timer); }
    }
}
