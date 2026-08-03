package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAdminListPayload(String mode,String query,int page,int pageCount,int total,List<NpcAdminEntry> entries,
        String notice,boolean error,long requestId) implements CustomPacketPayload {
    public static final Type<NpcAdminListPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_admin_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcAdminListPayload> STREAM_CODEC=StreamCodec.of(NpcAdminListPayload::encode,NpcAdminListPayload::decode);
    public NpcAdminListPayload{
        mode="templates".equalsIgnoreCase(mode)?"templates":"placements";query=query==null?"":query;page=Math.max(0,page);pageCount=Math.max(1,pageCount);total=Math.max(0,total);
        entries=entries==null?List.of():List.copyOf(entries.subList(0,Math.min(entries.size(),12)));notice=notice==null?"":notice;requestId=Math.max(0,requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b,NpcAdminListPayload p){
        b.writeUtf(p.mode,16);b.writeUtf(p.query,64);b.writeVarInt(p.page);b.writeVarInt(p.pageCount);b.writeVarInt(p.total);b.writeVarInt(p.entries.size());
        for(NpcAdminEntry e:p.entries)NpcAdminEntry.encode(b,e);b.writeUtf(p.notice,256);b.writeBoolean(p.error);b.writeVarLong(p.requestId);
    }
    private static NpcAdminListPayload decode(RegistryFriendlyByteBuf b){
        String mode=b.readUtf(16),query=b.readUtf(64);int page=b.readVarInt(),pages=b.readVarInt(),total=b.readVarInt(),count=b.readVarInt();
        if(count<0||count>12)throw new IllegalArgumentException("Invalid NPC admin row count");List<NpcAdminEntry> entries=new ArrayList<>(count);for(int i=0;i<count;i++)entries.add(NpcAdminEntry.decode(b));
        return new NpcAdminListPayload(mode,query,page,pages,total,entries,b.readUtf(256),b.readBoolean(),b.readVarLong());
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
