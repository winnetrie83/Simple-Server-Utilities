package be.winnetrie.mod.simpleserverutilities.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MailMessage {
    private UUID id = UUID.randomUUID();
    private UUID senderId;
    private String senderName = "Server";
    private UUID recipientId;
    private String recipientName = "";
    private String subject = "";
    private String body = "";
    private MailSource source = MailSource.PLAYER;
    private MailState state = MailState.QUEUED;
    private long createdAtEpochMilli = System.currentTimeMillis();
    private long visibleSinceEpochMilli;
    private long queuedSinceEpochMilli;
    private boolean read;
    private List<MailItemAttachment> items = new ArrayList<>();
    private long moneyMinor;
    private boolean moneyClaimed;
    private String correlationKey = "";

    public MailMessage() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id == null ? UUID.randomUUID() : id; }
    public UUID getSenderId() { return senderId; }
    public void setSenderId(UUID senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName == null ? "Server" : senderName; }
    public void setSenderName(String senderName) { this.senderName = clean(senderName, "Server", 64); }
    public UUID getRecipientId() { return recipientId; }
    public void setRecipientId(UUID recipientId) { this.recipientId = recipientId; }
    public String getRecipientName() { return recipientName == null ? "" : recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = clean(recipientName, "", 64); }
    public String getSubject() { return subject == null ? "" : subject; }
    public void setSubject(String subject) { this.subject = clean(subject, "", 96); }
    public String getBody() { return body == null ? "" : body; }
    public void setBody(String body) { this.body = MailRichText.normalize(body); }
    public MailSource getSource() { return source == null ? MailSource.SYSTEM : source; }
    public void setSource(MailSource source) { this.source = source == null ? MailSource.SYSTEM : source; }
    public MailState getState() { return state == null ? MailState.QUEUED : state; }
    public void setState(MailState state) { this.state = state == null ? MailState.QUEUED : state; }
    public long getCreatedAtEpochMilli() { return Math.max(0L, createdAtEpochMilli); }
    public void setCreatedAtEpochMilli(long value) { createdAtEpochMilli = Math.max(0L, value); }
    public long getVisibleSinceEpochMilli() { return Math.max(0L, visibleSinceEpochMilli); }
    public void setVisibleSinceEpochMilli(long value) { visibleSinceEpochMilli = Math.max(0L, value); }
    public long getQueuedSinceEpochMilli() { return Math.max(0L, queuedSinceEpochMilli); }
    public void setQueuedSinceEpochMilli(long value) { queuedSinceEpochMilli = Math.max(0L, value); }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public List<MailItemAttachment> getItems() {
        if (items == null) items = new ArrayList<>();
        return items;
    }
    public void setItems(List<MailItemAttachment> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
    public long getMoneyMinor() { return Math.max(0L, moneyMinor); }
    public void setMoneyMinor(long moneyMinor) { this.moneyMinor = Math.max(0L, moneyMinor); }
    public boolean isMoneyClaimed() { return moneyClaimed || getMoneyMinor() == 0L; }
    public void setMoneyClaimed(boolean moneyClaimed) { this.moneyClaimed = moneyClaimed; }
    public String getCorrelationKey() { return correlationKey == null ? "" : correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = clean(correlationKey, "", 160); }

    public boolean hasUnclaimedItems() {
        return getItems().stream().anyMatch(item -> item != null && !item.isClaimed());
    }

    public int unclaimedItemCount() {
        return (int) getItems().stream().filter(item -> item != null && !item.isClaimed()).count();
    }

    public boolean hasUnclaimedMoney() {
        return getMoneyMinor() > 0L && !moneyClaimed;
    }

    public boolean hasUnclaimedAttachments() {
        return hasUnclaimedItems() || hasUnclaimedMoney();
    }

    public void normalize() {
        if (id == null) id = UUID.randomUUID();
        senderName = clean(senderName, "Server", 64);
        recipientName = clean(recipientName, "", 64);
        subject = clean(subject, "", 96);
        body = MailRichText.normalize(body);
        source = source == null ? MailSource.SYSTEM : source;
        state = state == null ? MailState.QUEUED : state;
        createdAtEpochMilli = Math.max(0L, createdAtEpochMilli);
        visibleSinceEpochMilli = Math.max(0L, visibleSinceEpochMilli);
        queuedSinceEpochMilli = Math.max(0L, queuedSinceEpochMilli);
        if (state == MailState.VISIBLE && visibleSinceEpochMilli == 0L) {
            visibleSinceEpochMilli = Math.max(createdAtEpochMilli, System.currentTimeMillis());
        }
        if (state == MailState.VISIBLE) queuedSinceEpochMilli = 0L;
        if (state == MailState.QUEUED && queuedSinceEpochMilli == 0L) {
            queuedSinceEpochMilli = Math.max(1L, createdAtEpochMilli);
        }
        if (items == null) items = new ArrayList<>();
        items.removeIf(java.util.Objects::isNull);
        moneyMinor = Math.max(0L, moneyMinor);
        if (moneyMinor == 0L) moneyClaimed = true;
        correlationKey = clean(correlationKey, "", 160);
    }

    private static String clean(String value, String fallback, int max) {
        String result = value == null ? fallback : value.trim();
        if (result.isBlank()) result = fallback;
        return result.length() <= max ? result : result.substring(0, max);
    }
}
