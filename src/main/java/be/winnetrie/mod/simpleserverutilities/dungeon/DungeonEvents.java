package be.winnetrie.mod.simpleserverutilities.dungeon;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runtime adapters for dungeon queues, stages, lives, disconnects and recovery. */
public final class DungeonEvents {
    private DungeonEvents() {}

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (active()) SimpleServerUtilities.DUNGEONS.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!active() || event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.DUNGEONS.onPlayerDeath(player);
        Entity responsible = responsibleEntity(event.getSource().getEntity());
        if (responsible instanceof ServerPlayer killer && killer != event.getEntity()) {
            SimpleServerUtilities.DUNGEONS.onEntityKilled(killer, event.getEntity(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.DUNGEONS.onLogin(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.DUNGEONS.onPlayerRespawn(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.DUNGEONS.onLogout(player);
    }

    private static Entity responsibleEntity(Entity source) {
        return source instanceof Projectile projectile && projectile.getOwner() != null ? projectile.getOwner() : source;
    }

    private static boolean active() {
        return Config.ENABLE_DUNGEONS.get() && SimpleServerUtilities.CORE.modules().isActive("dungeons");
    }
}
