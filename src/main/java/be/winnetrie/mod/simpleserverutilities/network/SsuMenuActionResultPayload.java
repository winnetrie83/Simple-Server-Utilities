package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuMenuActionResultPayload(long requestId,boolean successful,String message,String refreshPage)
        implements CustomPacketPayload {
    public static final Type<SsuMenuActionResultPayload> TYPE=new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"menu_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SsuMenuActionResultPayload> STREAM_CODEC=
            StreamCodec.of(SsuMenuActionResultPayload::encode,SsuMenuActionResultPayload::decode);
    public SsuMenuActionResultPayload{requestId=Math.max(0L,requestId);message=PayloadBounds.string(message,512);
        refreshPage=PayloadBounds.string(refreshPage,32);}
    private static void encode(RegistryFriendlyByteBuf b,SsuMenuActionResultPayload p){b.writeVarLong(p.requestId);
        b.writeBoolean(p.successful);b.writeUtf(p.message,512);b.writeUtf(p.refreshPage,32);}
    private static SsuMenuActionResultPayload decode(RegistryFriendlyByteBuf b){return new SsuMenuActionResultPayload(
            b.readVarLong(),b.readBoolean(),b.readUtf(512),b.readUtf(32));}
@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
