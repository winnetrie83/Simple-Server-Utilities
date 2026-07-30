package be.winnetrie.mod.simpleserverutilities.mail;

import java.util.UUID;

/** Sender-side copy plus delivery/read/attachment status mirrored from the recipient mailbox. */
public final class MailSentRecord {
    private UUID mailId;
    private UUID recipientId;
    private String recipientName = "";
    private String subject = "";
    private String body = "";
    private int itemStackCount;
    private long moneyMinor;
    private long sentAtEpochMilli;
    private long openedAtEpochMilli;
    private long itemsClaimedAtEpochMilli;
    private long moneyClaimedAtEpochMilli;

    public MailSentRecord() {
    }

    public MailSentRecord(MailMessage message) {
        this.mailId = message.getId();
        this.recipientId = message.getRecipientId();
        this.recipientName = message.getRecipientName();
        this.subject = message.getSubject();
        this.body = message.getBody();
        this.itemStackCount = message.getItems().size();
        this.moneyMinor = message.getMoneyMinor();
        this.sentAtEpochMilli = message.getCreatedAtEpochMilli();
    }

    public UUID getMailId() { return mailId; }
    public UUID getRecipientId() { return recipientId; }
    public String getRecipientName() { return recipientName == null ? "" : recipientName; }
    public String getSubject() { return subject == null ? "" : subject; }
    public String getBody() { return body == null ? "" : body; }
    public int getItemStackCount() { return Math.max(0, itemStackCount); }
    public long getMoneyMinor() { return Math.max(0L, moneyMinor); }
    public long getSentAtEpochMilli() { return Math.max(0L, sentAtEpochMilli); }
    public long getOpenedAtEpochMilli() { return Math.max(0L, openedAtEpochMilli); }
    public long getItemsClaimedAtEpochMilli() { return Math.max(0L, itemsClaimedAtEpochMilli); }
    public long getMoneyClaimedAtEpochMilli() { return Math.max(0L, moneyClaimedAtEpochMilli); }
    public boolean isOpened() { return getOpenedAtEpochMilli() > 0L; }
    public boolean areItemsClaimed() { return getItemStackCount() == 0 || getItemsClaimedAtEpochMilli() > 0L; }
    public boolean isMoneyClaimed() { return getMoneyMinor() == 0L || getMoneyClaimedAtEpochMilli() > 0L; }

    public void markOpened(long now) {
        if (openedAtEpochMilli <= 0L) openedAtEpochMilli = Math.max(1L, now);
    }

    public void markItemsClaimed(long now) {
        if (getItemStackCount() > 0 && itemsClaimedAtEpochMilli <= 0L) {
            itemsClaimedAtEpochMilli = Math.max(1L, now);
        }
    }

    public void markMoneyClaimed(long now) {
        if (getMoneyMinor() > 0L && moneyClaimedAtEpochMilli <= 0L) {
            moneyClaimedAtEpochMilli = Math.max(1L, now);
        }
    }

    public void normalize() {
        if (recipientName == null) recipientName = "";
        if (subject == null) subject = "";
        if (body == null) body = "";
        itemStackCount = Math.max(0, Math.min(9, itemStackCount));
        moneyMinor = Math.max(0L, moneyMinor);
        sentAtEpochMilli = Math.max(0L, sentAtEpochMilli);
        openedAtEpochMilli = Math.max(0L, openedAtEpochMilli);
        itemsClaimedAtEpochMilli = Math.max(0L, itemsClaimedAtEpochMilli);
        moneyClaimedAtEpochMilli = Math.max(0L, moneyClaimedAtEpochMilli);
    }
}
