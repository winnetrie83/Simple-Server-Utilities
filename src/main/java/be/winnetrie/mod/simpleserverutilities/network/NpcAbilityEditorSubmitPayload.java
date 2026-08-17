package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAbilityEditorSubmitPayload(String originalAbilityId,String definitionJson,long requestId) implements CustomPacketPayload {
    public static final Type<NpcAbilityEditorSubmitPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_ability_editor_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcAbilityEditorSubmitPayload> STREAM_CODEC=StreamCodec.of(NpcAbilityEditorSubmitPayload::encode,NpcAbilityEditorSubmitPayload::decode);
    public NpcAbilityEditorSubmitPayload{originalAbilityId=PayloadBounds.string(originalAbilityId,64);definitionJson=PayloadBounds.string(definitionJson,NpcAbilityEditorOpenPayload.MAX_JSON);requestId=Math.max(0L,requestId);}
    private static void encode(RegistryFriendlyByteBuf b,NpcAbilityEditorSubmitPayload p){b.writeUtf(p.originalAbilityId,64);b.writeUtf(p.definitionJson,NpcAbilityEditorOpenPayload.MAX_JSON);b.writeVarLong(p.requestId);}private static NpcAbilityEditorSubmitPayload decode(RegistryFriendlyByteBuf b){return new NpcAbilityEditorSubmitPayload(b.readUtf(64),b.readUtf(NpcAbilityEditorOpenPayload.MAX_JSON),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
