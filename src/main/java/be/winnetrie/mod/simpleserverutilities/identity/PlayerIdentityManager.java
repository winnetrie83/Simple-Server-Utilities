package be.winnetrie.mod.simpleserverutilities.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.PlayerIdentitySyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.RankDisplayDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.RankDisplayRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.RankDisplaySavePayload;
import be.winnetrie.mod.simpleserverutilities.network.TitleManagerActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.TitleManagerDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.TitleManagerRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionRank;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.settings.MinecraftColorPalette;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative title catalogue, player title selections and rank presentation. */
public final class PlayerIdentityManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, PlayerIdentityData> players = new HashMap<>();
    private final Map<UUID, Path> knownPlayerFiles = new HashMap<>();
    private TitleCatalogData catalog = new TitleCatalogData();
    private Path catalogFile;
    private Path playerFolder;
    private MinecraftServer server;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        Path root = StoragePaths.identity(StoragePaths.root(server));
        catalogFile = StoragePaths.titleDefinitions(StoragePaths.root(server));
        playerFolder = StoragePaths.playerIdentities(StoragePaths.root(server));
        players.clear();
        knownPlayerFiles.clear();
        try {
            Files.createDirectories(root);
            Files.createDirectories(playerFolder);
            if (Files.exists(catalogFile)) {
                TitleCatalogData loaded = JsonStorage.read(GSON, catalogFile, TitleCatalogData.class);
                if (loaded != null) catalog = loaded;
            } else catalog = TitleCatalogData.createDefaultCatalogue();
            catalog.normalize();
            JsonStorage.write(GSON, catalogFile, catalog);
            for (Path file : JsonStorage.listJsonFiles(playerFolder)) {
                try { knownPlayerFiles.put(UUID.fromString(StoragePaths.fileBaseName(file)), file); }
                catch (IllegalArgumentException exception) {
                    Path archived = JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Invalid player identity filename. Archived: {}", archived, exception);
                }
            }
            SimpleServerUtilities.LOGGER.info("Loaded {} title definition(s) and indexed {} player identity record(s).",
                    catalog.titles.size(), knownPlayerFiles.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU player identities.", exception);
            catalog = TitleCatalogData.createDefaultCatalogue();
            catalog.normalize();
        }
    }

    public synchronized void clear() {
        players.clear(); knownPlayerFiles.clear(); server = null; catalogFile = null; playerFolder = null;
    }

    public synchronized void saveAll() {
        if (catalogFile == null || playerFolder == null) return;
        try {
            catalog.normalize();
            JsonStorage.write(GSON, catalogFile, catalog);
            for (Map.Entry<UUID, PlayerIdentityData> entry : players.entrySet()) savePlayer(entry.getKey(), entry.getValue());
        } catch (IOException exception) {
            SimpleServerUtilities.LOGGER.error("Failed to save player identity data.", exception);
        }
    }

    public synchronized PlayerIdentityData ensurePlayer(ServerPlayer player) {
        if (player == null) throw new IllegalArgumentException("Player is required.");
        UUID id = player.getUUID();
        PlayerIdentityData data = loadPlayer(id);
        boolean dirty = false;
        if (data == null) {
            data = new PlayerIdentityData();
            data.selectedTitleId = legacyTitleId(SimpleServerUtilities.MINIGAMES.legacySelectedTitle(id));
            players.put(id, data);
            dirty = true;
        }
        String currentName = player.getName().getString();
        if (!currentName.equals(data.lastKnownName)) {
            data.lastKnownName = currentName;
            dirty = true;
        }
        data.normalize(id);
        if (!isUnlocked(player, definition(data.selectedTitleId).orElse(null), data)) {
            String fallback = firstUnlocked(player, data).map(value -> value.id).orElse("");
            if (!fallback.equals(data.selectedTitleId)) {
                data.selectedTitleId = fallback;
                dirty = true;
            }
        }
        if (dirty) savePlayer(id, data);
        return data;
    }

    public synchronized Optional<PlayerTitleDefinition> selectedTitle(ServerPlayer player) {
        PlayerIdentityData data = ensurePlayer(player);
        PlayerTitleDefinition selected = definition(data.selectedTitleId).orElse(null);
        if (selected != null && selected.enabled && isUnlocked(player, selected, data)) return Optional.of(selected.copy());
        return firstUnlocked(player, data).map(PlayerTitleDefinition::copy);
    }

    public synchronized List<TitleManagerDataPayload.Entry> titleEntries(ServerPlayer player) {
        PlayerIdentityData data = ensurePlayer(player);
        ArrayList<TitleManagerDataPayload.Entry> result = new ArrayList<>();
        for (PlayerTitleDefinition definition : catalog.titles) {
            definition.normalize();
            boolean unlocked = isUnlocked(player, definition, data);
            result.add(new TitleManagerDataPayload.Entry(definition.id, definition.displayName, definition.color,
                    definition.unlockType.name(), definition.requirement, definition.requirementValue,
                    definition.enabled, unlocked, definition.id.equals(data.selectedTitleId),
                    definition.acquisitionDescription()));
        }
        result.sort(Comparator.comparing(TitleManagerDataPayload.Entry::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public void handleTitleRequest(TitleManagerRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        sendTitleData(player, payload.adminView(), "", false, payload.requestId());
    }

    public void handleTitleAction(TitleManagerActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String notice;
        boolean error = false;
        boolean adminView = !"select".equalsIgnoreCase(payload.action());
        try {
            notice = performTitleAction(player, payload);
        } catch (RuntimeException exception) {
            notice = exception.getMessage() == null ? "The title action failed safely." : exception.getMessage();
            error = true;
        }
        sendTitleData(player, adminView, notice, error, payload.requestId());
        syncAll();
    }

    private synchronized String performTitleAction(ServerPlayer actor, TitleManagerActionPayload payload) {
        String action = payload.action().toLowerCase(Locale.ROOT);
        if ("select".equals(action)) {
            PlayerIdentityData data = ensurePlayer(actor);
            PlayerTitleDefinition definition = definition(payload.id()).orElseThrow(() -> new IllegalArgumentException("That title no longer exists."));
            if (!definition.enabled || !isUnlocked(actor, definition, data)) throw new IllegalArgumentException("That title is not unlocked.");
            data.selectedTitleId = definition.id;
            savePlayer(actor.getUUID(), data);
            return "Selected title: " + definition.displayName + ".";
        }
        requireAdmin(actor);
        return switch (action) {
            case "save" -> {
                PlayerTitleDefinition definition = new PlayerTitleDefinition(payload.id(), payload.displayName(), payload.color(),
                        parseUnlockType(payload.unlockType()), payload.requirement(), payload.requirementValue());
                definition.enabled = true;
                upsert(definition);
                saveAll();
                yield "Saved title '" + definition.displayName + "'.";
            }
            case "toggle" -> {
                PlayerTitleDefinition definition = definition(payload.id()).orElseThrow(() -> new IllegalArgumentException("Title not found."));
                definition.enabled = !definition.enabled; saveAll();
                yield definition.displayName + " is now " + (definition.enabled ? "enabled" : "disabled") + ".";
            }
            case "delete" -> {
                String id = PlayerTitleDefinition.normalizeId(payload.id());
                boolean removed = catalog.titles.removeIf(value -> value != null && id.equals(value.id));
                if (!removed) throw new IllegalArgumentException("Title not found.");
                for (Map.Entry<UUID, PlayerIdentityData> entry : players.entrySet()) {
                    entry.getValue().manuallyUnlockedTitles.remove(id);
                    if (id.equals(entry.getValue().selectedTitleId)) entry.getValue().selectedTitleId = "";
                }
                saveAll(); yield "Deleted title '" + id + "'.";
            }
            case "grant", "revoke" -> {
                ServerPlayer target = findPlayer(payload.targetPlayer()).orElseThrow(() -> new IllegalArgumentException("That player is not online."));
                PlayerIdentityData data = ensurePlayer(target);
                String id = PlayerTitleDefinition.normalizeId(payload.id());
                if (definition(id).isEmpty()) throw new IllegalArgumentException("Title not found.");
                if ("grant".equals(action)) data.manuallyUnlockedTitles.add(id); else data.manuallyUnlockedTitles.remove(id);
                if (!isUnlocked(target, definition(id).orElse(null), data) && id.equals(data.selectedTitleId)) {
                    data.selectedTitleId = firstUnlocked(target, data).map(value -> value.id).orElse("");
                }
                savePlayer(target.getUUID(), data);
                yield ("grant".equals(action) ? "Granted " : "Revoked ") + id + " for " + target.getName().getString() + ".";
            }
            default -> throw new IllegalArgumentException("Unknown title action.");
        };
    }

    private void sendTitleData(ServerPlayer player, boolean requestedAdmin, String notice, boolean error, long requestId) {
        boolean admin = requestedAdmin && canAdmin(player);
        if (requestedAdmin && !admin) { notice = "Title administration permission is required."; error = true; }
        PlayerIdentityData data;
        List<TitleManagerDataPayload.Entry> entries;
        synchronized (this) { data = ensurePlayer(player); entries = titleEntries(player); }
        PacketDistributor.sendToPlayer(player, new TitleManagerDataPayload(admin, data.selectedTitleId, entries, notice, error, requestId));
    }

    public void handleRankDisplayRequest(RankDisplayRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canAdmin(player)) {
            PacketDistributor.sendToPlayer(player, new RankDisplayDataPayload(payload.rankName(), "", "Permission administration access is required.", true));
            return;
        }
        PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(payload.rankName());
        PacketDistributor.sendToPlayer(player, new RankDisplayDataPayload(payload.rankName(),
                rank == null ? "" : rank.getDisplayPrefix(), rank == null ? "Rank not found." : "", rank == null));
    }

    public void handleRankDisplaySave(RankDisplaySavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canAdmin(player)) {
            PacketDistributor.sendToPlayer(player, new RankDisplayDataPayload(payload.rankName(), payload.encodedPrefix(), "Permission administration access is required.", true));
            return;
        }
        PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(payload.rankName());
        if (rank == null) {
            PacketDistributor.sendToPlayer(player, new RankDisplayDataPayload(payload.rankName(), payload.encodedPrefix(), "Rank not found.", true));
            return;
        }
        rank.setDisplayPrefix(payload.encodedPrefix());
        SimpleServerUtilities.PERMISSIONS.save();
        syncAll();
        PacketDistributor.sendToPlayer(player, new RankDisplayDataPayload(payload.rankName(), rank.getDisplayPrefix(), "Rank prefix saved.", false));
    }

    public MutableComponent rankPrefix(UUID playerId) {
        String rankName = SimpleServerUtilities.PERMISSIONS.getPrimaryRankName(playerId);
        PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(rankName);
        String encoded = rank == null ? "" : rank.getDisplayPrefix();
        if (encoded == null || encoded.isBlank()) encoded = "§1[" + rankName + "]§r ";
        return RichTextComponents.fromEncoded(encoded);
    }

    public Component chatMessage(ServerPlayer player, String rawText) {
        MutableComponent result = Component.empty();
        MutableComponent prefix = rankPrefix(player.getUUID());
        result.append(prefix);
        if (!prefix.getString().endsWith(" ")) result.append(Component.literal(" ").withStyle(Style.EMPTY));
        result.append(Component.literal(player.getName().getString()).withStyle(Style.EMPTY));
        result.append(Component.literal(": ").withStyle(Style.EMPTY));
        result.append(Component.literal(rawText == null ? "" : rawText).withStyle(Style.EMPTY));
        return result;
    }

    public void syncAll() {
        MinecraftServer current = server;
        if (current == null) return;
        ArrayList<PlayerIdentitySyncPayload.Entry> entries = new ArrayList<>();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            var prefs = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
            Optional<PlayerTitleDefinition> title = selectedTitle(player);
            entries.add(new PlayerIdentitySyncPayload.Entry(player.getId(), player.getName().getString(),
                    encodedRankPrefix(player.getUUID()), prefs.isRankVisible(),
                    title.map(value -> value.displayName).orElse(""),
                    title.map(value -> value.color).orElse(MinecraftColorPalette.COLORS.getFirst().argb()),
                    prefs.isTitleVisible()));
        }
        PlayerIdentitySyncPayload payload = new PlayerIdentitySyncPayload(entries);
        for (ServerPlayer receiver : current.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(receiver, payload);
    }

    private String encodedRankPrefix(UUID playerId) {
        String rankName = SimpleServerUtilities.PERMISSIONS.getPrimaryRankName(playerId);
        PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(rankName);
        String encoded = rank == null ? "" : rank.getDisplayPrefix();
        return encoded == null || encoded.isBlank() ? "§1[" + rankName + "]§r " : encoded;
    }

    private synchronized PlayerIdentityData loadPlayer(UUID id) {
        PlayerIdentityData cached = players.get(id); if (cached != null) return cached;
        Path file = knownPlayerFiles.get(id); if (file == null || !Files.exists(file)) return null;
        try {
            PlayerIdentityData loaded = JsonStorage.read(GSON, file, PlayerIdentityData.class);
            if (loaded == null) throw new IllegalArgumentException("Empty identity record.");
            loaded.normalize(id); players.put(id, loaded); return loaded;
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(file); knownPlayerFiles.remove(id);
            SimpleServerUtilities.LOGGER.error("Failed to load player identity. Archived: {}", archived, exception); return null;
        }
    }

    private synchronized void savePlayer(UUID id, PlayerIdentityData data) {
        if (playerFolder == null || id == null || data == null) return;
        try {
            data.normalize(id); Path file = StoragePaths.jsonFile(playerFolder, id.toString());
            JsonStorage.write(GSON, file, data); knownPlayerFiles.put(id, file);
        } catch (IOException exception) { SimpleServerUtilities.LOGGER.error("Failed to save player identity {}.", id, exception); }
    }

    /** Grants a catalogue title as a durable manual unlock. Returns false when it was already unlocked manually. */
    public synchronized boolean grantManualTitle(ServerPlayer player, String rawId) {
        if (player == null) throw new IllegalArgumentException("Player is required.");
        String id = PlayerTitleDefinition.normalizeId(rawId);
        if (definition(id).isEmpty()) throw new IllegalArgumentException("Unknown title: " + id);
        PlayerIdentityData data = ensurePlayer(player);
        boolean changed = data.manuallyUnlockedTitles.add(id);
        if (changed) savePlayer(player.getUUID(), data);
        return changed;
    }

    public synchronized boolean revokeManualTitle(ServerPlayer player, String rawId) {
        if (player == null) return false;
        String id = PlayerTitleDefinition.normalizeId(rawId);
        PlayerIdentityData data = ensurePlayer(player);
        boolean changed = data.manuallyUnlockedTitles.remove(id);
        if (changed) {
            if (id.equals(data.selectedTitleId) && !isUnlocked(player, definition(id).orElse(null), data)) {
                data.selectedTitleId = firstUnlocked(player, data).map(value -> value.id).orElse("");
            }
            savePlayer(player.getUUID(), data);
        }
        return changed;
    }

    private synchronized Optional<PlayerTitleDefinition> definition(String rawId) {
        if (rawId == null || rawId.isBlank()) return Optional.empty();
        String id = PlayerTitleDefinition.normalizeId(rawId);
        return catalog.titles.stream().filter(value -> value != null && id.equals(value.id)).findFirst();
    }

    private synchronized void upsert(PlayerTitleDefinition definition) {
        definition.normalize();
        for (int i = 0; i < catalog.titles.size(); i++) {
            PlayerTitleDefinition existing = catalog.titles.get(i);
            if (existing != null && definition.id.equals(existing.id)) {
                definition.enabled = existing.enabled;
                catalog.titles.set(i, definition);
                return;
            }
        }
        if (catalog.titles.size() >= 512) throw new IllegalArgumentException("The title catalogue is full.");
        catalog.titles.add(definition);
    }

    private boolean isUnlocked(ServerPlayer player, PlayerTitleDefinition definition, PlayerIdentityData data) {
        if (player == null || definition == null || data == null || !definition.enabled) return false;
        if (data.manuallyUnlockedTitles.contains(definition.id)) return true;
        return switch (definition.unlockType) {
            case FREE -> true;
            case MINIGAME_LEVEL -> SimpleServerUtilities.MINIGAMES.progressionLevel(player.getUUID()) >= definition.requirement;
            case MINIGAME_WINS -> SimpleServerUtilities.MINIGAMES.progressionWins(player.getUUID()) >= definition.requirement;
            case RANK -> SimpleServerUtilities.PERMISSIONS.getAssignedRankNames(player.getUUID()).stream()
                    .anyMatch(value -> value.equalsIgnoreCase(definition.requirementValue));
            case PERMISSION -> !definition.requirementValue.isBlank()
                    && PermissionService.getBooleanWithoutOperatorBypass(player, definition.requirementValue, false);
            case MANUAL -> false;
        };
    }

    private Optional<PlayerTitleDefinition> firstUnlocked(ServerPlayer player, PlayerIdentityData data) {
        return catalog.titles.stream().filter(value -> value != null && value.enabled && isUnlocked(player, value, data))
                .min(Comparator.comparing(value -> value.displayName, String.CASE_INSENSITIVE_ORDER));
    }

    private Optional<ServerPlayer> findPlayer(String raw) {
        if (server == null || raw == null || raw.isBlank()) return Optional.empty();
        ServerPlayer byName = server.getPlayerList().getPlayerByName(raw.trim());
        if (byName != null) return Optional.of(byName);
        try { return Optional.ofNullable(server.getPlayerList().getPlayer(UUID.fromString(raw.trim()))); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    private static TitleUnlockType parseUnlockType(String raw) {
        try { return TitleUnlockType.valueOf(raw == null ? "FREE" : raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return TitleUnlockType.FREE; }
    }

    private static String legacyTitleId(String raw) {
        return PlayerTitleDefinition.normalizeId(raw == null || raw.isBlank() ? "rookie" : raw);
    }

    private static void requireAdmin(ServerPlayer player) {
        if (!canAdmin(player)) throw new IllegalArgumentException("Title administration permission is required.");
    }

    private static boolean canAdmin(ServerPlayer player) {
        return player != null && PermissionService.getBoolean(player, PermissionKeys.PERMISSIONS_ADMIN, false);
    }
}
