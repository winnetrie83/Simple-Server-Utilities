package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AuctionHouseActionResultPayload(
        boolean successful,
        String message,
        long requestId,
        boolean playPurchaseSound,
        boolean refresh
) implements CustomPacketPayload {
    public static final Type<AuctionHouseActionResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "auction_house_action_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionHouseActionResultPayload> STREAM_CODEC =
            StreamCodec.of((b, p) -> {
                b.writeBoolean(p.successful());
                b.writeUtf(p.message(), 256);
                b.writeVarLong(p.requestId());
                b.writeBoolean(p.playPurchaseSound());
                b.writeBoolean(p.refresh());
            }, b -> new AuctionHouseActionResultPayload(
                    b.readBoolean(), b.readUtf(256), b.readVarLong(), b.readBoolean(), b.readBoolean()));

    public AuctionHouseActionResultPayload {
        message = message == null ? "" : message;
        requestId = Math.max(0L, requestId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
