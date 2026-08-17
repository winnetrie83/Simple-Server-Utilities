package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DamageIndicatorPayload(double x,double y,double z,float amount,boolean healing,String style,int seed) implements CustomPacketPayload {
    public static final Type<DamageIndicatorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"damage_indicator"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DamageIndicatorPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(Math.max(0F,p.amount));b.writeBoolean(p.healing);b.writeUtf(PayloadBounds.string(p.style,16),16);b.writeInt(p.seed);},
            b->new DamageIndicatorPayload(b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readBoolean(),b.readUtf(16),b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
