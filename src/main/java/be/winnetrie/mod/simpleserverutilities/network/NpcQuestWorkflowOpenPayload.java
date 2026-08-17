package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcQuestWorkflowOpenPayload(String instanceId,String npcName,String questAccessMode,String notice,List<Entry> quests) implements CustomPacketPayload {
    public static final Type<NpcQuestWorkflowOpenPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_quest_workflow_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcQuestWorkflowOpenPayload> STREAM_CODEC=StreamCodec.of(NpcQuestWorkflowOpenPayload::encode,NpcQuestWorkflowOpenPayload::decode);
    public NpcQuestWorkflowOpenPayload{instanceId=PayloadBounds.string(instanceId,36);npcName=PayloadBounds.string(npcName,64);questAccessMode=PayloadBounds.string(questAccessMode,16);notice=PayloadBounds.string(notice,256);quests=bounded(quests);}private static List<Entry> bounded(List<Entry> in){ArrayList<Entry> out=new ArrayList<>();if(in!=null)for(Entry e:in){if(e==null)continue;out.add(e);if(out.size()>=256)break;}return List.copyOf(out);}private static void encode(RegistryFriendlyByteBuf b,NpcQuestWorkflowOpenPayload p){b.writeUtf(p.instanceId,36);b.writeUtf(p.npcName,64);b.writeUtf(p.questAccessMode,16);b.writeUtf(p.notice,256);b.writeVarInt(p.quests.size());for(Entry e:p.quests)e.encode(b);}private static NpcQuestWorkflowOpenPayload decode(RegistryFriendlyByteBuf b){String id=b.readUtf(36),name=b.readUtf(64),mode=b.readUtf(16),notice=b.readUtf(256);int n=Math.min(256,Math.max(0,b.readVarInt()));ArrayList<Entry> entries=new ArrayList<>();for(int i=0;i<n;i++)entries.add(Entry.decode(b));return new NpcQuestWorkflowOpenPayload(id,name,mode,notice,entries);}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public record Entry(String questId,String title,String relation,String availableText,String acceptText,String activeText,String readyText,String turnInText,String completedText,boolean showAvailable,boolean showActive,boolean showReady){
        public Entry{questId=PayloadBounds.string(questId,64);title=PayloadBounds.string(title,128);relation=PayloadBounds.string(relation,16);availableText=PayloadBounds.string(availableText,4096);acceptText=PayloadBounds.string(acceptText,256);activeText=PayloadBounds.string(activeText,4096);readyText=PayloadBounds.string(readyText,4096);turnInText=PayloadBounds.string(turnInText,256);completedText=PayloadBounds.string(completedText,4096);}private void encode(RegistryFriendlyByteBuf b){b.writeUtf(questId,64);b.writeUtf(title,128);b.writeUtf(relation,16);b.writeUtf(availableText,4096);b.writeUtf(acceptText,256);b.writeUtf(activeText,4096);b.writeUtf(readyText,4096);b.writeUtf(turnInText,256);b.writeUtf(completedText,4096);b.writeBoolean(showAvailable);b.writeBoolean(showActive);b.writeBoolean(showReady);}private static Entry decode(RegistryFriendlyByteBuf b){return new Entry(b.readUtf(64),b.readUtf(128),b.readUtf(16),b.readUtf(4096),b.readUtf(256),b.readUtf(4096),b.readUtf(4096),b.readUtf(256),b.readUtf(4096),b.readBoolean(),b.readBoolean(),b.readBoolean());}
    }
}
