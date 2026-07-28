package be.winnetrie.mod.simpleserverutilities.permission;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Creates/updates the persistent rank profile as soon as a player joins. */
public final class PermissionPlayerEvents {

    private PermissionPlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.PERMISSIONS.ensurePlayerProfile(player);
            SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        }
    }
}
