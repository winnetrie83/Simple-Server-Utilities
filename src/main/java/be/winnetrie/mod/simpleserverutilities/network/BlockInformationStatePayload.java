package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlockInformationStatePayload(
        boolean allowed,
        boolean enabled,
        boolean debugAllowed,
        boolean debugEnabled,
        boolean inventoryAllowed,
        int inventoryMaxItems
) implements CustomPacketPayload {
    public static final Type<BlockInformationStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "block_information_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockInformationStatePayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeBoolean(payload.allowed());
                buffer.writeBoolean(payload.enabled());
                buffer.writeBoolean(payload.debugAllowed());
                buffer.writeBoolean(payload.debugEnabled());
                buffer.writeBoolean(payload.inventoryAllowed());
                buffer.writeVarInt(payload.inventoryMaxItems());
            }, buffer -> new BlockInformationStatePayload(
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), Math.max(0, Math.min(BlockInformationContentPayload.MAX_ITEMS, buffer.readVarInt()))));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
