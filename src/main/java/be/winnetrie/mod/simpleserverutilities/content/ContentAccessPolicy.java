package be.winnetrie.mod.simpleserverutilities.content;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central server-authoritative access gate for all content modules.
 * Quests and NPCs remain independent; only their optional entry-point bridge is coupled here.
 */
public final class ContentAccessPolicy {
    private ContentAccessPolicy() {
    }

    public static boolean moduleEnabled(ContentFeature feature) {
        return SsuModuleAccess.active(switch (feature) {
            case NPCS -> "npcs";
            case QUESTS -> "quests";
            case MINIGAMES -> "minigames";
            case DUNGEONS -> "dungeons";
        });
    }

    public static boolean canUse(ServerPlayer player, ContentFeature feature) {
        return player != null && moduleEnabled(feature)
                && PermissionService.getBoolean(player, usePermission(feature), true);
    }

    public static boolean canAdmin(ServerPlayer player, ContentFeature feature) {
        return player != null && moduleEnabled(feature)
                && PermissionService.getBoolean(player, adminPermission(feature), false);
    }

    public static String usePermission(ContentFeature feature) {
        return switch (feature) {
            case NPCS -> PermissionKeys.NPCS_USE;
            case QUESTS -> PermissionKeys.QUESTS_USE;
            case MINIGAMES -> PermissionKeys.MINIGAMES_USE;
            case DUNGEONS -> PermissionKeys.DUNGEONS_USE;
        };
    }

    public static String adminPermission(ContentFeature feature) {
        return switch (feature) {
            case NPCS -> PermissionKeys.NPCS_ADMIN;
            case QUESTS -> PermissionKeys.QUESTS_ADMIN;
            case MINIGAMES -> PermissionKeys.MINIGAMES_ADMIN;
            case DUNGEONS -> PermissionKeys.DUNGEONS_ADMIN;
        };
    }

    public static boolean canInteractWithNpc(ServerPlayer player) {
        return canUse(player, ContentFeature.NPCS)
                && PermissionService.getBoolean(player, PermissionKeys.NPCS_INTERACT, true);
    }


    public static boolean canUseNpcDialogue(ServerPlayer player) {
        return canInteractWithNpc(player)
                && PermissionService.getBoolean(player, PermissionKeys.NPCS_DIALOGUE, true);
    }

    public static boolean canTrackQuests(ServerPlayer player) {
        return canUse(player, ContentFeature.QUESTS)
                && PermissionService.getBoolean(player, PermissionKeys.QUESTS_TRACK, true);
    }

    public static boolean canAbandonQuests(ServerPlayer player) {
        return canUse(player, ContentFeature.QUESTS)
                && PermissionService.getBoolean(player, PermissionKeys.QUESTS_ABANDON, true);
    }

    public static boolean canJoinMinigameQueue(ServerPlayer player) {
        return canUse(player, ContentFeature.MINIGAMES)
                && PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_QUEUE, true);
    }

    public static boolean canJoinDungeonQueue(ServerPlayer player) {
        return canUse(player, ContentFeature.DUNGEONS)
                && PermissionService.getBoolean(player, PermissionKeys.DUNGEONS_QUEUE, true);
    }

    public static QuestAccessMode configuredQuestAccessMode() {
        return QuestAccessMode.parse(Config.QUEST_ACCESS_MODE.get());
    }

    /**
     * NPC mode cannot strand players when the NPC module is disabled. In that case the only
     * effective quest entry point is the SSU menu, regardless of the saved preference.
     */
    public static QuestAccessMode effectiveQuestAccessMode() {
        QuestAccessMode configured = configuredQuestAccessMode();
        if (!SsuModuleAccess.active("npcs")) {
            return configured == QuestAccessMode.NPC ? QuestAccessMode.MENU : configured;
        }
        return configured;
    }

    public static boolean questsAvailableFromMenu(ServerPlayer player) {
        QuestAccessMode mode = effectiveQuestAccessMode();
        return canUse(player, ContentFeature.QUESTS)
                && (mode == QuestAccessMode.MENU || mode == QuestAccessMode.BOTH);
    }

    public static boolean questsAvailableFromNpc(ServerPlayer player) {
        QuestAccessMode mode = effectiveQuestAccessMode();
        return canUse(player, ContentFeature.QUESTS)
                && (mode == QuestAccessMode.NPC || mode == QuestAccessMode.BOTH)
                && canInteractWithNpc(player);
    }
}
