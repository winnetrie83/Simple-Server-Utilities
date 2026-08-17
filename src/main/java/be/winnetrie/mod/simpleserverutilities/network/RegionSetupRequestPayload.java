package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RegionSetupRequestPayload(String operation, String regionName, long requestId) implements CustomPacketPayload {
    public static final Type<RegionSetupRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"region_setup_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RegionSetupRequestPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(PayloadBounds.string(p.operation,32),32);b.writeUtf(PayloadBounds.string(p.regionName,64),64);b.writeVarLong(Math.max(0L,p.requestId));},
            b->new RegionSetupRequestPayload(b.readUtf(32),b.readUtf(64),b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
