package be.winnetrie.mod.simpleserverutilities.quest;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.content.QuestAccessMode;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative quest-definition editor bridge. */
public final class QuestEditorService {
    private QuestEditorService() {}

    public static void handleRequest(QuestEditorRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("quests")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canAdmin(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Quest administrator permission is required."));
            return;
        }
        QuestDefinition definition = payload.questId().isBlank()
                ? new QuestDefinition().normalize() : SimpleServerUtilities.QUESTS.definition(payload.questId());
        if (definition == null) definition = new QuestDefinition().normalize();
        PacketDistributor.sendToPlayer(player, new QuestEditorOpenPayload(
                payload.questId(), SimpleServerUtilities.QUESTS.toJson(definition.copy()), NpcQuestWorkflowService.npcChoices(), NpcQuestWorkflowService.questChoices(),
                QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get()).serializedName(), payload.requestId()));
    }

    public static void handleSubmit(QuestEditorSubmitPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("quests")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canAdmin(player)) {
            send(player, false, "Quest administrator permission is required.", "", payload.requestId());
            return;
        }
        try {
            QuestDefinition definition = SimpleServerUtilities.QUESTS.fromJson(payload.questJson());
            if (!definition.turnInNpcInstanceId.isBlank()) definition.requireTurnIn = true;
            if ((!definition.giverNpcInstanceId.isBlank() || !definition.turnInNpcInstanceId.isBlank())
                    && QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get()) == QuestAccessMode.MENU) {
                String requested = payload.requestedAccessMode() == null ? "" : payload.requestedAccessMode().trim().toLowerCase(java.util.Locale.ROOT);
                if (!"npc".equals(requested) && !"both".equals(requested)) {
                    send(player, false, "NPC quest links require NPC or Both quest access.", "", payload.requestId());
                    return;
                }
                if (("npc".equals(requested) || "both".equals(requested)) && !SsuModuleAccess.active("npcs")) {
                    send(player, false, "NPC quest access requires the NPC module.", "", payload.requestId());
                    return;
                }
                Config.QUEST_ACCESS_MODE.set(requested);
                Config.QUEST_ACCESS_MODE.save();
            }
            boolean npcActive = SsuModuleAccess.active("npcs");
            if ((!definition.giverNpcInstanceId.isBlank() || !definition.turnInNpcInstanceId.isBlank()) && !npcActive) {
                send(player, false, "NPC-linked quests require the NPC module to be active.", "", payload.requestId());
                return;
            }
            if (npcActive) QuestNpcBridge.validateSimpleLinkCapacity(SimpleServerUtilities.QUESTS, definition);
            if (!SimpleServerUtilities.QUESTS.saveDefinition(payload.originalQuestId(), definition)) {
                send(player, false, "The quest ID already exists or the quest library limit was reached.", "", payload.requestId());
                return;
            }
            if (npcActive) {
                QuestNpcBridge.rebuildManagedDialogues(SimpleServerUtilities.QUESTS, SimpleServerUtilities.NPC_DIALOGUE_DEFINITIONS);
                SimpleServerUtilities.NPCS.syncAll();
            }
            send(player, true, "Quest '" + definition.title + "' saved.", definition.id, payload.requestId());
        } catch (RuntimeException exception) {
            send(player, false, exception.getMessage() == null ? "Quest validation failed." : exception.getMessage(), "", payload.requestId());
        }
    }

    public static void open(ServerPlayer player, String questId) {
        if (!canAdmin(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Quest administrator permission is required."));
            return;
        }
        QuestDefinition definition = questId == null || questId.isBlank()
                ? new QuestDefinition().normalize() : SimpleServerUtilities.QUESTS.definition(questId);
        if (definition == null) definition = new QuestDefinition().normalize();
        PacketDistributor.sendToPlayer(player, new QuestEditorOpenPayload(
                questId == null ? "" : questId, SimpleServerUtilities.QUESTS.toJson(definition.copy()), NpcQuestWorkflowService.npcChoices(), NpcQuestWorkflowService.questChoices(),
                QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get()).serializedName(), 0L));
    }

    private static boolean canAdmin(ServerPlayer player) {
        return Config.ENABLE_QUESTS.get() && SimpleServerUtilities.CORE.modules().isActive("quests")
                && PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false);
    }
    private static void send(ServerPlayer player, boolean success, String message, String questId, long requestId) {
        PacketDistributor.sendToPlayer(player, new QuestEditorResultPayload(success, message, questId, requestId));
    }

}
