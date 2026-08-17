package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.ArrayList;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.content.ContentEvent;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventTypes;
import be.winnetrie.mod.simpleserverutilities.network.NpcFunctionMenuPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcFunctionUsePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative direct-service and generated-menu router for NPC Functions. */
public final class NpcFunctionService {
    private static final double MAX_DISTANCE_SQUARED = 100.0D;
    public boolean openMenu(ServerPlayer player, NpcInstance instance, NpcDefinition definition) {
        if (!canUse(player, instance, definition)) return false;
        ArrayList<NpcFunctionMenuPayload.Entry> entries = new ArrayList<>();
        for (NpcFunction function : definition.serviceFunctions()) {
            NpcServiceRegistry.ServiceResult validation = SimpleServerUtilities.NPC_SERVICES.validate(
                    player, instance, definition, function.service, function.target);
            entries.add(new NpcFunctionMenuPayload.Entry(function.id, function.label,
                    validation.successful(), validation.successful() ? "" : validation.message()));
        }
        if (entries.isEmpty()) {
            player.sendSystemMessage(Component.literal(definition.displayName + " has no configured services."), true);
            return false;
        }
        PacketDistributor.sendToPlayer(player, new NpcFunctionMenuPayload(
                instance.id, definition.displayName, definition.roleLabel(), definition.roleColor, entries));
        return true;
    }

    public boolean executePrimary(ServerPlayer player, NpcInstance instance, NpcDefinition definition) {
        if (!canUse(player, instance, definition)) return false;
        for (NpcFunction function : definition.serviceFunctions()) return execute(player, instance, definition, function);
        player.sendSystemMessage(Component.literal(definition.displayName + " has no configured direct service."), true);
        return false;
    }

    public static void handleUse(NpcFunctionUsePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        SimpleServerUtilities.NPC_FUNCTIONS.use(player, payload);
    }

    private void use(ServerPlayer player, NpcFunctionUsePayload payload) {
        NpcInstance instance = SimpleServerUtilities.NPCS.instance(payload.instanceId());
        NpcDefinition definition = SimpleServerUtilities.NPCS.definitionFor(instance);
        if (definition == null || definition.interactionMode() != NpcInteractionMode.SERVICE_MENU
                || !canUse(player, instance, definition)) {
            player.sendSystemMessage(Component.literal("The NPC service is no longer available."), true);
            return;
        }
        for (NpcFunction function : definition.serviceFunctions()) {
            if (function.id.equals(payload.functionId())) {
                execute(player, instance, definition, function);
                return;
            }
        }
        player.sendSystemMessage(Component.literal("That NPC function no longer exists."), true);
    }

    private boolean execute(ServerPlayer player, NpcInstance instance, NpcDefinition definition, NpcFunction function) {
        NpcServiceRegistry.ServiceResult validation = SimpleServerUtilities.NPC_SERVICES.validate(
                player, instance, definition, function.service, function.target);
        if (!validation.successful()) {
            player.sendSystemMessage(Component.literal(validation.message()), true);
            return false;
        }
        NpcServiceRegistry.ServiceResult result = SimpleServerUtilities.NPC_SERVICES.execute(
                player, instance, definition, function.service, function.target);
        if (!result.message().isBlank()) player.sendSystemMessage(Component.literal(result.message()), true);
        if (!result.successful()) return false;
        SimpleServerUtilities.CONTENT_EVENTS.publish(player.level().getServer(), ContentEvent.player(
                ContentEventTypes.NPC_SERVICE_USED, player.getUUID(), "npcs", instance.id,
                function.service, 1L, Map.of(
                        "npc_definition", definition.id,
                        "npc_instance", instance.id,
                        "npc_role", definition.roleId,
                        "npc_function", function.id,
                        "service", function.service,
                        "target", function.target)));
        return true;
    }

    private static boolean canUse(ServerPlayer player, NpcInstance instance, NpcDefinition definition) {
        if (player == null || instance == null || definition == null
                || !Config.ENABLE_NPCS.get() || !ContentAccessPolicy.canInteractWithNpc(player)
                || !definition.enabled || !instance.enabled || instance.dead) return false;
        if (!instance.dimension.equals(player.level().dimension().identifier().toString())) return false;
        double dx = player.getX() - instance.x, dy = player.getY() - instance.y, dz = player.getZ() - instance.z;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQUARED) return false;
        int reputation = definition.factionId.isBlank() ? 0
                : SimpleServerUtilities.CONTENT_PROGRESS.reputation(player.getUUID(), definition.factionId);
        return definition.factionId.isBlank() || reputation >= definition.minimumReputation;
    }

}
