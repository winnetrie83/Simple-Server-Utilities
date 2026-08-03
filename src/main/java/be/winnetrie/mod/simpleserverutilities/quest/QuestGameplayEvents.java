package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Vanilla gameplay adapters that publish generic Content Core events for Quest Core. */
public final class QuestGameplayEvents {
    private QuestGameplayEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!active() || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        publish(player, ContentEventTypes.BLOCK_BROKEN,
                BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString(), 1L, Map.of());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!active() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        publish(player, ContentEventTypes.BLOCK_PLACED,
                BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString(), 1L, Map.of());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!active() || event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer victim) {
            publish(victim, ContentEventTypes.PLAYER_DEATH, "*", 1L, Map.of());
        }
        ServerPlayer killer = attackingPlayer(event.getSource().getEntity());
        if (killer != null && killer != event.getEntity()) {
            publish(killer, ContentEventTypes.ENTITY_KILLED,
                    BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString(), 1L, Map.of());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!active()) return;
        long hundredths = Math.max(0L, Math.round(event.getInflictedDamage() * 100.0F));
        if (hundredths <= 0L) return;
        Entity responsible = responsibleEntity(event.getSource().getEntity());
        if (responsible instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            publish(attacker, ContentEventTypes.DAMAGE_DEALT,
                    BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString(), hundredths,
                    Map.of("unit", "hundredths"));
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            String source = responsible == null ? "*" : BuiltInRegistries.ENTITY_TYPE.getKey(responsible.getType()).toString();
            publish(victim, ContentEventTypes.DAMAGE_TAKEN, source, hundredths, Map.of("unit", "hundredths"));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active()) SimpleServerUtilities.QUESTS.savePlayer(player.getUUID());
    }

    private static void publish(ServerPlayer player, String type, String subject, long amount, Map<String,String> metadata) {
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(),
                ContentEvent.player(type, player.getUUID(), "minecraft", type, subject, amount, metadata));
    }
    private static boolean active() {
        return Config.ENABLE_QUESTS.get() && SimpleServerUtilities.CORE.modules().isActive("quests");
    }
    private static ServerPlayer attackingPlayer(Entity source) {
        Entity responsible = responsibleEntity(source); return responsible instanceof ServerPlayer player ? player : null;
    }
    private static Entity responsibleEntity(Entity source) {
        return source instanceof Projectile projectile && projectile.getOwner() != null ? projectile.getOwner() : source;
    }
}
