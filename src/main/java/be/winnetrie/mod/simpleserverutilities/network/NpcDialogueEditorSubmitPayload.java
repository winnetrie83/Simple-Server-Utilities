package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcDialogueEditorSubmitPayload(
        String instanceId, String originalDialogueId, String dialogueJson, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcDialogueEditorSubmitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_dialogue_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(NpcDialogueEditorSubmitPayload::encode, NpcDialogueEditorSubmitPayload::decode);
    public NpcDialogueEditorSubmitPayload {
        instanceId=PayloadBounds.string(instanceId,36);originalDialogueId=PayloadBounds.string(originalDialogueId,64);dialogueJson=PayloadBounds.string(dialogueJson,65_535);requestId=Math.max(0L,requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b,NpcDialogueEditorSubmitPayload p){b.writeUtf(p.instanceId,36);b.writeUtf(p.originalDialogueId,64);b.writeUtf(p.dialogueJson,65_535);b.writeVarLong(p.requestId);}
    private static NpcDialogueEditorSubmitPayload decode(RegistryFriendlyByteBuf b){return new NpcDialogueEditorSubmitPayload(b.readUtf(36),b.readUtf(64),b.readUtf(65_535),b.readVarLong());}

    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
