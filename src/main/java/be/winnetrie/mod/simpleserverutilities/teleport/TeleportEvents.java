package be.winnetrie.mod.simpleserverutilities.teleport;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class TeleportEvents {

    private TeleportEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("teleport")) return;
        SimpleServerUtilities.TELEPORTS.tick(event.getServer());
    }
}
