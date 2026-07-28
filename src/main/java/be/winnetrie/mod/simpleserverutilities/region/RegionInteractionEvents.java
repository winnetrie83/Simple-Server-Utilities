package be.winnetrie.mod.simpleserverutilities.region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.world.level.block.SignBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class RegionInteractionEvents {

    private static final Map<UUID, String> LAST_REGION = new HashMap<>();
    private static long nextEnterLeaveTick = 0L;

    private RegionInteractionEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (SimpleServerUtilities.REGION_SELECTION_TOOLS.isBoundTool(player, player.getMainHandItem())) {
            if (!RegionPolicy.canUseSelectionTool(player)) {
                return;
            }

            RegionCommands.getSelectionManager().setPoint2(player, event.getPos());
            SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(
                    player,
                    RegionCommands.getSelectionManager().getSelection(player)
            );
            player.sendSystemMessage(Component.literal("Region point 2 set to " + formatPos(event.getPos()) + "."));
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
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!SimpleServerUtilities.REGION_SELECTION_TOOLS.isBoundTool(player, player.getMainHandItem())) {
            return;
        }

        if (!RegionPolicy.canUseSelectionTool(player)) {
            return;
        }

        RegionCommands.getSelectionManager().setPoint1(player, event.getPos());
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(
                player,
                RegionCommands.getSelectionManager().getSelection(player)
        );
        player.sendSystemMessage(Component.literal("Region point 1 set to " + formatPos(event.getPos()) + "."));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();

        if (tick >= nextEnterLeaveTick) {
            nextEnterLeaveTick = tick + 10L;
            updateRegionMessages(server);
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
