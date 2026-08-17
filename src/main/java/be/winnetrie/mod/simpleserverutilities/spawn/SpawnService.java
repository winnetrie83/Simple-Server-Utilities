package be.winnetrie.mod.simpleserverutilities.spawn;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportOptions;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Shared command/dashboard implementation for requesting a server-spawn teleport. */
public final class SpawnService {

    private SpawnService() {
    }

    public static int requestTeleport(ServerPlayer player) {
        if (!SsuModuleAccess.active("spawn")) {
            player.sendSystemMessage(Component.literal("Server Spawn is disabled."));
            return 0;
        }
        if (!SsuModuleAccess.active("teleport")) {
            player.sendSystemMessage(Component.literal("The Teleport module is disabled; /spawn travel is unavailable."));
            return 0;
        }
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!SpawnPolicy.canUse(player, context)) {
            player.sendSystemMessage(Component.literal(TeleportPolicy.denialMessage(TeleportType.SPAWN, context)));
            return 0;
        }

        ServerSpawn spawn = SimpleServerUtilities.SERVER_SPAWN.get();
        if (spawn == null) {
            player.sendSystemMessage(Component.literal("The server spawn has not been set yet."));
            return 0;
        }

        ServerLevel targetLevel = level(player, spawn.getDimension());
        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal("Server spawn dimension is not loaded: " + spawn.getDimension()));
            return 0;
        }

        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.SPAWN, context);
        return SimpleServerUtilities.TELEPORTS.requestTeleport(
                player,
                "spawn",
                "server spawn",
                options,
                targetLevel,
                spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch(),
                candidate -> SpawnPolicy.canUse(candidate,
                        PermissionContext.at(candidate, candidate.blockPosition())),
                candidate -> TeleportPolicy.denialMessage(TeleportType.SPAWN,
                        PermissionContext.at(candidate, candidate.blockPosition()))
        );
    }

    private static ServerLevel level(ServerPlayer player, String rawDimension) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(rawDimension));
            return player.level().getServer().getLevel(key);
        } catch (Exception exception) {
            return null;
        }
    }
}
