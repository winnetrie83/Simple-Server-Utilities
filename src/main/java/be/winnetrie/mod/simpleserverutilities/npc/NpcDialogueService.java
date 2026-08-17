package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentActionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionContext;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionResult;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueChoicePayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueViewPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative dialogue graph runtime, sessions and admin editor bridge. */
public final class NpcDialogueService {
    private static final long SESSION_TICKS = 20L * 60L * 5L;
    private static final double MAX_DISTANCE_SQUARED = 100.0D;

    private final NpcDialogueManager dialogues;
    private final NpcServiceRegistry services;
    private final Map<UUID, NpcDialogueSession> sessions = new LinkedHashMap<>();
    private long nextSessionCleanupTick;

    public NpcDialogueService(NpcDialogueManager dialogues, NpcServiceRegistry services) {
        this.dialogues = dialogues;
        this.services = services;
    }

    public boolean open(ServerPlayer player, NpcInstance instance) {
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        return npc != null && open(player, instance, npc.dialogueId);
    }

    /** Opens a specific stored dialogue for this NPC without changing its normal advanced-dialogue link. */
    public boolean open(ServerPlayer player, NpcInstance instance, String dialogueId) {
        if (player == null || instance == null || !canUseDialogue(player)) return false;
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (npc == null || dialogueId == null || dialogueId.isBlank()) return false;
        NpcDialogueDefinition dialogue = dialogues.get(dialogueId);
        if (dialogue == null || !dialogue.enabled) return false;
        NpcDialogueNode start = resolveAvailableNode(player, npc, instance, dialogue, dialogue.startNode);
        if (start == null) return false;
        UUID sessionId = UUID.randomUUID();
        long expiry = player.level().getServer().getTickCount() + SESSION_TICKS;
        NpcDialogueSession session = new NpcDialogueSession(
                sessionId, player.getUUID(), instance.uuid(), dialogue.id, start.id, expiry, 0L, 0L);
        synchronized (sessions) {
            sessions.values().removeIf(existing -> player.getUUID().equals(existing.playerId()));
            sessions.put(sessionId, session);
        }
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(
                ContentEventTypes.DIALOGUE_OPENED, player.getUUID(), "npcs", instance.id,
                dialogue.id, 1L, variables(npc, instance, dialogue, start, null)));
        return enterAndSend(player, session, npc, instance, dialogue, start, "", false);
    }

    public static void handleChoice(NpcDialogueChoicePayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        SimpleServerUtilities.NPC_DIALOGUES.handleChoice(player, payload);
    }

    private void handleChoice(ServerPlayer player, NpcDialogueChoicePayload payload) {
        UUID sessionId;
        try { sessionId = UUID.fromString(payload.sessionId()); }
        catch (IllegalArgumentException exception) { return; }
        NpcDialogueSession session;
        synchronized (sessions) { session = sessions.get(sessionId); }
        if (session == null || !session.playerId().equals(player.getUUID())) return;
        if (payload.requestId() <= session.lastRequestId()) return;
        if (payload.choiceId().isBlank()) {
            synchronized (sessions) { sessions.remove(sessionId); }
            return;
        }
        if (player.level().getServer().getTickCount() > session.expiresAtTick()) {
            close(player, sessionId, "The dialogue session expired.", true);
            return;
        }
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(session.npcInstanceId());
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        NpcDialogueDefinition dialogue = dialogues.get(session.dialogueId());
        NpcDialogueNode node = dialogue == null ? null : dialogue.node(session.currentNode());
        if (instance == null || npc == null || dialogue == null || node == null || !near(player, instance)) {
            close(player, sessionId, "The NPC is no longer available.", true);
            return;
        }
        NpcDialogueNode resolvedCurrent = resolveAvailableNode(player, npc, instance, dialogue, node.id);
        if (resolvedCurrent == null) {
            close(player, sessionId, "This dialogue route is no longer available.", false);
            return;
        }
        if (!resolvedCurrent.id.equals(node.id)) {
            NpcDialogueSession rerouted = session.advance(resolvedCurrent.id,
                    player.level().getServer().getTickCount() + SESSION_TICKS, payload.requestId());
            synchronized (sessions) { sessions.put(sessionId, rerouted); }
            enterAndSend(player, rerouted, npc, instance, dialogue, resolvedCurrent, "", false);
            return;
        }
        NpcDialogueChoice choice = null;
        for (NpcDialogueChoice candidate : node.choices) if (candidate.id.equals(payload.choiceId())) { choice = candidate; break; }
        if (choice == null) {
            sendNode(player, session, npc, instance, dialogue, node, "That choice is no longer available.", true);
            return;
        }
        ContentConditionResult condition = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(choice.condition,
                conditionContext(player, npc, instance, dialogue, node, choice));
        if (!condition.matched()) {
            sendNode(player, session, npc, instance, dialogue, node,
                    condition.reason().isBlank() ? "That choice is locked." : condition.reason(), true);
            return;
        }
        NpcServiceRegistry.ServiceResult preflight = services.validate(
                player, instance, npc, choice.service, choice.serviceTarget);
        if (!preflight.successful()) {
            sendNode(player, session, npc, instance, dialogue, node, preflight.message(), true);
            return;
        }
        var actionResult = SimpleServerUtilities.CONTENT_ACTIONS.execute(choice.actions,
                actionContext(player, session, npc, instance, dialogue, node, choice, "choice"));
        if (!actionResult.successful()) {
            sendNode(player, session, npc, instance, dialogue, node,
                    actionResult.error().isBlank() ? "The dialogue action failed safely." : actionResult.error(), true);
            return;
        }
        NpcServiceRegistry.ServiceResult serviceResult = services.execute(player, instance, npc, choice.service, choice.serviceTarget);
        if (!serviceResult.successful()) {
            sendNode(player, session, npc, instance, dialogue, node, serviceResult.message(), true);
            return;
        }
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(
                ContentEventTypes.DIALOGUE_CHOICE, player.getUUID(), "npcs", instance.id,
                choice.id, 1L, variables(npc, instance, dialogue, node, choice)));
        if (!choice.service.isBlank()) {
            SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(
                    ContentEventTypes.NPC_SERVICE_USED, player.getUUID(), "npcs", instance.id,
                    choice.service, 1L, variables(npc, instance, dialogue, node, choice)));
        }
        if (choice.closeDialogue || serviceResult.closeDialogue()) {
            close(player, sessionId, serviceResult.message(), false);
            return;
        }
        if (!choice.nextNode.isBlank()) {
            NpcDialogueNode next = resolveAvailableNode(player, npc, instance, dialogue, choice.nextNode);
            if (next == null) {
                close(player, sessionId, "That dialogue route is not available for you right now.", false);
                return;
            }
            NpcDialogueSession advanced = session.advance(next.id,
                    player.level().getServer().getTickCount() + SESSION_TICKS, payload.requestId());
            synchronized (sessions) { sessions.put(sessionId, advanced); }
            enterAndSend(player, advanced, npc, instance, dialogue, next, serviceResult.message(), false);
            return;
        }
        NpcDialogueNode continuedNode = resolveAvailableNode(player, npc, instance, dialogue, node.id);
        if (continuedNode == null) {
            close(player, sessionId, serviceResult.message(), false);
            return;
        }
        NpcDialogueSession continued = session.advance(continuedNode.id,
                player.level().getServer().getTickCount() + SESSION_TICKS, payload.requestId());
        synchronized (sessions) { sessions.put(sessionId, continued); }
        if (!continuedNode.id.equals(node.id)) {
            enterAndSend(player, continued, npc, instance, dialogue, continuedNode, serviceResult.message(), false);
        } else {
            sendNode(player, continued, npc, instance, dialogue, continuedNode, serviceResult.message(), false);
        }
    }

    /** Resolves player-specific node gates without executing actions. Fallback chains are bounded and cycle-safe. */
    private static NpcDialogueNode resolveAvailableNode(ServerPlayer player, NpcDefinition npc, NpcInstance instance,
                                                        NpcDialogueDefinition dialogue, String requestedNode) {
        NpcDialogueNode current = dialogue == null ? null : dialogue.node(requestedNode);
        java.util.LinkedHashSet<String> visited = new java.util.LinkedHashSet<>();
        while (current != null && visited.add(current.id)) {
            ContentConditionResult result = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(current.condition,
                    conditionContext(player, npc, instance, dialogue, current, null));
            if (result.matched()) return current;
            if (current.fallbackNode == null || current.fallbackNode.isBlank()) return null;
            current = dialogue.node(current.fallbackNode);
        }
        return null;
    }

    private boolean enterAndSend(ServerPlayer player, NpcDialogueSession session, NpcDefinition npc,
                                 NpcInstance instance, NpcDialogueDefinition dialogue, NpcDialogueNode node,
                                 String notice, boolean error) {
        var result = SimpleServerUtilities.CONTENT_ACTIONS.execute(node.enterActions,
                actionContext(player, session, npc, instance, dialogue, node, null, "enter"));
        if (!result.successful()) {
            close(player, session.sessionId(), result.error().isBlank() ? "Dialogue entry failed safely." : result.error(), true);
            return false;
        }
        sendNode(player, session, npc, instance, dialogue, node, notice, error);
        return true;
    }

    private void sendNode(ServerPlayer player, NpcDialogueSession session, NpcDefinition npc, NpcInstance instance,
                          NpcDialogueDefinition dialogue, NpcDialogueNode node, String notice, boolean error) {
        ArrayList<NpcDialogueViewPayload.ChoiceEntry> entries = new ArrayList<>();
        for (NpcDialogueChoice choice : node.choices) {
            ContentConditionResult result = SimpleServerUtilities.CONTENT_CONDITIONS.evaluate(choice.condition,
                    conditionContext(player, npc, instance, dialogue, node, choice));
            if (!result.matched() && choice.hiddenWhenLocked) continue;
            entries.add(new NpcDialogueViewPayload.ChoiceEntry(choice.id, choice.text, result.matched(),
                    result.matched() ? "" : result.reason()));
        }
        PacketDistributor.sendToPlayer(player, new NpcDialogueViewPayload(
                false, session.sessionId().toString(), instance.id, dialogue.id, node.id,
                npc.displayName, node.speaker.isBlank() ? npc.displayName : node.speaker,
                node.text, notice, error, entries));
    }

    private void close(ServerPlayer player, UUID sessionId, String message, boolean error) {
        synchronized (sessions) { sessions.remove(sessionId); }
        PacketDistributor.sendToPlayer(player, NpcDialogueViewPayload.closed(sessionId.toString(), message, error));
        if (message != null && !message.isBlank()) player.sendSystemMessage(Component.literal(message), true);
    }

    public void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now < nextSessionCleanupTick) return;
        nextSessionCleanupTick = now + 20L;
        synchronized (sessions) {
            sessions.values().removeIf(session -> now > session.expiresAtTick());
        }
    }

    public void forget(UUID playerId) {
        if (playerId == null) return;
        synchronized (sessions) { sessions.values().removeIf(session -> playerId.equals(session.playerId())); }
    }

    public void clear() {
        synchronized (sessions) { sessions.clear(); }
        nextSessionCleanupTick = 0L;
    }

    public static void handleEditorRequest(NpcDialogueEditorRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!NpcEditorService.canAdmin(player)) return;
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(payload.instanceId());
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (instance == null || npc == null) return;
        String dialogueId = npc.dialogueId.isBlank() ? npc.id + "_dialogue" : npc.dialogueId;
        NpcDialogueDefinition dialogue = SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.get(dialogueId);
        if (dialogue == null) dialogue = NpcDialogueDefinition.simple(dialogueId, npc.displayName, "...");
        try {
            PacketDistributor.sendToPlayer(player, new NpcDialogueEditorOpenPayload(
                    instance.id, npc.dialogueId, SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.toJson(dialogue),
                    SimpleServerUtilities.CONTENT_CONDITIONS.registeredTypes(),
                    SimpleServerUtilities.CONTENT_ACTIONS.registeredTypes(),
                    SimpleServerUtilities.NPC_SERVICES.serviceIds(),
                    editorTargets()));
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage() == null
                    ? "The dialogue cannot be opened safely." : exception.getMessage()), true);
        }
    }


    private static List<NpcDialogueEditorOpenPayload.TargetEntry> editorTargets() {
        ArrayList<NpcDialogueEditorOpenPayload.TargetEntry> values = new ArrayList<>();
        if (SsuModuleAccess.active("warps")) {
            for (var warp : SimpleServerUtilities.WARPS.getWarps()) {
                values.add(new NpcDialogueEditorOpenPayload.TargetEntry("warp", warp.getName(), warp.getDisplayName()));
            }
        }
        if (SsuModuleAccess.active("quests")) {
            for (var quest : SimpleServerUtilities.QUESTS.definitions()) {
                String label = quest.title == null || quest.title.isBlank() ? quest.id : quest.title;
                values.add(new NpcDialogueEditorOpenPayload.TargetEntry("quest_offer", quest.id, label));
                values.add(new NpcDialogueEditorOpenPayload.TargetEntry("quest_turn_in", quest.id, label));
            }
        }
        if (SsuModuleAccess.active("minigames")) {
            for (var minigame : SimpleServerUtilities.MINIGAMES.definitions()) {
                String label = minigame.displayName == null || minigame.displayName.isBlank() ? minigame.id : minigame.displayName;
                values.add(new NpcDialogueEditorOpenPayload.TargetEntry("minigame_queue", minigame.id, label));
            }
        }
        if (SsuModuleAccess.active("dungeons")) {
            for (var dungeon : SimpleServerUtilities.DUNGEONS.definitions()) {
                String label = dungeon.displayName == null || dungeon.displayName.isBlank() ? dungeon.id : dungeon.displayName;
                values.add(new NpcDialogueEditorOpenPayload.TargetEntry("dungeon_queue", dungeon.id, label));
            }
        }
        if (SsuModuleAccess.active("npc_shops")) {
            for (var shop : SimpleServerUtilities.NPC_SHOPS.definitions()) {
                String label = shop.displayName == null || shop.displayName.isBlank() ? shop.id : shop.displayName;
                values.add(new NpcDialogueEditorOpenPayload.TargetEntry("shop", shop.id, label));
            }
        }
        values.sort(java.util.Comparator.comparing(NpcDialogueEditorOpenPayload.TargetEntry::serviceId)
                .thenComparing(NpcDialogueEditorOpenPayload.TargetEntry::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NpcDialogueEditorOpenPayload.TargetEntry::targetId));
        return List.copyOf(values);
    }

    private static Map<String, List<String>> editorTargetMap() {
        LinkedHashMap<String, List<String>> mutable = new LinkedHashMap<>();
        for (NpcDialogueEditorOpenPayload.TargetEntry target : editorTargets()) {
            mutable.computeIfAbsent(target.serviceId(), ignored -> new ArrayList<>()).add(target.targetId());
        }
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : mutable.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    public static void handleEditorSubmit(NpcDialogueEditorSubmitPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("npcs")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        long requestId = payload.requestId();
        if (!NpcEditorService.canAdmin(player)) {
            sendEditorResult(player, false, "NPC dialogue administration is not allowed.", "", requestId); return;
        }
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(payload.instanceId());
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (instance == null || npc == null) {
            sendEditorResult(player, false, "The NPC placement no longer exists.", "", requestId); return;
        }
        if (!npc.dialogueId.equals(payload.originalDialogueId())) {
            sendEditorResult(player, false,
                    "The NPC dialogue link changed while this editor was open. Reopen the dialogue editor.",
                    "", requestId);
            return;
        }
        try {
            NpcDialogueDefinition dialogue = SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.fromJson(payload.dialogueJson());
            NpcDialogueValidation.Report validation = NpcDialogueValidation.validate(
                    dialogue,
                    SimpleServerUtilities.CONTENT_CONDITIONS.registeredTypes(),
                    SimpleServerUtilities.CONTENT_ACTIONS.registeredTypes(),
                    SimpleServerUtilities.NPC_SERVICES.serviceIds(),
                    editorTargetMap());
            if (!validation.valid()) {
                String first = validation.issues().stream()
                        .filter(issue -> issue.severity() == NpcDialogueValidation.Severity.ERROR)
                        .findFirst().map(issue -> issue.location() + ": " + issue.message())
                        .orElse(validation.summary());
                throw new IllegalArgumentException("Dialogue validation failed: " + first);
            }
            NpcDialogueDefinition existing = SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.get(dialogue.id);
            if (existing != null && !dialogue.id.equals(payload.originalDialogueId()) && !dialogue.id.equals(npc.dialogueId)) {
                sendEditorResult(player, false, "A different dialogue with ID '" + dialogue.id + "' already exists.", "", requestId); return;
            }
            for (NpcDialogueNode node : dialogue.nodes) {
                validateConditionHandlers(node.condition, "node '" + node.id + "'");
                for (NpcDialogueChoice choice : node.choices) {
                    if (!choice.service.isBlank() && !SimpleServerUtilities.NPC_SERVICES.isRegistered(choice.service)) {
                        throw new IllegalArgumentException("Unknown service '" + choice.service + "' in node '" + node.id + "'.");
                    }
                    for (var action : choice.actions) {
                        if (!SimpleServerUtilities.CONTENT_ACTIONS.isRegistered(action.type())) {
                            throw new IllegalArgumentException("Unknown action '" + action.type() + "' in choice '" + choice.id + "'.");
                        }
                    }
                    validateConditionHandlers(choice.condition, "choice '" + choice.id + "'");
                }
                for (var action : node.enterActions) {
                    if (!SimpleServerUtilities.CONTENT_ACTIONS.isRegistered(action.type())) {
                        throw new IllegalArgumentException("Unknown entry action '" + action.type() + "' in node '" + node.id + "'.");
                    }
                }
            }
            if (!SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.save(dialogue)) {
                sendEditorResult(player, false, "The dialogue library limit was reached.", "", requestId); return;
            }
            String previousDialogueId = npc.dialogueId;
            npc.dialogueId = dialogue.id;
            if (!SimpleServerUtilities.NPCS.saveDefinition(npc.id, npc)) {
                sendEditorResult(player, false, "The dialogue was saved, but the NPC link could not be updated.", "", requestId); return;
            }
            if (!previousDialogueId.isBlank() && !previousDialogueId.equals(dialogue.id)) {
                boolean stillReferenced = SimpleServerUtilities.NPCS.definitions().stream()
                        .anyMatch(definition -> previousDialogueId.equals(definition.dialogueId));
                SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS.delete(previousDialogueId, stillReferenced);
            }
            sendEditorResult(player, true, "Dialogue '" + dialogue.displayName + "' saved and linked to " + npc.displayName + ".", dialogue.id, requestId);
        } catch (RuntimeException exception) {
            sendEditorResult(player, false, exception.getMessage() == null ? "Dialogue validation failed." : exception.getMessage(), "", requestId);
        }
    }


    private static void validateConditionHandlers(
            be.winnetrie.mod.simpleserverutilities.content.ContentCondition condition, String location) {
        if (condition == null) return;
        if (!SimpleServerUtilities.CONTENT_CONDITIONS.isRegistered(condition.type())) {
            throw new IllegalArgumentException("Unknown condition '" + condition.type() + "' in " + location + ".");
        }
        if ("not".equals(condition.type()) && condition.children().size() != 1) {
            throw new IllegalArgumentException("Condition 'not' requires exactly one child in " + location + ".");
        }
        for (var child : condition.children()) validateConditionHandlers(child, location);
    }

    private static void sendEditorResult(ServerPlayer player, boolean success, String message, String dialogueId, long requestId) {
        PacketDistributor.sendToPlayer(player, new NpcDialogueEditorResultPayload(success, message, dialogueId, requestId));
    }

    private static boolean canUseDialogue(ServerPlayer player) {
        return ContentAccessPolicy.canUseNpcDialogue(player);
    }

    private static boolean near(ServerPlayer player, NpcInstance instance) {
        return player.level().dimension().location().toString().equals(instance.dimension)
                && player.distanceToSqr(instance.x, instance.y, instance.z) <= MAX_DISTANCE_SQUARED;
    }

    private static ContentConditionContext conditionContext(ServerPlayer player, NpcDefinition npc,
            NpcInstance instance, NpcDialogueDefinition dialogue, NpcDialogueNode node, NpcDialogueChoice choice) {
        return new ContentConditionContext(player.level().getServer(), player, "npcs", instance.id,
                variables(npc, instance, dialogue, node, choice));
    }

    private static ContentActionContext actionContext(ServerPlayer player, NpcDialogueSession session,
            NpcDefinition npc, NpcInstance instance, NpcDialogueDefinition dialogue, NpcDialogueNode node,
            NpcDialogueChoice choice, String phase) {
        String choiceId = choice == null ? "node" : choice.id;
        return new ContentActionContext(player.level().getServer(), player, "npcs", dialogue.id,
                session.sessionId() + ":" + session.sequence() + ":" + node.id + ":" + choiceId + ":" + phase,
                variables(npc, instance, dialogue, node, choice));
    }

    private static Map<String, String> variables(NpcDefinition npc, NpcInstance instance,
            NpcDialogueDefinition dialogue, NpcDialogueNode node, NpcDialogueChoice choice) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("npc_definition", npc.id); values.put("npc_instance", instance.id);
        values.put("dialogue", dialogue.id); values.put("dialogue_node", node.id);
        if (choice != null) { values.put("dialogue_choice", choice.id); values.put("service", choice.service); }
        return Map.copyOf(values);
    }
}
