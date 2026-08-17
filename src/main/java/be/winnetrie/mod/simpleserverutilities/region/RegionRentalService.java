package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RegionRentalService {

    private static final long MAX_AUTO_RESET_VOLUME = 1_000_000L;
    private static final Set<String> PENDING_RENTAL_RESETS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Object> REGION_LOCKS = new ConcurrentHashMap<>();

    private RegionRentalService() {
    }

    public static RentalResult rent(ServerPlayer player, Region region) {
        Object lock = lockFor(region);
        synchronized (lock) {
            RentalResult validation = validateNewRental(player, region);
            if (validation != null) {
                return validation;
            }

            long priceMinor = region.getRentData().getPriceMinor(SimpleServerUtilities.ECONOMY.settings());
            long now = System.currentTimeMillis();
            long targetEnd = region.getRentData().isPermanent()
                    ? -1L
                    : safeAdd(now, region.getRentData().getPeriodMillis());

            return applyPaidRentalChange(
                    player,
                    region,
                    RegionRentOperationRecord.Action.RENT,
                    EconomyTransactionType.REGION_RENT,
                    priceMinor,
                    targetEnd,
                    false
            );
        }
    }

    public static RentalResult extend(ServerPlayer player, Region region) {
        Object lock = lockFor(region);
        synchronized (lock) {
            RegionRentData rentData = region.getRentData();

            if (!rentData.isRented()) {
                return RentalResult.fail("This region is not currently rented.");
            }
            if (!player.getUUID().equals(rentData.getRenter())) {
                return RentalResult.fail("You are not the renter of this region.");
            }
            if (rentData.isPermanent()) {
                return RentalResult.fail("This region is rented permanently and cannot be extended.");
            }
            if (!SsuModuleAccess.active("economy") || !SimpleServerUtilities.ECONOMY.isEnabled()) {
                return RentalResult.fail("The economy module is disabled, so paid rent cannot be extended.");
            }
            if (SimpleServerUtilities.JOBS.isResourceLocked(
                    SsuJobLocks.region(region.getDimension(), region.getName())
            )) {
                return RentalResult.fail("This region is currently being reset or edited.");
            }

            long priceMinor = rentData.getPriceMinor(SimpleServerUtilities.ECONOMY.settings());
            long now = System.currentTimeMillis();
            long currentRemaining = rentData.isRentPaused()
                    ? Math.max(0L, rentData.getPausedRemainingMillis())
                    : Math.max(0L, rentData.getRentEndTime() - now);
            long targetEnd = safeAdd(now, safeAdd(currentRemaining, rentData.getPeriodMillis()));

            return applyPaidRentalChange(
                    player,
                    region,
                    RegionRentOperationRecord.Action.RENEW,
                    EconomyTransactionType.REGION_RENEW,
                    priceMinor,
                    targetEnd,
                    true
            );
        }
    }

    private static RentalResult validateNewRental(ServerPlayer player, Region region) {
        if (!SimpleServerUtilities.REGIONS.isRentingEnabled()) {
            return RentalResult.fail("Region renting is currently disabled.");
        }
        if (!SsuModuleAccess.active("economy") || !SimpleServerUtilities.ECONOMY.isEnabled()) {
            return RentalResult.fail("The economy module is disabled, so paid regions cannot be rented.");
        }
        if (!region.getRentData().isRentable()) {
            return RentalResult.fail("This region is not rentable.");
        }
        if (region.getRentData().isRented()) {
            return RentalResult.fail("This region is already rented.");
        }
        if (SimpleServerUtilities.JOBS.isResourceLocked(
                SsuJobLocks.region(region.getDimension(), region.getName())
        )) {
            return RentalResult.fail("This region is currently being reset or edited. Try again when the active job is complete.");
        }
        long price = region.getRentData().getPriceMinor(SimpleServerUtilities.ECONOMY.settings());
        if (price < 0L || price == Long.MAX_VALUE) {
            return RentalResult.fail("The configured rent price is invalid.");
        }
        if (SimpleServerUtilities.ECONOMY.balance(player.getUUID()) < price) {
            return RentalResult.fail("You need " + format(price) + " to rent this region.");
        }
        return null;
    }

    private static RentalResult applyPaidRentalChange(
            ServerPlayer player,
            Region region,
            RegionRentOperationRecord.Action action,
            EconomyTransactionType paymentType,
            long grossAmountMinor,
            long targetEndTime,
            boolean renewal
    ) {
        RegionRentData rentData = region.getRentData();
        RentState before = RentState.capture(region, player.getUUID());
        long targetSequence = rentData.getRentalSequence() + 1L;
        UUID operationId = UUID.randomUUID();
        String baseKey = "region-rent:" + region.getDimension().identifier() + ":"
                + region.getName().toLowerCase(java.util.Locale.ROOT) + ":"
                + player.getUUID() + ":" + targetSequence;


        RegionRentOperationRecord journal = RegionRentOperationRecord.prepared(
                operationId,
                baseKey,
                action,
                region,
                player.getUUID(),
                player.getName().getString(),
                null,
                grossAmountMinor,
                0L,
                0L,
                targetSequence
        );

        try {
            SimpleServerUtilities.REGION_RENT_JOURNAL.prepare(journal);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Could not prepare rent journal for region '{}'.", region.getName(), e);
            return RentalResult.fail("The rental journal could not be written. No money was taken.");
        }

        EconomyResult debit = successfulNoop();
        if (grossAmountMinor > 0L) {
            debit = SimpleServerUtilities.ECONOMY.debitTyped(
                    player.getUUID(),
                    player.getName().getString(),
                    player.getUUID(),
                    grossAmountMinor,
                    paymentType,
                    "region_rent",
                    (renewal ? "Renew region " : "Rent region ") + region.getName(),
                    baseKey + ":debit"
            );
            if (!debit.successful()) {
                journal.markFailed("Debit failed: " + debit.message());
                SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);
                return RentalResult.fail(debit.message());
            }
        }

        // Region rent is a deliberate money sink: the renter is debited and no player/system account is credited.
        journal.markPaymentCommitted(debit.transactionId(), null);
        SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);

        long now = System.currentTimeMillis();
        if (!renewal) {
            rentData.setRenter(player.getUUID());
            rentData.setRenterName(player.getName().getString());
            rentData.setRentPaused(false);
            rentData.setPausedRemainingMillis(-1L);
            region.addMember(player.getUUID());
        }

        rentData.recordPayment(now, grossAmountMinor, targetEndTime, debit.transactionId());
        if (rentData.isPermanent()) {
            rentData.setRentEndTime(-1L);
        } else if (rentData.isRentPaused()) {
            rentData.setPausedRemainingMillis(Math.max(0L, targetEndTime - now));
            rentData.setRentEndTime(-1L);
        } else {
            rentData.setRentEndTime(targetEndTime);
        }

        SimpleServerUtilities.REGIONS.save();
        if (!SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(10))) {
            before.restore(region, player.getUUID());
            SimpleServerUtilities.REGIONS.save();

            if (!SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(10))) {
                journal.markPaymentRecoveryPending(
                        "Region storage failed and the restored pre-rental state could not be confirmed."
                );
                SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);
                return RentalResult.fail(
                        "The rental could not be confirmed. No automatic second charge will occur; "
                                + "the transaction will be reconciled safely when storage recovers or the server restarts."
                );
            }

            rollbackPaidRental(
                    journal,
                    player.getUUID(),
                    grossAmountMinor,
                    baseKey,
                    "Region storage did not flush, but the original region state was restored."
            );
            return RentalResult.fail("The region could not be saved. Your payment was returned.");
        }

        journal.markRegionCommitted();
        journal.markCompleted(null);
        SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);

        String actionText = renewal ? "Extended" : "Rented";
        String periodText = rentData.isPermanent()
                ? "permanently"
                : "for " + rentData.getPeriodDays() + " day(s)";
        return RentalResult.success(
                actionText + " region '" + region.getName() + "' " + periodText
                        + " for " + format(grossAmountMinor) + "."
        );
    }

    private static void rollbackPaidRental(
            RegionRentOperationRecord journal,
            UUID renter,
            long gross,
            String baseKey,
            String reason
    ) {
        if (gross > 0L) {
            EconomyResult refund = SimpleServerUtilities.ECONOMY.creditTyped(
                    null,
                    "server",
                    renter,
                    gross,
                    EconomyTransactionType.REGION_PAYMENT_ROLLBACK,
                    "region_rent",
                    "Rollback failed region rental",
                    baseKey + ":rollback"
            );
            if (!refund.successful() && !"duplicate".equals(refund.code())) {
                journal.markPaymentRecoveryPending(
                        reason + " Payment rollback refund is still pending: " + refund.message()
                );
                SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);
                return;
            }
        }

        journal.markRolledBack(reason);
        SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);
    }

    public static RentalResult adminAddTime(Region region, int days) {
        RegionRentData rentData = region.getRentData();
        if (!rentData.isRented()) {
            return RentalResult.fail("This region is not currently rented.");
        }
        if (rentData.isPermanent()) {
            return RentalResult.fail("This region is rented permanently.");
        }

        long addedMillis = days * 24L * 60L * 60L * 1000L;
        if (rentData.isRentPaused()) {
            rentData.setPausedRemainingMillis(Math.max(0L, rentData.getPausedRemainingMillis()) + addedMillis);
        } else {
            long baseTime = Math.max(System.currentTimeMillis(), rentData.getRentEndTime());
            rentData.setRentEndTime(baseTime + addedMillis);
        }
        SimpleServerUtilities.REGIONS.save();
        return RentalResult.success("Added " + days + " day(s) to region '" + region.getName() + "'.");
    }

    public static RentalResult setPaused(Region region, boolean paused) {
        RegionRentData rentData = region.getRentData();
        if (!rentData.isRented()) {
            return RentalResult.fail("This region is not currently rented.");
        }
        if (rentData.isPermanent()) {
            return RentalResult.fail("Permanent rentals cannot be paused.");
        }

        boolean changed = paused ? rentData.pause(System.currentTimeMillis()) : rentData.resume(System.currentTimeMillis());
        if (!changed) {
            return RentalResult.fail("Region rent timer is already " + (paused ? "paused" : "running") + ".");
        }
        SimpleServerUtilities.REGIONS.save();
        return RentalResult.success("Rent timer for region '" + region.getName() + "' is now "
                + (paused ? "paused" : "running") + ".");
    }

    public static RentalResult unrent(
            ServerPlayer actor,
            MinecraftServer server,
            Region region,
            boolean resetAllowed
    ) {
        if (!SsuModuleAccess.active("economy") || !SimpleServerUtilities.ECONOMY.isEnabled()) {
            return RentalResult.fail("The economy module is disabled, so rental cancellation/refunds are paused.");
        }
        RegionRentData rentData = region.getRentData();
        if (!rentData.isRented()) {
            return RentalResult.fail("This region is not currently rented.");
        }

        UUID renter = rentData.getRenter();
        boolean selfCancellation = actor != null && actor.getUUID().equals(renter);
        int refundPermille = selfCancellation
                ? SimpleServerUtilities.REGIONS.rentEconomySettings().getPlayerCancelRefundPermille()
                : SimpleServerUtilities.REGIONS.rentEconomySettings().getAdminCancelRefundPermille();
        UUID actorId = actor == null ? null : actor.getUUID();
        String actorName = actor == null ? "server" : actor.getName().getString();
        long frozenRefundMinor = renter == null
                ? 0L
                : rentData.calculateRefundMinor(System.currentTimeMillis(), refundPermille);

        if (resetAllowed && rentData.isResetOnUnrent()) {
            ResetScheduleResult reset = scheduleRegionReset(server, region, "unrent", () -> {
                RentalResult removal = finalizeRentalRemoval(
                        server, region, renter, false, actorId, actorName, frozenRefundMinor
                );
                ServerPlayer onlineRenter = renter == null ? null : server.getPlayerList().getPlayer(renter);
                if (onlineRenter != null) {
                    onlineRenter.sendSystemMessage(Component.literal(removal.message()));
                }
            });
            if (reset.status() == ResetStatus.SCHEDULED || reset.status() == ResetStatus.PENDING) {
                return RentalResult.success(reset.message());
            }
            if (reset.status() == ResetStatus.FAILED) {
                return RentalResult.fail(reset.message());
            }
        }

        return finalizeRentalRemoval(server, region, renter, false, actorId, actorName, frozenRefundMinor);
    }

    public static void expireRental(MinecraftServer server, Region region) {
        RegionRentData rentData = region.getRentData();
        UUID renter = rentData.getRenter();
        String previousRenter = rentData.getDisplayRenterName();

        if (rentData.isResetOnExpire()) {
            ResetScheduleResult reset = scheduleRegionReset(server, region, "rent expiry", () -> {
                finalizeRentalRemoval(server, region, renter, true, null, "server", 0);
                SimpleServerUtilities.LOGGER.info(
                        "Rental expired for region '{}'. Previous renter: {}. Snapshot reset completed.",
                        region.getName(), previousRenter
                );
            });
            if (reset.status() == ResetStatus.SCHEDULED || reset.status() == ResetStatus.PENDING) {
                return;
            }
            if (reset.status() == ResetStatus.FAILED) {
                SimpleServerUtilities.LOGGER.error(
                        "Region '{}' could not be reset after rent expiry: {}. Rental access will still be removed.",
                        region.getName(), reset.message()
                );
            }
        }

        finalizeRentalRemoval(server, region, renter, true, null, "server", 0);
        SimpleServerUtilities.LOGGER.info(
                "Rental expired for region '{}'. Previous renter: {}.",
                region.getName(), previousRenter
        );
    }

    private static RentalResult finalizeRentalRemoval(
            MinecraftServer server,
            Region region,
            UUID renter,
            boolean expired,
            UUID actorId,
            String actorName,
            long frozenRefundMinor
    ) {
        Object lock = lockFor(region);
        synchronized (lock) {
            RegionRentData rentData = region.getRentData();
            long refundMinor = renter == null ? 0L : Math.max(0L, frozenRefundMinor);
            long targetSequence = rentData.getRentalSequence() + 1L;
            String baseKey = "region-unrent:" + region.getDimension().identifier() + ":"
                    + region.getName().toLowerCase(java.util.Locale.ROOT) + ":" + targetSequence;
            RegionRentOperationRecord journal = RegionRentOperationRecord.prepared(
                    UUID.randomUUID(),
                    baseKey,
                    RegionRentOperationRecord.Action.CANCEL_REFUND,
                    region,
                    renter,
                    rentData.getRenterName(),
                    null,
                    0L,
                    0L,
                    refundMinor,
                    targetSequence
            );

            try {
                SimpleServerUtilities.REGION_RENT_JOURNAL.prepare(journal);
            } catch (IOException e) {
                SimpleServerUtilities.LOGGER.error("Could not prepare unrent journal for '{}'.", region.getName(), e);
                return RentalResult.fail("The cancellation journal could not be written.");
            }

            RentState before = RentState.capture(region, renter);
            if (renter != null) {
                region.removeMember(renter);
            }
            rentData.clearRental();
            rentData.setRentalSequence(targetSequence);
            SimpleServerUtilities.REGIONS.save();

            if (!SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(10))) {
                before.restore(region, renter);
                SimpleServerUtilities.REGIONS.save();

                if (!SimpleServerUtilities.STORAGE.flush(Duration.ofSeconds(10))) {
                    journal.markCancellationRecoveryPending(
                            "Region cancellation storage failed and restoration could not be confirmed."
                    );
                    SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);
                    return RentalResult.fail(
                            "The cancellation state could not be confirmed. It will be reconciled safely "
                                    + "when storage recovers or the server restarts."
                    );
                }

                journal.markFailed("Region cancellation was not stored; the active rental was restored.");
                SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);
                return RentalResult.fail("The rental could not be cancelled because region storage failed.");
            }

            journal.markRegionCommitted();
            SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);

            EconomyResult refund = successfulNoop();
            if (refundMinor > 0L && renter != null) {
                refund = SimpleServerUtilities.ECONOMY.creditTyped(
                        actorId,
                        actorName,
                        renter,
                        refundMinor,
                        EconomyTransactionType.REGION_REFUND,
                        "region_rent",
                        "Refund for region " + region.getName(),
                        baseKey + ":refund"
                );
                if (!refund.successful() && !"duplicate".equals(refund.code())) {
                    journal.markRefundPending("Region was cancelled, but refund failed: " + refund.message());
                    SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);
                    return RentalResult.fail(
                            "Region was cancelled, but the refund is pending recovery: " + refund.message()
                    );
                }
            }

            journal.markCompleted(refund.transactionId());
            SimpleServerUtilities.REGION_RENT_JOURNAL.persist(journal);

            if (expired && renter != null) {
                ServerPlayer onlineRenter = server.getPlayerList().getPlayer(renter);
                if (onlineRenter != null) {
                    onlineRenter.sendSystemMessage(Component.literal(
                            "Your rent for region '" + region.getName() + "' has expired."
                    ));
                }
            }

            String message = "Region '" + region.getName() + "' is no longer rented.";
            if (refundMinor > 0L) {
                message += " Refund: " + format(refundMinor) + ".";
            }
            return RentalResult.success(message);
        }
    }

    /** Compatibility helper for callers that only need to schedule a reset. */
    public static RentalResult resetRegionIfPossible(MinecraftServer server, Region region, String reason) {
        ResetScheduleResult result = scheduleRegionReset(server, region, reason, () -> {
        });
        return result.status() == ResetStatus.FAILED
                ? RentalResult.fail(result.message())
                : RentalResult.success(result.message());
    }

    private static ResetScheduleResult scheduleRegionReset(
            MinecraftServer server,
            Region region,
            String reason,
            Runnable onCompleted
    ) {
        String lock = SsuJobLocks.region(region.getDimension(), region.getName());
        if (SimpleServerUtilities.JOBS.isResourceLocked(lock)) {
            if (PENDING_RENTAL_RESETS.contains(lock)) {
                return new ResetScheduleResult(
                        ResetStatus.PENDING,
                        "Region '" + region.getName() + "' already has a pending rental reset."
                );
            }
            return new ResetScheduleResult(
                    ResetStatus.FAILED,
                    "Region '" + region.getName() + "' is currently being edited by another job."
            );
        }

        if (!SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
            return new ResetScheduleResult(
                    ResetStatus.NOT_REQUIRED,
                    "No saved snapshot exists for region '" + region.getName() + "'. Region was not reset."
            );
        }

        long volume = region.getVolume();
        if (volume > MAX_AUTO_RESET_VOLUME) {
            return new ResetScheduleResult(
                    ResetStatus.FAILED,
                    "Region is too large to reset safely: " + volume + " blocks."
            );
        }

        ServerLevel level = server.getLevel(region.getDimension());
        if (level == null) {
            return new ResetScheduleResult(ResetStatus.FAILED, "Region dimension is not loaded.");
        }

        try {
            RegionSnapshotManager.RegionSnapshotResetJob job =
                    SimpleServerUtilities.REGION_SNAPSHOTS.createResetJob(level, region);
            UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> {
                PENDING_RENTAL_RESETS.remove(lock);
                if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    try {
                        onCompleted.run();
                    } catch (Exception e) {
                        SimpleServerUtilities.LOGGER.error(
                                "Post-reset action failed for region '{}' after {}.",
                                region.getName(), reason, e
                        );
                    }
                } else {
                    SimpleServerUtilities.LOGGER.error(
                            "Region '{}' reset job ended with status {} after {}: {}",
                            region.getName(), result.status(), reason, result.error()
                    );
                }
            });
            PENDING_RENTAL_RESETS.add(lock);
            return new ResetScheduleResult(
                    ResetStatus.SCHEDULED,
                    "Region '" + region.getName() + "' reset scheduled as job " + jobId
                            + ". Rental removal completes after the reset."
            );
        } catch (IOException | IllegalStateException e) {
            SimpleServerUtilities.LOGGER.error(
                    "Failed to schedule reset for region '{}' after {}.",
                    region.getName(), reason, e
            );
            return new ResetScheduleResult(
                    ResetStatus.FAILED,
                    "Failed to reset region snapshot: " + e.getMessage()
            );
        }
    }


    private static Object lockFor(Region region) {
        String key = region.getDimension().identifier() + ":" + region.getName().toLowerCase(java.util.Locale.ROOT);
        return REGION_LOCKS.computeIfAbsent(key, ignored -> new Object());
    }

    private static String format(long amountMinor) {
        return MoneyFormat.format(amountMinor, SimpleServerUtilities.ECONOMY.settings());
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }


    private static EconomyResult successfulNoop() {
        return EconomyResult.success(null, "No economy mutation required.", 0L, 0L);
    }

    private record ResetScheduleResult(ResetStatus status, String message) {
    }

    private enum ResetStatus {
        NOT_REQUIRED,
        SCHEDULED,
        PENDING,
        FAILED
    }

    private record RentState(
            UUID renter,
            String renterName,
            long rentEndTime,
            boolean rentPaused,
            long pausedRemainingMillis,
            long rentalSequence,
            long currentTermPaidMinor,
            long totalPaidMinor,
            long refundableAmountMinor,
            long refundableWindowStartTime,
            long refundableWindowEndTime,
            UUID lastPaymentTransactionId,
            boolean wasMember
    ) {
        static RentState capture(Region region, UUID playerId) {
            RegionRentData data = region.getRentData();
            return new RentState(
                    data.getRenter(),
                    data.getRenterName(),
                    data.getRentEndTime(),
                    data.isRentPaused(),
                    data.getPausedRemainingMillis(),
                    data.getRentalSequence(),
                    data.getCurrentTermPaidMinor(),
                    data.getTotalPaidMinor(),
                    data.getRefundableAmountMinor(),
                    data.getRefundableWindowStartTime(),
                    data.getRefundableWindowEndTime(),
                    data.getLastPaymentTransactionId(),
                    playerId != null && region.getMembers().contains(playerId)
            );
        }

        void restore(Region region, UUID playerId) {
            RegionRentData data = region.getRentData();
            data.setRenter(renter);
            data.setRenterName(renterName);
            data.setRentEndTime(rentEndTime);
            data.setRentPaused(rentPaused);
            data.setPausedRemainingMillis(pausedRemainingMillis);
            data.setRentalSequence(rentalSequence);
            data.setCurrentTermPaidMinor(currentTermPaidMinor);
            data.setTotalPaidMinor(totalPaidMinor);
            data.setRefundableAmountMinor(refundableAmountMinor);
            data.setRefundableWindowStartTime(refundableWindowStartTime);
            data.setRefundableWindowEndTime(refundableWindowEndTime);
            data.setLastPaymentTransactionId(lastPaymentTransactionId);
            if (playerId != null) {
                if (wasMember) {
                    region.addMember(playerId);
                } else {
                    region.removeMember(playerId);
                }
            }
        }
    }

    public record RentalResult(boolean success, String message) {
        public static RentalResult success(String message) {
            return new RentalResult(true, message);
        }

        public static RentalResult fail(String message) {
            return new RentalResult(false, message);
        }
    }
}
