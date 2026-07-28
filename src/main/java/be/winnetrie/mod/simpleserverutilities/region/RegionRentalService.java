package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class RegionRentalService {

    private static final long MAX_AUTO_RESET_VOLUME = 1_000_000L;
    private static final Set<String> PENDING_RENTAL_RESETS = ConcurrentHashMap.newKeySet();

    private RegionRentalService() {
    }

    public static RentalResult rent(ServerPlayer player, Region region) {
        if (!SimpleServerUtilities.REGIONS.isRentingEnabled()) {
            return RentalResult.fail("Region renting is currently disabled.");
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

        RegionRentData rentData = region.getRentData();
        rentData.setRenter(player.getUUID());
        rentData.setRenterName(player.getName().getString());
        rentData.setRentPaused(false);
        rentData.setPausedRemainingMillis(-1L);

        if (rentData.isPermanent()) {
            rentData.setRentEndTime(-1L);
        } else {
            rentData.setRentEndTime(System.currentTimeMillis() + rentData.getPeriodMillis());
        }

        region.addMember(player.getUUID());
        SimpleServerUtilities.REGIONS.save();

        return RentalResult.success("You rented region '" + region.getName() + "'.");
    }

    public static RentalResult extend(ServerPlayer player, Region region) {
        RegionRentData rentData = region.getRentData();

        if (!player.getUUID().equals(rentData.getRenter())) {
            return RentalResult.fail("You are not the renter of this region.");
        }

        if (!rentData.isRented()) {
            return RentalResult.fail("This region is not currently rented.");
        }

        if (rentData.isPermanent()) {
            return RentalResult.fail("This region is rented permanently and cannot be extended.");
        }

        if (rentData.isRentPaused()) {
            rentData.setPausedRemainingMillis(rentData.getPausedRemainingMillis() + rentData.getPeriodMillis());
        } else {
            long baseTime = Math.max(System.currentTimeMillis(), rentData.getRentEndTime());
            rentData.setRentEndTime(baseTime + rentData.getPeriodMillis());
        }

        SimpleServerUtilities.REGIONS.save();
        return RentalResult.success("Extended region '" + region.getName() + "' for " + rentData.getPeriodDays()
                + " day(s). Current price for this extension: " + rentData.getAmount() + ".");
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

        boolean changed = paused
                ? rentData.pause(System.currentTimeMillis())
                : rentData.resume(System.currentTimeMillis());

        if (!changed) {
            return RentalResult.fail("Region rent timer is already " + (paused ? "paused" : "running") + ".");
        }

        SimpleServerUtilities.REGIONS.save();
        return RentalResult.success("Rent timer for region '" + region.getName() + "' is now "
                + (paused ? "paused" : "running") + ".");
    }

    public static RentalResult unrent(MinecraftServer server, Region region, boolean resetAllowed) {
        RegionRentData rentData = region.getRentData();

        if (!rentData.isRented()) {
            return RentalResult.fail("This region is not currently rented.");
        }

        UUID renter = rentData.getRenter();

        if (resetAllowed && rentData.isResetOnUnrent()) {
            ResetScheduleResult reset = scheduleRegionReset(server, region, "unrent", () -> {
                finalizeRentalRemoval(server, region, renter, false);
                ServerPlayer onlineRenter = renter == null ? null : server.getPlayerList().getPlayer(renter);
                if (onlineRenter != null) {
                    onlineRenter.sendSystemMessage(Component.literal(
                            "Region '" + region.getName() + "' was reset and is no longer rented."
                    ));
                }
            });

            if (reset.status() == ResetStatus.SCHEDULED || reset.status() == ResetStatus.PENDING) {
                return RentalResult.success(reset.message());
            }
            if (reset.status() == ResetStatus.FAILED) {
                return RentalResult.fail(reset.message());
            }
        }

        finalizeRentalRemoval(server, region, renter, false);
        return RentalResult.success("Region '" + region.getName() + "' is no longer rented.");
    }

    public static void expireRental(MinecraftServer server, Region region) {
        RegionRentData rentData = region.getRentData();
        UUID renter = rentData.getRenter();
        String previousRenter = rentData.getDisplayRenterName();

        if (rentData.isResetOnExpire()) {
            ResetScheduleResult reset = scheduleRegionReset(server, region, "rent expiry", () -> {
                finalizeRentalRemoval(server, region, renter, true);
                SimpleServerUtilities.LOGGER.info(
                        "Rental expired for region '{}'. Previous renter: {}. Snapshot reset completed.",
                        region.getName(),
                        previousRenter
                );
            });

            if (reset.status() == ResetStatus.SCHEDULED || reset.status() == ResetStatus.PENDING) {
                return;
            }
            if (reset.status() == ResetStatus.FAILED) {
                SimpleServerUtilities.LOGGER.error(
                        "Region '{}' could not be reset after rent expiry: {}. Rental access will still be removed.",
                        region.getName(),
                        reset.message()
                );
            }
        }

        finalizeRentalRemoval(server, region, renter, true);
        SimpleServerUtilities.LOGGER.info(
                "Rental expired for region '{}'. Previous renter: {}.",
                region.getName(),
                previousRenter
        );
    }

    /**
     * Compatibility helper for callers that only need to schedule a reset.
     */
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
            SimpleServerUtilities.LOGGER.info(
                    "Region '{}' was not reset after {} because no snapshot exists.",
                    region.getName(),
                    reason
            );
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
            java.util.UUID jobId = SimpleServerUtilities.JOBS.submit(job, result -> {
                PENDING_RENTAL_RESETS.remove(lock);
                if (result.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    SimpleServerUtilities.LOGGER.info(
                            "Region '{}' reset after {}. Restored {} block(s).",
                            region.getName(),
                            reason,
                            job.restoredBlocks()
                    );
                    try {
                        onCompleted.run();
                    } catch (Exception e) {
                        SimpleServerUtilities.LOGGER.error(
                                "Post-reset action failed for region '{}' after {}.",
                                region.getName(),
                                reason,
                                e
                        );
                    }
                } else {
                    SimpleServerUtilities.LOGGER.error(
                            "Region '{}' reset job ended with status {} after {}: {}",
                            region.getName(),
                            result.status(),
                            reason,
                            result.error()
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
                    region.getName(),
                    reason,
                    e
            );
            return new ResetScheduleResult(
                    ResetStatus.FAILED,
                    "Failed to reset region snapshot: " + e.getMessage()
            );
        }
    }

    private static void finalizeRentalRemoval(
            MinecraftServer server,
            Region region,
            UUID renter,
            boolean expired
    ) {
        if (renter != null) {
            region.removeMember(renter);
            if (expired) {
                ServerPlayer onlineRenter = server.getPlayerList().getPlayer(renter);
                if (onlineRenter != null) {
                    onlineRenter.sendSystemMessage(Component.literal(
                            "Your rent for region '" + region.getName() + "' has expired."
                    ));
                }
            }
        }
        region.getRentData().clearRental();
        SimpleServerUtilities.REGIONS.save();
    }

    private record ResetScheduleResult(ResetStatus status, String message) {
    }

    private enum ResetStatus {
        NOT_REQUIRED,
        SCHEDULED,
        PENDING,
        FAILED
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
