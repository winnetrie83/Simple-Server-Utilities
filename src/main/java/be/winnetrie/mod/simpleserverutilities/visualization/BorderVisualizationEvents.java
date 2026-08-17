package be.winnetrie.mod.simpleserverutilities.visualization;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Border synchronization is revision/event driven. A low-frequency fallback
 * tick remains for ordinary chunk movement and disconnected-player cleanup.
 */
public final class BorderVisualizationEvents {

    private BorderVisualizationEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SsuModuleAccess.active("visualization")) return;
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.tick(event.getServer());
        } finally {
            SimpleServerUtilities.PERFORMANCE.stopTimer("border_visualization", timer);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SsuModuleAccess.active("visualization")) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!SsuModuleAccess.active("visualization")) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!SsuModuleAccess.active("visualization")) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        }
    }
}
