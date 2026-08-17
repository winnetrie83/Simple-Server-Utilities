package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.ResourceLocation;
public record DungeonEditorSubmitPayload(String originalDungeonId,String definitionJson,long requestId) implements CustomPacketPayload {
 public static final Type<DungeonEditorSubmitPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"dungeon_editor_submit"));
 public static final StreamCodec<RegistryFriendlyByteBuf,DungeonEditorSubmitPayload> STREAM_CODEC=StreamCodec.of(DungeonEditorSubmitPayload::encode,DungeonEditorSubmitPayload::decode);
 public DungeonEditorSubmitPayload { originalDungeonId=PayloadBounds.string(originalDungeonId,64); definitionJson=PayloadBounds.string(definitionJson,65535); requestId=Math.max(0L,requestId); }
 private static void encode(RegistryFriendlyByteBuf b,DungeonEditorSubmitPayload p){b.writeUtf(p.originalDungeonId,64);b.writeUtf(p.definitionJson,65535);b.writeVarLong(p.requestId);} private static DungeonEditorSubmitPayload decode(RegistryFriendlyByteBuf b){return new DungeonEditorSubmitPayload(b.readUtf(64),b.readUtf(65535),b.readVarLong());}
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
