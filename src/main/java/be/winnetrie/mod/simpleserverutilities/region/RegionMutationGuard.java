package be.winnetrie.mod.simpleserverutilities.region;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;

/**
 * Central safety gate for administrative operations that can invalidate a
 * region snapshot, rental or long-running world edit.
 */
public final class RegionMutationGuard {

    private RegionMutationGuard() {
    }

    public static Check delete(Region region) {
        return check(region, true, true, true);
    }

    public static Check redefine(Region region) {
        return check(region, true, true, true);
    }

    public static Check saveSnapshot(Region region) {
        return check(region, true, true, true);
    }

    public static Check clearRegion(Region region) {
        return check(region, true, true, true);
    }

    /** A fresh verified reset is the recovery path for an interrupted reset. */
    public static Check resetFromSnapshot(Region region) {
        return check(region, true, true, false);
    }

    private static Check check(
            Region region,
            boolean rejectRental,
            boolean rejectPendingRentOperation,
            boolean rejectUnresolvedReset
    ) {
        if (region == null) {
            return Check.deny("Region no longer exists.");
        }

        String lock = SsuJobLocks.region(region.getDimension(), region.getName());
        if (SimpleServerUtilities.JOBS.isResourceLocked(lock)) {
            return Check.deny(
                    "Region '" + region.getName() + "' is currently locked by a snapshot or world-edit job."
            );
        }

        if (rejectPendingRentOperation
                && SimpleServerUtilities.REGION_RENT_JOURNAL.hasPendingForRegion(region.getName())) {
            return Check.deny(
                    "Region '" + region.getName() + "' has an unfinished rental transaction. "
                            + "Let recovery finish before changing it."
            );
        }

        if (rejectRental && region.getRentData().isRented()) {
            return Check.deny(
                    "Region '" + region.getName() + "' is rented. Unrent it first so payments, refunds "
                            + "and the configured reset can finish safely."
            );
        }

        if (rejectUnresolvedReset
                && SimpleServerUtilities.REGION_SNAPSHOTS.hasUnresolvedReset(region.getName())) {
            return Check.deny(
                    "Region '" + region.getName() + "' has an interrupted or failed snapshot reset. "
                            + "Run /regions reset " + region.getName() + " successfully before changing it."
            );
        }

        return Check.permit();
    }

    public record Check(boolean allowed, String message) {
        private static Check permit() {
            return new Check(true, "");
        }

        private static Check deny(String message) {
            return new Check(false, message == null ? "Region operation is not safe right now." : message);
        }
    }
}
