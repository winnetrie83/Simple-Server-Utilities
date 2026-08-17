package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AuctionHouseRequestPayload(
        String mode,
        String category,
        String search,
        String sort,
        int pageIndex,
        int pageSize,
        long requestId
) implements CustomPacketPayload {
    public static final Type<AuctionHouseRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "auction_house_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionHouseRequestPayload> STREAM_CODEC =
            StreamCodec.of((b, p) -> {
                b.writeUtf(p.mode(), 16);
                b.writeUtf(p.category(), 32);
                b.writeUtf(p.search(), 96);
                b.writeUtf(p.sort(), 32);
                b.writeVarInt(p.pageIndex());
                b.writeVarInt(p.pageSize());
                b.writeVarLong(p.requestId());
            }, b -> new AuctionHouseRequestPayload(
                    b.readUtf(16), b.readUtf(32), b.readUtf(96), b.readUtf(32),
                    b.readVarInt(), b.readVarInt(), b.readVarLong()));

    public AuctionHouseRequestPayload {
        mode = mode == null ? "browse" : mode;
        category = category == null ? "all" : category;
        search = search == null ? "" : search;
        sort = sort == null ? "name_asc" : sort;
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(12, pageSize));
        requestId = Math.max(0L, requestId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
