package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests one filtered page from the visual NPC shop library manager. */
public record NpcShopAdminRequestPayload(String query, int pageIndex, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcShopAdminRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_admin_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopAdminRequestPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopAdminRequestPayload::encode, NpcShopAdminRequestPayload::decode);

    public NpcShopAdminRequestPayload {
        query = PayloadBounds.trimmedString(query, 64);
        pageIndex = Math.max(0, pageIndex);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcShopAdminRequestPayload payload) {
        buffer.writeUtf(payload.query, 64);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarLong(payload.requestId);
    }

    private static NpcShopAdminRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NpcShopAdminRequestPayload(buffer.readUtf(64), buffer.readVarInt(), buffer.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
