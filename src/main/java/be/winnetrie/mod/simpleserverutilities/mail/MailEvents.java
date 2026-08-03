package be.winnetrie.mod.simpleserverutilities.mail;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class MailEvents {
    private static final long MAINTENANCE_INTERVAL_TICKS = 20L * 60L;
    private static long nextMaintenanceTick;
    private MailEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.ENABLE_MAIL.get()) {
            nextMaintenanceTick = 0L;
            return;
        }
        long tick = event.getServer().getTickCount();
        if (tick + MAINTENANCE_INTERVAL_TICKS < nextMaintenanceTick) {
            nextMaintenanceTick = 0L;
        }
        if (tick < nextMaintenanceTick) return;
        nextMaintenanceTick = tick + MAINTENANCE_INTERVAL_TICKS;
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try {
            SimpleServerUtilities.MAIL.maintenanceTick();
        } finally {
            SimpleServerUtilities.PERFORMANCE.stopTimer("mail_maintenance", timer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (Config.ENABLE_MAIL.get() && event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.MAIL.ensurePlayer(player);
        }
    }
}
