package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MailRequestPayload(String mode, int pageIndex, int pageSize, long requestId) implements CustomPacketPayload {
    public static final Type<MailRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "mail_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MailRequestPayload> STREAM_CODEC =
            StreamCodec.of(MailRequestPayload::encode, MailRequestPayload::decode);

    public MailRequestPayload {
        mode = "sent".equalsIgnoreCase(mode) ? "sent" : "inbox";
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(20, pageSize));
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, MailRequestPayload p) {
        b.writeUtf(p.mode, 16); b.writeVarInt(p.pageIndex); b.writeVarInt(p.pageSize); b.writeVarLong(p.requestId);
    }

    private static MailRequestPayload decode(RegistryFriendlyByteBuf b) {
        return new MailRequestPayload(b.readUtf(16), b.readVarInt(), b.readVarInt(), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
