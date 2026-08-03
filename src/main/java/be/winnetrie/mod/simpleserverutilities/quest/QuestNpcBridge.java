package be.winnetrie.mod.simpleserverutilities.quest;

import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.npc.NpcServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;

/** Optional bridge registered by Quest Core without making either module depend on the other. */
public final class QuestNpcBridge {
    private QuestNpcBridge() {}

    public static void register(QuestManager manager, NpcServiceRegistry services) {
        if (!services.isRegistered("questbook")) {
            services.register("questbook",
                    (player, instance, definition, target) -> validateNpcQuestAccess(player),
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult validation = validateNpcQuestAccess(player);
                        if (!validation.successful()) return validation;
                        manager.openFromNpc(player);
                        return NpcServiceRegistry.ServiceResult.ok(true, "Questbook opened.");
                    });
        }
        if (!services.isRegistered("quest_offer")) {
            services.register("quest_offer",
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        String problem = manager.validateStart(player, target, "npc");
                        return problem.isBlank() ? NpcServiceRegistry.ServiceResult.ok(false, "")
                                : NpcServiceRegistry.ServiceResult.fail(problem);
                    },
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        try {
                            String message = manager.start(player, target, "npc");
                            return NpcServiceRegistry.ServiceResult.ok(true, message);
                        } catch (RuntimeException exception) {
                            return NpcServiceRegistry.ServiceResult.fail(safeMessage(exception, "The quest could not be started."));
                        }
                    });
        }
        if (!services.isRegistered("quest_turn_in")) {
            services.register("quest_turn_in",
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        String problem = manager.validateTurnIn(player, target, "npc");
                        return problem.isBlank() ? NpcServiceRegistry.ServiceResult.ok(false, "")
                                : NpcServiceRegistry.ServiceResult.fail(problem);
                    },
                    (player, instance, definition, target) -> {
                        NpcServiceRegistry.ServiceResult access = validateNpcQuestAccess(player);
                        if (!access.successful()) return access;
                        try {
                            String message = manager.turnIn(player, target, "npc");
                            return NpcServiceRegistry.ServiceResult.ok(true, message);
                        } catch (RuntimeException exception) {
                            return NpcServiceRegistry.ServiceResult.fail(safeMessage(exception, "The quest could not be turned in."));
                        }
                    });
        }
    }

    private static NpcServiceRegistry.ServiceResult validateNpcQuestAccess(net.minecraft.server.level.ServerPlayer player) {
        if (!PermissionService.getBoolean(player, PermissionKeys.NPCS_SERVICE_QUESTS, true)) {
            return NpcServiceRegistry.ServiceResult.fail("You cannot use NPC quest services.");
        }
        if (!ContentAccessPolicy.questsAvailableFromNpc(player)) {
            return NpcServiceRegistry.ServiceResult.fail(
                    "Quests are not configured for NPC access, or you lack quest/NPC permissions.");
        }
        return NpcServiceRegistry.ServiceResult.ok(false, "");
    }

    private static String safeMessage(RuntimeException exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }
}
