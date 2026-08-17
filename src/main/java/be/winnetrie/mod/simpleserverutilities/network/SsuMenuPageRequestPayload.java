package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests one bounded dashboard page. */
public record SsuMenuPageRequestPayload(
        String page,
        int pageIndex,
        int pageSize,
        String query,
        long requestId
) implements CustomPacketPayload {

    public static final int MAX_PAGE_SIZE = 50;
    public static final Type<SsuMenuPageRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "menu_page_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuMenuPageRequestPayload> STREAM_CODEC =
            StreamCodec.of(SsuMenuPageRequestPayload::encode, SsuMenuPageRequestPayload::decode);

    public SsuMenuPageRequestPayload {
        page = PayloadBounds.string(page, 32).trim().toLowerCase(java.util.Locale.ROOT);
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
        query = PayloadBounds.string(query, 96).trim();
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuMenuPageRequestPayload payload) {
        buffer.writeUtf(payload.page, 32);
        buffer.writeVarInt(payload.pageIndex);
        buffer.writeVarInt(payload.pageSize);
        buffer.writeUtf(payload.query, 96);
        buffer.writeVarLong(payload.requestId);
    }

    private static SsuMenuPageRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuMenuPageRequestPayload(
                buffer.readUtf(32),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(96),
                buffer.readVarLong()
        );
    }
@Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
