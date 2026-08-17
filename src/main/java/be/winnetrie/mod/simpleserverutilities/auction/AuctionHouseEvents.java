package be.winnetrie.mod.simpleserverutilities.auction;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class AuctionHouseEvents {
    private static final long INTERVAL = 20L * 60L;
    private static long nextTick;
    private AuctionHouseEvents() {}

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!SimpleServerUtilities.CORE.modules().isActive("auction_house")) { nextTick = 0L; return; }
        long tick = event.getServer().getTickCount();
        if (tick < nextTick) return;
        nextTick = tick + INTERVAL;
        SimpleServerUtilities.AUCTION_HOUSE.maintenanceTick();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (SimpleServerUtilities.CORE.modules().isActive("auction_house")) SimpleServerUtilities.AUCTION_HOUSE.closeSession(event.getEntity().getUUID());
    }
}
