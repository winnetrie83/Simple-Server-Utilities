package be.winnetrie.mod.simpleserverutilities.mail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MailboxData {
    public static final int CURRENT_SCHEMA = 3;
    private static final int DEFAULT_INBOX_SOFT_CAP = 20;
    private static final int DEFAULT_SENT_LIMIT = 20;

    private int schema = CURRENT_SCHEMA;
    private UUID playerId;
    private String lastKnownName = "";
    private int lastKnownInboxSoftCap = DEFAULT_INBOX_SOFT_CAP;
    private int lastKnownSentLimit = DEFAULT_SENT_LIMIT;
    private List<MailMessage> inbox = new ArrayList<>();
    private List<MailSentRecord> sent = new ArrayList<>();
    /** Independent rolling-rate history so clearing/capping Sent Mail cannot reset anti-spam limits. */
    private List<Long> outgoingSendHistory = new ArrayList<>();
    private Map<String, Long> deliveryReceipts = new HashMap<>();

    public MailboxData() {
    }

    public MailboxData(UUID playerId, String lastKnownName) {
        this.playerId = playerId;
        this.lastKnownName = lastKnownName == null ? "" : lastKnownName;
    }

    public UUID getPlayerId() { return playerId; }
    public String getLastKnownName() { return lastKnownName == null ? "" : lastKnownName; }
    public void updateName(String name) { if (name != null && !name.isBlank()) lastKnownName = name.trim(); }
    public int getLastKnownInboxSoftCap() { return Math.max(1, lastKnownInboxSoftCap); }
    public void updateInboxSoftCap(int value) { lastKnownInboxSoftCap = Math.max(1, Math.min(100_000, value)); }
    public int getLastKnownSentLimit() { return Math.max(0, lastKnownSentLimit); }
    public void updateSentLimit(int value) { lastKnownSentLimit = Math.max(0, Math.min(100_000, value)); }
    public List<MailMessage> getInbox() { if (inbox == null) inbox = new ArrayList<>(); return inbox; }
    public List<MailSentRecord> getSent() { if (sent == null) sent = new ArrayList<>(); return sent; }
    public List<Long> getOutgoingSendHistory() {
        if (outgoingSendHistory == null) outgoingSendHistory = new ArrayList<>();
        return outgoingSendHistory;
    }
    public Map<String, Long> getDeliveryReceipts() {
        if (deliveryReceipts == null) deliveryReceipts = new HashMap<>();
        return deliveryReceipts;
    }

    public void recordOutgoingSend(long now) {
        getOutgoingSendHistory().add(Math.max(1L, now));
        pruneOutgoingHistory(now);
    }

    public void removeOutgoingSend(long timestamp) {
        getOutgoingSendHistory().remove(Long.valueOf(timestamp));
    }

    public void pruneOutgoingHistory(long now) {
        long cutoff = Math.max(0L, now - Duration.ofDays(1).toMillis());
        getOutgoingSendHistory().removeIf(value -> value == null || value <= 0L || value < cutoff);
    }

    public boolean hasDeliveryReceipt(MailSource source, String correlationKey) {
        String key = receiptKey(source, correlationKey);
        return !key.isBlank() && getDeliveryReceipts().containsKey(key);
    }

    public boolean rememberDeliveryReceipt(MailSource source, String correlationKey, long deliveredAt) {
        String key = receiptKey(source, correlationKey);
        if (key.isBlank()) return false;
        return getDeliveryReceipts().putIfAbsent(key, Math.max(0L, deliveredAt)) == null;
    }

    public void forgetDeliveryReceipt(MailSource source, String correlationKey) {
        String key = receiptKey(source, correlationKey);
        if (!key.isBlank()) getDeliveryReceipts().remove(key);
    }

    public void normalize(UUID fallbackId) {
        int previousSchema = schema;
        if (playerId == null) playerId = fallbackId;
        if (lastKnownName == null) lastKnownName = "";
        updateInboxSoftCap(lastKnownInboxSoftCap <= 0 ? DEFAULT_INBOX_SOFT_CAP : lastKnownInboxSoftCap);
        // Schema 2 did not contain this field, so Gson supplies zero. Preserve a real
        // zero only for schema 3+, where it can be an intentional permission-derived limit.
        updateSentLimit(previousSchema < 3 ? DEFAULT_SENT_LIMIT
                : (lastKnownSentLimit < 0 ? DEFAULT_SENT_LIMIT : lastKnownSentLimit));
        if (inbox == null) inbox = new ArrayList<>();
        if (sent == null) sent = new ArrayList<>();
        if (outgoingSendHistory == null) outgoingSendHistory = new ArrayList<>();
        if (deliveryReceipts == null) deliveryReceipts = new HashMap<>();
        inbox.removeIf(java.util.Objects::isNull);
        sent.removeIf(java.util.Objects::isNull);
        inbox.forEach(MailMessage::normalize);
        sent.forEach(MailSentRecord::normalize);
        if (previousSchema < 3 && outgoingSendHistory.isEmpty()) {
            sent.stream().map(MailSentRecord::getSentAtEpochMilli).filter(value -> value > 0L)
                    .forEach(outgoingSendHistory::add);
        }
        pruneOutgoingHistory(System.currentTimeMillis());
        deliveryReceipts.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank());
        for (MailMessage mail : inbox) {
            rememberDeliveryReceipt(mail.getSource(), mail.getCorrelationKey(), mail.getCreatedAtEpochMilli());
        }
        schema = CURRENT_SCHEMA;
    }

    private static String receiptKey(MailSource source, String correlationKey) {
        if (correlationKey == null || correlationKey.isBlank()) return "";
        MailSource safeSource = source == null ? MailSource.SYSTEM : source;
        return safeSource.name() + ":" + correlationKey.trim();
    }
}
