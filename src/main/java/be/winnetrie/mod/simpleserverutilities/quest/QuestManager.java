package be.winnetrie.mod.simpleserverutilities.quest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.ContentAction;
import be.winnetrie.mod.simpleserverutilities.content.ContentActionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentCondition;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventBus;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.network.QuestBookDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestBookRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Independent, persistent and event-driven Quest Core. */
public final class QuestManager {
    public static final int MAX_QUESTS = 512;
    public static final int MAX_SERIALIZED_CHARACTERS = 65_535;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, PlayerQuestJournal> journals = new LinkedHashMap<>();
    private final DirtyJsonRecordStore definitionStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore journalStore = new DirtyJsonRecordStore();
    private MinecraftServer server;
    private Path definitionFolder;
    private Path journalFolder;
    private ContentEventBus.Subscription eventSubscription;

    public synchronized void load(MinecraftServer server) {
        clear();
        this.server = server;
        Path root = StoragePaths.quests(StoragePaths.root(server));
        definitionFolder = StoragePaths.questDefinitions(StoragePaths.root(server));
        journalFolder = StoragePaths.questJournals(StoragePaths.root(server));
        try {
            Files.createDirectories(root);
            Files.createDirectories(definitionFolder);
            Files.createDirectories(journalFolder);
            definitionStore.discover(definitionFolder);
            journalStore.discover(journalFolder);
            loadDefinitions();
            loadJournals();
            eventSubscription = SimpleServerUtilities.CONTENT_EVENTS.subscribe(ContentEventBus.WILDCARD, this::onContentEvent);
            saveAll();
            SimpleServerUtilities.LOGGER.info("Loaded {} SSU quests and {} player quest journals.", definitions.size(), journals.size());
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to load SSU Quest Core.", exception);
        }
    }

    private void loadDefinitions() throws Exception {
        for (Path file : JsonStorage.listJsonFiles(definitionFolder)) {
            try {
                QuestDefinition definition = JsonStorage.read(GSON, file, QuestDefinition.class);
                if (definition == null) continue;
                definition.normalize();
                validateDefinition(definition, false);
                if (GSON.toJson(definition).length() > MAX_SERIALIZED_CHARACTERS) {
                    throw new IllegalArgumentException("Quest exceeds the serialized size limit.");
                }
                if (definitions.putIfAbsent(definition.id, definition) != null) {
                    throw new IllegalArgumentException("Duplicate quest ID across files: " + definition.id);
                }
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load quest definition; archived as {}.", archived, exception);
            }
        }
        sanitizeLoadedDependencies();
    }

    private void sanitizeLoadedDependencies() {
        for (QuestDefinition definition : definitions.values()) {
            for (String reference : questReferences(definition.prerequisites)) {
                if (!definitions.containsKey(reference)) {
                    definition.enabled = false;
                    SimpleServerUtilities.LOGGER.error(
                            "Disabled loaded quest '{}' because prerequisite quest '{}' does not exist.",
                            definition.id, reference);
                }
            }
        }
        while (true) {
            List<String> cycle = firstEnabledDependencyCycle();
            if (cycle.isEmpty()) break;
            for (String id : cycle) {
                QuestDefinition definition = definitions.get(id);
                if (definition != null) definition.enabled = false;
            }
            SimpleServerUtilities.LOGGER.error(
                    "Disabled cyclic loaded quest definitions: {}. Edit and save one definition to repair the chain.",
                    String.join(" -> ", cycle));
        }
    }

    private List<String> firstEnabledDependencyCycle() {
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        ArrayList<String> path = new ArrayList<>();
        LinkedHashSet<String> visiting = new LinkedHashSet<>();
        for (QuestDefinition definition : definitions.values()) {
            if (!definition.enabled || visited.contains(definition.id)) continue;
            List<String> cycle = findEnabledDependencyCycle(definition.id, visiting, visited, path);
            if (!cycle.isEmpty()) return cycle;
        }
        return List.of();
    }

