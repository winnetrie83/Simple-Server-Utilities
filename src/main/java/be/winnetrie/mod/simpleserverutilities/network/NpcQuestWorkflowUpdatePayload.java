package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** action = save, create, or access. */
public record NpcQuestWorkflowUpdatePayload(String instanceId,String action,String questId,String relation,String requestedAccessMode,String availableText,String acceptText,String activeText,String readyText,String turnInText,String completedText,boolean showAvailable,boolean showActive,boolean showReady,long requestId) implements CustomPacketPayload {
    public static final Type<NpcQuestWorkflowUpdatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_quest_workflow_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcQuestWorkflowUpdatePayload> STREAM_CODEC=StreamCodec.of(NpcQuestWorkflowUpdatePayload::encode,NpcQuestWorkflowUpdatePayload::decode);
    public NpcQuestWorkflowUpdatePayload{instanceId=PayloadBounds.string(instanceId,36);action=PayloadBounds.string(action,16);questId=PayloadBounds.string(questId,64);relation=PayloadBounds.string(relation,16);requestedAccessMode=PayloadBounds.string(requestedAccessMode,16);availableText=PayloadBounds.string(availableText,4096);acceptText=PayloadBounds.string(acceptText,256);activeText=PayloadBounds.string(activeText,4096);readyText=PayloadBounds.string(readyText,4096);turnInText=PayloadBounds.string(turnInText,256);completedText=PayloadBounds.string(completedText,4096);requestId=Math.max(0L,requestId);}private static void encode(RegistryFriendlyByteBuf b,NpcQuestWorkflowUpdatePayload p){b.writeUtf(p.instanceId,36);b.writeUtf(p.action,16);b.writeUtf(p.questId,64);b.writeUtf(p.relation,16);b.writeUtf(p.requestedAccessMode,16);b.writeUtf(p.availableText,4096);b.writeUtf(p.acceptText,256);b.writeUtf(p.activeText,4096);b.writeUtf(p.readyText,4096);b.writeUtf(p.turnInText,256);b.writeUtf(p.completedText,4096);b.writeBoolean(p.showAvailable);b.writeBoolean(p.showActive);b.writeBoolean(p.showReady);b.writeVarLong(p.requestId);}private static NpcQuestWorkflowUpdatePayload decode(RegistryFriendlyByteBuf b){return new NpcQuestWorkflowUpdatePayload(b.readUtf(36),b.readUtf(16),b.readUtf(64),b.readUtf(16),b.readUtf(16),b.readUtf(4096),b.readUtf(256),b.readUtf(4096),b.readUtf(4096),b.readUtf(256),b.readUtf(4096),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarLong());}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
