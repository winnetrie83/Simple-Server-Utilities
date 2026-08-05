package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupToolService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SignBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public class ClaimProtectionEvents {

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        if (ProtectionHelper.canPlayerPerform(
                player,
                player.level(),
                event.getPos(),
                ProtectionHelper.ActionType.BREAK
        )) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot break blocks here."));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (ProtectionHelper.canPlayerPerform(
                player,
                player.level(),
                event.getPos(),
                ProtectionHelper.ActionType.PLACE
        )) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot place blocks here."));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Placing a block begins with RightClickBlock before EntityPlaceEvent fires.
        // In Minigame Setup Tool edit mode, deny the clicked block's own interaction
        // (containers, buttons, doors, etc.) while still allowing the held BlockItem
        // to run its placement logic. The later EntityPlaceEvent remains the final
        // boundary check and only permits positions inside the selected idle arena.
        if (player.getItemInHand(event.getHand()).getItem() instanceof BlockItem
                && MinigameSetupToolService.canEditBlock(player, event.getPos())) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.DEFAULT);
            return;
        }

        if (player.level().getBlockState(event.getPos()).getBlock() instanceof SignBlock) {
            Region region = SimpleServerUtilities.REGIONS.getAt(player.level().dimension(), event.getPos());

            if (region != null && region.getRentData().isRentable()) {
                return;
            }
        }

        if (ProtectionHelper.canPlayerPerform(
                player,
                player.level(),
                event.getPos(),
                ProtectionHelper.ActionType.INTERACT
        )) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.sendSystemMessage(Component.literal("You cannot interact with blocks here."));
    }
}