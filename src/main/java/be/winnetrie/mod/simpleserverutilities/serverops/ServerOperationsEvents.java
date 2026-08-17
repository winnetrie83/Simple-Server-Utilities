package be.winnetrie.mod.simpleserverutilities.serverops;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Event-driven bridge; no world/player scanning is performed by this class. */
public final class ServerOperationsEvents {
    private ServerOperationsEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakBlockEvent event) {
        if (!SimpleServerUtilities.CORE.modules().isActive("server_operations")) return;
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        SimpleServerUtilities.SERVER_OPERATIONS.logBlockBreak(player, event.getPos(), event.getState());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!SimpleServerUtilities.CORE.modules().isActive("server_operations")) return;
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        SimpleServerUtilities.SERVER_OPERATIONS.logBlockPlace(player, event.getPos(), event.getBlockSnapshot().getState(), event.getPlacedBlock());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        if (!SimpleServerUtilities.CORE.modules().isActive("server_operations")) return;
        if (event.isCanceled()) return;
        ServerOperationsManager.ChatDecision decision = SimpleServerUtilities.SERVER_OPERATIONS.chat(event.getPlayer(), event.getRawText());
        if (decision.allowed()) return;
        event.setCanceled(true);
        if (!decision.silent() && !decision.message().isBlank()) event.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.literal(decision.message()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SimpleServerUtilities.CORE.modules().isActive("server_operations")) return;
        if (event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.SERVER_OPERATIONS.onLogin(player);
    }

    @SubscribeEvent
    public static void onTickPre(ServerTickEvent.Pre event) { if (SimpleServerUtilities.CORE.modules().isActive("server_operations")) SimpleServerUtilities.SERVER_OPERATIONS.beginTick(); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTickPost(ServerTickEvent.Post event) { if (SimpleServerUtilities.CORE.modules().isActive("server_operations")) SimpleServerUtilities.SERVER_OPERATIONS.endTick(event.getServer()); }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) { ServerOperationsManager.applyPendingRestoreAfterStop(); }
}