    private List<String> findEnabledDependencyCycle(String id, Set<String> visiting,
                                                     Set<String> visited, List<String> path) {
        if (visiting.contains(id)) {
            int start = path.indexOf(id);
            if (start < 0) return List.of(id);
            return List.copyOf(path.subList(start, path.size()));
        }
        if (visited.contains(id)) return List.of();
        QuestDefinition definition = definitions.get(id);
        if (definition == null || !definition.enabled) {
            visited.add(id);
            return List.of();
        }
        visiting.add(id);
        path.add(id);
        for (String dependency : questReferences(definition.prerequisites)) {
            QuestDefinition target = definitions.get(dependency);
            if (target == null || !target.enabled) continue;
            List<String> cycle = findEnabledDependencyCycle(dependency, visiting, visited, path);
            if (!cycle.isEmpty()) return cycle;
        }
        path.remove(path.size() - 1);
        visiting.remove(id);
        visited.add(id);
        return List.of();
    }

    private void loadJournals() throws Exception {
        for (Path file : JsonStorage.listJsonFiles(journalFolder)) {
            try {
                UUID id = UUID.fromString(StoragePaths.fileBaseName(file));
                PlayerQuestJournal journal = JsonStorage.read(GSON, file, PlayerQuestJournal.class);
                if (journal == null) continue;
                journal.normalize(id);
                journals.put(journal.uuid(), journal);
            } catch (Exception exception) {
                Path archived = JsonStorage.archiveBrokenFile(file);
                SimpleServerUtilities.LOGGER.error("Failed to load player quest journal; archived as {}.", archived, exception);
            }
        }
    }

