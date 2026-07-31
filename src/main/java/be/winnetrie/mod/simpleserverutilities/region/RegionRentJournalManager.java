package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;

/**
 * Small durable journal for cross-module region-rent operations.
 *
 * <p>Economy mutations already have their own journal. This layer links those
 * mutations to the region rental sequence so a restart can decide whether to
 * finish or compensate an operation.</p>
 */
public final class RegionRentJournalManager {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, RegionRentOperationRecord> records = new LinkedHashMap<>();
    private Path folder;

    public synchronized void loadAndRecover(MinecraftServer server) {
        folder = StoragePaths.regionRentTransactions(StoragePaths.root(server));
        records.clear();

        try {
            Files.createDirectories(folder);
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                try {
                    RegionRentOperationRecord record = JsonStorage.read(
                            gson,
                            file,
                            RegionRentOperationRecord.class
                    );
                    if (record != null && record.getOperationId() != null) {
                        records.put(record.getOperationId(), record);
                    }
                } catch (Exception e) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error(
                            "Could not load region-rent journal. Broken file archived as {}.",
                            archived,
                            e
                    );
                }
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Could not initialize region-rent journal folder.", e);
            return;
        }

        for (RegionRentOperationRecord record : records.values()) {
            recoverRecord(server, record);
        }
    }

    public synchronized void clear() {
        records.clear();
        folder = null;
    }

    public synchronized RegionRentOperationRecord prepare(RegionRentOperationRecord record) throws IOException {
        records.put(record.getOperationId(), record);
        write(record);
        return record;
    }

    public synchronized void persist(RegionRentOperationRecord record) {
        records.put(record.getOperationId(), record);
        try {
            write(record);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error(
                    "Could not persist region-rent journal operation {}.",
                    record.getOperationId(),
                    e
            );
        }
    }

    public synchronized int pendingCount() {
        int count = 0;
        for (RegionRentOperationRecord record : records.values()) {
            if (record.getStatus() != RegionRentOperationRecord.Status.COMPLETED
                    && record.getStatus() != RegionRentOperationRecord.Status.ROLLED_BACK
                    && record.getStatus() != RegionRentOperationRecord.Status.FAILED) {
                count++;
            }
        }
        return count;
    }

    public synchronized java.util.List<RegionRentOperationRecord> records() {
        return records.values().stream()
                .sorted(java.util.Comparator.comparingLong(RegionRentOperationRecord::getUpdatedAtEpochMilli).reversed())
                .toList();
    }

    public synchronized boolean hasPendingForRegion(String regionName) {
        if (regionName == null || regionName.isBlank()) {
            return false;
        }
        for (RegionRentOperationRecord record : records.values()) {
            if (!regionName.equalsIgnoreCase(record.getRegionName())) {
                continue;
            }
            if (record.getStatus() != RegionRentOperationRecord.Status.COMPLETED
                    && record.getStatus() != RegionRentOperationRecord.Status.ROLLED_BACK
                    && record.getStatus() != RegionRentOperationRecord.Status.FAILED) {
                return true;
            }
        }
        return false;
    }

    private void write(RegionRentOperationRecord record) throws IOException {
        if (folder == null) {
            throw new IOException("Region-rent journal is not initialized.");
        }
        JsonStorage.write(gson, folder.resolve(record.getOperationId() + ".json"), record);
    }

    private void recoverRecord(MinecraftServer server, RegionRentOperationRecord record) {
        if (record.getStatus() == RegionRentOperationRecord.Status.COMPLETED
                || record.getStatus() == RegionRentOperationRecord.Status.ROLLED_BACK
                || record.getStatus() == RegionRentOperationRecord.Status.FAILED) {
            return;
        }

        Region region = SimpleServerUtilities.REGIONS.get(record.getRegionName());
        String baseKey = record.getIdempotencyKey();

        if (record.getAction() == RegionRentOperationRecord.Action.CANCEL_REFUND) {
            recoverCancellation(record, region, baseKey);
            return;
        }

        boolean debitCommitted = record.getGrossAmountMinor() <= 0L
                || SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(baseKey + ":debit");
        boolean ownerCommitted = record.getOwnerPayoutMinor() <= 0L
                || SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(baseKey + ":owner");
        boolean regionCommitted = region != null
                && region.getRentData().getRentalSequence() >= record.getTargetRentalSequence()
                && record.getRenterId() != null
                && record.getRenterId().equals(region.getRentData().getRenter());

        if (regionCommitted) {
            record.markRegionCommitted();
            record.markCompleted(null);
            persist(record);
            return;
        }

        if (!debitCommitted) {
            record.markFailed("Prepared rental had no committed debit after restart.");
            persist(record);
            return;
        }

        compensatePayment(record, ownerCommitted, baseKey, "restart recovery before region commit");
    }

    private void recoverCancellation(
            RegionRentOperationRecord record,
            Region region,
            String baseKey
    ) {
        boolean regionCleared = region == null
                || region.getRentData().getRentalSequence() >= record.getTargetRentalSequence()
                || !Objects.equals(record.getRenterId(), region.getRentData().getRenter());

        if (!regionCleared) {
            record.markFailed("Cancellation refund was not applied because the rental still exists.");
            persist(record);
            return;
        }

        if (record.getRefundAmountMinor() <= 0L) {
            record.markCompleted(null);
            persist(record);
            return;
        }

        EconomyResult refund = SimpleServerUtilities.ECONOMY.creditTyped(
                null,
                "server",
                record.getRenterId(),
                record.getRefundAmountMinor(),
                EconomyTransactionType.REGION_REFUND,
                "region_rent",
                "Recovered refund for region " + record.getRegionName(),
                baseKey + ":refund"
        );
        if (refund.successful() || "duplicate".equals(refund.code())) {
            record.markCompleted(refund.transactionId());
        } else {
            record.markRefundPending("Refund recovery failed: " + refund.message());
        }
        persist(record);
    }

    private void compensatePayment(
            RegionRentOperationRecord record,
            boolean ownerCommitted,
            String baseKey,
            String reason
    ) {
        if (ownerCommitted && record.getOwnerPayoutMinor() > 0L && record.getOwnerRecipientId() != null) {
            EconomyResult reversal = SimpleServerUtilities.ECONOMY.debitTyped(
                    null,
                    "server",
                    record.getOwnerRecipientId(),
                    record.getOwnerPayoutMinor(),
                    EconomyTransactionType.REGION_OWNER_PAYOUT_REVERSAL,
                    "region_rent",
                    "Reverse incomplete payout for region " + record.getRegionName(),
                    baseKey + ":owner-reverse"
            );
            if (!reversal.successful() && !"duplicate".equals(reversal.code())) {
                record.markPaymentRecoveryPending("Owner payout reversal is still pending: " + reversal.message());
                persist(record);
                return;
            }
        }

        EconomyResult refund = SimpleServerUtilities.ECONOMY.creditTyped(
                null,
                "server",
                record.getRenterId(),
                record.getGrossAmountMinor(),
                EconomyTransactionType.REGION_PAYMENT_ROLLBACK,
                "region_rent",
                "Rollback incomplete rental for region " + record.getRegionName(),
                baseKey + ":rollback"
        );
        if (refund.successful() || "duplicate".equals(refund.code())) {
            record.markRolledBack(reason);
        } else {
            record.markPaymentRecoveryPending("Rental rollback refund is still pending: " + refund.message());
        }
        persist(record);
    }
}
