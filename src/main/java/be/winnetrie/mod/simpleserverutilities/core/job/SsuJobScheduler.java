package be.winnetrie.mod.simpleserverutilities.core.job;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.Consumer;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.server.MinecraftServer;

/**
 * Fair, bounded server-thread scheduler for large world operations.
 */
public final class SsuJobScheduler {

    private static final int DEFAULT_TICK_OPERATION_BUDGET = 5_000;
    private static final long DEFAULT_TICK_TIME_BUDGET_NANOS = 4_000_000L;

    private final Map<UUID, ScheduledJob> jobs = new LinkedHashMap<>();
    private final Queue<UUID> runnable = new ArrayDeque<>();
    private final Map<String, UUID> resourceLocks = new LinkedHashMap<>();

    private int tickOperationBudget = DEFAULT_TICK_OPERATION_BUDGET;
    private long tickTimeBudgetNanos = DEFAULT_TICK_TIME_BUDGET_NANOS;

    public synchronized UUID submit(SsuJob job, Consumer<JobResult> completion) {
        Set<String> locks = normalizeLocks(job.resourceLocks());
        for (String lock : locks) {
            UUID owner = resourceLocks.get(lock);
            if (owner != null) {
                throw new IllegalStateException("Another SSU job already owns resource '" + lock + "' (" + owner + ").");
            }
        }

        UUID id = UUID.randomUUID();
        ScheduledJob scheduled = new ScheduledJob(id, job, completion == null ? result -> {
        } : completion, locks);
        jobs.put(id, scheduled);
        runnable.add(id);
        for (String lock : locks) {
            resourceLocks.put(lock, id);
        }
        return id;
    }

    public boolean cancel(UUID id) {
        ScheduledJob scheduled;
        synchronized (this) {
            scheduled = jobs.remove(id);
            if (scheduled == null) {
                return false;
            }
            runnable.remove(id);
            releaseLocks(scheduled);
        }

        scheduled.job().cancel();
        invokeCompletion(scheduled, JobResult.cancelled(
                id,
                scheduled.job().description(),
                scheduled.operations()
        ));
        return true;
    }

    public void tick(MinecraftServer server) {
        long deadline = System.nanoTime() + tickTimeBudgetNanos;
        int remainingOperations = tickOperationBudget;

        while (remainingOperations > 0 && System.nanoTime() < deadline) {
            ScheduledJob scheduled;
            synchronized (this) {
                UUID id = runnable.poll();
                if (id == null) {
                    return;
                }
                scheduled = jobs.get(id);
            }

            if (scheduled == null) {
                continue;
            }

            int slice = Math.min(remainingOperations, 500);
            try {
                int used = Math.max(0, Math.min(slice, scheduled.job().runStep(server, slice)));
                scheduled.addOperations(used);
                remainingOperations -= Math.max(1, used);

                if (scheduled.job().isComplete()) {
                    finish(scheduled, JobResult.completed(
                            scheduled.id(),
                            scheduled.job().description(),
                            scheduled.operations()
                    ));
                } else {
                    synchronized (this) {
                        if (jobs.containsKey(scheduled.id())) {
                            runnable.add(scheduled.id());
                        }
                    }
                }
            } catch (Exception e) {
                SimpleServerUtilities.LOGGER.error("SSU job failed: {}", scheduled.job().description(), e);
                finish(scheduled, JobResult.failed(
                        scheduled.id(),
                        scheduled.job().description(),
                        scheduled.operations(),
                        e.getMessage()
                ));
            }
        }
    }

    private void finish(ScheduledJob scheduled, JobResult result) {
        synchronized (this) {
            jobs.remove(scheduled.id());
            runnable.remove(scheduled.id());
            releaseLocks(scheduled);
        }
        invokeCompletion(scheduled, result);
    }

    private void invokeCompletion(ScheduledJob scheduled, JobResult result) {
        try {
            scheduled.completion().accept(result);
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error(
                    "SSU job completion callback failed: {} ({})",
                    scheduled.job().description(),
                    result.status(),
                    e
            );
        }
    }

    public synchronized Collection<JobSnapshot> snapshots() {
        Collection<JobSnapshot> snapshots = new ArrayList<>();
        for (ScheduledJob scheduled : jobs.values()) {
            snapshots.add(new JobSnapshot(
                    scheduled.id(),
                    scheduled.job().description(),
                    scheduled.operations(),
                    scheduled.job().progress()
            ));
        }
        return ListCopy.copyOf(snapshots);
    }

    public void clear() {
        java.util.List<ScheduledJob> cancelled;
        synchronized (this) {
            cancelled = java.util.List.copyOf(jobs.values());
            jobs.clear();
            runnable.clear();
            resourceLocks.clear();
        }

        for (ScheduledJob scheduled : cancelled) {
            scheduled.job().cancel();
            invokeCompletion(scheduled, JobResult.cancelled(
                    scheduled.id(),
                    scheduled.job().description(),
                    scheduled.operations()
            ));
        }
    }

    public synchronized boolean isResourceLocked(String resource) {
        return resource != null && resourceLocks.containsKey(resource.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private void releaseLocks(ScheduledJob scheduled) {
        for (String lock : scheduled.resourceLocks()) {
            resourceLocks.remove(lock, scheduled.id());
        }
    }

    private static Set<String> normalizeLocks(Set<String> rawLocks) {
        if (rawLocks == null || rawLocks.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String raw : rawLocks) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            result.add(raw.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    public synchronized int size() {
        return jobs.size();
    }

    public void setTickOperationBudget(int tickOperationBudget) {
        this.tickOperationBudget = Math.max(100, tickOperationBudget);
    }

    public void setTickTimeBudgetNanos(long tickTimeBudgetNanos) {
        this.tickTimeBudgetNanos = Math.max(500_000L, tickTimeBudgetNanos);
    }

    public record JobSnapshot(UUID id, String description, long operations, double progress) {
    }

    public record JobResult(
            UUID id,
            String description,
            Status status,
            long operations,
            String error
    ) {
        static JobResult completed(UUID id, String description, long operations) {
            return new JobResult(id, description, Status.COMPLETED, operations, "");
        }

        static JobResult cancelled(UUID id, String description, long operations) {
            return new JobResult(id, description, Status.CANCELLED, operations, "");
        }

        static JobResult failed(UUID id, String description, long operations, String error) {
            return new JobResult(id, description, Status.FAILED, operations, error == null ? "Unknown error" : error);
        }
    }

    public enum Status {
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private static final class ScheduledJob {
        private final UUID id;
        private final SsuJob job;
        private final Consumer<JobResult> completion;
        private final Set<String> resourceLocks;
        private long operations;

        private ScheduledJob(
                UUID id,
                SsuJob job,
                Consumer<JobResult> completion,
                Set<String> resourceLocks
        ) {
            this.id = id;
            this.job = job;
            this.completion = completion;
            this.resourceLocks = resourceLocks;
        }

        UUID id() {
            return id;
        }

        SsuJob job() {
            return job;
        }

        Consumer<JobResult> completion() {
            return completion;
        }

        Set<String> resourceLocks() {
            return resourceLocks;
        }

        long operations() {
            return operations;
        }

        void addOperations(long amount) {
            operations += amount;
        }
    }

    /** Avoids exposing a mutable collection without depending on preview APIs. */
    private static final class ListCopy {
        private ListCopy() {
        }

        static <T> Collection<T> copyOf(Collection<T> source) {
            return java.util.List.copyOf(source);
        }
    }
}
