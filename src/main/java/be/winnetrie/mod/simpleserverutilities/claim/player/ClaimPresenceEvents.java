package be.winnetrie.mod.simpleserverutilities.claim.player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Sends claim welcome messages only when a player actually crosses into a different claim. */
public final class ClaimPresenceEvents {
    private static final Map<UUID, UUID> LAST_CLAIM = new HashMap<>();
    private static long nextPresenceTick;

    private ClaimPresenceEvents() {
    }

    public static void clearRuntimeState() {
        LAST_CLAIM.clear();
        nextPresenceTick = 0L;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SsuModuleAccess.active("claims")) {
            LAST_CLAIM.clear();
            return;
        }

        MinecraftServer server = event.getServer();
        long tick = server.getTickCount();
        if (tick < nextPresenceTick) return;
        nextPresenceTick = tick + 10L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaim(player.level(), player.chunkPosition());
            UUID currentClaimId = claim == null ? null : claim.getId();
            UUID previousClaimId = LAST_CLAIM.get(player.getUUID());
            if (Objects.equals(currentClaimId, previousClaimId)) continue;

            if (claim != null && !claim.getWelcomeMessage().isBlank()) {
                player.sendSystemMessage(Component.literal(claim.getWelcomeMessage()), true);
            }

            if (currentClaimId == null) LAST_CLAIM.remove(player.getUUID());
            else LAST_CLAIM.put(player.getUUID(), currentClaimId);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CLAIM.remove(event.getEntity().getUUID());
    }
}
