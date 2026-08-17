package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.mail.MailRichText;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MailDataPayload(
        String mode,
        int pageIndex,
        int pageSize,
        int totalEntries,
        long requestId,
        boolean accessAllowed,
        boolean canSend,
        boolean canSendItems,
        boolean canSendMoney,
        int maxAttachments,
        int inboxSoftCap,
        int sentLimit,
        int visibleCount,
        int queuedCount,
        int unreadCount,
        int retentionDays,
        String formattedBalance,
        String notice,
        boolean error,
        List<Entry> entries
) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 20;

    public static final Type<MailDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "mail_data")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MailDataPayload> STREAM_CODEC =
            StreamCodec.of(MailDataPayload::encode, MailDataPayload::decode);

    public MailDataPayload {
        mode = "sent".equalsIgnoreCase(mode) ? "sent" : "inbox";
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_ENTRIES, pageSize));
        totalEntries = Math.max(0, totalEntries);
        requestId = Math.max(0L, requestId);
        maxAttachments = Math.max(0, Math.min(9, maxAttachments));
        inboxSoftCap = Math.max(1, inboxSoftCap);
        sentLimit = Math.max(0, sentLimit);
        visibleCount = Math.max(0, visibleCount);
        queuedCount = Math.max(0, queuedCount);
        unreadCount = Math.max(0, unreadCount);
        retentionDays = Math.max(1, retentionDays);
        formattedBalance = PayloadBounds.string(formattedBalance, 128);
        notice = PayloadBounds.string(notice, 256);
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many mail entries in payload.");
    }

    public static MailDataPayload denied(String mode, int pageIndex, int pageSize, long requestId, String notice) {
        return new MailDataPayload(mode, pageIndex, pageSize, 0, requestId, false, false, false, false,
                0, 20, 20, 0, 0, 0, 30, "", notice, true, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf b, MailDataPayload p) {
        b.writeUtf(p.mode, 16); b.writeVarInt(p.pageIndex); b.writeVarInt(p.pageSize); b.writeVarInt(p.totalEntries);
        b.writeVarLong(p.requestId); b.writeBoolean(p.accessAllowed); b.writeBoolean(p.canSend);
        b.writeBoolean(p.canSendItems); b.writeBoolean(p.canSendMoney); b.writeVarInt(p.maxAttachments);
        b.writeVarInt(p.inboxSoftCap); b.writeVarInt(p.sentLimit); b.writeVarInt(p.visibleCount);
        b.writeVarInt(p.queuedCount); b.writeVarInt(p.unreadCount); b.writeVarInt(p.retentionDays);
        b.writeUtf(p.formattedBalance, 128); b.writeUtf(p.notice, 256); b.writeBoolean(p.error);
        b.writeVarInt(p.entries.size());
        for (Entry e : p.entries) writeEntry(b, e);
    }

    private static MailDataPayload decode(RegistryFriendlyByteBuf b) {
        String mode = b.readUtf(16); int pageIndex = b.readVarInt(); int pageSize = b.readVarInt(); int total = b.readVarInt();
        long requestId = b.readVarLong(); boolean access = b.readBoolean(); boolean canSend = b.readBoolean();
        boolean canItems = b.readBoolean(); boolean canMoney = b.readBoolean(); int maxAttachments = b.readVarInt();
        int softCap = b.readVarInt(); int sentLimit = b.readVarInt(); int visible = b.readVarInt();
        int queued = b.readVarInt(); int unread = b.readVarInt(); int retention = b.readVarInt();
        String balance = b.readUtf(128); String notice = b.readUtf(256); boolean error = b.readBoolean();
        int size = b.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid mail entry payload size: " + size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(readEntry(b));
        return new MailDataPayload(mode, pageIndex, pageSize, total, requestId, access, canSend, canItems, canMoney,
                maxAttachments, softCap, sentLimit, visible, queued, unread, retention, balance, notice, error, entries);
    }

    private static void writeEntry(RegistryFriendlyByteBuf b, Entry e) {
        b.writeUtf(e.id, 64); b.writeUtf(e.otherParty, 64); b.writeUtf(e.subject, 96); b.writeUtf(e.body, MailRichText.MAX_STORED_CHARACTERS);
        b.writeUtf(e.source, 24); b.writeVarLong(e.createdAt); b.writeVarLong(e.visibleSince); b.writeBoolean(e.read);
        b.writeVarInt(e.itemStackCount); b.writeVarInt(e.unclaimedItemCount); b.writeUtf(e.itemSummary, 512);
        b.writeVarLong(e.moneyMinor); b.writeUtf(e.formattedMoney, 128); b.writeBoolean(e.moneyUnclaimed);
        b.writeVarLong(e.openedAt); b.writeVarLong(e.itemsClaimedAt); b.writeVarLong(e.moneyClaimedAt);
    }

    private static Entry readEntry(RegistryFriendlyByteBuf b) {
        return new Entry(b.readUtf(64), b.readUtf(64), b.readUtf(96), b.readUtf(MailRichText.MAX_STORED_CHARACTERS), b.readUtf(24),
                b.readVarLong(), b.readVarLong(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readUtf(512),
                b.readVarLong(), b.readUtf(128), b.readBoolean(), b.readVarLong(), b.readVarLong(), b.readVarLong());
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(
            String id,
            String otherParty,
            String subject,
            String body,
            String source,
            long createdAt,
            long visibleSince,
            boolean read,
            int itemStackCount,
            int unclaimedItemCount,
            String itemSummary,
            long moneyMinor,
            String formattedMoney,
            boolean moneyUnclaimed,
            long openedAt,
            long itemsClaimedAt,
            long moneyClaimedAt
    ) {
        public Entry {
            id = PayloadBounds.string(id, 64); otherParty = PayloadBounds.string(otherParty, 64); subject = PayloadBounds.string(subject, 96);
            body = MailRichText.normalize(body); source = PayloadBounds.string(source, 24); createdAt = Math.max(0L, createdAt);
            visibleSince = Math.max(0L, visibleSince); itemStackCount = Math.max(0, Math.min(9, itemStackCount));
            unclaimedItemCount = Math.max(0, Math.min(itemStackCount, unclaimedItemCount));
            itemSummary = PayloadBounds.string(itemSummary, 512); moneyMinor = Math.max(0L, moneyMinor);
            formattedMoney = PayloadBounds.string(formattedMoney, 128); openedAt = Math.max(0L, openedAt);
            itemsClaimedAt = Math.max(0L, itemsClaimedAt); moneyClaimedAt = Math.max(0L, moneyClaimedAt);
        }
    }
}
