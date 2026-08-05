package be.winnetrie.mod.simpleserverutilities.client.region;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.Minecraft;

/** Local-only storage for portable region-selection templates. */
public final class RegionSelectionClientStorage {
    private static final int MAX_TRANSFER_BYTES = 8 * 1024 * 1024;
    private static final String FILE_EXTENSION = ".ssusel";
    private RegionSelectionClientStorage() {
    }

    public static List<String> list() {
        Path folder = folder();
        if (!Files.isDirectory(folder)) return List.of();
        try (var stream = Files.list(folder)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(FILE_EXTENSION))
                    .map(name -> name.substring(0, name.length() - FILE_EXTENSION.length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(256)
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    public static void save(String rawName, byte[] data) throws IOException {
        String name = validateName(rawName);
        if (data == null || data.length == 0) throw new IOException("Template data is empty.");
        if (data.length > MAX_TRANSFER_BYTES) {
            throw new IOException("Template exceeds the 8 MiB transfer limit.");
        }
        Path folder = folder();
        Files.createDirectories(folder);
        writeAtomically(folder.resolve(name + FILE_EXTENSION), data);
    }

    public static byte[] load(String rawName) throws IOException {
        String name = validateName(rawName);
        Path file = folder().resolve(name + FILE_EXTENSION);
        if (!Files.isRegularFile(file)) throw new IOException("Client template not found: " + name);
        if (Files.size(file) > MAX_TRANSFER_BYTES) {
            throw new IOException("Template exceeds the 8 MiB transfer limit.");
        }
        byte[] data = Files.readAllBytes(file);
        if (data.length > MAX_TRANSFER_BYTES) {
            throw new IOException("Template exceeds the 8 MiB transfer limit.");
        }
        return data;
    }

    private static void writeAtomically(Path file, byte[] data) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(temporary, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!name.matches("[A-Za-z0-9._-]{1,64}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Use 1-64 letters, numbers, dots, underscores or dashes for the template name.");
        }
        return name;
    }

    private static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("simpleserverutilities")
                .resolve("region_templates");
    }
}
