package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runtime adapters for queue cleanup, match progression, deaths and crash recovery. */
public final class MinigameEvents {
    private MinigameEvents() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (active()) SimpleServerUtilities.MINIGAMES.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.MINIGAMES.onPlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.MINIGAMES.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.MINIGAMES.onPlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.MINIGAMES.onLogout(player);
        }
    }

    private static boolean active() {
        return Config.ENABLE_MINIGAMES.get() && SimpleServerUtilities.CORE.modules().isActive("minigames");
    }
}
