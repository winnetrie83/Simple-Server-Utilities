package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.Identifier;
public record OnboardingActionPayload(String action,int pageIndex,long requestId) implements CustomPacketPayload{
 public static final Type<OnboardingActionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"onboarding_action"));public static final StreamCodec<RegistryFriendlyByteBuf,OnboardingActionPayload> STREAM_CODEC=StreamCodec.of(OnboardingActionPayload::encode,OnboardingActionPayload::decode);
 public OnboardingActionPayload{action=PayloadBounds.string(action,32);pageIndex=Math.max(0,pageIndex);requestId=Math.max(0L,requestId);}private static void encode(RegistryFriendlyByteBuf b,OnboardingActionPayload p){b.writeUtf(p.action,32);b.writeVarInt(p.pageIndex);b.writeVarLong(p.requestId);}private static OnboardingActionPayload decode(RegistryFriendlyByteBuf b){return new OnboardingActionPayload(b.readUtf(32),b.readVarInt(),b.readVarLong());}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
