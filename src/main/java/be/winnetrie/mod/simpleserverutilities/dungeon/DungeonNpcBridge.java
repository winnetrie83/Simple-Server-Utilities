package be.winnetrie.mod.simpleserverutilities.dungeon;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.npc.NpcServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;

/** Optional NPC dialogue services; Dungeon Core itself never depends on NPC Core. */
public final class DungeonNpcBridge {
    private DungeonNpcBridge() {}

    public static void register(DungeonManager manager, NpcServiceRegistry services) {
        if (!services.isRegistered("dungeon_lobby")) {
            services.register("dungeon_lobby",
                    (player, instance, definition, target) -> canOpen(player)
                            ? NpcServiceRegistry.ServiceResult.ok(false, "")
                            : NpcServiceRegistry.ServiceResult.fail("You cannot use dungeons through NPCs."),
                    (player, instance, definition, target) -> {
                        if (!canOpen(player)) return NpcServiceRegistry.ServiceResult.fail("You cannot use dungeons through NPCs.");
                        manager.open(player);
                        return NpcServiceRegistry.ServiceResult.ok(true, "Dungeon lobby opened.");
                    });
        }
        if (!services.isRegistered("dungeon_queue")) {
            services.register("dungeon_queue",
                    (player, instance, definition, target) -> {
                        if (!canQueue(player)) return NpcServiceRegistry.ServiceResult.fail("You cannot use dungeons through NPCs.");
                        if (target == null || target.isBlank()) return NpcServiceRegistry.ServiceResult.fail("This NPC has no dungeon queue target.");
                        return manager.definition(target) == null
                                ? NpcServiceRegistry.ServiceResult.fail("That dungeon does not exist.")
                                : NpcServiceRegistry.ServiceResult.ok(false, "");
                    },
                    (player, instance, definition, target) -> {
                        if (!canQueue(player)) return NpcServiceRegistry.ServiceResult.fail("You cannot use dungeons through NPCs.");
                        try { return NpcServiceRegistry.ServiceResult.ok(false, manager.joinQueue(player, target)); }
                        catch (RuntimeException exception) { return NpcServiceRegistry.ServiceResult.fail(exception.getMessage()); }
                    });
        }
    }

    private static boolean canOpen(net.minecraft.server.level.ServerPlayer player) {
        return baseAllowed(player) && ContentAccessPolicy.canUse(player, be.winnetrie.mod.simpleserverutilities.content.ContentFeature.DUNGEONS);
    }

    private static boolean canQueue(net.minecraft.server.level.ServerPlayer player) {
        return baseAllowed(player) && ContentAccessPolicy.canJoinDungeonQueue(player);
    }

    private static boolean baseAllowed(net.minecraft.server.level.ServerPlayer player) {
        return Config.ENABLE_DUNGEONS.get()
                && SimpleServerUtilities.CORE.modules().isActive("dungeons")
                && PermissionService.getBoolean(player, PermissionKeys.NPCS_SERVICE_DUNGEONS, true);
    }
}
