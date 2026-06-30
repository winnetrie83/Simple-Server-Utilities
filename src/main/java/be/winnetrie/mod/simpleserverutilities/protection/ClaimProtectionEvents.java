package be.winnetrie.mod.simpleserverutilities.protection;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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

        if (ProtectionHelper.canPlayerBreak(player, player.level(), event.getPos())) {
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

        if (ProtectionHelper.canPlayerPlace(player, player.level(), event.getPos())) {
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

        if (ProtectionHelper.canPlayerInteract(player, player.level(), event.getPos())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.sendSystemMessage(Component.literal("You cannot interact with blocks here."));
    }
}