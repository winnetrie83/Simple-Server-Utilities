package be.winnetrie.mod.simpleserverutilities.onboarding;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Enforces the no-world-actions onboarding state until the player explicitly completes it. */
public final class OnboardingEvents {
    private OnboardingEvents() {}
    private static boolean active() { return SimpleServerUtilities.CORE.modules().isActive("onboarding"); }
    private static boolean moderated(ServerPlayer player) { return SimpleServerUtilities.CORE.modules().isActive("moderation") && SimpleServerUtilities.MODERATION.restricted(player.getUUID()); }
    private static boolean locked(ServerPlayer player) { return active() && player != null && !moderated(player) && SimpleServerUtilities.ONBOARDING.restricted(player.getUUID()); }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { if (active() && event.getEntity() instanceof ServerPlayer player && !moderated(player)) SimpleServerUtilities.ONBOARDING.onLogin(player); }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { if (active() && event.getEntity() instanceof ServerPlayer player) SimpleServerUtilities.ONBOARDING.onLogout(player); }
    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) { if (active()) SimpleServerUtilities.ONBOARDING.tick(event.getServer()); }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onRightBlock(PlayerInteractEvent.RightClickBlock event) { if (event.getEntity() instanceof ServerPlayer p && locked(p)) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); } }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onRightItem(PlayerInteractEvent.RightClickItem event) { if (event.getEntity() instanceof ServerPlayer p && locked(p)) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); } }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onLeftBlock(PlayerInteractEvent.LeftClickBlock event) { if (event.getEntity() instanceof ServerPlayer p && locked(p)) event.setCanceled(true); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onEntity(PlayerInteractEvent.EntityInteract event) { if (event.getEntity() instanceof ServerPlayer p && locked(p)) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); } }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) { if (event.getEntity() instanceof ServerPlayer p && locked(p)) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); } }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onAttack(AttackEntityEvent event) { if (event.getEntity() instanceof ServerPlayer p && locked(p)) event.setCanceled(true); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onDamage(LivingIncomingDamageEvent event) { if ((event.getEntity() instanceof ServerPlayer target && locked(target)) || (event.getSource().getEntity() instanceof ServerPlayer source && locked(source))) event.setCanceled(true); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onBreak(BlockEvent.BreakEvent event) { if (event.getPlayer() instanceof ServerPlayer p && locked(p)) event.setCanceled(true); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onPickup(ItemEntityPickupEvent.Pre event) { if (event.getPlayer() instanceof ServerPlayer p && locked(p)) event.setCanPickup(TriState.FALSE); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onToss(ItemTossEvent event) { if (!(event.getPlayer() instanceof ServerPlayer p) || !locked(p)) return; ItemStack s=event.getEntity().getItem().copy(); if(!s.isEmpty()&&p.getInventory().add(s)){p.getInventory().setChanged();p.containerMenu.broadcastChanges();event.setCanceled(true);} }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onCommand(CommandEvent event) { if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p && locked(p)) { event.setCanceled(true); p.sendSystemMessage(Component.literal("Complete the welcome process before using commands."), true); } }
}
