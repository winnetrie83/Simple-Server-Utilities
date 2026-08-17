package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RankDisplayDataPayload(String rankName, String encodedPrefix, String notice, boolean error) implements CustomPacketPayload {
    public static final Type<RankDisplayDataPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "rank_display_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RankDisplayDataPayload> STREAM_CODEC = StreamCodec.of(
            (b,p)->{b.writeUtf(PayloadBounds.string(p.rankName,64),64);b.writeUtf(PayloadBounds.string(p.encodedPrefix,256),256);b.writeUtf(PayloadBounds.string(p.notice,256),256);b.writeBoolean(p.error);},
            b->new RankDisplayDataPayload(b.readUtf(64),b.readUtf(256),b.readUtf(256),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
