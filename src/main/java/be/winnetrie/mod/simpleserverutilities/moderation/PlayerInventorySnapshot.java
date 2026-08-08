package be.winnetrie.mod.simpleserverutilities.moderation;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Exact editable inventory/armor/offhand and ender-chest snapshot for online/offline administration. */
public final class PlayerInventorySnapshot {
    public static final int SCHEMA_VERSION = 1;
    public int schemaVersion = SCHEMA_VERSION;
    public String lastKnownName = "";
    public long updatedAt;
    public boolean pendingApply;
    public List<JsonElement> inventory = new ArrayList<>();
    public List<JsonElement> enderChest = new ArrayList<>();

    public static PlayerInventorySnapshot capture(ServerPlayer player) {
        PlayerInventorySnapshot snapshot = new PlayerInventorySnapshot();
        snapshot.lastKnownName = player.getName().getString();
        snapshot.updatedAt = System.currentTimeMillis();
        var ops = RegistryOps.create(JsonOps.INSTANCE, player.level().registryAccess());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            snapshot.inventory.add(encode(ops, player.getInventory().getItem(slot)));
        }
        for (int slot = 0; slot < player.getEnderChestInventory().getContainerSize(); slot++) {
            snapshot.enderChest.add(encode(ops, player.getEnderChestInventory().getItem(slot)));
        }
        return snapshot;
    }

    public void apply(ServerPlayer player) {
        normalize();
        var ops = RegistryOps.create(JsonOps.INSTANCE, player.level().registryAccess());
        ArrayList<ItemStack> inv = decodeAll(ops, inventory, player.getInventory().getContainerSize());
        ArrayList<ItemStack> ender = decodeAll(ops, enderChest, player.getEnderChestInventory().getContainerSize());
        player.getInventory().clearContent();
        for (int slot = 0; slot < inv.size(); slot++) player.getInventory().setItem(slot, inv.get(slot));
        player.getEnderChestInventory().clearContent();
        for (int slot = 0; slot < ender.size(); slot++) player.getEnderChestInventory().setItem(slot, ender.get(slot));
        player.getInventory().setChanged();
        player.getEnderChestInventory().setChanged();
        player.containerMenu.broadcastChanges();
        pendingApply = false;
        lastKnownName = player.getName().getString();
        updatedAt = System.currentTimeMillis();
    }

    public List<ItemStack> inventoryStacks(net.minecraft.core.HolderLookup.Provider registries, int maximum) {
        normalize();
        return List.copyOf(decodeAll(RegistryOps.create(JsonOps.INSTANCE, registries), inventory, maximum));
    }

    public List<ItemStack> enderStacks(net.minecraft.core.HolderLookup.Provider registries, int maximum) {
        normalize();
        return List.copyOf(decodeAll(RegistryOps.create(JsonOps.INSTANCE, registries), enderChest, maximum));
    }

    public void setInventoryStacks(net.minecraft.core.HolderLookup.Provider registries, List<ItemStack> values) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        inventory = new ArrayList<>();
        if (values != null) for (ItemStack value : values) inventory.add(encode(ops, value));
        updatedAt = System.currentTimeMillis(); normalize();
    }

    public void setEnderStacks(net.minecraft.core.HolderLookup.Provider registries, List<ItemStack> values) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        enderChest = new ArrayList<>();
        if (values != null) for (ItemStack value : values) enderChest.add(encode(ops, value));
        updatedAt = System.currentTimeMillis(); normalize();
    }

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        lastKnownName = lastKnownName == null ? "" : lastKnownName.trim();
        updatedAt = Math.max(0L, updatedAt);
        if (inventory == null) inventory = new ArrayList<>();
        if (enderChest == null) enderChest = new ArrayList<>();
        inventory = bounded(inventory, 128);
        enderChest = bounded(enderChest, 54);
    }

    private static JsonElement encode(com.mojang.serialization.DynamicOps<JsonElement> ops, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return JsonNull.INSTANCE;
        return ItemStack.CODEC.encodeStart(ops, stack.copy()).result().orElse(JsonNull.INSTANCE);
    }

    private static ArrayList<ItemStack> decodeAll(com.mojang.serialization.DynamicOps<JsonElement> ops, List<JsonElement> values, int maximum) {
        int count = Math.min(maximum, values == null ? 0 : values.size());
        ArrayList<ItemStack> result = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            JsonElement encoded = values.get(slot);
            ItemStack stack = encoded == null || encoded.isJsonNull() ? ItemStack.EMPTY
                    : ItemStack.CODEC.parse(ops, encoded).result().orElse(ItemStack.EMPTY).copy();
            result.add(stack);
        }
        return result;
    }

    private static List<JsonElement> bounded(List<JsonElement> values, int maximum) {
        ArrayList<JsonElement> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (result.size() >= maximum) break;
            result.add(value == null ? JsonNull.INSTANCE : value.deepCopy());
        }
        return result;
    }
}
