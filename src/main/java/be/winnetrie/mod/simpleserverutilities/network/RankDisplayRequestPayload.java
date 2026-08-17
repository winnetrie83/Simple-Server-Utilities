package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RankDisplayRequestPayload(String rankName) implements CustomPacketPayload {
    public static final Type<RankDisplayRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "rank_display_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RankDisplayRequestPayload> STREAM_CODEC =
            StreamCodec.of((b,p)->b.writeUtf(PayloadBounds.string(p.rankName,64),64), b->new RankDisplayRequestPayload(b.readUtf(64)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
