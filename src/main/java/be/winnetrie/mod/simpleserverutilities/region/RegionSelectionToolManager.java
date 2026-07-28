package be.winnetrie.mod.simpleserverutilities.region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class RegionSelectionToolManager {

    private final Map<UUID, String> boundTools = new HashMap<>();

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

        String boundTool = boundTools.get(player.getUUID());

        if (boundTool == null) {
            return false;
        }

        return boundTool.equals(getStackKey(stack));
    }

    public String getBoundTool(ServerPlayer player) {
        return boundTools.getOrDefault(player.getUUID(), "none");
    }

    private String getStackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
