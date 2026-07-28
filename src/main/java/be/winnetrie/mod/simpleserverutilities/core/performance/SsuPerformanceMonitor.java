package be.winnetrie.mod.simpleserverutilities.core.performance;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight in-process counters for SSU hot paths.
 *
 * <p>The monitor never touches world state and is safe to call from the
 * server thread, storage worker and command/reporting code.</p>
 */
public final class SsuPerformanceMonitor {

    private final LongAdder regionLookups = new LongAdder();
    private final LongAdder regionCandidates = new LongAdder();
    private final LongAdder regionIndexFallbacks = new LongAdder();

    private final LongAdder permissionChecks = new LongAdder();
    private final LongAdder permissionCacheHits = new LongAdder();
    private final LongAdder permissionCacheMisses = new LongAdder();
    private final LongAdder permissionCacheInvalidations = new LongAdder();

    private final LongAdder jobsCompleted = new LongAdder();
    private final LongAdder jobsCancelled = new LongAdder();
    private final LongAdder jobsFailed = new LongAdder();
    private final LongAdder jobOperations = new LongAdder();
    private final LongAdder jobRuntimeNanos = new LongAdder();

    public void recordRegionLookup(int candidateCount, boolean fallback) {
        regionLookups.increment();
        regionCandidates.add(Math.max(0, candidateCount));
        if (fallback) {
            regionIndexFallbacks.increment();
        }
    }

    public void recordPermissionCheck(boolean cacheHit) {
        permissionChecks.increment();
        if (cacheHit) {
            permissionCacheHits.increment();
        } else {
            permissionCacheMisses.increment();
        }
    }

    public void recordPermissionCacheInvalidation() {
        permissionCacheInvalidations.increment();
    }

    public void recordJobCompleted(long operations, long runtimeNanos) {
        jobsCompleted.increment();
        recordJobTotals(operations, runtimeNanos);
    }

    public void recordJobCancelled(long operations, long runtimeNanos) {
        jobsCancelled.increment();
        recordJobTotals(operations, runtimeNanos);
    }

    public void recordJobFailed(long operations, long runtimeNanos) {
        jobsFailed.increment();
        recordJobTotals(operations, runtimeNanos);
    }

    private void recordJobTotals(long operations, long runtimeNanos) {
        jobOperations.add(Math.max(0L, operations));
        jobRuntimeNanos.add(Math.max(0L, runtimeNanos));
    }

    public Snapshot snapshot() {
        long lookups = regionLookups.sum();
        long candidates = regionCandidates.sum();
        long checks = permissionChecks.sum();
        long hits = permissionCacheHits.sum();
        long misses = permissionCacheMisses.sum();
        long completed = jobsCompleted.sum();
        long cancelled = jobsCancelled.sum();
        long failed = jobsFailed.sum();
        long jobCount = completed + cancelled + failed;
        long runtime = jobRuntimeNanos.sum();

        return new Snapshot(
                lookups,
                candidates,
                lookups == 0L ? 0.0D : candidates / (double) lookups,
                regionIndexFallbacks.sum(),
                checks,
                hits,
                misses,
                checks == 0L ? 0.0D : hits / (double) checks,
                permissionCacheInvalidations.sum(),
                completed,
                cancelled,
                failed,
                jobOperations.sum(),
                runtime,
                jobCount == 0L ? 0.0D : runtime / 1_000_000.0D / jobCount
        );
    }

    public void reset() {
        regionLookups.reset();
        regionCandidates.reset();
        regionIndexFallbacks.reset();
        permissionChecks.reset();
        permissionCacheHits.reset();
        permissionCacheMisses.reset();
        permissionCacheInvalidations.reset();
        jobsCompleted.reset();
        jobsCancelled.reset();
        jobsFailed.reset();
        jobOperations.reset();
        jobRuntimeNanos.reset();
    }

    public record Snapshot(
            long regionLookups,
            long regionCandidates,
            double averageRegionCandidates,
            long regionIndexFallbacks,
            long permissionChecks,
            long permissionCacheHits,
            long permissionCacheMisses,
            double permissionCacheHitRate,
            long permissionCacheInvalidations,
            long jobsCompleted,
            long jobsCancelled,
            long jobsFailed,
            long jobOperations,
            long jobRuntimeNanos,
            double averageJobRuntimeMillis
    ) {
    }
}
