package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcQuestWorkflowRequestPayload(String instanceId) implements CustomPacketPayload {
    public static final Type<NpcQuestWorkflowRequestPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_quest_workflow_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcQuestWorkflowRequestPayload> STREAM_CODEC=StreamCodec.of(NpcQuestWorkflowRequestPayload::encode,NpcQuestWorkflowRequestPayload::decode);
    public NpcQuestWorkflowRequestPayload{instanceId=PayloadBounds.string(instanceId,36);}private static void encode(RegistryFriendlyByteBuf b,NpcQuestWorkflowRequestPayload p){b.writeUtf(p.instanceId,36);}private static NpcQuestWorkflowRequestPayload decode(RegistryFriendlyByteBuf b){return new NpcQuestWorkflowRequestPayload(b.readUtf(36));}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
