package be.winnetrie.mod.simpleserverutilities.storage;

import java.nio.file.Path;
import java.util.Locale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class StoragePaths {

    public static final String ROOT_FOLDER = "simpleserverutilities";
    public static final String LEGACY_ROOT_FOLDER = "simple_server_utilities";

    private StoragePaths() {
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(ROOT_FOLDER);
    }

    public static Path legacyRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(LEGACY_ROOT_FOLDER);
    }

    public static Path permissions(Path root) {
        return root.resolve("permissions");
    }

    public static Path permissionSettings(Path root) {
        return permissions(root).resolve("settings.json");
    }

    public static Path permissionRanks(Path root) {
        return permissions(root).resolve("ranks");
    }

    public static Path permissionPlayers(Path root) {
        return permissions(root).resolve("players");
    }

    public static Path permissionDimensions(Path root) {
        return permissions(root).resolve("dimensions");
    }

    public static Path permissionClaimContext(Path root) {
        return permissions(root).resolve("claim_context");
    }

    public static Path playerSettings(Path root) {
        return root.resolve("player_settings");
    }

    public static Path mapMarkers(Path root) {
        return root.resolve("map_markers");
    }

    public static Path mapMarkerPlayers(Path root) {
        return mapMarkers(root).resolve("players");
    }

    public static Path homes(Path root) {
        return root.resolve("homes");
    }

    public static Path homePlayers(Path root) {
        return homes(root).resolve("players");
    }

    public static Path playerClaims(Path root) {
        return root.resolve("player_claims");
    }

    public static Path playerClaimEntries(Path root) {
        return playerClaims(root).resolve("claims");
    }

    public static Path playerClaimLimits(Path root) {
        return playerClaims(root).resolve("limits");
    }

    public static Path economy(Path root) {
        return root.resolve("economy");
    }

    public static Path economyAccounts(Path root) {
        return economy(root).resolve("accounts");
    }

    public static Path economyTransactions(Path root) {
        return economy(root).resolve("transactions");
    }


    public static Path holograms(Path root) {
        return root.resolve("holograms");
    }

    public static Path statistics(Path root) {
        return root.resolve("statistics");
    }

    public static Path utilityMining(Path root) {
        return root.resolve("utility_mining");
    }

    public static Path mail(Path root) {
        return root.resolve("mail");
    }

    public static Path mailboxes(Path root) {
        return mail(root).resolve("mailboxes");
    }

    public static Path serverSpawn(Path root) {
        return root.resolve("spawn").resolve("server_spawn.json");
    }

    public static Path regions(Path root) {
        return root.resolve("regions");
    }

    public static Path regionEntries(Path root) {
        return regions(root).resolve("entries");
    }

    public static Path regionRentTransactions(Path root) {
        return regions(root).resolve("rent_transactions");
    }

    public static Path jsonFile(Path folder, String name) {
        return folder.resolve(sanitizeFileName(name) + ".json");
    }

    public static String fileBaseName(Path file) {
        String fileName = file.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }

        return fileName;
    }

    public static String sanitizeFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "unnamed";
        }

        String sanitized = rawName.trim()
                .replace(':', '_')
                .replace('/', '_')
                .replace('\\', '_')
                .replace(' ', '_')
                .replaceAll("[^A-Za-z0-9._-]", "_");

        sanitized = sanitized.replaceAll("_+", "_");

        if (sanitized.equals(".") || sanitized.equals("..") || sanitized.isBlank()) {
            return "unnamed";
        }

        if (sanitized.length() > 120) {
            return sanitized.substring(0, 120);
        }

        return sanitized;
    }
}
