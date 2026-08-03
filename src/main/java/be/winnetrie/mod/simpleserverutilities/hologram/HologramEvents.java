package be.winnetrie.mod.simpleserverutilities.hologram;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class HologramEvents {
    private HologramEvents() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try {
            SimpleServerUtilities.HOLOGRAMS.tick(event.getServer());
        } finally {
            SimpleServerUtilities.PERFORMANCE.stopTimer("holograms", timer);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.HOLOGRAMS.syncPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.HOLOGRAM_TOOLS.forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.HOLOGRAMS.syncPlayer(player);
        }
    }
}
