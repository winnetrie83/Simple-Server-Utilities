package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RegionSetupActionPayload(String operation,String regionName,String value,long requestId) implements CustomPacketPayload {
    public static final Type<RegionSetupActionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"region_setup_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RegionSetupActionPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(PayloadBounds.string(p.operation,40),40);b.writeUtf(PayloadBounds.string(p.regionName,64),64);b.writeUtf(PayloadBounds.string(p.value,128),128);b.writeVarLong(Math.max(0L,p.requestId));},
            b->new RegionSetupActionPayload(b.readUtf(40),b.readUtf(64),b.readUtf(128),b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
