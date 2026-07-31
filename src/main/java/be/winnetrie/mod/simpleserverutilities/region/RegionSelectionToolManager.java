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

    public boolean isBoundTool(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        // The dashboard-issued SSU tool remains usable after relog/restart.
        // Permission checks still run for every interaction, so recognizing this
        // named tool does not grant region access by itself.
        if (stack.is(Items.WOODEN_AXE)
                && SSU_ADMIN_TOOL_NAME.equals(stack.getHoverName().getString())) {
            return true;
        }

        String boundTool = boundTools.get(player.getUUID());
        return boundTool != null && boundTool.equals(getStackKey(stack));
    }

    public String getBoundTool(ServerPlayer player) {
        return boundTools.getOrDefault(player.getUUID(), "none");
    }

    private String getStackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getHoverName().getString();
    }
}
