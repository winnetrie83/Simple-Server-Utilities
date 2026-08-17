package be.winnetrie.mod.simpleserverutilities.teleport;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class TeleportManager {

    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<String, Long> cooldownUntilTick = new HashMap<>();

    public int requestTeleport(
            ServerPlayer player,
            String cooldownKey,
            String targetName,
            TeleportOptions options,
            ServerLevel targetLevel,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        return requestTeleport(player, cooldownKey, targetName, options, targetLevel,
                x, y, z, yaw, pitch, ignored -> true, "Teleport is no longer allowed.");
    }

    public int requestTeleport(
            ServerPlayer player,
            String cooldownKey,
            String targetName,
            TeleportOptions options,
            ServerLevel targetLevel,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Predicate<ServerPlayer> executionGuard,
            String guardFailureMessage
    ) {
        return requestTeleport(
                player, cooldownKey, targetName, options, targetLevel, x, y, z, yaw, pitch,
                executionGuard, ignored -> guardFailureMessage
        );
    }

    public int requestTeleport(
            ServerPlayer player,
            String cooldownKey,
            String targetName,
            TeleportOptions options,
            ServerLevel targetLevel,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Predicate<ServerPlayer> executionGuard,
            Function<ServerPlayer, String> guardFailureMessage
    ) {
        MinecraftServer server = player.level().getServer();

        if (!SsuModuleAccess.active("teleport")) {
            player.sendSystemMessage(Component.literal("Teleport is disabled by the server."));
            return 0;
        }

        if (SsuModuleAccess.active("moderation") && SimpleServerUtilities.MODERATION.jailed(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Teleport is disabled while jailed."), true);
            return 0;
        }

        if (server == null) {
            player.sendSystemMessage(Component.literal("Teleport failed: server not available."));
            return 0;
        }

        long currentTick = server.getTickCount();
        String cooldownMapKey = getCooldownMapKey(player, cooldownKey);

        long cooldownEndTick = cooldownUntilTick.getOrDefault(cooldownMapKey, 0L);

        if (cooldownEndTick > currentTick) {
            long remainingTicks = cooldownEndTick - currentTick;

            player.sendSystemMessage(Component.literal(
                    "You must wait " + formatSeconds(remainingTicks) + " seconds before teleporting again."
            ));

            return 0;
        }

        if (cooldownEndTick != 0L) {
            cooldownUntilTick.remove(cooldownMapKey);
        }

        if (pendingTeleports.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already have a pending teleport."));
            return 0;
        }

        Predicate<ServerPlayer> resolvedGuard = executionGuard == null ? ignored -> true : executionGuard;
        Function<ServerPlayer, String> resolvedGuardFailure = candidate -> {
            String message = guardFailureMessage == null ? null : guardFailureMessage.apply(candidate);
            return message == null || message.isBlank()
                    ? "Teleport is no longer allowed." : message;
        };
        if (!resolvedGuard.test(player)) {
            player.sendSystemMessage(Component.literal(resolvedGuardFailure.apply(player)));
            return 0;
        }

        Optional<TeleportDestination> safeDestination = TeleportSafety.findSafeDestination(
                targetLevel,
                x,
                y,
                z
        );

        if (safeDestination.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "Teleport failed: no safe destination was found near " + targetName + "."
            ));
            return 0;
        }

        if (options.delaySeconds() <= 0) {
            TeleportDestination destination = safeDestination.get();
            doTeleport(player, targetLevel, destination.x(), destination.y(), destination.z(), yaw, pitch);
            applyCooldown(server, player, cooldownKey, options.cooldownSeconds());

            player.sendSystemMessage(Component.literal("Teleported to " + targetName + "."));
            return 1;
        }

        long executeAtTick = currentTick + options.delaySeconds() * 20L;

        PendingTeleport pendingTeleport = new PendingTeleport(
                player.getUUID(),
                cooldownKey,
                targetName,
                targetLevel,
                x,
                y,
                z,
                yaw,
                pitch,
                options.cooldownSeconds(),
                options.requireStill(),
                executeAtTick,
                player.level().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                resolvedGuard,
                resolvedGuardFailure
        );

        pendingTeleports.put(player.getUUID(), pendingTeleport);

        String countdownMessage = "Teleporting to " + targetName + " in " + options.delaySeconds() + " seconds.";
        if (options.requireStill()) {
            countdownMessage += " Remain still or the teleport will be cancelled.";
        }
        player.sendSystemMessage(Component.literal(countdownMessage));

        return 1;
    }

    public void tick(MinecraftServer server) {
        if (!SsuModuleAccess.active("teleport")) {
            clear();
            return;
        }
        if (pendingTeleports.isEmpty()) {
            return;
        }

        long currentTick = server.getTickCount();

        Iterator<Map.Entry<UUID, PendingTeleport>> iterator = pendingTeleports.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTeleport> entry = iterator.next();
            PendingTeleport pendingTeleport = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(pendingTeleport.playerId());

            if (player == null) {
                iterator.remove();
                continue;
            }

            if (pendingTeleport.requireStill() && hasMoved(player, pendingTeleport)) {
                iterator.remove();
                player.sendSystemMessage(Component.literal("Teleport cancelled because you moved."));
                continue;
            }

            if (currentTick < pendingTeleport.executeAtTick()) {
                continue;
            }

            if (!pendingTeleport.executionGuard().test(player)) {
                iterator.remove();
                player.sendSystemMessage(Component.literal(pendingTeleport.guardFailureMessage().apply(player)));
                continue;
            }

            Optional<TeleportDestination> safeDestination = TeleportSafety.findSafeDestination(
                    pendingTeleport.targetLevel(),
                    pendingTeleport.x(),
                    pendingTeleport.y(),
                    pendingTeleport.z()
            );

            iterator.remove();

            if (safeDestination.isEmpty()) {
                player.sendSystemMessage(Component.literal(
                        "Teleport failed: the destination near " + pendingTeleport.targetName() + " is no longer safe."
                ));
                continue;
            }

            TeleportDestination destination = safeDestination.get();
            doTeleport(
                    player,
                    pendingTeleport.targetLevel(),
                    destination.x(),
                    destination.y(),
                    destination.z(),
                    pendingTeleport.yaw(),
                    pendingTeleport.pitch()
            );

            applyCooldown(server, player, pendingTeleport.cooldownKey(), pendingTeleport.cooldownSeconds());

            player.sendSystemMessage(Component.literal(
                    "Teleported to " + pendingTeleport.targetName() + "."
            ));
        }
    }

    public boolean cancel(ServerPlayer player) {
        return pendingTeleports.remove(player.getUUID()) != null;
    }

    private boolean hasMoved(ServerPlayer player, PendingTeleport pendingTeleport) {
        String currentDimension = player.level().dimension().location().toString();
        if (!currentDimension.equals(pendingTeleport.startDimension())) {
            return true;
        }

        return TeleportMovementGuard.hasMoved(
                pendingTeleport.startDimension(),
                pendingTeleport.startX(), pendingTeleport.startY(), pendingTeleport.startZ(),
                currentDimension,
                player.getX(), player.getY(), player.getZ()
        );
    }

    private void doTeleport(
            ServerPlayer player,
            ServerLevel level,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch);
    }

    private void applyCooldown(
            MinecraftServer server,
            ServerPlayer player,
            String cooldownKey,
            int cooldownSeconds
    ) {
        if (cooldownSeconds <= 0) {
            return;
        }

        long cooldownEndTick = server.getTickCount() + cooldownSeconds * 20L;
        cooldownUntilTick.put(getCooldownMapKey(player, cooldownKey), cooldownEndTick);
    }

    private String getCooldownMapKey(ServerPlayer player, String cooldownKey) {
        return player.getUUID() + ":" + cooldownKey;
    }

    private int formatSeconds(long ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 20.0D));
    }

    /** Clears session-only pending teleports and cooldowns between server lifecycles. */
    public void clear() {
        pendingTeleports.clear();
        cooldownUntilTick.clear();
    }

    private record PendingTeleport(
            UUID playerId,
            String cooldownKey,
            String targetName,
            ServerLevel targetLevel,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int cooldownSeconds,
            boolean requireStill,
            long executeAtTick,
            String startDimension,
            double startX,
            double startY,
            double startZ,
            Predicate<ServerPlayer> executionGuard,
            Function<ServerPlayer, String> guardFailureMessage
    ) {
    }
}