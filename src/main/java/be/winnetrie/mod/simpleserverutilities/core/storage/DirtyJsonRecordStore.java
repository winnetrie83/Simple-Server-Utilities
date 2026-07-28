package be.winnetrie.mod.simpleserverutilities.core.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;

/**
 * Tracks serialized record snapshots and only queues files whose content
 * actually changed. Deletions are also queued through the single-writer
 * storage service.
 */
public final class DirtyJsonRecordStore {

    private final Map<Path, String> lastQueuedContent = new HashMap<>();
    private final Set<Path> knownFiles = new HashSet<>();

    public synchronized void reset() {
        lastQueuedContent.clear();
        knownFiles.clear();
    }

    public synchronized void discover(Path folder) {
        try {
            for (Path file : JsonStorage.listJsonFiles(folder)) {
                Path normalized = normalize(file);
                knownFiles.add(normalized);
                lastQueuedContent.put(normalized, Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to discover dirty-record storage folder: {}", folder, e);
        }
    }

    public synchronized boolean queueJson(Gson gson, Path rawFile, Object snapshot) {
        Path file = normalize(rawFile);
        String content = gson.toJson(snapshot);
        String previous = lastQueuedContent.put(file, content);
        knownFiles.add(file);

        if (content.equals(previous) && !SimpleServerUtilities.STORAGE.requiresRetry(file)) {
            return false;
        }

        SimpleServerUtilities.STORAGE.queueWrite(file, content);
        return true;
    }

    public synchronized int queueDeleteMissing(Set<Path> rawKeptFiles) {
        Set<Path> keptFiles = new HashSet<>();
        for (Path file : rawKeptFiles) {
            keptFiles.add(normalize(file));
        }

        int deleted = 0;
        for (Path known : Set.copyOf(knownFiles)) {
            if (keptFiles.contains(known)) {
                continue;
            }
            SimpleServerUtilities.STORAGE.queueDelete(known);
            knownFiles.remove(known);
            lastQueuedContent.remove(known);
            deleted++;
        }
        knownFiles.addAll(keptFiles);
        return deleted;
    }

    public synchronized void forget(Path rawFile) {
        Path file = normalize(rawFile);
        knownFiles.remove(file);
        lastQueuedContent.remove(file);
    }

    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }
}
