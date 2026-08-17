package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RankDisplaySavePayload(String rankName, String encodedPrefix) implements CustomPacketPayload {
    public static final Type<RankDisplaySavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "rank_display_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RankDisplaySavePayload> STREAM_CODEC = StreamCodec.of(
            (b,p)->{b.writeUtf(PayloadBounds.string(p.rankName,64),64);b.writeUtf(PayloadBounds.string(p.encodedPrefix,256),256);},
            b->new RankDisplaySavePayload(b.readUtf(64),b.readUtf(256)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
