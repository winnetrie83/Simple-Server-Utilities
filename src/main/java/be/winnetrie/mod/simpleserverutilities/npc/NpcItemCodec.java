package be.winnetrie.mod.simpleserverutilities.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/** Registry-aware persistence codec for exact NPC equipment and loot stacks. */
public final class NpcItemCodec {
    private NpcItemCodec() {
    }

    public static JsonElement encode(HolderLookup.Provider registries, ItemStack stack) {
        if (registries == null || stack == null || stack.isEmpty()) return JsonNull.INSTANCE;
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return ItemStack.CODEC.encodeStart(ops, stack.copy()).result().orElse(JsonNull.INSTANCE);
    }

    public static ItemStack decode(HolderLookup.Provider registries, JsonElement encoded,
            String legacyItemId, int legacyCount) {
        if (registries != null && encoded != null && !encoded.isJsonNull()) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).result().orElse(ItemStack.EMPTY);
            if (!decoded.isEmpty()) return decoded.copy();
        }
        if (legacyItemId == null || legacyItemId.isBlank()) return ItemStack.EMPTY;
        try {
            ItemStack stack = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(legacyItemId.trim()))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), legacyCount)));
            return stack;
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static JsonElement safeCopy(JsonElement encoded) {
        return encoded == null || encoded.isJsonNull() ? JsonNull.INSTANCE : encoded.deepCopy();
    }

    public static boolean configured(JsonElement encoded, String legacyItemId) {
        return encoded != null && !encoded.isJsonNull()
                || legacyItemId != null && !legacyItemId.isBlank();
    }
}
