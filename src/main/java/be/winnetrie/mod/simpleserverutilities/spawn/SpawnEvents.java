package be.winnetrie.mod.simpleserverutilities.spawn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportDestination;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportSafety;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Applies SSU's dimension-aware respawn fallback without overriding a valid bed or respawn anchor. */
public final class SpawnEvents {
    private static final Set<UUID> FALLBACK_PENDING = new HashSet<>();
    private static final Map<UUID, Boolean> PERSONAL_RESPAWN_AT_DEATH = new HashMap<>();

    private SpawnEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!SsuModuleAccess.active("spawn")) return;
        UUID playerId = player.getUUID();
        if ((SsuModuleAccess.active("minigames") && SimpleServerUtilities.MINIGAMES.isInMatch(playerId, ""))
                || (SsuModuleAccess.active("dungeons") && SimpleServerUtilities.DUNGEONS.isInRun(playerId, ""))) {
            FALLBACK_PENDING.remove(playerId);
            PERSONAL_RESPAWN_AT_DEATH.remove(playerId);
            return;
        }
        boolean personal = hasPersonalRespawn(player);
        PERSONAL_RESPAWN_AT_DEATH.put(playerId, personal);
        if (personal) FALLBACK_PENDING.remove(playerId);
        else FALLBACK_PENDING.add(playerId);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!SsuModuleAccess.active("spawn")) return;
        UUID playerId = player.getUUID();
        boolean directFallback = FALLBACK_PENDING.remove(playerId);
        boolean hadPersonal = PERSONAL_RESPAWN_AT_DEATH.getOrDefault(playerId, false);
        PERSONAL_RESPAWN_AT_DEATH.remove(playerId);
        player.level().getServer().execute(() -> {
            // Vanilla sends an invalid/missing bed or anchor to the Overworld shared spawn.
            // In that case SSU's server spawn remains the preferred fallback.
            if (directFallback || (hadPersonal && atVanillaFallback(player))) teleportFallback(player);
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FALLBACK_PENDING.remove(player.getUUID());
            PERSONAL_RESPAWN_AT_DEATH.remove(player.getUUID());
        }
    }

    public static boolean teleport(ServerPlayer player, ServerSpawn destination) {
        if (player == null || destination == null || player.level().getServer() == null) return false;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(destination.getDimension()));
            ServerLevel level = player.level().getServer().getLevel(key);
            if (level == null) return false;
            player.teleportTo(level, destination.getX(), destination.getY(), destination.getZ(), Set.of(),
                    destination.getYaw(), destination.getPitch(), true);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }


    private static boolean atVanillaFallback(ServerPlayer player) {
        if (player == null || player.level().getServer() == null || player.level() != player.level().getServer().overworld()) return false;
        var spawn = player.level().getServer().overworld().getWorldBorderAdjustedRespawnData(player.level().getServer().overworld().getRespawnData()).pos();
        return player.distanceToSqr(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D) <= 16.0D;
    }

    /** Teleports to SSU server spawn, then to vanilla Overworld spawn as the final fallback. */
    public static boolean teleportFallback(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return false;
        if (teleport(player, SimpleServerUtilities.SERVER_SPAWN.get())) return true;
        ServerLevel level = player.level().getServer().overworld();
        if (level == null) return false;
        var spawn = level.getWorldBorderAdjustedRespawnData(level.getRespawnData()).pos();
        Optional<TeleportDestination> safe = TeleportSafety.findSafeDestination(level, spawn.getX()+0.5D, spawn.getY(), spawn.getZ()+0.5D, 12);
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;
        if (safe.isPresent()) {
            x = safe.get().x(); y = safe.get().y(); z = safe.get().z();
        }
        player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
        return true;
    }

    /** Mapping-tolerant lookup for the current personal respawn record. */
    private static boolean hasPersonalRespawn(ServerPlayer player) {
        for (String name : new String[] {"getRespawnConfig", "getRespawn", "getRespawnData", "getRespawnPosition"}) {
            try {
                Method method = player.getClass().getMethod(name);
                Object result = method.invoke(player);
                if (result instanceof Optional<?> optional) return optional.isPresent();
                if (result != null) return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        // Mapping fallback: inspect respawn-related fields before deciding no personal point exists.
        for (Field field : player.getClass().getDeclaredFields()) {
            if (!field.getName().toLowerCase(java.util.Locale.ROOT).contains("respawn")) continue;
            try {
                field.setAccessible(true);
                Object result = field.get(player);
                if (result instanceof Optional<?> optional && optional.isPresent()) return true;
                if (result != null && !(result instanceof Boolean value && !value)) return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return false;
    }
}
