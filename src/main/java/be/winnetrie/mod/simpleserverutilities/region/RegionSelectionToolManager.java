package be.winnetrie.mod.simpleserverutilities.region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RegionSelectionToolManager {

    public static final String SSU_ADMIN_TOOL_NAME = "SSU Region Tool";
    public static final String SSU_WORLD_EDIT_TOOL_NAME = "SSU World Edit Tool";

    private final Map<UUID, String> boundTools = new HashMap<>();

    public void clear() {
        boundTools.clear();
    }

    public void bind(ServerPlayer player, ItemStack stack) {
        boundTools.put(player.getUUID(), getStackKey(stack));
    }

    public void unbind(ServerPlayer player) {
        boundTools.remove(player.getUUID());
    }

    public boolean hasBoundTool(ServerPlayer player) {
        return boundTools.containsKey(player.getUUID());
    }

    public boolean isRegionTool(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.WOODEN_AXE) && SSU_ADMIN_TOOL_NAME.equals(stack.getHoverName().getString())) return true;
        String boundTool = boundTools.get(player.getUUID());
        return boundTool != null && boundTool.equals(getStackKey(stack))
                && !SSU_WORLD_EDIT_TOOL_NAME.equals(stack.getHoverName().getString());
    }

    public boolean isWorldEditTool(ServerPlayer player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty()
                && stack.is(Items.GOLDEN_AXE)
                && SSU_WORLD_EDIT_TOOL_NAME.equals(stack.getHoverName().getString());
    }

    /** Any SSU cuboid-selection tool. Region and World Edit deliberately use their own interaction flow. */
    public boolean isSelectionTool(ServerPlayer player, ItemStack stack) {
        return isRegionTool(player, stack) || isWorldEditTool(player, stack);
    }

    /** Backward-compatible alias for older call sites; prefer isRegionTool/isSelectionTool. */
    public boolean isBoundTool(ServerPlayer player, ItemStack stack) {
        return isRegionTool(player, stack);
    }

    public String getBoundTool(ServerPlayer player) {
        return boundTools.getOrDefault(player.getUUID(), "none");
    }

    private String getStackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getHoverName().getString();
    }
}
