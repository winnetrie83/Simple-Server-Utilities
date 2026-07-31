package be.winnetrie.mod.simpleserverutilities.mapmarker;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Initial marker synchronization when a player joins. */
public final class MapMarkerEvents {
    private MapMarkerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MapMarkerService.sync(player);
        }
    }
}
