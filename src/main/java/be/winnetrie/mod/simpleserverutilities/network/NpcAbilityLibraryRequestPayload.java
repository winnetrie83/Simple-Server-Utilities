package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcAbilityLibraryRequestPayload(String query, int pageIndex, long requestId) implements CustomPacketPayload {
    public static final Type<NpcAbilityLibraryRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_ability_library_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcAbilityLibraryRequestPayload> STREAM_CODEC = StreamCodec.of(NpcAbilityLibraryRequestPayload::encode, NpcAbilityLibraryRequestPayload::decode);
    public NpcAbilityLibraryRequestPayload { query = PayloadBounds.trimmedString(query, 64); pageIndex = Math.max(0, pageIndex); requestId = Math.max(0L, requestId); }
    private static void encode(RegistryFriendlyByteBuf b, NpcAbilityLibraryRequestPayload p){b.writeUtf(p.query,64);b.writeVarInt(p.pageIndex);b.writeVarLong(p.requestId);} 
    private static NpcAbilityLibraryRequestPayload decode(RegistryFriendlyByteBuf b){return new NpcAbilityLibraryRequestPayload(b.readUtf(64),b.readVarInt(),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
