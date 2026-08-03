package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.npc.NpcServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;

/** Optional NPC dialogue services. Minigame Core itself never depends on NPC Core. */
public final class MinigameNpcBridge {
    private MinigameNpcBridge() {
    }

    public static void register(MinigameManager manager, NpcServiceRegistry services) {
        if (!services.isRegistered("minigame_lobby")) {
            services.register("minigame_lobby",
                    (player, instance, definition, target) -> allowed(player)
                            ? NpcServiceRegistry.ServiceResult.ok(false, "")
                            : NpcServiceRegistry.ServiceResult.fail("You cannot use minigames through NPCs."),
                    (player, instance, definition, target) -> {
                        if (!allowed(player)) return NpcServiceRegistry.ServiceResult.fail("You cannot use minigames through NPCs.");
                        manager.open(player);
                        return NpcServiceRegistry.ServiceResult.ok(true, "Minigame lobby opened.");
                    });
        }
        if (!services.isRegistered("minigame_queue")) {
            services.register("minigame_queue",
                    (player, instance, definition, target) -> {
                        if (!allowed(player)) return NpcServiceRegistry.ServiceResult.fail("You cannot use minigames through NPCs.");
                        if (target == null || target.isBlank()) return NpcServiceRegistry.ServiceResult.fail("This NPC has no minigame queue target.");
                        return manager.definition(target) == null
                                ? NpcServiceRegistry.ServiceResult.fail("That minigame does not exist.")
                                : NpcServiceRegistry.ServiceResult.ok(false, "");
                    },
                    (player, instance, definition, target) -> {
                        if (!allowed(player)) return NpcServiceRegistry.ServiceResult.fail("You cannot use minigames through NPCs.");
                        try {
                            return NpcServiceRegistry.ServiceResult.ok(false, manager.joinQueue(player, target));
                        } catch (RuntimeException exception) {
                            return NpcServiceRegistry.ServiceResult.fail(exception.getMessage());
                        }
                    });
        }
    }

    private static boolean allowed(net.minecraft.server.level.ServerPlayer player) {
        return Config.ENABLE_MINIGAMES.get()
                && SimpleServerUtilities.CORE.modules().isActive("minigames")
                && ContentAccessPolicy.canJoinMinigameQueue(player)
                && PermissionService.getBoolean(player, PermissionKeys.NPCS_SERVICE_MINIGAMES, true);
    }
}
