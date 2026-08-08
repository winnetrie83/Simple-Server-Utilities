package be.winnetrie.mod.simpleserverutilities.region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public class RegionInteractionEvents {

    private static final Map<UUID, String> LAST_REGION = new HashMap<>();
    private static long nextEnterLeaveTick = 0L;
    private static long nextResetTick = 0L;

    private RegionInteractionEvents() {
    }

    public static void clearRuntimeState() {
        LAST_REGION.clear();
        RegionSelectionSchematicManager.clearRuntimeState();
        WorldEditHistoryManager.clearAll();
        nextEnterLeaveTick = 0L;
        nextResetTick = 0L;
        RegionResetScheduler.clearRuntime();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RegionSetupToolService.hasActivePreview(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RegionSetupToolService.hasActivePreview(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RegionSetupToolService.hasActivePreview(player)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.ENABLE_ADMIN_REGIONS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (RegionSetupToolService.hasActivePreview(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack heldSelectionTool = player.getMainHandItem();
        if (SimpleServerUtilities.REGION_SELECTION_TOOLS.isWorldEditTool(player, heldSelectionTool)) {
            if (!RegionPolicy.canUseSelectionTool(player)) return;
            RegionSelectionManager manager = RegionCommands.getSelectionManager();
            RegionSelection selection = manager.getSelection(player);
            if (selection.getPoint1() == null || selection.getDimension() == null
                    || !selection.getDimension().equals(player.level().dimension())) {
                player.sendSystemMessage(Component.literal("Set World Edit Point 1 with left-click first."), true);
            } else {
                manager.setPoint2(player, event.getPos());
                SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, manager.getSelection(player));
                player.sendSystemMessage(Component.literal(
                        "World Edit point 2 set to " + formatPos(event.getPos()) + ". Right-click the air for the full editor or use the compact-tools key."
                ), true);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (SimpleServerUtilities.REGION_SELECTION_TOOLS.isRegionTool(player, heldSelectionTool)) {
            if (!RegionPolicy.canUseSelectionTool(player)) return;
            RegionSelectionManager manager = RegionCommands.getSelectionManager();
            RegionSelection selection = manager.getSelection(player);
            if (selection.getPoint1() == null || selection.getDimension() == null
                    || !selection.getDimension().equals(player.level().dimension())) {
                player.sendSystemMessage(Component.literal("Set Region Point 1 with left-click first."), true);
            } else {
                manager.setPoint2(player, event.getPos());
                SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, manager.getSelection(player));
                player.sendSystemMessage(Component.literal(
                        "Region point 2 set to " + formatPos(event.getPos()) + ". Right-click the air to open the Region menu."
                ), true);
            }
            // A block-targeted right click belongs exclusively to Point 2 selection.
            // Consuming it here prevents the same input from opening the Region GUI.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!(level.getBlockState(event.getPos()).getBlock() instanceof SignBlock)) {
            return;
        }

        Region region = SimpleServerUtilities.REGIONS.getAt(level.dimension(), event.getPos());

        if (region == null || !region.getRentData().isRentable()) {
            return;
        }

        if (!RegionPolicy.canRentRegion(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to rent regions."));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        sendRentOffer(player, region);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!Config.ENABLE_ADMIN_REGIONS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (RegionSetupToolService.hasActivePreview(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !RegionPolicy.canUseSelectionTool(player)) return;
        ItemStack heldSelectionTool = player.getMainHandItem();
        if (SimpleServerUtilities.REGION_SELECTION_TOOLS.isWorldEditTool(player, heldSelectionTool)) {
            if (!RegionSelectionToolService.open(player)) {
                player.sendSystemMessage(Component.literal("Set World Edit point 1 with left-click and point 2 with right-click on a block first."), true);
            }
        } else if (SimpleServerUtilities.REGION_SELECTION_TOOLS.isRegionTool(player, heldSelectionTool)) {
            // RightClickItem can be emitted by the interaction pipeline after a block
            // interaction on some paths. Only an actual MISS is allowed to open the GUI.
            HitResult target = player.pick(Math.max(0.0D, player.blockInteractionRange()), 1.0F, false);
            if (target.getType() != HitResult.Type.MISS) return;
            RegionSetupToolService.openContext(player);
        } else return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!Config.ENABLE_ADMIN_REGIONS.get()) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (RegionSetupToolService.hasActivePreview(player)) {
            event.setCanceled(true);
            return;
        }

        if (!SimpleServerUtilities.REGION_SELECTION_TOOLS.isSelectionTool(player, player.getMainHandItem())) {
            return;
        }

        if (!RegionPolicy.canUseSelectionTool(player)) {
            return;
        }

        RegionSelectionManager manager = RegionCommands.getSelectionManager();
        boolean worldEdit = SimpleServerUtilities.REGION_SELECTION_TOOLS.isWorldEditTool(player, player.getMainHandItem());
        RegionSelection selection = manager.getSelection(player);
        if (worldEdit) {
            // World Edit intentionally has deterministic mouse controls: left = P1, right block = P2.
            manager.clear(player);
            manager.setPoint1(player, event.getPos());
            player.sendSystemMessage(Component.literal(
                    "World Edit point 1 set to " + formatPos(event.getPos()) + ". Right-click a block for point 2."
            ), true);
        } else {
            // Region Tool mirrors the World Edit Tool: left block = Point 1,
            // right block = Point 2, right-click air = open Region GUI.
            manager.clear(player);
            manager.setPoint1(player, event.getPos());
            player.sendSystemMessage(Component.literal(
                    "Region point 1 set to " + formatPos(event.getPos()) + ". Right-click a block for point 2."
            ), true);
        }
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, manager.getSelection(player));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RegionSetupToolService.hasActivePreview(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !RegionSetupToolService.hasActivePreview(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && RegionSetupToolService.hasActivePreview(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && RegionSetupToolService.hasActivePreview(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && RegionSetupToolService.hasActivePreview(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !RegionSetupToolService.hasActivePreview(player)) return;
        ItemStack stack = event.getEntity().getItem().copy();
        if (!stack.isEmpty() && player.getInventory().add(stack)) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && RegionSetupToolService.hasActivePreview(player)) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player
                && RegionSetupToolService.hasActivePreview(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Commands are disabled while a snapshot preview is active."), true);
        }
    }

    @SubscribeEvent
    public static void onPreviewDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RegionSetupToolService.cancelPreview(player, "Snapshot preview cancelled after changing dimension.");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPreviewDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RegionSetupToolService.cancelPreview(player, "Snapshot preview cancelled because you died.");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RegionCommands.getSelectionManager().clear(player);
        RegionSelectionSchematicManager.clearClipboard(player.getUUID());
        WorldEditHistoryManager.clear(player.getUUID());
        RegionSetupToolService.clearPreview(player.getUUID());
        LAST_REGION.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (RegionSetupToolService.hasActivePreview(player) && player.containerMenu != player.inventoryMenu) player.closeContainer();
        }
        if (!Config.ENABLE_ADMIN_REGIONS.get()) return;
        long tick = server.getTickCount();

        if (tick >= nextEnterLeaveTick) {
            nextEnterLeaveTick = tick + 10L;
            updateRegionMessages(server);
        }
        if (tick >= nextResetTick) {
            nextResetTick = tick + 20L;
            RegionResetScheduler.tick(server);
        }
    }

    public static void showBoundary(ServerPlayer player, String regionName) {
        Region region = SimpleServerUtilities.REGIONS.get(regionName);
        if (region != null) {
            SimpleServerUtilities.BORDER_VISUALIZATIONS.showRegion(player, region);
        }
    }

    public static void hideBoundary(ServerPlayer player, String regionName) {
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideRegion(player, regionName);
    }

    public static void hideAllBoundaries(ServerPlayer player) {
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideRegion(player);
    }

    private static void updateRegionMessages(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Region region = SimpleServerUtilities.REGIONS.getAt(player.level().dimension(), player.blockPosition());
            String currentName = region == null ? "" : region.getName();
            String previousName = LAST_REGION.getOrDefault(player.getUUID(), "");

            if (currentName.equals(previousName)) {
                continue;
            }

            if (!previousName.isBlank()) {
                Region previousRegion = SimpleServerUtilities.REGIONS.get(previousName);

                if (previousRegion != null && !previousRegion.getLeaveMessage().isBlank()) {
                    player.sendSystemMessage(Component.literal(previousRegion.getLeaveMessage()), true);
                }
            }

            if (region != null && !region.getWelcomeMessage().isBlank()) {
                player.sendSystemMessage(Component.literal(region.getWelcomeMessage()), true);
            }

            LAST_REGION.put(player.getUUID(), currentName);
        }
    }

    private static void sendRentOffer(ServerPlayer player, Region region) {
        RegionRentData rentData = region.getRentData();

        player.sendSystemMessage(Component.literal("Rent region: " + region.getName()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Price: " + MoneyFormat.format(
                rentData.getPriceMinor(SimpleServerUtilities.ECONOMY.settings()),
                SimpleServerUtilities.ECONOMY.settings()
        ) + " / " + formatPeriod(rentData)));
        player.sendSystemMessage(Component.literal("Remaining status: " + (rentData.isRented() ? "already rented" : "available")));

        if (rentData.isRented()) {
            return;
        }

        Component accept = Component.literal("[ACCEPT]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/regions rentaccept " + region.getName())));

        Component decline = Component.literal(" [DECLINE]")
                .withStyle(style -> style.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/regions rentdecline " + region.getName())));

        player.sendSystemMessage(Component.literal("").append(accept).append(decline));
    }

    private static String formatPeriod(RegionRentData rentData) {
        if (rentData.isPermanent()) {
            return "permanent";
        }

        return rentData.getPeriodDays() + " day(s)";
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
