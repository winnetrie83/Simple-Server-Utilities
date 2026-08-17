package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcAbilityLibraryDataPayload(String query,int pageIndex,int pageCount,int totalAbilities,List<Entry> entries,String notice,boolean error,long requestId) implements CustomPacketPayload {
    public static final int MAX_ENTRIES=12;
    public static final Type<NpcAbilityLibraryDataPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_ability_library_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcAbilityLibraryDataPayload> STREAM_CODEC=StreamCodec.of(NpcAbilityLibraryDataPayload::encode,NpcAbilityLibraryDataPayload::decode);
    public NpcAbilityLibraryDataPayload{query=PayloadBounds.string(query,64);pageIndex=Math.max(0,pageIndex);pageCount=Math.max(1,pageCount);totalAbilities=Math.max(0,totalAbilities);entries=entries==null?List.of():List.copyOf(entries.subList(0,Math.min(entries.size(),MAX_ENTRIES)));notice=PayloadBounds.string(notice,256);requestId=Math.max(0L,requestId);}
    private static void encode(RegistryFriendlyByteBuf b,NpcAbilityLibraryDataPayload p){b.writeUtf(p.query,64);b.writeVarInt(p.pageIndex);b.writeVarInt(p.pageCount);b.writeVarInt(p.totalAbilities);b.writeVarInt(p.entries.size());for(Entry e:p.entries)e.encode(b);b.writeUtf(p.notice,256);b.writeBoolean(p.error);b.writeVarLong(p.requestId);}
    private static NpcAbilityLibraryDataPayload decode(RegistryFriendlyByteBuf b){String q=b.readUtf(64);int page=b.readVarInt(),pages=b.readVarInt(),total=b.readVarInt(),count=b.readVarInt();if(count<0||count>MAX_ENTRIES)throw new IllegalArgumentException("Invalid NPC ability library row count.");ArrayList<Entry> es=new ArrayList<>();for(int i=0;i<count;i++)es.add(Entry.decode(b));return new NpcAbilityLibraryDataPayload(q,page,pages,total,es,b.readUtf(256),b.readBoolean(),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public record Entry(String id,String displayName,String channel,String executor,int usageCount){public Entry{id=PayloadBounds.string(id,64);displayName=PayloadBounds.string(displayName,64);channel=PayloadBounds.string(channel,24);executor=PayloadBounds.string(executor,32);usageCount=Math.max(0,usageCount);}private void encode(RegistryFriendlyByteBuf b){b.writeUtf(id,64);b.writeUtf(displayName,64);b.writeUtf(channel,24);b.writeUtf(executor,32);b.writeVarInt(usageCount);}private static Entry decode(RegistryFriendlyByteBuf b){return new Entry(b.readUtf(64),b.readUtf(64),b.readUtf(24),b.readUtf(32),b.readVarInt());}}
}
