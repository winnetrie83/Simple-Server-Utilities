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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
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
        if (interruptCastAction(player, "tried to break a block")) {
            event.setCanceled(true);
            return;
        }
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
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player) || !active()) return;
        if (interruptCastAction(player, "attacked a block")) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!active() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (interruptCastAction(player, "tried to place a block")) {
            event.setCanceled(true);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.shouldCancelBlockPlace(player, event.getPos())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Blocks cannot be placed during this minigame."), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !active()) return;

        // The main-hand objective click is the action that starts the cast. Handle it before
        // generic cast interruption, but only when no cast was already running. A later
        // deliberate right-click while channeling still interrupts normally.
        if (event.getHand() == InteractionHand.MAIN_HAND
                && !SimpleServerUtilities.MINIGAMES.hasActiveObjectiveCast(player)
                && SimpleServerUtilities.MINIGAMES.handleRightClickBlock(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // One physical right-click may continue through the offhand/item interaction stages.
        // Do not let that same input immediately cancel the cast it just started.
        if (SimpleServerUtilities.MINIGAMES.objectiveCastStartedThisTick(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (interruptCastAction(player, "interacted with a block")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (!participant(player)) return;
        if (SimpleServerUtilities.MINIGAMES.handleRoleAbility(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleBowUse(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        // Start the offhand shield directly so the block itself remains protected
        // from interaction while the Tank can still defend.
        if (SimpleServerUtilities.MINIGAMES.handleRoleShieldUse(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !participant(player)) return;
        if (SimpleServerUtilities.MINIGAMES.objectiveCastStartedThisTick(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (interruptCastAction(player, "used an item")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleAbility(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleBowUse(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.allowRightClickItem(player, event.getHand())) return;
        boolean controlledProjectile = SimpleServerUtilities.MINIGAMES.isControlledSpleefProjectile(
                player, event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(controlledProjectile ? InteractionResult.SUCCESS : InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!active() || !(event.getEntity() instanceof Projectile projectile)) return;
        SimpleServerUtilities.MINIGAMES.prepareSpleefProjectile(projectile);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!active() || !(event.getProjectile() instanceof Projectile projectile)) return;
        SimpleServerUtilities.MINIGAMES.handleRoleProjectileImpact(projectile, event.getRayTraceResult());
        if (SimpleServerUtilities.MINIGAMES.handleSpleefProjectileImpact(
                projectile, event.getRayTraceResult())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !participant(player)) return;
        if (SimpleServerUtilities.MINIGAMES.objectiveCastStartedThisTick(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (interruptCastAction(player, "interacted with an entity")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleAbility(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleBowUse(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleShieldUse(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !participant(player)) return;
        if (SimpleServerUtilities.MINIGAMES.objectiveCastStartedThisTick(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (interruptCastAction(player, "interacted with an entity")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleAbility(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleBowUse(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.MINIGAMES.handleRoleShieldUse(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !participant(player)) return;
        interruptCastAction(player, "tried to drop an item");
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
        if (interruptCastAction(attacker, "attacked")) {
            event.setCanceled(true);
            return;
        }
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
    public static void onHeal(LivingHealEvent event) {
        if (active() && event.getEntity() instanceof ServerPlayer player
                && SimpleServerUtilities.MINIGAMES.shouldCancelAutomaticHealing(player)) {
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

    private static boolean interruptCastAction(ServerPlayer player, String action) {
        return player != null && active()
                && SimpleServerUtilities.MINIGAMES.interruptActiveCastForAction(player, action);
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
