package be.winnetrie.mod.simpleserverutilities.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>Callers serialize immutable snapshots on the server thread and enqueue the
 * resulting text. Repeated writes to the same path are coalesced so only the
 * newest snapshot reaches disk. No Minecraft world state is ever accessed from
 * the storage worker.</p>
 */
public final class BatchedStorageService {

    private final ConcurrentHashMap<Path, PendingOperation> pending = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Path> order = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final AtomicLong queuedWrites = new AtomicLong();
    private final AtomicLong completedWrites = new AtomicLong();
    private final AtomicLong failedWrites = new AtomicLong();
    private final AtomicLong coalescedWrites = new AtomicLong();
    private final Set<Path> retryRequired = ConcurrentHashMap.newKeySet();

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
                if (pending.isEmpty() && order.isEmpty() && !drainScheduled.get()) {
                    return true;
                }
            }
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Failed while flushing SSU storage queue.", e);
            return false;
        }

        SimpleServerUtilities.LOGGER.error(
                "Timed out while flushing SSU storage queue. {} operation(s) remain; {} path(s) require retry.",
                pending.size(),
                retryRequired.size()
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

    public StorageStatistics statistics() {
        return new StorageStatistics(
                queuedWrites.get(),
                completedWrites.get(),
                failedWrites.get(),
                coalescedWrites.get(),
                pending.size(),
                retryRequired.size()
        );
    }

    public record StorageStatistics(
            long queued,
            long completed,
            long failed,
            long coalesced,
            int pending,
            int retryRequired
    ) {
    }

    private record PendingOperation(OperationType type, String content, int attempts) {
    }

    private enum OperationType {
        WRITE,
        DELETE
    }
}
