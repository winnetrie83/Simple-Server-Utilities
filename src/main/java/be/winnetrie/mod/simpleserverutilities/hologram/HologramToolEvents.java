package be.winnetrie.mod.simpleserverutilities.hologram;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Right-click creates a hologram one block ahead; targeted client clicks open existing holograms. */
public final class HologramToolEvents {
    private HologramToolEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !SimpleServerUtilities.HOLOGRAM_TOOLS.isTool(player, player.getMainHandItem())) {
            return;
        }
        if (!canUse(player)) return;
        // The admin tool should never accidentally damage a block.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !SimpleServerUtilities.HOLOGRAM_TOOLS.isTool(player, player.getMainHandItem())) {
            return;
        }
        if (!openCreate(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !SimpleServerUtilities.HOLOGRAM_TOOLS.isTool(player, player.getMainHandItem())) {
            return;
        }
        if (!openCreate(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !SimpleServerUtilities.HOLOGRAM_TOOLS.isTool(player, player.getMainHandItem())) {
            return;
        }
        if (!openCreate(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean openCreate(ServerPlayer player) {
        if (!canUse(player)) return false;
        if (SimpleServerUtilities.HOLOGRAM_TOOLS.consumeCreateSuppression(player)) return true;
        SimpleServerUtilities.HOLOGRAM_TOOLS.openCreateEditor(player);
        return true;
    }

    private static boolean canUse(ServerPlayer player) {
        return Config.ENABLE_HOLOGRAMS.get()
                && PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false);
    }
}
