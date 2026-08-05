package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runtime adapters for queue cleanup, match rules, deaths and crash recovery. */
public final class MinigameEvents {
    private MinigameEvents() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (active()) SimpleServerUtilities.MINIGAMES.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!active() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        MinigameManager.BlockBreakDecision decision = SimpleServerUtilities.MINIGAMES.blockBreakDecision(
                player, event.getPos(), event.getState());
        if (decision == MinigameManager.BlockBreakDecision.DENY) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("That block cannot be broken during this minigame."), true);
        } else if (decision == MinigameManager.BlockBreakDecision.ALLOW_NO_DROPS) {
            event.setCanceled(true);
            SimpleServerUtilities.MINIGAMES.breakSpleefBlockWithoutDrops(player, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!active() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (SimpleServerUtilities.MINIGAMES.shouldCancelBlockPlace(player, event.getPos())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Blocks cannot be placed during this minigame."), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active()) return;
        if (event.getHand() == InteractionHand.MAIN_HAND
                && SimpleServerUtilities.MINIGAMES.handleRightClickBlock(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else if (participant(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (participant(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (participant(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (participant(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !participant(player)) return;
        var stack = event.getEntity().getItem().copy();
        // NeoForge fires this after removing the stack from the inventory. Put the
        // exact temporary stack back before cancelling so Q/drop cannot leak match
        // tools into the world or silently destroy them.
        if (stack.isEmpty() || !player.getInventory().add(stack)) return;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (SimpleServerUtilities.MINIGAMES.shouldCancelItemPickup(
                player, event.getItemEntity().blockPosition())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)
                || SimpleServerUtilities.MINIGAMES.matchView(attacker.getUUID()) == null) return;
        if (event.getTarget() instanceof ServerPlayer victim
                && !SimpleServerUtilities.MINIGAMES.shouldCancelDamage(victim, attacker)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!active()) return;
        ServerPlayer attacker = attackingPlayer(event.getSource().getEntity());
        if (event.getEntity() instanceof ServerPlayer victim) {
            SimpleServerUtilities.MINIGAMES.onPlayerDamaged(victim);
            if (SimpleServerUtilities.MINIGAMES.shouldCancelDamage(victim, attacker)) event.setCanceled(true);
        } else if (attacker != null && SimpleServerUtilities.MINIGAMES.matchView(attacker.getUUID()) != null) {
            // Temporary match state must never be used to farm or damage ordinary
            // world entities while a player is in the waiting lobby or arena.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player) {
            if (SimpleServerUtilities.MINIGAMES.handlePlayerDeath(player)) {
                event.setCanceled(true);
            } else {
                SimpleServerUtilities.MINIGAMES.onPlayerDeath(player);
            }
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

    private static boolean participant(Entity entity) {
        return active() && entity instanceof ServerPlayer player
                && SimpleServerUtilities.MINIGAMES.matchView(player.getUUID()) != null;
    }

    private static ServerPlayer attackingPlayer(Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        return null;
    }

    private static boolean active() {
        return Config.ENABLE_MINIGAMES.get() && SimpleServerUtilities.CORE.modules().isActive("minigames");
    }
}
