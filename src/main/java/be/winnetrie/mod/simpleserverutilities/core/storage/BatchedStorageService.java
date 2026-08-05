package be.winnetrie.mod.simpleserverutilities.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;

import com.google.gson.Gson;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;

/**
 * Coalescing, single-writer storage queue.
 *
 * <p>Callers may enqueue already-serialized immutable snapshots or submit
 * immutable parsing/serialization tasks. Repeated writes to the same path are
 * coalesced so only the newest snapshot reaches disk. No live Minecraft world
 * state may ever be accessed from the storage worker.</p>
 */
public final class BatchedStorageService {

    private final ConcurrentHashMap<Path, PendingOperation> pending = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Path> order = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final AtomicLong queuedWrites = new AtomicLong();
    private final AtomicLong completedWrites = new AtomicLong();
    private final AtomicLong failedWrites = new AtomicLong();
    private final AtomicLong coalescedWrites = new AtomicLong();
    private final AtomicLong activeTasks = new AtomicLong();
    private final Set<Path> retryRequired = ConcurrentHashMap.newKeySet();
    private final Set<Path> activePaths = ConcurrentHashMap.newKeySet();

    private volatile ExecutorService executor;

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }

        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SSU-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void queueJson(Gson gson, Path file, Object snapshot) {
        Objects.requireNonNull(gson, "gson");
        queueWrite(file, gson.toJson(snapshot));
    }

    public void queueWrite(Path file, String content) {
        enqueue(file, new PendingOperation(OperationType.WRITE, Objects.requireNonNull(content, "content"), 0));
    }

    public void queueDelete(Path file) {
        enqueue(file, new PendingOperation(OperationType.DELETE, "", 0));
    }

    /**
     * Runs immutable serialization or parsing work on SSU's single storage
     * worker. The task must never access live Minecraft world state.
     */
    public <T> CompletableFuture<T> submitTask(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        start();
        CompletableFuture<T> result = new CompletableFuture<>();
        activeTasks.incrementAndGet();
        ExecutorService current = executor;
        if (current == null || current.isShutdown()) {
            activeTasks.decrementAndGet();
            result.completeExceptionally(new IllegalStateException("SSU storage worker is not available."));
            return result;
        }
        try {
            current.execute(() -> {
                try {
                    result.complete(task.call());
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                } finally {
                    activeTasks.decrementAndGet();
                }
            });
        } catch (RuntimeException e) {
            activeTasks.decrementAndGet();
            result.completeExceptionally(e);
        }
        return result;
    }

    private void enqueue(Path rawFile, PendingOperation operation) {
        Objects.requireNonNull(rawFile, "file");
        start();

        Path file = rawFile.toAbsolutePath().normalize();
        PendingOperation previous = pending.put(file, operation);

        if (previous == null) {
            order.add(file);
        } else {
            coalescedWrites.incrementAndGet();
        }

        queuedWrites.incrementAndGet();
        scheduleDrain();
    }

    private void scheduleDrain() {
        ExecutorService current = executor;
        if (current == null || current.isShutdown() || !drainScheduled.compareAndSet(false, true)) {
            return;
        }

        current.execute(this::drain);
    }

    private void drain() {
        try {
            Path file;
            while ((file = order.poll()) != null) {
                PendingOperation operation = pending.remove(file);
                if (operation == null) {
                    continue;
                }

                activePaths.add(file);
                long storageTimer = SimpleServerUtilities.PERFORMANCE.startTimer();
                try {
                    if (operation.type() == OperationType.DELETE) {
                        Files.deleteIfExists(file);
                    } else {
                        JsonStorage.writeStringAtomic(file, operation.content());
                    }
                    retryRequired.remove(file);
                    completedWrites.incrementAndGet();
                } catch (IOException e) {
                    failedWrites.incrementAndGet();
                    if (operation.attempts() < 2) {
                        PendingOperation retry = new PendingOperation(
                                operation.type(),
                                operation.content(),
                                operation.attempts() + 1
                        );
                        if (pending.putIfAbsent(file, retry) == null) {
                            order.add(file);
                        }
                        SimpleServerUtilities.LOGGER.warn(
                                "Retrying queued SSU storage operation for {} (attempt {}).",
                                file,
                                retry.attempts() + 1
                        );
                    } else {
                        retryRequired.add(file);
                        SimpleServerUtilities.LOGGER.error("Failed queued SSU storage operation for {}.", file, e);
                    }
                } finally {
                    activePaths.remove(file);
                    SimpleServerUtilities.PERFORMANCE.stopTimer("storage_io", storageTimer);
                }
            }
        } finally {
            drainScheduled.set(false);
            if (!order.isEmpty()) {
                scheduleDrain();
            }
        }
    }

    public boolean flush(Duration timeout) {
        ExecutorService current = executor;
        if (current == null) {
            return true;
        }

        long deadline = System.nanoTime() + Math.max(1L, timeout.toNanos());
        try {
            while (System.nanoTime() < deadline) {
                scheduleDrain();
                Future<?> barrier = current.submit(() -> {
                });
                long remainingNanos = Math.max(1L, deadline - System.nanoTime());
                barrier.get(remainingNanos, TimeUnit.NANOSECONDS);
                if (pending.isEmpty() && order.isEmpty() && !drainScheduled.get()
                        && retryRequired.isEmpty() && activeTasks.get() == 0L) {
                    return true;
                }
            }
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed while flushing SSU storage queue.", e);
            return false;
        }

        SimpleServerUtilities.LOGGER.error(
                "Timed out while flushing SSU storage queue. {} operation(s) remain; {} path(s) require retry; "
                        + "{} immutable task(s) are still active.",
                pending.size(),
                retryRequired.size(),
                activeTasks.get()
        );
        return false;
    }

    /**
     * Waits until the newest queued write/delete for one exact path has reached
     * durable storage, without forcing unrelated SSU storage records to finish.
     *
     * <p>This is intended for narrow safety barriers such as persisting a player
     * recovery record before live inventory state is replaced. A path that has
     * exhausted its retries is never reported as successfully flushed.</p>
     */
    public boolean flushPath(Path rawFile, Duration timeout) {
        if (rawFile == null) return true;
        Path file = rawFile.toAbsolutePath().normalize();
        ExecutorService current = executor;
        if (current == null) {
            return !hasPending(file) && !requiresRetry(file);
        }

        long deadline = System.nanoTime() + Math.max(1L, timeout.toNanos());
        try {
            while (System.nanoTime() < deadline) {
                scheduleDrain();
                Future<?> barrier = current.submit(() -> {
                });
                long remainingNanos = Math.max(1L, deadline - System.nanoTime());
                barrier.get(remainingNanos, TimeUnit.NANOSECONDS);

                if (requiresRetry(file)) {
                    SimpleServerUtilities.LOGGER.error(
                            "Failed to flush critical SSU storage path because it requires retry: {}", file);
                    return false;
                }
                if (!hasPending(file)) {
                    return true;
                }
            }
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed while flushing critical SSU storage path {}.", file, e);
            return false;
        }

        SimpleServerUtilities.LOGGER.error(
                "Timed out while flushing critical SSU storage path {} (pending={}, retryRequired={}).",
                file,
                hasPending(file),
                requiresRetry(file)
        );
        return false;
    }

    public synchronized void stop(Duration timeout) {
        ExecutorService current = executor;
        if (current == null) {
            return;
        }

        flush(timeout);
        current.shutdown();
        try {
            if (!current.awaitTermination(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        } finally {
            executor = null;
        }
    }

    public boolean requiresRetry(Path rawFile) {
        if (rawFile == null) {
            return false;
        }
        return retryRequired.contains(rawFile.toAbsolutePath().normalize());
    }

    /** Returns whether the newest write/delete for this path is still queued or executing. */
    public boolean hasPending(Path rawFile) {
        if (rawFile == null) return false;
        Path file = rawFile.toAbsolutePath().normalize();
        return pending.containsKey(file) || order.contains(file) || activePaths.contains(file);
    }

    public StorageStatistics statistics() {
        return new StorageStatistics(
                queuedWrites.get(),
                completedWrites.get(),
                failedWrites.get(),
                coalescedWrites.get(),
                pending.size(),
                retryRequired.size(),
                activeTasks.get()
        );
    }

    public record StorageStatistics(
            long queued,
            long completed,
            long failed,
            long coalesced,
            int pending,
            int retryRequired,
            long activeTasks
    ) {
    }

    private record PendingOperation(OperationType type, String content, int attempts) {
    }

    private enum OperationType {
        WRITE,
        DELETE
    }
}
