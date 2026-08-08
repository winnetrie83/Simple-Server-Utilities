package be.winnetrie.mod.simpleserverutilities.statistics;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Persistence/tick adapter. Gameplay counters are fed exclusively through Content Core events. */
public final class StatisticsEvents {
    private StatisticsEvents() {}

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!active()) return;
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try { SimpleServerUtilities.STATISTICS.tick(event.getServer()); }
        finally { SimpleServerUtilities.PERFORMANCE.stopTimer("custom_statistics", timer); }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.STATISTICS.savePlayer(player.getUUID());
    }

    private static boolean active() {
        return Config.ENABLE_CUSTOM_STATISTICS.get() && SimpleServerUtilities.CORE.modules().isActive("statistics");
    }
}
