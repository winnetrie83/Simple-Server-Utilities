package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative bridge for the structured minigame definition editor. */
public final class MinigameEditorService {
    private MinigameEditorService() {
    }

    public static void handleRequest(MinigameEditorRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> open(player, payload.minigameId(), payload.requestId()));
    }

    public static void handleSubmit(MinigameEditorSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                send(player, false, "Minigame administrator permission is required.", "", payload.requestId());
                return;
            }
            try {
                MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.fromJson(payload.definitionJson());
                if (!SimpleServerUtilities.MINIGAMES.saveDefinition(payload.originalMinigameId(), definition)) {
                    send(player, false, "The minigame ID already exists or the library limit was reached.", "", payload.requestId());
                    return;
                }
                send(player, true, "Minigame '" + definition.displayName + "' saved.", definition.id, payload.requestId());
            } catch (RuntimeException exception) {
                send(player, false, exception.getMessage() == null ? "Minigame validation failed." : exception.getMessage(), "", payload.requestId());
            }
        });
    }

    public static void open(ServerPlayer player, String minigameId) {
        open(player, minigameId, 0L);
    }

    private static void open(ServerPlayer player, String minigameId, long requestId) {
        if (!canAdmin(player)) {
            player.sendSystemMessage(Component.literal("Minigame administrator permission is required."));
            return;
        }
        String id = minigameId == null ? "" : minigameId.trim();
        MinigameDefinition definition = id.isBlank()
                ? new MinigameDefinition()
                : SimpleServerUtilities.MINIGAMES.copy(SimpleServerUtilities.MINIGAMES.definition(id));
        if (definition == null) definition = new MinigameDefinition();
        definition.normalize();
        PacketDistributor.sendToPlayer(player, new MinigameEditorOpenPayload(
                id, SimpleServerUtilities.MINIGAMES.toJson(definition), requestId));
    }

    private static boolean canAdmin(ServerPlayer player) {
        return Config.ENABLE_MINIGAMES.get() && SimpleServerUtilities.CORE.modules().isActive("minigames")
                && PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false);
    }

    private static void send(ServerPlayer player, boolean success, String message, String id, long requestId) {
        PacketDistributor.sendToPlayer(player, new MinigameEditorResultPayload(success, message, id, requestId));
    }
}