    public synchronized Collection<QuestDefinition> definitions() {
        ArrayList<QuestDefinition> values = new ArrayList<>(definitions.values());
        values.sort(Comparator.comparing((QuestDefinition value) -> value.category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(value -> value.title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(values);
    }

    public synchronized QuestDefinition definition(String rawId) {
        return definitions.get(ContentId.normalize(rawId));
    }

    public synchronized boolean saveDefinition(String rawOriginalId, QuestDefinition definition) {
        if (definition == null) return false;
        definition.normalize();
        validateDefinition(definition, true);
        String original = ContentId.normalize(rawOriginalId);
        if (!original.equals(definition.id) && definitions.containsKey(definition.id)) return false;
        if (!definitions.containsKey(original) && !definitions.containsKey(definition.id) && definitions.size() >= MAX_QUESTS) return false;
        if (!original.isBlank() && !original.equals(definition.id)) {
            String dependent = firstDependentQuest(original, original);
            if (!dependent.isBlank()) {
                throw new IllegalArgumentException("Quest '" + original + "' is still referenced by quest '" + dependent + "'. Update that prerequisite before renaming it.");
            }
        }
        LinkedHashMap<String, QuestDefinition> candidate = new LinkedHashMap<>(definitions);
        if (!original.isBlank()) candidate.remove(original);
        candidate.put(definition.id, definition);
        for (String reference : questReferences(definition.prerequisites)) {
            if (!candidate.containsKey(reference)) {
                throw new IllegalArgumentException("Quest prerequisite references missing quest: " + reference);
            }
        }
        validateDependencyCycles(candidate);
        String serialized = GSON.toJson(definition);
        if (serialized.length() > MAX_SERIALIZED_CHARACTERS) {
            throw new IllegalArgumentException("Quest exceeds " + MAX_SERIALIZED_CHARACTERS + " serialized characters.");
        }
        if (!original.isBlank() && !original.equals(definition.id)) {
            definitions.remove(original);
            for (PlayerQuestJournal journal : journals.values()) {
                QuestProgress progress = journal.quests.remove(original);
                if (progress != null) { progress.questId = definition.id; journal.quests.put(definition.id, progress); }
                if (original.equals(journal.trackedQuestId)) journal.trackedQuestId = definition.id;
            }
        }
        definitions.put(definition.id, definition);
        saveAll();
        return true;
    }

    public synchronized boolean deleteDefinition(String rawId) {
        String id = ContentId.normalize(rawId);
        if (!definitions.containsKey(id)) return false;
        String dependent = firstDependentQuest(id, id);
        if (!dependent.isBlank()) {
            throw new IllegalArgumentException("Quest '" + id + "' is still referenced by quest '" + dependent + "'. Remove that prerequisite first.");
        }
        definitions.remove(id);
        for (PlayerQuestJournal journal : journals.values()) {
            journal.quests.remove(id);
            if (id.equals(journal.trackedQuestId)) journal.trackedQuestId = "";
        }
        saveAll();
        return true;
    }

    public synchronized PlayerQuestJournal journal(UUID playerId) {
        return journals.computeIfAbsent(playerId, id -> {
            PlayerQuestJournal journal = new PlayerQuestJournal();
            journal.playerId = id.toString();
            return journal.normalize(id);
        });
    }

    public synchronized boolean hasCompleted(UUID playerId, String questId) {
        QuestProgress progress = journal(playerId).quests.get(ContentId.normalize(questId));
        return progress != null && progress.completionCount > 0;
    }

    public synchronized boolean isActive(UUID playerId, String questId) {
        QuestProgress progress = journal(playerId).quests.get(ContentId.normalize(questId));
        return progress != null && (progress.statusValue() == QuestStatus.ACTIVE
                || progress.statusValue() == QuestStatus.READY_TO_TURN_IN);
    }

    public synchronized boolean isReady(UUID playerId, String questId) {
        QuestProgress progress = journal(playerId).quests.get(ContentId.normalize(questId));
        return progress != null && progress.statusValue() == QuestStatus.READY_TO_TURN_IN;
    }

    public void handleRequest(QuestBookRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String source = normalizeSource(payload.source());
        String action = payload.action().trim().toLowerCase(Locale.ROOT);
        String notice = "";
        boolean error = false;
        try {
            if (!routeAllowed(player, source)) throw new IllegalArgumentException(routeDenial(source));
            notice = switch (action) {
                case "open", "refresh" -> "";
                case "start" -> start(player, payload.questId(), source);
                case "turn_in" -> turnIn(player, payload.questId(), source);
                case "abandon" -> abandon(player, payload.questId());
                case "track" -> track(player, payload.questId());
                case "untrack" -> track(player, "");
                case "delete" -> deleteAsAdmin(player, payload.questId());
                default -> throw new IllegalArgumentException("Unknown questbook action.");
            };
        } catch (RuntimeException exception) {
            error = true;
            notice = exception.getMessage() == null ? "The quest action failed safely." : exception.getMessage();
        }
        sendBook(player, source, notice, error, payload.page(), payload.requestId());
    }

    public void openFromMenu(ServerPlayer player) {
        if (!routeAllowed(player, "menu")) { player.sendSystemMessage(Component.literal(routeDenial("menu"))); return; }
        sendBook(player, "menu", "", false, 0, 0L);
    }

    public void openFromNpc(ServerPlayer player) {
        if (!routeAllowed(player, "npc")) { player.sendSystemMessage(Component.literal(routeDenial("npc"))); return; }
        sendBook(player, "npc", "", false, 0, 0L);
    }

    public synchronized String validateStart(ServerPlayer player, String rawQuestId, String source) {
        if (!routeAllowed(player, source)) return routeDenial(source);
        String id;
        try { id = ContentId.require(rawQuestId, "Quest ID"); }
        catch (RuntimeException exception) { return exception.getMessage(); }
        QuestDefinition definition = definitions.get(id);
        if (definition == null || !definition.enabled) return "Quest not found or disabled: " + id;
        Availability availability = availability(player, definition, journal(player.getUUID()).quests.get(id));
        return availability.available() ? "" : availability.reason();
    }

    public synchronized String validateTurnIn(ServerPlayer player, String rawQuestId, String source) {
        if (!routeAllowed(player, source)) return routeDenial(source);
        String id;
        try { id = ContentId.require(rawQuestId, "Quest ID"); }
        catch (RuntimeException exception) { return exception.getMessage(); }
        QuestProgress progress = journal(player.getUUID()).quests.get(id);
        return definitions.containsKey(id) && progress != null && progress.statusValue() == QuestStatus.READY_TO_TURN_IN
                ? "" : "That quest is not ready to turn in.";
    }

    public synchronized String start(ServerPlayer player, String rawQuestId, String source) {
        if (!routeAllowed(player, source)) throw new IllegalArgumentException(routeDenial(source));
        String id = ContentId.require(rawQuestId, "Quest ID");
        QuestDefinition definition = definitions.get(id);
        if (definition == null || !definition.enabled) throw new IllegalArgumentException("Quest not found or disabled: " + id);
        PlayerQuestJournal journal = journal(player.getUUID());
        QuestProgress previous = journal.quests.get(id);
        Availability availability = availability(player, definition, previous);
        if (!availability.available()) throw new IllegalArgumentException(availability.reason());

        QuestProgress progress = previous == null ? new QuestProgress() : previous;
        progress.questId = id;
        progress.setStatus(QuestStatus.ACTIVE);
        progress.objectiveProgress.clear();
        progress.startedAtEpochMilli = System.currentTimeMillis();
        progress.updatedAtEpochMilli = progress.startedAtEpochMilli;
        progress.completedAtEpochMilli = 0L;
        journal.quests.put(id, progress);
        saveJournal(journal);
        SimpleServerUtilities.CONTENT_EVENTS.publish(server, ContentEvent.player(
                ContentEventTypes.QUEST_STARTED, player.getUUID(), "quests", id, id, 1L, Map.of("source", source)));
        return "Quest started: " + definition.title;
    }

    public synchronized String abandon(ServerPlayer player, String rawQuestId) {
        if (!PermissionService.getBoolean(player, PermissionKeys.QUESTS_ABANDON, true)) {
            throw new IllegalArgumentException("You cannot abandon quests.");
        }
        String id = ContentId.require(rawQuestId, "Quest ID");
        QuestDefinition definition = definitions.get(id);
        QuestProgress progress = journal(player.getUUID()).quests.get(id);
        if (definition == null || progress == null || !isInProgress(progress)) throw new IllegalArgumentException("That quest is not active.");
        if (!definition.allowAbandon) throw new IllegalArgumentException("This quest cannot be abandoned.");
        progress.setStatus(QuestStatus.ABANDONED);
        progress.updatedAtEpochMilli = System.currentTimeMillis();
        if (id.equals(journal(player.getUUID()).trackedQuestId)) journal(player.getUUID()).trackedQuestId = "";
        saveJournal(journal(player.getUUID()));
        return "Quest abandoned: " + definition.title;
    }

    public synchronized String track(ServerPlayer player, String rawQuestId) {
        if (!PermissionService.getBoolean(player, PermissionKeys.QUESTS_TRACK, true)) {
            throw new IllegalArgumentException("You cannot track quests.");
        }
        PlayerQuestJournal journal = journal(player.getUUID());
        String id = ContentId.normalize(rawQuestId);
        if (id.isBlank()) {
            journal.trackedQuestId = "";
            saveJournal(journal);
            return "Quest tracking cleared.";
        }
        QuestProgress progress = journal.quests.get(id);
        if (progress == null || !isInProgress(progress)) throw new IllegalArgumentException("Only active quests can be tracked.");
        journal.trackedQuestId = id;
        saveJournal(journal);
        QuestDefinition definition = definitions.get(id);
        return "Tracking: " + (definition == null ? id : definition.title);
    }

    public synchronized String turnIn(ServerPlayer player, String rawQuestId, String source) {
        if (!routeAllowed(player, source)) throw new IllegalArgumentException(routeDenial(source));
        String id = ContentId.require(rawQuestId, "Quest ID");
        QuestDefinition definition = definitions.get(id);
        QuestProgress progress = journal(player.getUUID()).quests.get(id);
        if (definition == null || progress == null || progress.statusValue() != QuestStatus.READY_TO_TURN_IN) {
            throw new IllegalArgumentException("That quest is not ready to turn in.");
        }
        complete(player, definition, progress);
        return "Quest completed: " + definition.title;
    }

    private synchronized String deleteAsAdmin(ServerPlayer player, String rawQuestId) {
        if (!PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false)) {
            throw new IllegalArgumentException("Quest administrator permission is required.");
        }
        String id = ContentId.require(rawQuestId, "Quest ID");
        if (!deleteDefinition(id)) throw new IllegalArgumentException("Quest not found: " + id);
        QuestNpcBridge.rebuildManagedDialogues(this, SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS);
        SimpleServerUtilities.NPCS.syncAll();
        return "Quest deleted: " + id;
    }

    private void onContentEvent(ContentEvent event) {
        MinecraftServer activeServer;
        synchronized (this) { activeServer = server; }
        if (activeServer == null || event == null || event.playerId() == null) return;
        ServerPlayer player = activeServer.getPlayerList().getPlayer(event.playerId());
        if (player == null) return;
        synchronized (this) {
            PlayerQuestJournal journal = journal(player.getUUID());
            boolean changed = false;
            ArrayList<QuestDefinition> autoComplete = new ArrayList<>();
            for (QuestProgress progress : journal.quests.values()) {
                if (progress.statusValue() != QuestStatus.ACTIVE) continue;
                QuestDefinition definition = definitions.get(progress.questId);
                if (definition == null || !definition.enabled) continue;
                boolean questChanged = false;
                for (QuestObjectiveDefinition objective : definition.objectives) {
                    if (!objective.matches(event)) continue;
                    long before = progress.amount(objective.id);
                    long after;
                    try { after = Math.addExact(before, objective.increment(event)); }
                    catch (ArithmeticException ignored) { after = Long.MAX_VALUE; }
                    after = Math.min(objective.targetAmount, after);
                    if (after != before) {
                        progress.objectiveProgress.put(objective.id, after);
                        questChanged = true;
                    }
                }
                if (!questChanged) continue;
                changed = true;
                progress.updatedAtEpochMilli = System.currentTimeMillis();
                if (objectivesComplete(definition, progress)) {
                    if (definition.requireTurnIn) {
                        progress.setStatus(QuestStatus.READY_TO_TURN_IN);
                        player.sendSystemMessage(Component.literal("Quest ready to turn in: " + definition.title));
                    } else autoComplete.add(definition);
                }
            }
            if (changed) saveJournal(journal);
            for (QuestDefinition definition : autoComplete) {
                QuestProgress progress = journal.quests.get(definition.id);
                if (progress != null && progress.statusValue() == QuestStatus.ACTIVE) {
                    try { complete(player, definition, progress); }
                    catch (RuntimeException exception) {
                        progress.setStatus(QuestStatus.READY_TO_TURN_IN);
                        saveJournal(journal);
                        player.sendSystemMessage(Component.literal("Quest rewards could not be delivered yet: " + exception.getMessage()));
                    }
                }
            }
        }
    }

    private void complete(ServerPlayer player, QuestDefinition definition, QuestProgress progress) {
        int nextCompletion = progress.completionCount + 1;
        var result = SimpleServerUtilities.CONTENT_ACTIONS.execute(definition.rewards,
                new ContentActionContext(server, player, "quests", definition.id,
                        player.getUUID() + ":" + definition.id + ":completion:" + nextCompletion,
                        Map.of("quest", definition.id, "completion", Integer.toString(nextCompletion))));
        if (!result.successful()) {
            throw new IllegalArgumentException(result.error().isBlank() ? "Quest rewards failed." : result.error());
        }
        long now = System.currentTimeMillis();
        progress.setStatus(QuestStatus.COMPLETED);
        progress.completionCount = nextCompletion;
        progress.completedAtEpochMilli = now;
        progress.updatedAtEpochMilli = now;
        PlayerQuestJournal journal = journal(player.getUUID());
        if (definition.id.equals(journal.trackedQuestId)) journal.trackedQuestId = "";
        saveJournal(journal);
        SimpleServerUtilities.CONTENT_EVENTS.publish(server, ContentEvent.player(
                ContentEventTypes.QUEST_COMPLETED, player.getUUID(), "quests", definition.id,
                definition.id, 1L, Map.of("completion", Integer.toString(nextCompletion))));
        player.sendSystemMessage(Component.literal("Quest completed: " + definition.title));
    }

    private boolean objectivesComplete(QuestDefinition definition, QuestProgress progress) {
        for (QuestObjectiveDefinition objective : definition.objectives) {
            if (!objective.optional && progress.amount(objective.id) < objective.targetAmount) return false;
        }
        return true;
    }

    private boolean isInProgress(QuestProgress progress) {
        return progress.statusValue() == QuestStatus.ACTIVE || progress.statusValue() == QuestStatus.READY_TO_TURN_IN;
    }

    private Availability availability(ServerPlayer player, QuestDefinition definition, QuestProgress progress) {
        if (!definition.enabled) return new Availability(false, "This quest is disabled.", 0L);
        if (progress != null && isInProgress(progress)) return new Availability(false, "This quest is already active.", 0L);
        if (progress != null && progress.completionCount > 0 && !definition.repeatable) {
            return new Availability(false, "This quest has already been completed.", 0L);
        }
        long remaining = cooldownRemaining(definition, progress);
        if (remaining > 0L) return new Availability(false, "This quest is on cooldown for " + remaining + " seconds.", remaining);
        var condition = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(definition.prerequisites,
                new ContentConditionContext(server, player, "quests", definition.id, Map.of("quest", definition.id)));
        return condition.matched() ? new Availability(true, "", 0L) : new Availability(false, condition.reason(), 0L);
    }

    private long cooldownRemaining(QuestDefinition definition, QuestProgress progress) {
        if (!definition.repeatable || progress == null || progress.completedAtEpochMilli <= 0L || definition.cooldownSeconds <= 0L) return 0L;
        long readyAt;
        try { readyAt = Math.addExact(progress.completedAtEpochMilli, Math.multiplyExact(definition.cooldownSeconds, 1000L)); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE / 1000L; }
        return Math.max(0L, (readyAt - System.currentTimeMillis() + 999L) / 1000L);
    }

    private void sendBook(ServerPlayer player, String source, String notice, boolean error, int requestedPage, long requestId) {
        List<QuestBookDataPayload.QuestEntry> pageEntries;
        String tracked;
        int page;
        int totalPages;
        int totalQuests;
        synchronized (this) {
            PlayerQuestJournal journal = journal(player.getUUID());
            tracked = journal.trackedQuestId;
            ArrayList<QuestBookDataPayload.QuestEntry> built = new ArrayList<>();
            for (QuestDefinition definition : definitions()) {
                QuestProgress progress = journal.quests.get(definition.id);
                Availability availability = availability(player, definition, progress);
                boolean visible = definition.enabled && (!definition.hiddenUntilAvailable || availability.available()
                        || progress != null || PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false));
                if (!visible) continue;
                QuestStatus state = progress == null ? null : progress.statusValue();
                String status = state == null ? (availability.available() ? "available" : "locked") : state.serializedName();
                if (state == QuestStatus.COMPLETED && definition.repeatable && availability.available()) status = "available";
                ArrayList<QuestBookDataPayload.ObjectiveEntry> objectives = new ArrayList<>();
                for (QuestObjectiveDefinition objective : definition.objectives) {
                    long current = progress == null ? 0L : progress.amount(objective.id);
                    objectives.add(new QuestBookDataPayload.ObjectiveEntry(objective.id, objective.description,
                            current, objective.targetAmount, objective.optional, current >= objective.targetAmount));
                }
                boolean active = progress != null && isInProgress(progress);
                built.add(new QuestBookDataPayload.QuestEntry(
                        definition.id, definition.title, definition.category, definition.description, definition.iconItem,
                        status, availability.available(), availability.available(),
                        state == QuestStatus.READY_TO_TURN_IN, active && definition.allowAbandon,
                        definition.id.equals(tracked), definition.repeatable, availability.cooldownRemainingSeconds(),
                        progress == null ? 0 : progress.completionCount, objectives, rewardSummaries(definition.rewards)));
            }
            built.sort(Comparator.comparingInt((QuestBookDataPayload.QuestEntry entry) -> statusPriority(entry.status()))
                    .thenComparing(QuestBookDataPayload.QuestEntry::category, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(QuestBookDataPayload.QuestEntry::title, String.CASE_INSENSITIVE_ORDER));
            totalQuests = built.size();
            totalPages = Math.max(1, (totalQuests + QuestBookDataPayload.MAX_QUESTS - 1) / QuestBookDataPayload.MAX_QUESTS);
            page = Math.max(0, Math.min(totalPages - 1, requestedPage));
            int from = Math.min(totalQuests, page * QuestBookDataPayload.MAX_QUESTS);
            int to = Math.min(totalQuests, from + QuestBookDataPayload.MAX_QUESTS);
            pageEntries = List.copyOf(built.subList(from, to));
        }
        PacketDistributor.sendToPlayer(player, new QuestBookDataPayload(source, tracked, notice, error,
                PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false), requestId,
                page, totalPages, totalQuests, pageEntries));
    }

    private static int statusPriority(String status) {
        return switch (status) {
            case "ready_to_turn_in" -> 0;
            case "active" -> 1;
            case "available" -> 2;
            case "locked" -> 3;
            case "abandoned" -> 4;
            case "completed" -> 5;
            default -> 6;
        };
    }

    private List<String> rewardSummaries(List<ContentAction> rewards) {
        ArrayList<String> summaries = new ArrayList<>();
        for (ContentAction reward : rewards) {
            String summary = switch (reward.type()) {
                case "give_item" -> reward.parameter("count") + "× " + reward.parameter("item");
                case "give_money" -> {
                    try { yield SimpleServerUtilities.ECONOMY.format(Long.parseLong(reward.parameter("amount_minor"))); }
                    catch (RuntimeException ignored) { yield "Money reward"; }
                }
                case "grant_permission", "set_permission" -> "Permission: " + reward.parameter("permission");
                case "set_player_unlock" -> "Unlock: " + reward.parameter("key");
                case "add_reputation", "set_reputation" -> "Reputation: " + reward.parameter("faction") + " " + reward.parameter("amount");
                default -> reward.type();
            };
            summaries.add(summary);
        }
        return List.copyOf(summaries);
    }

    private boolean routeAllowed(ServerPlayer player, String source) {
        if (!Config.ENABLE_QUESTS.get() || !SimpleServerUtilities.CORE.modules().isActive("quests")) return false;
        return "npc".equals(normalizeSource(source))
                ? ContentAccessPolicy.questsAvailableFromNpc(player)
                : ContentAccessPolicy.questsAvailableFromMenu(player);
    }

    private String routeDenial(String source) {
        if (!Config.ENABLE_QUESTS.get()) return "The quest module is disabled.";
        if ("npc".equals(normalizeSource(source))) return "Quests are not configured for NPC access, or you lack quest/NPC permissions.";
        return "Quests are configured for NPC access, or you lack quest permission.";
    }

    private static String normalizeSource(String source) {
        return source != null && source.trim().equalsIgnoreCase("npc") ? "npc" : "menu";
    }

    public synchronized String toJson(QuestDefinition definition) {
        String json = GSON.toJson(definition == null ? new QuestDefinition().normalize() : definition);
        if (json.length() > MAX_SERIALIZED_CHARACTERS) throw new IllegalArgumentException("Quest exceeds the editor size limit.");
        return json;
    }

    public synchronized QuestDefinition fromJson(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("Quest data is empty.");
        if (json.length() > MAX_SERIALIZED_CHARACTERS) throw new IllegalArgumentException("Quest data exceeds the editor size limit.");
        QuestDefinition definition = GSON.fromJson(json, QuestDefinition.class);
        if (definition == null) throw new IllegalArgumentException("Quest data is invalid.");
        return definition.normalize();
    }

    private void validateDefinition(QuestDefinition definition, boolean requireKnownQuestReferences) {
        validateCondition(definition.prerequisites, definition.id, requireKnownQuestReferences);
        for (ContentAction reward : definition.rewards) {
            if (!SimpleServerUtilities.CONTENT_ACTIONS.isRegistered(reward.type())) {
                throw new IllegalArgumentException("Unknown quest reward action: " + reward.type());
            }
        }
    }

    private void validateCondition(ContentCondition condition, String owner, boolean requireKnownQuestReferences) {
        if (condition == null) return;
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered(condition.type())) {
            throw new IllegalArgumentException("Unknown quest prerequisite condition: " + condition.type());
        }
        if (condition.type().startsWith("quest_")) {
            String reference = ContentId.normalize(condition.parameter("quest"));
            if (reference.isBlank()) throw new IllegalArgumentException("Quest condition in '" + owner + "' is missing parameter quest.");
            if (reference.equals(owner)) throw new IllegalArgumentException("Quest '" + owner + "' cannot depend on itself.");
            if (requireKnownQuestReferences && !definitions.containsKey(reference)) {
                throw new IllegalArgumentException("Quest prerequisite references missing quest: " + reference);
            }
        }
        for (ContentCondition child : condition.children()) validateCondition(child, owner, requireKnownQuestReferences);
    }

