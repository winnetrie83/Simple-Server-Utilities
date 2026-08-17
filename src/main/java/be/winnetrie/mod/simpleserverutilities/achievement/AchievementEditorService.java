package be.winnetrie.mod.simpleserverutilities.achievement;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorSubmitPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class AchievementEditorService {
    private AchievementEditorService() {
    }

    public static void handleRequest(AchievementEditorRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("achievements")) return;
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!SimpleServerUtilities.ACHIEVEMENTS.canAdmin(player)) {
            player.sendSystemMessage(Component.literal("Achievement administrator permission is required."));
            return;
        }

        String requestedId = payload.achievementId() == null ? "" : payload.achievementId().trim();
        AchievementDefinition definition;
        if (requestedId.isBlank()) {
            definition = new AchievementDefinition().normalize();
        } else {
            definition = SimpleServerUtilities.ACHIEVEMENTS.definition(requestedId);
            if (definition == null) {
                player.sendSystemMessage(Component.literal("Achievement '" + requestedId + "' no longer exists."));
                return;
            }
        }

        PacketDistributor.sendToPlayer(
                player,
                new AchievementEditorOpenPayload(
                        requestedId,
                        SimpleServerUtilities.ACHIEVEMENTS.toJson(definition),
                        SsuModuleAccess.active("economy") ? SimpleServerUtilities.ECONOMY.settings().getCurrencySymbol() : "",
                        SsuModuleAccess.active("economy") ? SimpleServerUtilities.ECONOMY.settings().getDecimalPlaces() : 0,
                        payload.requestId()
                )
        );
    }

    public static void handleSubmit(AchievementEditorSubmitPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("achievements")) return;
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!SimpleServerUtilities.ACHIEVEMENTS.canAdmin(player)) {
            send(player, false, "Achievement administrator permission is required.", "", payload.requestId());
            return;
        }

        try {
            AchievementDefinition definition = SimpleServerUtilities.ACHIEVEMENTS.fromJson(payload.achievementJson());
            if (!SimpleServerUtilities.ACHIEVEMENTS.saveDefinition(payload.originalAchievementId(), definition)) {
                send(player, false, "The achievement ID already exists or the library limit was reached.", "", payload.requestId());
                return;
            }
            send(
                    player,
                    true,
                    "Achievement '" + AchievementRichText.plain(definition.title) + "' saved.",
                    definition.id,
                    payload.requestId()
            );
        } catch (RuntimeException e) {
            send(
                    player,
                    false,
                    e.getMessage() == null ? "Achievement validation failed." : e.getMessage(),
                    "",
                    payload.requestId()
            );
        }
    }

    private static void send(ServerPlayer player, boolean ok, String message, String id, long requestId) {
        PacketDistributor.sendToPlayer(player, new AchievementEditorResultPayload(ok, message, id, requestId));
    }
}
