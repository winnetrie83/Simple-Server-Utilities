package be.winnetrie.mod.simpleserverutilities.statistics;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Low-overhead event adapters for the indexed statistics manager. */
public final class StatisticsEvents {
    private StatisticsEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!active() || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        String id = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        SimpleServerUtilities.STATISTICS.increment(player, StatisticEventType.BLOCK_BROKEN, id, 1L);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!active() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        String id = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
        SimpleServerUtilities.STATISTICS.increment(player, StatisticEventType.BLOCK_PLACED, id, 1L);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!active() || event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            SimpleServerUtilities.STATISTICS.increment(victim, StatisticEventType.PLAYER_DEATH, "*", 1L);
        }
        ServerPlayer killer = attackingPlayer(event.getSource().getEntity());
        if (killer != null && killer != event.getEntity()) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
            SimpleServerUtilities.STATISTICS.increment(killer, StatisticEventType.ENTITY_KILLED, id, 1L);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!active()) return;
        long hundredths = Math.max(0L, Math.round(event.getInflictedDamage() * 100.0F));
        if (hundredths <= 0L) return;
        Entity responsible = responsibleEntity(event.getSource().getEntity());
        ServerPlayer attacker = responsible instanceof ServerPlayer player ? player : null;
        String targetType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        if (attacker != null && attacker != event.getEntity()) {
            SimpleServerUtilities.STATISTICS.increment(attacker, StatisticEventType.DAMAGE_DEALT, targetType, hundredths);
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            String sourceType = responsible == null ? "*"
                    : BuiltInRegistries.ENTITY_TYPE.getKey(responsible.getType()).toString();
            SimpleServerUtilities.STATISTICS.increment(victim, StatisticEventType.DAMAGE_TAKEN, sourceType, hundredths);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (active()) SimpleServerUtilities.STATISTICS.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.STATISTICS.savePlayer(player.getUUID());
    }

    private static boolean active() {
        return Config.ENABLE_CUSTOM_STATISTICS.get()
                && SimpleServerUtilities.CORE.modules().isActive("statistics");
    }

    private static ServerPlayer attackingPlayer(Entity source) {
        Entity responsible = responsibleEntity(source);
        return responsible instanceof ServerPlayer player ? player : null;
    }

    private static Entity responsibleEntity(Entity source) {
        if (source instanceof Projectile projectile && projectile.getOwner() != null) return projectile.getOwner();
        return source;
    }
}
