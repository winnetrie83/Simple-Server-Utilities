package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MailComposeResultPayload(boolean successful, String message, long requestId, boolean reopenMailbox)
        implements CustomPacketPayload {
    public static final Type<MailComposeResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "mail_compose_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MailComposeResultPayload> STREAM_CODEC =
            StreamCodec.of(MailComposeResultPayload::encode, MailComposeResultPayload::decode);

    public MailComposeResultPayload {
        message = message == null ? "" : message.length() <= 256 ? message : message.substring(0, 256);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, MailComposeResultPayload p) {
        b.writeBoolean(p.successful); b.writeUtf(p.message, 256); b.writeVarLong(p.requestId); b.writeBoolean(p.reopenMailbox);
    }

    private static MailComposeResultPayload decode(RegistryFriendlyByteBuf b) {
        return new MailComposeResultPayload(b.readBoolean(), b.readUtf(256), b.readVarLong(), b.readBoolean());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
