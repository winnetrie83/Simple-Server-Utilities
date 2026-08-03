package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Complete bounded shop draft plus navigation and NPC-reference information for the visual editor. */
public record NpcShopEditorOpenPayload(String originalShopId, String definitionJson, String selectedEntryId,
        String currencySymbol, int decimalPlaces, int shopIndex, int shopCount, List<Usage> usages,
        String notice, long requestId) implements CustomPacketPayload {
    public static final int MAX_JSON = 131_072;
    public static final int MAX_USAGES = 128;
    public static final Type<NpcShopEditorOpenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopEditorOpenPayload::encode, NpcShopEditorOpenPayload::decode);

    public NpcShopEditorOpenPayload {
        originalShopId = PayloadBounds.string(originalShopId, 64);
        definitionJson = PayloadBounds.string(definitionJson, MAX_JSON);
        selectedEntryId = PayloadBounds.string(selectedEntryId, 64);
        currencySymbol = PayloadBounds.string(currencySymbol, 16);
        decimalPlaces = Math.max(0, Math.min(4, decimalPlaces));
        shopCount = Math.max(0, shopCount);
        shopIndex = shopCount == 0 ? -1 : Math.max(-1, Math.min(shopIndex, shopCount - 1));
        usages = usages == null ? List.of()
                : List.copyOf(usages.subList(0, Math.min(usages.size(), MAX_USAGES)));
        notice = PayloadBounds.string(notice, 256);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcShopEditorOpenPayload payload) {
        buffer.writeUtf(payload.originalShopId, 64);
        buffer.writeUtf(payload.definitionJson, MAX_JSON);
        buffer.writeUtf(payload.selectedEntryId, 64);
        buffer.writeUtf(payload.currencySymbol, 16);
        buffer.writeVarInt(payload.decimalPlaces);
        buffer.writeVarInt(payload.shopIndex + 1);
        buffer.writeVarInt(payload.shopCount);
        buffer.writeVarInt(payload.usages.size());
        for (Usage usage : payload.usages) usage.encode(buffer);
        buffer.writeUtf(payload.notice, 256);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcShopEditorOpenPayload decode(RegistryFriendlyByteBuf buffer) {
        String originalShopId = buffer.readUtf(64);
        String definitionJson = buffer.readUtf(MAX_JSON);
        String selectedEntryId = buffer.readUtf(64);
        String currencySymbol = buffer.readUtf(16);
        int decimalPlaces = buffer.readVarInt();
        int shopIndex = buffer.readVarInt() - 1;
        int shopCount = buffer.readVarInt();
        int usageCount = buffer.readVarInt();
        if (usageCount < 0 || usageCount > MAX_USAGES) {
            throw new IllegalArgumentException("Invalid NPC shop usage count.");
        }
        ArrayList<Usage> usages = new ArrayList<>(usageCount);
        for (int index = 0; index < usageCount; index++) usages.add(Usage.decode(buffer));
        return new NpcShopEditorOpenPayload(originalShopId, definitionJson, selectedEntryId,
                currencySymbol, decimalPlaces, shopIndex, shopCount, usages,
                buffer.readUtf(256), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** One reusable NPC definition that exposes this shop, plus its placed instance count. */
    public record Usage(String definitionId, String displayName, int placementCount, int functionCount) {
        public Usage {
            definitionId = PayloadBounds.string(definitionId, 64);
            displayName = PayloadBounds.string(displayName, 64);
            placementCount = Math.max(0, placementCount);
            functionCount = Math.max(1, functionCount);
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(definitionId, 64);
            buffer.writeUtf(displayName, 64);
            buffer.writeVarInt(placementCount);
            buffer.writeVarInt(functionCount);
        }

        private static Usage decode(RegistryFriendlyByteBuf buffer) {
            return new Usage(buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readVarInt(), buffer.readVarInt());
        }
    }
}
