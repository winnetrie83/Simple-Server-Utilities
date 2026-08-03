package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative actions from the visual NPC shop library manager. */
public record NpcShopAdminActionPayload(String action, String shopId, String newShopId,
        String query, int pageIndex, long requestId) implements CustomPacketPayload {
    public static final Type<NpcShopAdminActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_admin_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopAdminActionPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopAdminActionPayload::encode, NpcShopAdminActionPayload::decode);

    public NpcShopAdminActionPayload {
        action = PayloadBounds.trimmedString(action, 24);
        shopId = PayloadBounds.trimmedString(shopId, 64);
        newShopId = PayloadBounds.trimmedString(newShopId, 64);
        query = PayloadBounds.trimmedString(query, 64);
        pageIndex = Math.max(0, pageIndex);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcShopAdminActionPayload payload) {
        buffer.writeUtf(payload.action, 24);
        buffer.writeUtf(payload.shopId, 64);
        buffer.writeUtf(payload.newShopId, 64);
        buffer.writeUtf(payload.query, 64);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcShopAdminActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcShopAdminActionPayload(buffer.readUtf(24), buffer.readUtf(64), buffer.readUtf(64),
                buffer.readUtf(64), buffer.readVarInt(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
