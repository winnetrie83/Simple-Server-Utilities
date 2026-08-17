package be.winnetrie.mod.simpleserverutilities.utilitymining;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningActivationPayload;
import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningPreviewPayload;
import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningPreviewRequestPayload;
import be.winnetrie.mod.simpleserverutilities.settings.PlayerUiPreferences;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Runtime state and server-authoritative preview service for Treecapitator and Veinminer. */
public final class UtilityMiningManager {
    private static final long KEY_STATE_TIMEOUT_TICKS = 40L;
    private static final double MAX_PREVIEW_DISTANCE_SQR = 12.0D * 12.0D;

    private final Map<UUID, KeyState> keyStates = new ConcurrentHashMap<>();

    public void clear() {
        keyStates.clear();
    }

    public void clearClients(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendClear(player);
    }

    public void handleActivation(UtilityMiningActivationPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("utility_mining")) return;
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        boolean treeHeld = Config.ENABLE_TREECAPITATOR.get() && payload.treecapitatorHeld();
        boolean veinHeld = Config.ENABLE_VEINMINER.get() && payload.veinminerHeld();
        if (!treeHeld && !veinHeld) {
            keyStates.remove(player.getUUID());
            return;
        }
        long now = player.level().getGameTime();
        keyStates.put(player.getUUID(), new KeyState(treeHeld, veinHeld, now));
    }

    public void handlePreview(UtilityMiningPreviewRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("utility_mining")) return;
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos origin = payload.blockPos();
        if (!player.level().hasChunkAt(origin)
                || origin.distSqr(player.blockPosition()) > MAX_PREVIEW_DISTANCE_SQR) {
            sendClear(player);
            return;
        }

        PlayerUiPreferences preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        if (!hasAnyActiveMode(player, preferences)) {
            sendClear(player);
            return;
        }

        UtilityMiningTarget target = UtilityMiningResolver.resolve(player, origin);
        if (target.isEmpty() || !isEnabledAndActive(player, target.type(), preferences)) {
            sendClear(player);
            return;
        }

        int color = target.type() == UtilityMiningType.TREECAPITATOR
                ? preferences.getTreecapitatorOutlineColor()
                : preferences.getVeinminerOutlineColor();
        int brightness = target.type() == UtilityMiningType.TREECAPITATOR
                ? preferences.getTreecapitatorOutlineBrightness()
                : preferences.getVeinminerOutlineBrightness();
        boolean showInfo = target.type() == UtilityMiningType.TREECAPITATOR
                ? preferences.isTreecapitatorInfoEnabled()
                : preferences.isVeinminerInfoEnabled();
        String blockName = player.level().getBlockState(origin).getBlock().getName().getString();

        PacketDistributor.sendToPlayer(player, UtilityMiningPreviewPayload.of(
                target.type(),
                player.level().dimension().location().toString(),
                color,
                brightness,
                showInfo,
                blockName,
                target.blocks()
        ));
    }

    public boolean isEnabledAndActive(ServerPlayer player, UtilityMiningType type) {
        return isEnabledAndActive(player, type, SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player));
    }

    public boolean hasAnyActiveMode(ServerPlayer player) {
        return hasAnyActiveMode(player, SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player));
    }

    private boolean hasAnyActiveMode(ServerPlayer player, PlayerUiPreferences preferences) {
        return isEnabledAndActive(player, UtilityMiningType.TREECAPITATOR, preferences)
                || isEnabledAndActive(player, UtilityMiningType.VEINMINER, preferences);
    }

    public boolean isEnabledAndActive(
            ServerPlayer player,
            UtilityMiningType type,
            PlayerUiPreferences preferences
    ) {
        if (type == UtilityMiningType.TREECAPITATOR) {
            return Config.ENABLE_TREECAPITATOR.get()
                    && preferences.isTreecapitatorEnabled()
                    && isActivationActive(player, preferences.getTreecapitatorActivation(), true);
        }
        if (type == UtilityMiningType.VEINMINER) {
            return Config.ENABLE_VEINMINER.get()
                    && preferences.isVeinminerEnabled()
                    && isActivationActive(player, preferences.getVeinminerActivation(), false);
        }
        return false;
    }

    public void forget(UUID playerId) {
        if (playerId != null) {
            keyStates.remove(playerId);
        }
    }

    private boolean isActivationActive(ServerPlayer player, MiningActivationMode mode, boolean tree) {
        if (mode == MiningActivationMode.SNEAK) {
            return player.isShiftKeyDown();
        }

        KeyState state = keyStates.get(player.getUUID());
        if (state == null || player.level().getGameTime() - state.updatedAt() > KEY_STATE_TIMEOUT_TICKS) {
            return false;
        }
        return tree ? state.treecapitatorHeld() : state.veinminerHeld();
    }

    private static void sendClear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, UtilityMiningPreviewPayload.clear());
    }

    private record KeyState(boolean treecapitatorHeld, boolean veinminerHeld, long updatedAt) {
    }
}
