package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.Identifier;
public record DungeonEditorOpenPayload(String originalDungeonId,String definitionJson,long requestId) implements CustomPacketPayload {
 public static final Type<DungeonEditorOpenPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"dungeon_editor_open"));
 public static final StreamCodec<RegistryFriendlyByteBuf,DungeonEditorOpenPayload> STREAM_CODEC=StreamCodec.of(DungeonEditorOpenPayload::encode,DungeonEditorOpenPayload::decode);
 public DungeonEditorOpenPayload { originalDungeonId=PayloadBounds.string(originalDungeonId,64); definitionJson=PayloadBounds.string(definitionJson,65535); requestId=Math.max(0L,requestId); }
 private static void encode(RegistryFriendlyByteBuf b,DungeonEditorOpenPayload p){b.writeUtf(p.originalDungeonId,64);b.writeUtf(p.definitionJson,65535);b.writeVarLong(p.requestId);} private static DungeonEditorOpenPayload decode(RegistryFriendlyByteBuf b){return new DungeonEditorOpenPayload(b.readUtf(64),b.readUtf(65535),b.readVarLong());}
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
