package be.winnetrie.mod.simpleserverutilities.content;

import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Login/logout and persistence adapters for the Content & Progression Core. */
public final class ContentCoreEvents {
    private ContentCoreEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active()) return;
        SimpleServerUtilities.CONTENT_PROGRESS.ensurePlayer(player);
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(
                ContentEventTypes.PLAYER_LOGIN, player.getUUID(), "content_core", "login",
                player.getName().getString(), 1L, Map.of()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active()) return;
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(
                ContentEventTypes.PLAYER_LOGOUT, player.getUUID(), "content_core", "logout",
                player.getName().getString(), 1L, Map.of()));
        SimpleServerUtilities.CONTENT_PROGRESS.savePlayer(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!active()) return;
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try {
            SimpleServerUtilities.CONTENT_PROGRESS.tick(event.getServer());
        } finally {
            SimpleServerUtilities.PERFORMANCE.stopTimer("content_progression", timer);
        }
    }

    private static boolean active() {
        return SimpleServerUtilities.CORE.modules().isActive("content_core");
    }
}
