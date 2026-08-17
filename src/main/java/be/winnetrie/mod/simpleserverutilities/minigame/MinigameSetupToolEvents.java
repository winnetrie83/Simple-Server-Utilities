package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Click routing and protected build-mode hooks for the Minigame Setup Tool. */
public final class MinigameSetupToolEvents {
    private MinigameSetupToolEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!SsuModuleAccess.active("minigames")) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!SimpleServerUtilities.MINIGAME_SETUP_TOOLS.isTool(player, player.getMainHandItem())) return;
        MinigameSetupToolService.open(player);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!SsuModuleAccess.active("minigames")) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!SimpleServerUtilities.MINIGAME_SETUP_TOOLS.isTool(player, player.getMainHandItem())) return;
        MinigameSetupToolService.open(player);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!SsuModuleAccess.active("minigames")) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!SimpleServerUtilities.MINIGAME_SETUP_TOOLS.isTool(player, player.getMainHandItem())) return;
        if (MinigameSetupToolService.handleLeftClick(player, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterBreak(BlockEvent.BreakEvent event) {
        if (!SsuModuleAccess.active("minigames")) return;
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        MinigameSetupToolService.onArenaBlockEdited(player, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterPlace(BlockEvent.EntityPlaceEvent event) {
        if (!SsuModuleAccess.active("minigames")) return;
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        MinigameSetupToolService.onArenaBlockEdited(player, event.getPos());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SimpleServerUtilities.MINIGAME_SETUP_TOOLS.forget(player.getUUID());
        }
    }
}
