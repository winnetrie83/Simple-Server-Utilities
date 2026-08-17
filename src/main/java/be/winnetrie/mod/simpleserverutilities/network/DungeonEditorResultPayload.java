package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.ResourceLocation;
public record DungeonEditorResultPayload(boolean successful,String message,String dungeonId,long requestId) implements CustomPacketPayload {
 public static final Type<DungeonEditorResultPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"dungeon_editor_result"));
 public static final StreamCodec<RegistryFriendlyByteBuf,DungeonEditorResultPayload> STREAM_CODEC=StreamCodec.of(DungeonEditorResultPayload::encode,DungeonEditorResultPayload::decode);
 public DungeonEditorResultPayload { message=PayloadBounds.string(message,512); dungeonId=PayloadBounds.string(dungeonId,64); requestId=Math.max(0L,requestId); }
 private static void encode(RegistryFriendlyByteBuf b,DungeonEditorResultPayload p){b.writeBoolean(p.successful);b.writeUtf(p.message,512);b.writeUtf(p.dungeonId,64);b.writeVarLong(p.requestId);} private static DungeonEditorResultPayload decode(RegistryFriendlyByteBuf b){return new DungeonEditorResultPayload(b.readBoolean(),b.readUtf(512),b.readUtf(64),b.readVarLong());}
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
