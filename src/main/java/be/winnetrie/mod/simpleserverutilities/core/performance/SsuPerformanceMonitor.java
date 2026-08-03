package be.winnetrie.mod.simpleserverutilities.core.performance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight in-process counters for SSU hot paths.
 *
 * <p>The monitor never touches world state and is safe to call from the
 * server thread, storage worker and command/reporting code. Module timings use
 * a bounded rolling sample window, so profiling cannot grow memory over time.</p>
 */
public final class SsuPerformanceMonitor {

    private static final int MODULE_SAMPLE_WINDOW = 256;

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

    private final Map<String, ModuleTimingAccumulator> moduleTimings = new LinkedHashMap<>();

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

    /** Records one completed tick/operation for a named SSU subsystem. */
    public synchronized void recordModuleTiming(String rawModule, long runtimeNanos) {
        String module = normalizeModule(rawModule);
        if (module.isEmpty()) return;
        moduleTimings.computeIfAbsent(module, ignored -> new ModuleTimingAccumulator())
                .record(Math.max(0L, runtimeNanos));
    }

    /** Convenience helper for event wrappers. */
    public long startTimer() {
        return System.nanoTime();
    }

    /** Completes a timer created with {@link #startTimer()}. */
    public void stopTimer(String module, long startedAtNanos) {
        recordModuleTiming(module, Math.max(0L, System.nanoTime() - startedAtNanos));
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

    public synchronized List<ModuleTiming> moduleTimingSnapshot() {
        List<ModuleTiming> result = new ArrayList<>(moduleTimings.size());
        for (Map.Entry<String, ModuleTimingAccumulator> entry : moduleTimings.entrySet()) {
            result.add(entry.getValue().snapshot(entry.getKey()));
        }
        result.sort(Comparator
                .comparingDouble(ModuleTiming::p95Millis).reversed()
                .thenComparing(ModuleTiming::module));
        return List.copyOf(result);
    }

    public synchronized void reset() {
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
        moduleTimings.clear();
    }

    private static String normalizeModule(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '_').replace('-', '_');
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
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

    public record ModuleTiming(
            String module,
            long totalSamples,
            int rollingSamples,
            double rollingAverageMillis,
            double p95Millis,
            double maximumMillis,
            double lastMillis
    ) {
    }

    private static final class ModuleTimingAccumulator {
        private final Deque<Long> samples = new ArrayDeque<>(MODULE_SAMPLE_WINDOW);
        private long totalSamples;
        private long maximumNanos;
        private long lastNanos;

        void record(long nanos) {
            totalSamples++;
            maximumNanos = Math.max(maximumNanos, nanos);
            lastNanos = nanos;
            samples.addLast(nanos);
            while (samples.size() > MODULE_SAMPLE_WINDOW) samples.removeFirst();
        }

        ModuleTiming snapshot(String module) {
            long[] values = new long[samples.size()];
            long sum = 0L;
            int index = 0;
            for (long value : samples) {
                values[index++] = value;
                sum += value;
            }
            Arrays.sort(values);
            long p95 = values.length == 0 ? 0L
                    : values[Math.min(values.length - 1, (int) Math.ceil(values.length * 0.95D) - 1)];
            return new ModuleTiming(
                    module,
                    totalSamples,
                    values.length,
                    values.length == 0 ? 0.0D : sum / 1_000_000.0D / values.length,
                    p95 / 1_000_000.0D,
                    maximumNanos / 1_000_000.0D,
                    lastNanos / 1_000_000.0D
            );
        }
    }
}
