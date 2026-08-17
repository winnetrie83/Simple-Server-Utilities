package be.winnetrie.mod.simpleserverutilities.moderation;

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

/** Enforces freeze/jail restrictions while retaining the explicit community-mining route. */
public final class ModerationEvents {
    private ModerationEvents(){}
    private static boolean active(){return SimpleServerUtilities.CORE.modules().isActive("moderation");}
    private static boolean restricted(ServerPlayer p){return active()&&p!=null&&SimpleServerUtilities.MODERATION.restricted(p.getUUID());}
    private static boolean jailed(ServerPlayer p){return active()&&p!=null&&SimpleServerUtilities.MODERATION.jailed(p.getUUID());}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onLogin(PlayerEvent.PlayerLoggedInEvent e){if(active()&&e.getEntity() instanceof ServerPlayer p)SimpleServerUtilities.MODERATION.onLogin(p);}
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e){if(active()&&e.getEntity() instanceof ServerPlayer p)SimpleServerUtilities.MODERATION.onLogout(p);}
    @SubscribeEvent public static void onTick(ServerTickEvent.Post e){if(active())SimpleServerUtilities.MODERATION.tick(e.getServer());}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onRightBlock(PlayerInteractEvent.RightClickBlock e){if(e.getEntity() instanceof ServerPlayer p&&restricted(p)){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onRightItem(PlayerInteractEvent.RightClickItem e){if(e.getEntity() instanceof ServerPlayer p&&restricted(p)){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onLeftBlock(PlayerInteractEvent.LeftClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        if(SimpleServerUtilities.MODERATION.frozen(p.getUUID()))e.setCanceled(true);
        // Jailed task miners must reach BlockEvent.BreakEvent, where the attempt is validated and always cancelled.
        else if(jailed(p)&&!SimpleServerUtilities.MODERATION.hasActiveTask(p.getUUID()))e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onEntity(PlayerInteractEvent.EntityInteract e){if(e.getEntity() instanceof ServerPlayer p&&restricted(p)){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onEntitySpecific(PlayerInteractEvent.EntityInteractSpecific e){if(e.getEntity() instanceof ServerPlayer p&&restricted(p)){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onAttack(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p&&restricted(p))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onDamage(LivingIncomingDamageEvent e){if((e.getEntity() instanceof ServerPlayer target&&restricted(target))||(e.getSource().getEntity() instanceof ServerPlayer source&&restricted(source)))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onBreak(BlockEvent.BreakEvent e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||!restricted(p))return;
        e.setCanceled(true);
        if(jailed(p)&&p.level() instanceof net.minecraft.server.level.ServerLevel level)SimpleServerUtilities.MODERATION.handleJailBreak(p,level,e.getPos());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onPickup(ItemEntityPickupEvent.Pre e){if(e.getPlayer() instanceof ServerPlayer p&&restricted(p))e.setCanPickup(TriState.FALSE);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onToss(ItemTossEvent e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||!restricted(p))return;
        ItemStack stack=e.getEntity().getItem().copy();
        if(!stack.isEmpty()&&p.getInventory().add(stack)){p.getInventory().setChanged();p.containerMenu.broadcastChanges();e.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onCommand(CommandEvent e){
        if(e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p&&restricted(p)){
            e.setCanceled(true);p.sendSystemMessage(Component.literal(jailed(p)?"Commands are disabled while jailed.":"Commands are disabled while frozen."),true);
        }
    }
}
