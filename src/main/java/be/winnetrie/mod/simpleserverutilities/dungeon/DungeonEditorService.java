package be.winnetrie.mod.simpleserverutilities.dungeon;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative bridge for the structured dungeon definition editor. */
public final class DungeonEditorService {
    private DungeonEditorService() {}

    public static void handleRequest(DungeonEditorRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> open(player, payload.dungeonId(), payload.requestId()));
    }

    public static void handleSubmit(DungeonEditorSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) { send(player, false, "Dungeon administrator permission is required.", "", payload.requestId()); return; }
            try {
                DungeonDefinition definition = SimpleServerUtilities.DUNGEONS.fromJson(payload.definitionJson());
                if (!SimpleServerUtilities.DUNGEONS.saveDefinition(payload.originalDungeonId(), definition)) {
                    send(player, false, "The dungeon ID already exists or the library limit was reached.", "", payload.requestId()); return;
                }
                send(player, true, "Dungeon '" + definition.displayName + "' saved.", definition.id, payload.requestId());
            } catch (RuntimeException exception) {
                send(player, false, exception.getMessage() == null ? "Dungeon validation failed." : exception.getMessage(), "", payload.requestId());
            }
        });
    }

    public static void open(ServerPlayer player, String dungeonId) { open(player, dungeonId, 0L); }

    private static void open(ServerPlayer player, String dungeonId, long requestId) {
        if (!canAdmin(player)) { player.sendSystemMessage(Component.literal("Dungeon administrator permission is required.")); return; }
        String id = dungeonId == null ? "" : dungeonId.trim();
        DungeonDefinition definition = id.isBlank() ? new DungeonDefinition() : SimpleServerUtilities.DUNGEONS.copy(SimpleServerUtilities.DUNGEONS.definition(id));
        if (definition == null) definition = new DungeonDefinition();
        definition.normalize();
        PacketDistributor.sendToPlayer(player, new DungeonEditorOpenPayload(id, SimpleServerUtilities.DUNGEONS.toJson(definition), requestId));
    }

    private static boolean canAdmin(ServerPlayer player) {
        return Config.ENABLE_DUNGEONS.get() && SimpleServerUtilities.CORE.modules().isActive("dungeons")
                && PermissionService.getBoolean(player, PermissionKeys.DUNGEONS_ADMIN, false);
    }

    private static void send(ServerPlayer player, boolean success, String message, String id, long requestId) {
        PacketDistributor.sendToPlayer(player, new DungeonEditorResultPayload(success, message, id, requestId));
    }
}
