package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AuctionHouseActionPayload(
        String action,
        String target,
        int quantity,
        String value,
        int durationHours,
        long requestId
) implements CustomPacketPayload {
    public static final Type<AuctionHouseActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "auction_house_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionHouseActionPayload> STREAM_CODEC =
            StreamCodec.of((b, p) -> {
                b.writeUtf(p.action(), 32);
                b.writeUtf(p.target(), 256);
                b.writeVarInt(p.quantity());
                b.writeUtf(p.value(), 256);
                b.writeVarInt(p.durationHours());
                b.writeVarLong(p.requestId());
            }, b -> new AuctionHouseActionPayload(
                    b.readUtf(32), b.readUtf(256), b.readVarInt(), b.readUtf(256), b.readVarInt(), b.readVarLong()));

    public AuctionHouseActionPayload {
        action = action == null ? "" : action;
        target = target == null ? "" : target;
        quantity = Math.max(0, quantity);
        value = value == null ? "" : value;
        durationHours = Math.max(0, durationHours);
        requestId = Math.max(0L, requestId);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
