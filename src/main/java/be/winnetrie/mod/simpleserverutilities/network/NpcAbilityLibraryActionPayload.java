package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAbilityLibraryActionPayload(String action, String abilityId, String newAbilityId, String query, int pageIndex, long requestId) implements CustomPacketPayload {
    public static final Type<NpcAbilityLibraryActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_ability_library_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcAbilityLibraryActionPayload> STREAM_CODEC = StreamCodec.of(NpcAbilityLibraryActionPayload::encode, NpcAbilityLibraryActionPayload::decode);
    public NpcAbilityLibraryActionPayload { action=PayloadBounds.trimmedString(action,24);abilityId=PayloadBounds.trimmedString(abilityId,64);newAbilityId=PayloadBounds.trimmedString(newAbilityId,64);query=PayloadBounds.trimmedString(query,64);pageIndex=Math.max(0,pageIndex);requestId=Math.max(0L,requestId);}
    private static void encode(RegistryFriendlyByteBuf b,NpcAbilityLibraryActionPayload p){b.writeUtf(p.action,24);b.writeUtf(p.abilityId,64);b.writeUtf(p.newAbilityId,64);b.writeUtf(p.query,64);b.writeVarInt(p.pageIndex);b.writeVarLong(p.requestId);} 
    private static NpcAbilityLibraryActionPayload decode(RegistryFriendlyByteBuf b){return new NpcAbilityLibraryActionPayload(b.readUtf(24),b.readUtf(64),b.readUtf(64),b.readUtf(64),b.readVarInt(),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
