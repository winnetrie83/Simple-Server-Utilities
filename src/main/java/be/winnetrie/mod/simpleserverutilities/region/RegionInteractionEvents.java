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

        if (SimpleServerUtilities.REGION_SELECTION_TOOLS.isBoundTool(player, player.getMainHandItem())) {
            if (!RegionPolicy.canUseSelectionTool(player)) {
                return;
            }

            RegionSetupToolService.openContext(player);
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
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !SimpleServerUtilities.REGION_SELECTION_TOOLS.isBoundTool(player, player.getMainHandItem())
                || !RegionPolicy.canUseSelectionTool(player)) {
            return;
        }
        RegionSetupToolService.openContext(player);
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

        if (!SimpleServerUtilities.REGION_SELECTION_TOOLS.isBoundTool(player, player.getMainHandItem())) {
            return;
        }

        if (!RegionPolicy.canUseSelectionTool(player)) {
            return;
        }

        RegionSelectionManager manager = RegionCommands.getSelectionManager();
        RegionSelection selection = manager.getSelection(player);
        if (selection.getPoint1() == null || selection.isComplete()) {
            if (selection.isComplete()) {
                manager.clear(player);
            }
            manager.setPoint1(player, event.getPos());
            player.sendSystemMessage(Component.literal(
                    "Region point 1 set to " + formatPos(event.getPos()) + ". Left-click point 2 next."
            ), true);
        } else {
            manager.setPoint2(player, event.getPos());
            player.sendSystemMessage(Component.literal(
                    "Region point 2 set to " + formatPos(event.getPos()) + ". Right-click to choose an action."
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
