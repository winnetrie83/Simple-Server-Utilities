package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MailActionPayload(String action, String mailId, String mode, int pageIndex, long requestId)
        implements CustomPacketPayload {
    public static final Type<MailActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "mail_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MailActionPayload> STREAM_CODEC =
            StreamCodec.of(MailActionPayload::encode, MailActionPayload::decode);

    public MailActionPayload {
        action = bound(action, 32);
        mailId = bound(mailId, 64);
        mode = "sent".equalsIgnoreCase(mode) ? "sent" : "inbox";
        pageIndex = Math.max(0, pageIndex);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, MailActionPayload p) {
        b.writeUtf(p.action, 32); b.writeUtf(p.mailId, 64); b.writeUtf(p.mode, 16);
        b.writeVarInt(p.pageIndex); b.writeVarLong(p.requestId);
    }

    private static MailActionPayload decode(RegistryFriendlyByteBuf b) {
        return new MailActionPayload(b.readUtf(32), b.readUtf(64), b.readUtf(16), b.readVarInt(), b.readVarLong());
    }

    private static String bound(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
