package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Refreshes or pages an already-authorized NPC shop session. */
public record NpcShopRequestPayload(String instanceId, String shopId, int pageIndex, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcShopRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopRequestPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopRequestPayload::encode, NpcShopRequestPayload::decode);
    public NpcShopRequestPayload {
        instanceId = PayloadBounds.string(instanceId, 36); shopId = PayloadBounds.string(shopId, 64);
        pageIndex = Math.max(0, pageIndex); requestId = Math.max(0L, requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b, NpcShopRequestPayload p) {
        b.writeUtf(p.instanceId,36); b.writeUtf(p.shopId,64); b.writeVarInt(p.pageIndex); b.writeVarLong(p.requestId);
    }
    private static NpcShopRequestPayload decode(RegistryFriendlyByteBuf b) {
        return new NpcShopRequestPayload(b.readUtf(36), b.readUtf(64), b.readVarInt(), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
