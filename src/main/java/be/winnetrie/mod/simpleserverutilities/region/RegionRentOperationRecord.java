package be.winnetrie.mod.simpleserverutilities.region;

import java.util.UUID;

/** Durable reconciliation journal for a region-rent economy operation. */
public final class RegionRentOperationRecord {

    public enum Action {
        RENT,
        RENEW,
        CANCEL_REFUND
    }

    public enum Status {
        PREPARED,
        PAYMENT_COMMITTED,
        REGION_COMMITTED,
        COMPLETED,
        ROLLED_BACK,
        FAILED
    }

    private UUID operationId;
    private String idempotencyKey = "";
    private Action action = Action.RENT;
    private Status status = Status.PREPARED;
    private String regionName = "";
    private String dimension = "";
    private UUID renterId;
    private String renterName = "";
    private UUID ownerRecipientId;
    private long grossAmountMinor;
    private long ownerPayoutMinor;
    private long refundAmountMinor;
    private long targetRentalSequence;
    private UUID debitTransactionId;
    private UUID ownerPayoutTransactionId;
    private UUID refundTransactionId;
    private long createdAtEpochMilli;
    private long updatedAtEpochMilli;
    private String error = "";

    public static RegionRentOperationRecord prepared(
            UUID operationId,
            String idempotencyKey,
            Action action,
            Region region,
            UUID renterId,
            String renterName,
            UUID ownerRecipientId,
            long grossAmountMinor,
            long ownerPayoutMinor,
            long refundAmountMinor,
            long targetRentalSequence
    ) {
        RegionRentOperationRecord record = new RegionRentOperationRecord();
        record.operationId = operationId;
        record.idempotencyKey = safe(idempotencyKey);
        record.action = action;
        record.status = Status.PREPARED;
        record.regionName = region.getName();
        record.dimension = region.getDimension().identifier().toString();
        record.renterId = renterId;
        record.renterName = safe(renterName);
        record.ownerRecipientId = ownerRecipientId;
        record.grossAmountMinor = Math.max(0L, grossAmountMinor);
        record.ownerPayoutMinor = Math.max(0L, ownerPayoutMinor);
        record.refundAmountMinor = Math.max(0L, refundAmountMinor);
        record.targetRentalSequence = Math.max(0L, targetRentalSequence);
        long now = System.currentTimeMillis();
        record.createdAtEpochMilli = now;
        record.updatedAtEpochMilli = now;
        return record;
    }

    public void markPaymentCommitted(UUID debitId, UUID ownerPayoutId) {
        status = Status.PAYMENT_COMMITTED;
        debitTransactionId = debitId;
        ownerPayoutTransactionId = ownerPayoutId;
        touch();
    }

    public void markRegionCommitted() {
        status = Status.REGION_COMMITTED;
        touch();
    }

    public void markCompleted(UUID refundId) {
        status = Status.COMPLETED;
        refundTransactionId = refundId;
        error = "";
        touch();
    }

    public void markRefundPending(String reason) {
        status = Status.REGION_COMMITTED;
        error = safe(reason);
        touch();
    }

    /**
     * Keeps a paid operation recoverable when region storage or compensation
     * could not be confirmed. Startup recovery will reconcile the region state
     * and either keep the rental or compensate the payment exactly once.
     */
    public void markPaymentRecoveryPending(String reason) {
        status = Status.PAYMENT_COMMITTED;
        error = safe(reason);
        touch();
    }

    /**
     * Keeps a cancellation recoverable while it is uncertain whether the
     * cleared or restored region state reached disk.
     */
    public void markCancellationRecoveryPending(String reason) {
        status = Status.PREPARED;
        error = safe(reason);
        touch();
    }

    public void markRolledBack(String reason) {
        status = Status.ROLLED_BACK;
        error = safe(reason);
        touch();
    }

    public void markFailed(String reason) {
        status = Status.FAILED;
        error = safe(reason);
        touch();
    }

    private void touch() {
        updatedAtEpochMilli = System.currentTimeMillis();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public UUID getOperationId() { return operationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Action getAction() { return action; }
    public Status getStatus() { return status; }
    public String getRegionName() { return regionName; }
    public String getDimension() { return dimension; }
    public UUID getRenterId() { return renterId; }
    public String getRenterName() { return renterName; }
    public UUID getOwnerRecipientId() { return ownerRecipientId; }
    public long getGrossAmountMinor() { return grossAmountMinor; }
    public long getOwnerPayoutMinor() { return ownerPayoutMinor; }
    public long getRefundAmountMinor() { return refundAmountMinor; }
    public long getTargetRentalSequence() { return targetRentalSequence; }
    public UUID getDebitTransactionId() { return debitTransactionId; }
    public UUID getOwnerPayoutTransactionId() { return ownerPayoutTransactionId; }
    public UUID getRefundTransactionId() { return refundTransactionId; }
    public long getCreatedAtEpochMilli() { return createdAtEpochMilli; }
    public long getUpdatedAtEpochMilli() { return updatedAtEpochMilli; }
    public String getError() { return error; }
}
