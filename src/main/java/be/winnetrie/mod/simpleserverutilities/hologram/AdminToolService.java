package be.winnetrie.mod.simpleserverutilities.hologram;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionToolManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Issues named, permission-gated admin tools from the dashboard. */
public final class AdminToolService {
    private AdminToolService() {
    }

    public static boolean giveRegionTool(ServerPlayer player) {
        if (!Config.ENABLE_ADMIN_REGIONS.get()) return false;
        ItemStack stack = new ItemStack(Items.WOODEN_AXE);
        stack.set(DataComponents.ITEM_NAME, Component.literal(RegionSelectionToolManager.SSU_ADMIN_TOOL_NAME));
        SimpleServerUtilities.REGION_SELECTION_TOOLS.bind(player, stack);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return true;
    }

    public static boolean giveHologramTool(ServerPlayer player) {
        if (!Config.ENABLE_HOLOGRAMS.get()) return false;
        SimpleServerUtilities.HOLOGRAM_TOOLS.giveTool(player);
        return true;
    }
}
