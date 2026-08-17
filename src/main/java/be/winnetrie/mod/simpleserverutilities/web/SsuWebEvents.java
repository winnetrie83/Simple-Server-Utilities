package be.winnetrie.mod.simpleserverutilities.web;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Main-thread snapshot refresh for the read-only web bridge. */
public final class SsuWebEvents {
    private SsuWebEvents() { }
    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) { SimpleServerUtilities.WEB_API.tick(event.getServer()); }
}
