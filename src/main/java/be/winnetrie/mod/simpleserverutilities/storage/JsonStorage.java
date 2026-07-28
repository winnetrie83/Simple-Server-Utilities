package be.winnetrie.mod.simpleserverutilities.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.Gson;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;

public final class JsonStorage {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private JsonStorage() {
    }

    public static <T> T read(Gson gson, Path file, Class<T> type) throws IOException {
        return gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
    }

    public static void write(Gson gson, Path file, Object value) throws IOException {
        writeStringAtomic(file, gson.toJson(value));
    }

    public static void writeStringAtomic(Path file, String content) throws IOException {
        Path parent = file.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmpFile, content, StandardCharsets.UTF_8);

        if (Files.exists(file)) {
            Path backupFile = file.resolveSibling(file.getFileName() + ".bak");
            Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            Files.move(tmpFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    public static boolean hasJsonFiles(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }

        try (Stream<Path> stream = Files.walk(folder)) {
            return stream.anyMatch(JsonStorage::isJsonFile);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to scan JSON storage folder: {}", folder, e);
            return false;
        }
    }

    public static List<Path> listJsonFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(JsonStorage::isJsonFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String::compareToIgnoreCase))
                    .toList();
        }
    }

    public static void deleteStaleJsonFiles(Path folder, Set<Path> keptFiles) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }

        Set<Path> normalizedKeptFiles = new HashSet<>();

        for (Path keptFile : keptFiles) {
            normalizedKeptFiles.add(keptFile.toAbsolutePath().normalize());
        }

        for (Path file : listJsonFiles(folder)) {
            if (!normalizedKeptFiles.contains(file.toAbsolutePath().normalize())) {
                Files.deleteIfExists(file);
            }
        }
    }

    public static Path archiveLegacyFile(Path file) {
        return archiveFile(file, "legacy");
    }

    public static Path archiveBrokenFile(Path file) {
        return archiveFile(file, "broken");
    }

    private static Path archiveFile(Path file, String reason) {
        if (file == null || !Files.exists(file)) {
            return null;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String fileName = file.getFileName().toString();
        Path target = file.resolveSibling(fileName + "." + reason + "-" + timestamp);

        int counter = 2;
        while (Files.exists(target)) {
            target = file.resolveSibling(fileName + "." + reason + "-" + timestamp + "-" + counter);
            counter++;
        }

        try {
            Files.move(file, target);
            return target;
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to archive {} file: {}", reason, file, e);
            return null;
        }
    }

    private static boolean isJsonFile(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json");
    }
}
