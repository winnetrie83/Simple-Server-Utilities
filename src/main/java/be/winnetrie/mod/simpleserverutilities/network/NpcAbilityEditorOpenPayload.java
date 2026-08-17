package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAbilityEditorOpenPayload(String originalAbilityId,String definitionJson,int usageCount,long requestId) implements CustomPacketPayload {
    public static final int MAX_JSON=32_768;
    public static final Type<NpcAbilityEditorOpenPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_ability_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcAbilityEditorOpenPayload> STREAM_CODEC=StreamCodec.of(NpcAbilityEditorOpenPayload::encode,NpcAbilityEditorOpenPayload::decode);
    public NpcAbilityEditorOpenPayload{originalAbilityId=PayloadBounds.string(originalAbilityId,64);definitionJson=PayloadBounds.string(definitionJson,MAX_JSON);usageCount=Math.max(0,usageCount);requestId=Math.max(0L,requestId);}
    private static void encode(RegistryFriendlyByteBuf b,NpcAbilityEditorOpenPayload p){b.writeUtf(p.originalAbilityId,64);b.writeUtf(p.definitionJson,MAX_JSON);b.writeVarInt(p.usageCount);b.writeVarLong(p.requestId);}private static NpcAbilityEditorOpenPayload decode(RegistryFriendlyByteBuf b){return new NpcAbilityEditorOpenPayload(b.readUtf(64),b.readUtf(MAX_JSON),b.readVarInt(),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
