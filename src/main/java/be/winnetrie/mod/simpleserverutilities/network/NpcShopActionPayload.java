package be.winnetrie.mod.simpleserverutilities.network;

import java.util.Locale;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Direct click action for one current NPC shop session; quantity carries the clicked inventory slot for sales. */
public record NpcShopActionPayload(String action, String instanceId, String shopId, String entryId,
                                   int quantity, int pageIndex, long requestId) implements CustomPacketPayload {
    public static final Type<NpcShopActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_shop_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopActionPayload> STREAM_CODEC =
            StreamCodec.of(NpcShopActionPayload::encode, NpcShopActionPayload::decode);
    public NpcShopActionPayload {
        action = PayloadBounds.string(action==null?"":action.trim().toLowerCase(Locale.ROOT),16);
        instanceId=PayloadBounds.string(instanceId,36);shopId=PayloadBounds.string(shopId,64);entryId=PayloadBounds.string(entryId,64);
        quantity=Math.max(0,Math.min(64_000,quantity));pageIndex=Math.max(0,pageIndex);requestId=Math.max(0L,requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b,NpcShopActionPayload p){b.writeUtf(p.action,16);b.writeUtf(p.instanceId,36);b.writeUtf(p.shopId,64);b.writeUtf(p.entryId,64);b.writeVarInt(p.quantity);b.writeVarInt(p.pageIndex);b.writeVarLong(p.requestId);}
    private static NpcShopActionPayload decode(RegistryFriendlyByteBuf b){return new NpcShopActionPayload(b.readUtf(16),b.readUtf(36),b.readUtf(64),b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readVarLong());}

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
