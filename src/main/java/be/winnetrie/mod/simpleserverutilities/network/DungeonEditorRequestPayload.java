package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.Identifier;
public record DungeonEditorRequestPayload(String dungeonId, long requestId) implements CustomPacketPayload {
 public static final Type<DungeonEditorRequestPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"dungeon_editor_request"));
 public static final StreamCodec<RegistryFriendlyByteBuf,DungeonEditorRequestPayload> STREAM_CODEC=StreamCodec.of(DungeonEditorRequestPayload::encode,DungeonEditorRequestPayload::decode);
 public DungeonEditorRequestPayload { dungeonId=PayloadBounds.string(dungeonId,64); requestId=Math.max(0L,requestId); }
 private static void encode(RegistryFriendlyByteBuf b,DungeonEditorRequestPayload p){b.writeUtf(p.dungeonId,64);b.writeVarLong(p.requestId);} private static DungeonEditorRequestPayload decode(RegistryFriendlyByteBuf b){return new DungeonEditorRequestPayload(b.readUtf(64),b.readVarLong());}
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
