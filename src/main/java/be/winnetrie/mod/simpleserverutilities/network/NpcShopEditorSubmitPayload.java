package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Saves a visual shop draft or asks the server to copy one administrator inventory slot into it. */
public record NpcShopEditorSubmitPayload(String operation, String originalShopId, String definitionJson,
        String selectedEntryId, int inventorySlot, long requestId) implements CustomPacketPayload {
    public static final Type<NpcShopEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopEditorSubmitPayload::encode, NpcShopEditorSubmitPayload::decode);

    public NpcShopEditorSubmitPayload {
        operation = PayloadBounds.string(operation, 24);
        originalShopId = PayloadBounds.string(originalShopId, 64);
        definitionJson = PayloadBounds.string(definitionJson, NpcShopEditorOpenPayload.MAX_JSON);
        selectedEntryId = PayloadBounds.string(selectedEntryId, 64);
        inventorySlot = inventorySlot >= 0 && inventorySlot < 36 ? inventorySlot : -1;
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcShopEditorSubmitPayload payload) {
        buffer.writeUtf(payload.operation, 24);
        buffer.writeUtf(payload.originalShopId, 64);
        buffer.writeUtf(payload.definitionJson, NpcShopEditorOpenPayload.MAX_JSON);
        buffer.writeUtf(payload.selectedEntryId, 64);
        buffer.writeVarInt(payload.inventorySlot);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcShopEditorSubmitPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcShopEditorSubmitPayload(buffer.readUtf(24), buffer.readUtf(64),
                buffer.readUtf(NpcShopEditorOpenPayload.MAX_JSON), buffer.readUtf(64),
                buffer.readVarInt(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
