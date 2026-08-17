package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcAdminActionPayload(String action,String target,long requestId) implements CustomPacketPayload{
    public static final Type<NpcAdminActionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_admin_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcAdminActionPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.action,32);b.writeUtf(p.target,64);b.writeVarLong(p.requestId);},b->new NpcAdminActionPayload(b.readUtf(32),b.readUtf(64),b.readVarLong()));
    public NpcAdminActionPayload{action=action==null?"":action;target=target==null?"":target;requestId=Math.max(0,requestId);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
