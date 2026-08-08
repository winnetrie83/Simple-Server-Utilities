package be.winnetrie.mod.simpleserverutilities.identity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Periodic bounded synchronization for Entity Insight. */
public final class EntityInsightEvents {
    private static int tickCounter;
    private static int cleanupCounter;

    private EntityInsightEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EntityInsightService.clearViewer(player.getUUID());
            EntityInsightService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) EntityInsightService.clearViewer(player.getUUID());
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof Projectile projectile && projectile.getOwner() != null) source = projectile.getOwner();
        if (source instanceof ServerPlayer && !(event.getEntity() instanceof ServerPlayer)) {
            EntityInsightService.notePlayerHit(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++cleanupCounter >= 200) {
            cleanupCounter = 0;
            EntityInsightService.cleanupRecentHits();
        }
        if (++tickCounter < 10) return;
        tickCounter = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) EntityInsightService.sync(player);
    }
}
