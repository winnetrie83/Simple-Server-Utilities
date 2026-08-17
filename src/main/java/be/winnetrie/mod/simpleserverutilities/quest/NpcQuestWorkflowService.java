package be.winnetrie.mod.simpleserverutilities.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.QuestAccessMode;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowUpdatePayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcEditorService;
import be.winnetrie.mod.simpleserverutilities.npc.NpcInstance;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative simple NPC quest workflow. */
public final class NpcQuestWorkflowService {
    private NpcQuestWorkflowService() {}

    public static void handleRequest(NpcQuestWorkflowRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canAdmin(player)) return;
        if (!SsuModuleAccess.active("npcs")) {
            player.sendSystemMessage(Component.literal("NPCs is disabled; NPC quest workflow is unavailable."), true);
            return;
        }
        sendOpen(player, payload.instanceId(), "");
    }

    public static void handleUpdate(NpcQuestWorkflowUpdatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canAdmin(player)) return;
        if (!SsuModuleAccess.active("npcs")) {
            player.sendSystemMessage(Component.literal("NPCs is disabled; NPC quest workflow is unavailable."), true);
            return;
        }
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(payload.instanceId());
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (instance == null || npc == null) { player.sendSystemMessage(Component.literal("The NPC no longer exists."), true); return; }
        try {
            String action = payload.action().trim().toLowerCase(Locale.ROOT);
            if ("create".equals(action)) {
                QuestDefinition quest = new QuestDefinition();
                quest.giverNpcInstanceId = instance.id;
                quest.turnInNpcInstanceId = instance.id;
                quest.requireTurnIn = true;
                quest.title = "New quest for " + npc.displayName;
                quest.normalize();
                ensureNpcAccess(payload.requestedAccessMode());
                PacketDistributor.sendToPlayer(player, new QuestEditorOpenPayload("", SimpleServerUtilities.QUESTS.toJson(quest), npcChoices(), questChoices(),
                        QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get()).serializedName(), payload.requestId()));
                return;
            }
            if ("access".equals(action)) {
                String mode = normalizeAccess(payload.requestedAccessMode());
                if (("npc".equals(mode) || "both".equals(mode)) && !SsuModuleAccess.active("npcs"))
                    throw new IllegalArgumentException("NPC quest access requires the active NPC module.");
                Config.QUEST_ACCESS_MODE.set(mode); Config.QUEST_ACCESS_MODE.save();
                sendOpen(player, instance.id, "Quest access changed to " + accessLabel(mode) + ".");
                return;
            }
            if (!"save".equals(action)) throw new IllegalArgumentException("Unknown NPC quest workflow action.");
            QuestDefinition quest = SimpleServerUtilities.QUESTS.definition(payload.questId());
            if (quest == null) throw new IllegalArgumentException("Quest not found: " + payload.questId());
            quest = quest.copy();
            String relation = normalizeRelation(payload.relation());
            boolean offer = "offer".equals(relation) || "both".equals(relation);
            boolean turnIn = "turnin".equals(relation) || "both".equals(relation);
            if (offer) quest.giverNpcInstanceId = instance.id; else if (instance.id.equals(quest.giverNpcInstanceId)) quest.giverNpcInstanceId = "";
            if (turnIn) { quest.turnInNpcInstanceId = instance.id; quest.requireTurnIn = true; }
            else if (instance.id.equals(quest.turnInNpcInstanceId)) quest.turnInNpcInstanceId = "";
            quest.npcAvailableText = payload.availableText(); quest.npcAcceptText = payload.acceptText(); quest.npcActiveText = payload.activeText(); quest.npcReadyText = payload.readyText(); quest.npcTurnInText = payload.turnInText(); quest.npcCompletedText = payload.completedText();
            quest.npcShowAvailableMarker = payload.showAvailable(); quest.npcShowActiveMarker = payload.showActive(); quest.npcShowReadyMarker = payload.showReady();
            String accessChanged = "";
            if (!"none".equals(relation) && QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get()) == QuestAccessMode.MENU) {
                String requested = payload.requestedAccessMode() == null ? "" : payload.requestedAccessMode().trim().toLowerCase(Locale.ROOT);
                if (!"npc".equals(requested) && !"both".equals(requested)) {
                    throw new IllegalArgumentException("Choose NPCs or Both before linking this quest.");
                }
                Config.QUEST_ACCESS_MODE.set(requested); Config.QUEST_ACCESS_MODE.save();
                accessChanged = " NPC quest access was enabled as " + accessLabel(requested) + ".";
            }
            QuestNpcBridge.validateSimpleLinkCapacity(SimpleServerUtilities.QUESTS, quest);
            if (!SimpleServerUtilities.QUESTS.saveDefinition(quest.id, quest)) throw new IllegalArgumentException("The quest could not be saved.");
            QuestNpcBridge.rebuildManagedDialogues(SimpleServerUtilities.QUESTS, SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS);
            SimpleServerUtilities.NPCS.syncAll();
            sendOpen(player, instance.id, ("none".equals(relation) ? "Quest unlinked from " : "Quest link saved for ") + npc.displayName + "." + accessChanged);
        } catch (RuntimeException exception) {
            sendOpen(player, payload.instanceId(), exception.getMessage() == null ? "NPC quest update failed." : exception.getMessage());
        }
    }

    private static void ensureNpcAccess(String requested) {
        if (QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get()) != QuestAccessMode.MENU) return;
        String mode = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
        if (!"npc".equals(mode) && !"both".equals(mode)) {
            throw new IllegalArgumentException("Choose NPCs or Both before creating an NPC-linked quest.");
        }
        Config.QUEST_ACCESS_MODE.set(mode); Config.QUEST_ACCESS_MODE.save();
    }

    private static void sendOpen(ServerPlayer player, String instanceId, String notice) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(instanceId);
        NpcDefinition npc = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (instance == null || npc == null) return;
        ArrayList<NpcQuestWorkflowOpenPayload.Entry> entries = new ArrayList<>();
        for (QuestDefinition quest : SimpleServerUtilities.QUESTS.definitions()) {
            entries.add(new NpcQuestWorkflowOpenPayload.Entry(quest.id, quest.title, relation(instance.id, quest),
                    quest.npcAvailableText, quest.npcAcceptText, quest.npcActiveText, quest.npcReadyText, quest.npcTurnInText, quest.npcCompletedText,
                    quest.npcShowAvailableMarker, quest.npcShowActiveMarker, quest.npcShowReadyMarker));
        }
        entries.sort(Comparator.comparing((NpcQuestWorkflowOpenPayload.Entry e) -> "none".equals(e.relation()))
                .thenComparing(NpcQuestWorkflowOpenPayload.Entry::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NpcQuestWorkflowOpenPayload.Entry::questId));
        PacketDistributor.sendToPlayer(player, new NpcQuestWorkflowOpenPayload(instance.id, npc.displayName,
                QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get()).serializedName(), notice, List.copyOf(entries)));
    }

    private static String relation(String instanceId, QuestDefinition q) {
        boolean offer = instanceId.equals(q.giverNpcInstanceId), turnIn = instanceId.equals(q.turnInNpcInstanceId);
        return offer && turnIn ? "both" : offer ? "offer" : turnIn ? "turnin" : "none";
    }
    private static String normalizeRelation(String value) { String v=value==null?"":value.trim().toLowerCase(Locale.ROOT); return switch(v){case "offer","turnin","both"->v;default->"none";}; }
    private static String normalizeAccess(String value) { String v=value==null?"":value.trim().toLowerCase(Locale.ROOT); return switch(v){case "npc","both"->v;default->"menu";}; }
    private static String accessLabel(String mode){return switch(mode){case "npc"->"NPCs only";case "both"->"Both";default->"Quest Menu only";};}

    public static List<QuestEditorOpenPayload.NpcChoice> npcChoices() {
        if (!SsuModuleAccess.active("npcs")) return List.of();
        ArrayList<QuestEditorOpenPayload.NpcChoice> result = new ArrayList<>();
        for (NpcInstance instance : SimpleServerUtilities.NPCS.instances()) {
            NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
            String name = definition == null || definition.displayName == null || definition.displayName.isBlank() ? instance.definitionId : definition.displayName;
            String dimension = instance.dimension == null ? "" : instance.dimension.replace("minecraft:", "");
            result.add(new QuestEditorOpenPayload.NpcChoice(instance.id, name + " — " + dimension + " @ " + (int)Math.floor(instance.x) + ", " + (int)Math.floor(instance.y) + ", " + (int)Math.floor(instance.z)));
        }
        result.sort(Comparator.comparing(QuestEditorOpenPayload.NpcChoice::label, String.CASE_INSENSITIVE_ORDER).thenComparing(QuestEditorOpenPayload.NpcChoice::instanceId));
        return List.copyOf(result);
    }


    public static List<QuestEditorOpenPayload.QuestChoice> questChoices() {
        ArrayList<QuestEditorOpenPayload.QuestChoice> result = new ArrayList<>();
        for (QuestDefinition quest : SimpleServerUtilities.QUESTS.definitions()) {
            result.add(new QuestEditorOpenPayload.QuestChoice(quest.id, quest.title));
        }
        result.sort(Comparator.comparing(QuestEditorOpenPayload.QuestChoice::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(QuestEditorOpenPayload.QuestChoice::questId));
        return List.copyOf(result);
    }

    private static boolean canAdmin(ServerPlayer player) {
        return player != null && SsuModuleAccess.active("npcs") && SsuModuleAccess.active("quests")
                && NpcEditorService.canAdmin(player)
                && PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false);
    }
}
