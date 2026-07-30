package be.winnetrie.mod.simpleserverutilities.mail;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

public final class MailItemCodec {
    private MailItemCodec() {
    }

    public static JsonElement encode(HolderLookup.Provider registries, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot encode an empty mail attachment.");
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return ItemStack.CODEC.encodeStart(ops, stack.copy()).getOrThrow();
    }

    public static ItemStack decode(HolderLookup.Provider registries, JsonElement encoded) {
        if (encoded == null || encoded.isJsonNull()) {
            return ItemStack.EMPTY;
        }
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return ItemStack.CODEC.parse(ops, encoded).result().orElse(ItemStack.EMPTY).copy();
    }
}
