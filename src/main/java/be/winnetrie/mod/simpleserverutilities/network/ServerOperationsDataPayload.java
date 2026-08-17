package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;
public record ServerOperationsDataPayload(boolean admin,String json,String notice,boolean error,long requestId) implements CustomPacketPayload{
 public static final Type<ServerOperationsDataPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"server_operations_data"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ServerOperationsDataPayload> STREAM_CODEC=StreamCodec.of(ServerOperationsDataPayload::encode,ServerOperationsDataPayload::decode);
 public ServerOperationsDataPayload{json=PayloadBounds.string(json,262144);notice=PayloadBounds.string(notice,1024);requestId=Math.max(0L,requestId);}
 private static void encode(RegistryFriendlyByteBuf b,ServerOperationsDataPayload p){b.writeBoolean(p.admin);b.writeUtf(p.json,262144);b.writeUtf(p.notice,1024);b.writeBoolean(p.error);b.writeVarLong(p.requestId);}
 private static ServerOperationsDataPayload decode(RegistryFriendlyByteBuf b){return new ServerOperationsDataPayload(b.readBoolean(),b.readUtf(262144),b.readUtf(1024),b.readBoolean(),b.readVarLong());}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