    private void validateDependencyCycles(Map<String, QuestDefinition> candidates) {
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        for (String id : candidates.keySet()) visitQuest(id, candidates, new LinkedHashSet<>(), visited);
    }

    private void visitQuest(String id, Map<String, QuestDefinition> candidates,
                            Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Cyclic quest dependency involving: " + id);
        QuestDefinition definition = candidates.get(id);
        if (definition != null) {
            for (String dependency : questReferences(definition.prerequisites)) {
                if (candidates.containsKey(dependency)) visitQuest(dependency, candidates, visiting, visited);
            }
        }
        visiting.remove(id); visited.add(id);
    }

    private String firstDependentQuest(String referencedId, String ignoredOwner) {
        for (QuestDefinition candidate : definitions.values()) {
            if (candidate.id.equals(ignoredOwner)) continue;
            if (questReferences(candidate.prerequisites).contains(referencedId)) return candidate.id;
        }
        return "";
    }

    private Set<String> questReferences(ContentCondition condition) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectQuestReferences(condition, result);
        return result;
    }

    private void collectQuestReferences(ContentCondition condition, Set<String> result) {
        if (condition == null) return;
        if (condition.type().startsWith("quest_")) {
            String id = ContentId.normalize(condition.parameter("quest")); if (!id.isBlank()) result.add(id);
        }
        for (ContentCondition child : condition.children()) collectQuestReferences(child, result);
    }

    public synchronized void saveAll() {
        if (definitionFolder == null || journalFolder == null) return;
        Set<Path> definitionFiles = new LinkedHashSet<>();
        for (QuestDefinition definition : definitions.values()) {
            Path file = StoragePaths.jsonFile(definitionFolder, definition.id);
            definitionFiles.add(file.toAbsolutePath().normalize());
            definitionStore.queueJson(GSON, file, definition.normalize());
        }
        definitionStore.queueDeleteMissing(definitionFiles);
        Set<Path> journalFiles = new LinkedHashSet<>();
        for (PlayerQuestJournal journal : journals.values()) {
            Path file = StoragePaths.jsonFile(journalFolder, journal.playerId);
            journalFiles.add(file.toAbsolutePath().normalize());
            journalStore.queueJson(GSON, file, journal.normalize(journal.uuid()));
        }
        journalStore.queueDeleteMissing(journalFiles);
    }

    public synchronized void savePlayer(UUID playerId) {
        PlayerQuestJournal journal = journals.get(playerId); if (journal != null) saveJournal(journal);
    }

    private void saveJournal(PlayerQuestJournal journal) {
        if (journalFolder == null || journal == null) return;
        Path file = StoragePaths.jsonFile(journalFolder, journal.playerId);
        journalStore.queueJson(GSON, file, journal.normalize(journal.uuid()));
    }

    public synchronized void clear() {
        if (eventSubscription != null) { eventSubscription.close(); eventSubscription = null; }
        definitions.clear(); journals.clear(); definitionStore.reset(); journalStore.reset();
        server = null; definitionFolder = null; journalFolder = null;
    }

    public synchronized Snapshot snapshot() {
        int active = 0, ready = 0, completed = 0;
        for (PlayerQuestJournal journal : journals.values()) for (QuestProgress progress : journal.quests.values()) {
            if (progress.statusValue() == QuestStatus.ACTIVE) active++;
            else if (progress.statusValue() == QuestStatus.READY_TO_TURN_IN) ready++;
            completed += progress.completionCount;
        }
        return new Snapshot(definitions.size(), journals.size(), active, ready, completed);
    }

    public record Snapshot(int definitions, int journals, int active, int readyToTurnIn, int completions) {}
    private record Availability(boolean available, String reason, long cooldownRemainingSeconds) {}
}
