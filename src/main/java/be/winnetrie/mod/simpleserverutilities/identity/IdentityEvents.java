package be.winnetrie.mod.simpleserverutilities.identity;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Login/chat/synchronization bridge for global titles and rank presentation. */
public final class IdentityEvents {
    private static int syncTicker;
    private IdentityEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SimpleServerUtilities.IDENTITY.ensurePlayer(player);
        SimpleServerUtilities.IDENTITY.syncAll();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) SimpleServerUtilities.IDENTITY.syncAll();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null || event.isCanceled()) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        // ServerChatEvent#setMessage changes only the chat body; vanilla still prepends <player>,
        // which duplicated the name when SSU also rendered it. Cancel the vanilla line and
        // broadcast the complete authoritative SSU format exactly once instead.
        event.setCanceled(true);
        server.getPlayerList().broadcastSystemMessage(
                SimpleServerUtilities.IDENTITY.chatMessage(player, event.getRawText()), false);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++syncTicker >= 100) {
            syncTicker = 0;
            SimpleServerUtilities.IDENTITY.syncAll();
        }
    }
}
