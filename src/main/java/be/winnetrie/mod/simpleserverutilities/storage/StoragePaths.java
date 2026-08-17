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

    public static Path dimensions(Path root) {
        return root.resolve("dimensions");
    }

    public static Path dimensionDefinitions(Path root) {
        return dimensions(root).resolve("definitions");
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

    public static Path identity(Path root) {
        return root.resolve("identity");
    }

    public static Path titleDefinitions(Path root) {
        return identity(root).resolve("titles.json");
    }

    public static Path playerIdentities(Path root) {
        return identity(root).resolve("players");
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

    public static Path content(Path root) {
        return root.resolve("content");
    }

    public static Path contentProgression(Path root) {
        return content(root).resolve("progression");
    }

    public static Path contentProgressionPlayers(Path root) {
        return contentProgression(root).resolve("players");
    }

    public static Path achievements(Path root) { return root.resolve("achievements"); }
    public static Path achievementDefinitions(Path root) { return achievements(root).resolve("definitions"); }
    public static Path achievementPlayers(Path root) { return achievements(root).resolve("players"); }

    public static Path npcs(Path root) {
        return root.resolve("npcs");
    }

    public static Path npcDefinitions(Path root) {
        return npcs(root).resolve("definitions");
    }

    public static Path npcInstances(Path root) {
        return npcs(root).resolve("instances");
    }

    public static Path npcAbilities(Path root) {
        return npcs(root).resolve("abilities");
    }

    public static Path npcSpawnProfiles(Path root) {
        return npcs(root).resolve("spawn_profiles");
    }

    public static Path npcDialogues(Path root) {
        return npcs(root).resolve("dialogues");
    }

    public static Path npcShops(Path root) {
        return npcs(root).resolve("shops");
    }

    /** Server-local PNG skins used by custom player-style NPCs. */
    public static Path npcTextures(Path root) {
        return npcs(root).resolve("textures");
    }

    public static Path npcItemPrices(Path root) {
        return npcs(root).resolve("item_prices.json");
    }

    public static Path quests(Path root) {
        return root.resolve("quests");
    }

    public static Path questDefinitions(Path root) {
        return quests(root).resolve("definitions");
    }

    public static Path questJournals(Path root) {
        return quests(root).resolve("players");
    }

    public static Path minigames(Path root) {
        return root.resolve("minigames");
    }

    public static Path minigameDefinitions(Path root) {
        return minigames(root).resolve("definitions");
    }

    public static Path minigameRecovery(Path root) {
        return minigames(root).resolve("recovery.json");
    }

    public static Path minigameProgression(Path root) {
        return minigames(root).resolve("progression.json");
    }

    public static Path minigameHistory(Path root) {
        return minigames(root).resolve("history.json");
    }

    public static Path minigameExports(Path root) {
        return minigames(root).resolve("exports");
    }

    public static Path dungeons(Path root) {
        return root.resolve("dungeons");
    }

    public static Path dungeonDefinitions(Path root) {
        return dungeons(root).resolve("definitions");
    }

    public static Path dungeonRecovery(Path root) {
        return dungeons(root).resolve("recovery.json");
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

    public static Path auctionHouse(Path root) {
        return root.resolve("auction_house");
    }

    public static Path auctionListings(Path root) {
        return auctionHouse(root).resolve("listings");
    }

    public static Path auctionPurchases(Path root) {
        return auctionHouse(root).resolve("purchases");
    }

    public static Path auctionSettings(Path root) {
        return auctionHouse(root).resolve("settings.json");
    }

    public static Path serverSpawn(Path root) {
        return root.resolve("spawn").resolve("server_spawn.json");
    }

    public static Path onboarding(Path root) {
        return root.resolve("onboarding");
    }

    public static Path moderation(Path root) {
        return root.resolve("moderation");
    }

    public static Path moderationRecords(Path root) {
        return moderation(root).resolve("players");
    }

    public static Path playerInventorySnapshots(Path root) {
        return moderation(root).resolve("inventories");
    }

    public static Path jails(Path root) {
        return root.resolve("jails");
    }

    public static Path jailDefinitions(Path root) {
        return jails(root).resolve("definitions");
    }

    public static Path mines(Path root) {
        return root.resolve("mines");
    }

    public static Path mineDefinitions(Path root) {
        return mines(root).resolve("definitions");
    }

    public static Path kits(Path root) {
        return root.resolve("kits");
    }

    public static Path kitDefinitions(Path root) {
        return kits(root).resolve("definitions");
    }

    public static Path kitPlayers(Path root) {
        return kits(root).resolve("players");
    }

    public static Path regions(Path root) {
        return root.resolve("regions");
    }

    public static Path regionEntries(Path root) {
        return regions(root).resolve("entries");
    }

    public static Path regionSelectionTemplates(Path root) {
        return regions(root).resolve("selection_templates");
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
