package be.winnetrie.mod.simpleserverutilities.core.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchedStorageServiceTest {

    @TempDir
    Path temp;

    @Test
    void coalescesWritesAndFlushesCustomTasks() throws Exception {
        BatchedStorageService storage = new BatchedStorageService();
        Path file = temp.resolve("state.json");
        try {
            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch releaseTask = new CountDownLatch(1);
            var blocker = storage.submitTask(() -> {
                taskStarted.countDown();
                if (!releaseTask.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release test storage task.");
                }
                return 42;
            });
            assertTrue(taskStarted.await(5, TimeUnit.SECONDS));
            storage.queueWrite(file, "old");
            storage.queueWrite(file, "new");
            releaseTask.countDown();
            assertEquals(42, blocker.get());
            assertTrue(storage.flush(Duration.ofSeconds(5)));
            assertEquals("new", Files.readString(file));
            assertTrue(storage.statistics().coalesced() >= 1L);
            assertEquals(0L, storage.statistics().activeTasks());
        } finally {
            storage.stop(Duration.ofSeconds(5));
        }
    }
}
