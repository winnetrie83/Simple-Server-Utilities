package be.winnetrie.mod.simpleserverutilities.permission;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Creates/updates a persistent rank profile on login only while Permissions is enabled. */
public final class PermissionPlayerEvents {

    private PermissionPlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Config.ENABLE_PERMISSION_SYSTEM.get()) {
                SimpleServerUtilities.PERMISSIONS.ensurePlayerProfile(player);
            }
            SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        }
    }
}
