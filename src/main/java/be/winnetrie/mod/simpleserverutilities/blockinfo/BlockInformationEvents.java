package be.winnetrie.mod.simpleserverutilities.blockinfo;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BlockInformationEvents {
    private BlockInformationEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BlockInformationService.syncPlayer(player);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BlockInformationService.syncPlayer(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockInformationService.clearPlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long timer = SimpleServerUtilities.PERFORMANCE.startTimer();
        try {
            BlockInformationService.tick(event.getServer());
        } finally {
            SimpleServerUtilities.PERFORMANCE.stopTimer("block_information", timer);
        }
    }
}
