package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.mail.MailRichText;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MailComposeSubmitPayload(
        int containerId,
        String recipient,
        String subject,
        String body,
        String money,
        long requestId
) implements CustomPacketPayload {
    public static final Type<MailComposeSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "mail_compose_submit")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MailComposeSubmitPayload> STREAM_CODEC =
            StreamCodec.of(MailComposeSubmitPayload::encode, MailComposeSubmitPayload::decode);

    public MailComposeSubmitPayload {
        containerId = Math.max(0, containerId);
        recipient = bound(recipient, 64);
        subject = bound(subject, 96);
        body = MailRichText.normalize(body);
        money = bound(money, 64);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, MailComposeSubmitPayload p) {
        b.writeVarInt(p.containerId); b.writeUtf(p.recipient, 64); b.writeUtf(p.subject, 96);
        b.writeUtf(p.body, MailRichText.MAX_STORED_CHARACTERS); b.writeUtf(p.money, 64); b.writeVarLong(p.requestId);
    }

    private static MailComposeSubmitPayload decode(RegistryFriendlyByteBuf b) {
        return new MailComposeSubmitPayload(b.readVarInt(), b.readUtf(64), b.readUtf(96), b.readUtf(MailRichText.MAX_STORED_CHARACTERS),
                b.readUtf(64), b.readVarLong());
    }

    private static String bound(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
