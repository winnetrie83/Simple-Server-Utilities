package be.winnetrie.mod.simpleserverutilities.mine;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Mine access, drop-rule/progress hooks plus the dedicated Mine Setup Tool workflow. */
public final class MineEvents {
    private MineEvents() { }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event){
        if(event.getAction()!=PlayerInteractEvent.LeftClickBlock.Action.START||!(event.getEntity() instanceof ServerPlayer player))return;
        if(!SimpleServerUtilities.MINE_SETUP_TOOLS.isTool(player,player.getMainHandItem()))return;
        if(!canAdmin(player))return;
        String dim=player.level().dimension().identifier().toString();SimpleServerUtilities.MINE_SETUP_TOOLS.selection(player).setPoint1(dim,event.getPos());
        player.sendSystemMessage(Component.literal("Mine corner 1 set to "+format(event.getPos())+" in "+dim+"."));event.setCanceled(true);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getHand()!=InteractionHand.MAIN_HAND)return;
        if(!SimpleServerUtilities.MINE_SETUP_TOOLS.isTool(player,player.getMainHandItem())||!canAdmin(player))return;
        String dim=player.level().dimension().identifier().toString();SimpleServerUtilities.MINE_SETUP_TOOLS.selection(player).setPoint2(dim,event.getPos());
        player.sendSystemMessage(Component.literal("Mine corner 2 set to "+format(event.getPos())+". Open Mines to apply the selection."));event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event){
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getHand()!=InteractionHand.MAIN_HAND)return;
        if(!SimpleServerUtilities.MINE_SETUP_TOOLS.isTool(player,player.getMainHandItem())||!canAdmin(player))return;
        MineService.send(player,true,"",0L,"",false);event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void protectBreak(BreakBlockEvent event){
        if(!(event.getPlayer() instanceof ServerPlayer player))return;MineDefinition mine=SimpleServerUtilities.MINES.at(player.level(),event.getPos());if(mine==null)return;
        // Jail task mining is adjudicated by ModerationEvents/ModerationManager so the narrow jail-safe
        // permission path can enforce both the global and per-mine keys without unlocking other SSU features.
        if(SimpleServerUtilities.MODERATION.jailed(player.getUUID()))return;
        if(!canUse(player,mine)){event.setCanceled(true);player.sendOverlayMessage(Component.literal("You do not have permission to mine here."));}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void afterBreak(BreakBlockEvent event){
        if(event.isCanceled()||!(event.getPlayer() instanceof ServerPlayer player))return;MineDefinition mine=SimpleServerUtilities.MINES.at(player.level(),event.getPos());if(mine!=null&&canUse(player,mine))SimpleServerUtilities.MINES.blockMined(mine,player,event.getState());
    }

    /** Applies mine-local drop, XP and Fortune/Silk rules after vanilla has calculated the drop result. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onDrops(BlockDropsEvent event){
        if(!(event.getBreaker() instanceof ServerPlayer player))return;MineDefinition mine=SimpleServerUtilities.MINES.at(event.getLevel(),event.getPos());if(mine==null||!canUse(player,mine))return;
        if("NONE".equals(mine.dropMode))event.getDrops().clear();
        else if("CUSTOM".equals(mine.dropMode))replaceWithCustomDrops(event,mine);
        else if(!mine.allowFortune||!mine.allowSilkTouch)replaceWithRestrictedVanillaDrops(event,mine,player);
        int experience=(int)Math.max(0L,Math.min(Integer.MAX_VALUE,Math.round(event.getDroppedExperience()*mine.experienceMultiplier)));event.setDroppedExperience(experience);
    }

    private static void replaceWithRestrictedVanillaDrops(BlockDropsEvent event,MineDefinition mine,ServerPlayer player){
        ItemStack tool=event.getTool().copy();
        EnchantmentHelper.updateEnchantments(tool,mutable->mutable.removeIf(holder->(!mine.allowFortune&&holder.is(Enchantments.FORTUNE))||(!mine.allowSilkTouch&&holder.is(Enchantments.SILK_TOUCH))));
        List<ItemStack> drops=Block.getDrops(event.getState(),event.getLevel(),event.getPos(),event.getBlockEntity(),player,tool);event.getDrops().clear();
        for(ItemStack stack:drops)if(!stack.isEmpty())event.getDrops().add(new ItemEntity(event.getLevel(),event.getPos().getX()+0.5D,event.getPos().getY()+0.5D,event.getPos().getZ()+0.5D,stack));
    }

    private static void replaceWithCustomDrops(BlockDropsEvent event,MineDefinition mine){
        event.getDrops().clear();ThreadLocalRandom random=ThreadLocalRandom.current();
        for(MineDefinition.DropEntry rule:mine.customDrops){if(rule==null||rule.itemId.isBlank()||random.nextDouble(100.0D)>=rule.chancePercent)continue;Item item;try{item=BuiltInRegistries.ITEM.getOptional(Identifier.parse(rule.itemId)).orElse(null);}catch(Exception ignored){item=null;}if(item==null)continue;int count=rule.minCount>=rule.maxCount?rule.minCount:random.nextInt(rule.minCount,rule.maxCount+1);while(count>0){ItemStack stack=new ItemStack(item);int part=Math.min(count,Math.max(1,stack.getMaxStackSize()));stack.setCount(part);event.getDrops().add(new ItemEntity(event.getLevel(),event.getPos().getX()+0.5D,event.getPos().getY()+0.5D,event.getPos().getZ()+0.5D,stack));count-=part;}}
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event){if(!(event.getEntity() instanceof ServerPlayer player)||PermissionService.isAdmin(player))return;MineDefinition mine=SimpleServerUtilities.MINES.at(player.level(),event.getPos());if(mine!=null)event.setCanceled(true);}

    @SubscribeEvent public static void onTick(ServerTickEvent.Post event){SimpleServerUtilities.MINES.tick(event.getServer());}
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)SimpleServerUtilities.MINE_SETUP_TOOLS.forget(player.getUUID());}

    private static boolean canUse(ServerPlayer p,MineDefinition d){if(SimpleServerUtilities.MODERATION.jailed(p.getUUID()))return PermissionService.getBooleanForJailGameplay(p,PermissionKeys.MINES_USE,true)&&(d.permissionKey.isBlank()||PermissionService.getBooleanForJailGameplay(p,d.permissionKey,false));return PermissionService.isAdmin(p)||(PermissionService.getBoolean(p,PermissionKeys.MINES_USE,true)&&(d.permissionKey.isBlank()||PermissionService.getBoolean(p,d.permissionKey,false)));}
    private static boolean canAdmin(ServerPlayer p){return PermissionService.isAdmin(p)&&PermissionService.getBoolean(p,PermissionKeys.MINES_ADMIN,false);}
    private static String format(net.minecraft.core.BlockPos p){return p.getX()+", "+p.getY()+", "+p.getZ();}
}
