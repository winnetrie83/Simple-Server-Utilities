package be.winnetrie.mod.simpleserverutilities.economy;

import java.time.Instant;
import java.util.UUID;

public final class EconomyTransactionRecord {

    private int schemaVersion = 1;
    private UUID transactionId;
    private String idempotencyKey;
    private EconomyTransactionType type;
    private EconomyTransactionStatus status;
    private String module;
    private String reason;
    private UUID actorId;
    private String actorName;
    private UUID sourceId;
    private String sourceName;
    private UUID destinationId;
    private String destinationName;
    private long amountMinor;
    private long sourceBalanceBefore;
    private long sourceBalanceAfter;
    private long sourceRevisionBefore;
    private long sourceRevisionAfter;
    private long destinationBalanceBefore;
    private long destinationBalanceAfter;
    private long destinationRevisionBefore;
    private long destinationRevisionAfter;
    private long createdAtEpochMilli;
    private long completedAtEpochMilli;
    private String failureMessage;

    public EconomyTransactionRecord() {
    }

    public static EconomyTransactionRecord prepared(
            UUID transactionId,
            String idempotencyKey,
            EconomyTransactionType type,
            String module,
            String reason,
            UUID actorId,
            String actorName,
            EconomyAccount source,
            EconomyAccount destination,
            long amountMinor,
            long sourceAfter,
            long destinationAfter
    ) {
        EconomyTransactionRecord record = new EconomyTransactionRecord();
        record.transactionId = transactionId;
        record.idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        record.type = type;
        record.status = EconomyTransactionStatus.PREPARED;
        record.module = module == null ? "economy" : module;
        record.reason = reason == null ? "" : reason;
        record.actorId = actorId;
        record.actorName = safe(actorName);
        record.amountMinor = amountMinor;
        record.createdAtEpochMilli = Instant.now().toEpochMilli();

        if (source != null) {
            record.sourceId = source.getPlayerId();
            record.sourceName = safe(source.getLastKnownName());
            record.sourceBalanceBefore = source.getBalanceMinor();
            record.sourceBalanceAfter = sourceAfter;
            record.sourceRevisionBefore = source.getRevision();
            record.sourceRevisionAfter = source.getRevision() + 1L;
        }

        if (destination != null) {
            record.destinationId = destination.getPlayerId();
            record.destinationName = safe(destination.getLastKnownName());
            record.destinationBalanceBefore = destination.getBalanceMinor();
            record.destinationBalanceAfter = destinationAfter;
            record.destinationRevisionBefore = destination.getRevision();
            record.destinationRevisionAfter = destination.getRevision() + 1L;
        }

        return record;
    }

    public void normalize() {
        schemaVersion = Math.max(1, schemaVersion);
        idempotencyKey = safe(idempotencyKey).trim();
        if (idempotencyKey.length() > 256) idempotencyKey = idempotencyKey.substring(0, 256);
        type = type == null ? EconomyTransactionType.RECOVERY : type;
        status = status == null ? EconomyTransactionStatus.FAILED : status;
        module = safe(module);
        reason = safe(reason);
        actorName = safe(actorName);
        sourceName = safe(sourceName);
        destinationName = safe(destinationName);
        amountMinor = Math.max(0L, amountMinor);
        failureMessage = safe(failureMessage);
    }

    public void markCommitted() {
        status = EconomyTransactionStatus.COMMITTED;
        completedAtEpochMilli = Instant.now().toEpochMilli();
        failureMessage = "";
    }

    public void markRolledBack(String failure) {
        status = EconomyTransactionStatus.ROLLED_BACK;
        completedAtEpochMilli = Instant.now().toEpochMilli();
        failureMessage = safe(failure);
    }

    public void markFailed(String failure) {
        status = EconomyTransactionStatus.FAILED;
        completedAtEpochMilli = Instant.now().toEpochMilli();
        failureMessage = safe(failure);
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public EconomyTransactionType getType() {
        return type;
    }

    public EconomyTransactionStatus getStatus() {
        return status;
    }

    public String getModule() {
        return module;
    }

    public String getReason() {
        return reason;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public long getSourceBalanceBefore() {
        return sourceBalanceBefore;
    }

    public long getSourceBalanceAfter() {
        return sourceBalanceAfter;
    }

    public long getSourceRevisionBefore() {
        return sourceRevisionBefore;
    }

    public long getSourceRevisionAfter() {
        return sourceRevisionAfter;
    }

    public long getDestinationBalanceBefore() {
        return destinationBalanceBefore;
    }

    public long getDestinationBalanceAfter() {
        return destinationBalanceAfter;
    }

    public long getDestinationRevisionBefore() {
        return destinationRevisionBefore;
    }

    public long getDestinationRevisionAfter() {
        return destinationRevisionAfter;
    }

    public long getCreatedAtEpochMilli() {
        return createdAtEpochMilli;
    }

    public long getCompletedAtEpochMilli() {
        return completedAtEpochMilli;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
