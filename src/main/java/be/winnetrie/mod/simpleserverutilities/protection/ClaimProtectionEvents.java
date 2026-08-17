package be.winnetrie.mod.simpleserverutilities.protection;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupToolService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class ClaimProtectionEvents {

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
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
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
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
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
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
            Region region = SsuModuleAccess.active("regions")
                    ? SimpleServerUtilities.REGIONS.getAt(player.level().dimension(), event.getPos())
                    : null;

            if (region != null && region.getRentData().isRentable()) {
                return;
            }
        }

        boolean allowed;
        if (ProtectionHelper.getRegionAt(player.level(), event.getPos()) != null
                || ProtectionHelper.getClaimAt(player.level(), event.getPos()) == null) {
            allowed = ProtectionHelper.canPlayerPerform(player, player.level(), event.getPos(),
                    ProtectionHelper.ActionType.INTERACT);
        } else {
            var state = player.level().getBlockState(event.getPos());
            var block = state.getBlock();
            boolean container = player.level().getBlockEntity(event.getPos()) instanceof Container
                    || state.getMenuProvider(player.level(), event.getPos()) != null;
            if (container) allowed = ProtectionHelper.canOpenClaimContainer(player, player.level(), event.getPos());
            else if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock)
                allowed = ProtectionHelper.canUseClaimDoor(player, player.level(), event.getPos());
            else if (block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BasePressurePlateBlock)
                allowed = ProtectionHelper.canUseClaimSwitch(player, player.level(), event.getPos());
            else allowed = ProtectionHelper.canPlayerPerform(player, player.level(), event.getPos(),
                        ProtectionHelper.ActionType.INTERACT);
        }
        if (allowed) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.sendSystemMessage(Component.literal("You cannot interact with blocks here."));
    }
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!ProtectionHelper.canTransferClaimItems(player, player.level(), event.getItemEntity().blockPosition())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (ProtectionHelper.canTransferClaimItems(player, player.level(), event.getEntity().blockPosition())) return;
        var stack = event.getEntity().getItem().copy();
        // NeoForge removes the stack before firing ItemTossEvent. Only cancel after
        // the exact stack was restored, otherwise a full inventory could destroy it.
        if (stack.isEmpty() || !player.getInventory().add(stack)) return;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("You cannot drop items in this claim."));
    }

    /**
     * Pressure plates are activated by collision rather than a right-click event.
     * Revert powered plates beneath unauthorized players at the end of the same
     * server tick so claim-role switch permissions also cover pressure plates.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.anyActive("claims", "regions")) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            suppressUnauthorizedPressurePlate(player, player.blockPosition());
            suppressUnauthorizedPressurePlate(player, player.blockPosition().below());
        }
    }

    private static void suppressUnauthorizedPressurePlate(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof BasePressurePlateBlock)) return;
        if (ProtectionHelper.canUseClaimSwitch(player, player.level(), pos)) return;

        BlockState reset = state;
        if (reset.hasProperty(BlockStateProperties.POWERED) && reset.getValue(BlockStateProperties.POWERED)) {
            reset = reset.setValue(BlockStateProperties.POWERED, false);
        }
        if (reset.hasProperty(BlockStateProperties.POWER) && reset.getValue(BlockStateProperties.POWER) > 0) {
            reset = reset.setValue(BlockStateProperties.POWER, 0);
        }
        if (reset != state) {
            player.level().setBlock(pos, reset, 3);
        }
    }

}
