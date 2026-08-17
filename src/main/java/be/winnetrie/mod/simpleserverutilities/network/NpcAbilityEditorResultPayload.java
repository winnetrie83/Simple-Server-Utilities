package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAbilityEditorResultPayload(boolean success,String message,String abilityId,long requestId) implements CustomPacketPayload {
    public static final Type<NpcAbilityEditorResultPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"npc_ability_editor_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NpcAbilityEditorResultPayload> STREAM_CODEC=StreamCodec.of(NpcAbilityEditorResultPayload::encode,NpcAbilityEditorResultPayload::decode);
    public NpcAbilityEditorResultPayload{message=PayloadBounds.string(message,256);abilityId=PayloadBounds.string(abilityId,64);requestId=Math.max(0L,requestId);}private static void encode(RegistryFriendlyByteBuf b,NpcAbilityEditorResultPayload p){b.writeBoolean(p.success);b.writeUtf(p.message,256);b.writeUtf(p.abilityId,64);b.writeVarLong(p.requestId);}private static NpcAbilityEditorResultPayload decode(RegistryFriendlyByteBuf b){return new NpcAbilityEditorResultPayload(b.readBoolean(),b.readUtf(256),b.readUtf(64),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
