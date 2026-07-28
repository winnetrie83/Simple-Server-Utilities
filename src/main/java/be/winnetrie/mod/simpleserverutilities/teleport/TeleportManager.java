package be.winnetrie.mod.simpleserverutilities.teleport;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportOptions;
import net.minecraft.core.BlockPos;
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
        MinecraftServer server = player.level().getServer();

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
                options.cancelOnMove(),
                executeAtTick,
                player.level().dimension().identifier().toString(),
                player.blockPosition()
        );

        pendingTeleports.put(player.getUUID(), pendingTeleport);

        player.sendSystemMessage(Component.literal(
                "Teleporting to " + targetName + " in " + options.delaySeconds() + " seconds. Stand still."
        ));

        return 1;
    }

    public void tick(MinecraftServer server) {
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

            if (pendingTeleport.cancelOnMove() && hasMoved(player, pendingTeleport)) {
                iterator.remove();
                player.sendSystemMessage(Component.literal("Teleport cancelled because you moved."));
                continue;
            }

            if (currentTick < pendingTeleport.executeAtTick()) {
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
        String currentDimension = player.level().dimension().identifier().toString();

        if (!currentDimension.equals(pendingTeleport.startDimension())) {
            return true;
        }

        return !player.blockPosition().equals(pendingTeleport.startBlockPos());
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
        player.teleportTo(
                level,
                x,
                y,
                z,
                Set.of(),
                yaw,
                pitch,
                true
        );
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
            boolean cancelOnMove,
            long executeAtTick,
            String startDimension,
            BlockPos startBlockPos
    ) {
    }
}