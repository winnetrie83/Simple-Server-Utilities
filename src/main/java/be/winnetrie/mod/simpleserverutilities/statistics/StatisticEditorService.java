package be.winnetrie.mod.simpleserverutilities.statistics;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Closed, server-authoritative editor bridge for custom statistics. */
public final class StatisticEditorService {
    private StatisticEditorService() {
    }

    public static boolean open(ServerPlayer player, String rawId) {
        if (!canAdmin(player)) return false;
        PlayerStatisticDefinition definition = rawId == null || rawId.isBlank()
                ? null : SimpleServerUtilities.STATISTICS.get(rawId);
        PacketDistributor.sendToPlayer(player, definition == null
                ? new StatisticEditorOpenPayload(false, "", "", "", StatisticEventType.BLOCK_BROKEN,
                        "*", StatisticEventType.BLOCK_BROKEN.defaultUnit(), true)
                : new StatisticEditorOpenPayload(true, definition.id, definition.id, definition.displayName,
                        definition.eventType, definition.target, definition.unit, definition.enabled));
        return true;
    }

    public static void handleSubmit(StatisticEditorSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Result result = save(player, payload);
        PacketDistributor.sendToPlayer(player,
                new StatisticEditorResultPayload(result.success(), result.message(), payload.requestId()));
    }

    private static Result save(ServerPlayer player, StatisticEditorSubmitPayload payload) {
        if (!canAdmin(player)) return Result.fail("Statistic administration is not allowed.");
        String id = PlayerStatisticDefinition.sanitizeId(payload.id());
        if (!payload.id().trim().matches("[A-Za-z0-9._-]{1,64}")) {
            return Result.fail("Use 1-64 letters, numbers, dots, underscores or dashes for the ID.");
        }
        if (payload.displayName().isBlank()) return Result.fail("Enter a display name.");
        PlayerStatisticDefinition definition = new PlayerStatisticDefinition();
        definition.id = id;
        definition.displayName = payload.displayName();
        definition.eventType = payload.eventType();
        definition.target = payload.target();
        definition.unit = payload.unit();
        definition.enabled = payload.enabled();
        try {
            definition.normalize();
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
        PlayerStatisticDefinition existing = SimpleServerUtilities.STATISTICS.get(payload.originalId());
        if (existing != null) definition.createdAtEpochMilli = existing.createdAtEpochMilli;
        if (!SimpleServerUtilities.STATISTICS.put(payload.originalId(), definition)) {
            return Result.fail("The statistic could not be saved. The ID may already exist or the definition limit was reached.");
        }
        SimpleServerUtilities.HOLOGRAMS.syncAll();
        return Result.ok("Statistic '" + definition.displayName + "' saved.");
    }

    public static boolean canAdmin(ServerPlayer player) {
        return player != null && Config.ENABLE_CUSTOM_STATISTICS.get()
                && SimpleServerUtilities.CORE.modules().isActive("statistics")
                && PermissionService.getBoolean(player, PermissionKeys.STATISTICS_ADMIN, false);
    }

    private record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message == null ? "Operation failed." : message); }
    }
}
