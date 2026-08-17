package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Save/capture feedback for the visual NPC shop editor. */
public record NpcShopEditorResultPayload(boolean successful, String message, String shopId,
        boolean closeEditor, long requestId) implements CustomPacketPayload {
    public static final Type<NpcShopEditorResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopEditorResultPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopEditorResultPayload::encode, NpcShopEditorResultPayload::decode);

    public NpcShopEditorResultPayload {
        message = PayloadBounds.string(message, 512);
        shopId = PayloadBounds.string(shopId, 64);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcShopEditorResultPayload payload) {
        buffer.writeBoolean(payload.successful);
        buffer.writeUtf(payload.message, 512);
        buffer.writeUtf(payload.shopId, 64);
        buffer.writeBoolean(payload.closeEditor);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcShopEditorResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcShopEditorResultPayload(buffer.readBoolean(), buffer.readUtf(512), buffer.readUtf(64),
                buffer.readBoolean(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
