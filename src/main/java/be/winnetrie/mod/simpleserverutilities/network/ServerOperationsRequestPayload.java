package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;
public record ServerOperationsRequestPayload(boolean admin,long requestId) implements CustomPacketPayload{
 public static final Type<ServerOperationsRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"server_operations_request"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ServerOperationsRequestPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeBoolean(p.admin);b.writeVarLong(p.requestId);},b->new ServerOperationsRequestPayload(b.readBoolean(),b.readVarLong()));
 public ServerOperationsRequestPayload{requestId=Math.max(0L,requestId);}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
