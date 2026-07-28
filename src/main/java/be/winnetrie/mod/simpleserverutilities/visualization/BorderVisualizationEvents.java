package be.winnetrie.mod.simpleserverutilities.visualization;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BorderVisualizationEvents {

    private BorderVisualizationEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SimpleServerUtilities.BORDER_VISUALIZATIONS.tick(event.getServer());
    }
}
