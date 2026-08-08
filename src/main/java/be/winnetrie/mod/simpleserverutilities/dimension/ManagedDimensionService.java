package be.winnetrie.mod.simpleserverutilities.dimension;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportSafety;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ManagedDimensionService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ManagedDimensionService() {
    }

    public static void handleRequest(SsuDimensionManagerRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        PacketDistributor.sendToPlayer(player, data(player, payload.selectedId(), payload.requestId(), "", false));
    }

    public static void handleSubmit(SsuDimensionManagerSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canAdmin(player)) {
            PacketDistributor.sendToPlayer(player, SsuDimensionManagerDataPayload.denied(
                    payload.requestId(), "Dimension administration denied."));
            return;
        }

        String selected = payload.originalId();
        String notice;
        boolean error = false;
        try {
            switch (payload.action()) {
                case "create" -> {
                    ManagedDimensionDefinition submitted = parse(payload.definitionJson());
                    ManagedDimensionDefinition created = SimpleServerUtilities.DIMENSIONS.create(submitted);
                    selected = created.resourceId();
                    notice = "Dimension created. Restart the server to load it.";
                }
                case "save" -> {
                    ManagedDimensionDefinition submitted = parse(payload.definitionJson());
                    ManagedDimensionDefinition saved = SimpleServerUtilities.DIMENSIONS.save(payload.originalId(), submitted);
                    selected = saved.resourceId();
                    notice = "Dimension settings saved. Restart the server to apply world-generation changes.";
                }
                case "delete" -> {
                    if (!SimpleServerUtilities.DIMENSIONS.delete(payload.originalId())) {
                        throw new IllegalArgumentException("Managed dimension not found.");
                    }
                    selected = "";
                    notice = "Dimension definition deleted. Restart the server to unload it; existing world data was retained.";
                }
                case "teleport" -> {
                    String id = payload.originalId() == null ? "" : payload.originalId().trim();
                    if (id.isBlank()) throw new IllegalArgumentException("Select a loaded dimension first.");
                    var key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
                    var level = player.level().getServer().getLevel(key);
                    if (level == null) throw new IllegalArgumentException("That dimension is not loaded. Restart after creating or enabling it.");
                    var spawn = level.getWorldBorderAdjustedRespawnData(level.getRespawnData()).pos();
                    var safe = TeleportSafety.findSafeDestination(level, spawn.getX()+0.5D, spawn.getY(), spawn.getZ()+0.5D, 32);
                    if (safe.isPresent()) {
                        var d = safe.get();
                        player.teleportTo(level, d.x(), d.y(), d.z(), java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    } else {
                        player.teleportTo(level, spawn.getX()+0.5D, spawn.getY()+1.0D, spawn.getZ()+0.5D, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                    }
                    selected = id;
                    notice = "Teleported to " + id + ".";
                }
                default -> throw new IllegalArgumentException("Unknown dimension action.");
            }
        } catch (Exception exception) {
            notice = exception.getMessage() == null ? "Dimension action failed safely." : exception.getMessage();
            error = true;
        }
        if (!error && !"teleport".equals(payload.action())) {
            SimpleServerUtilities.SERVER_OPERATIONS.audit(player, "dimension." + payload.action(),
                    payload.originalId() == null ? selected : payload.originalId(), notice);
        }
        PacketDistributor.sendToPlayer(player, data(player, selected, payload.requestId(), notice, error));
    }

    private static ManagedDimensionDefinition parse(String json) {
        ManagedDimensionDefinition value = GSON.fromJson(json, ManagedDimensionDefinition.class);
        if (value == null) throw new IllegalArgumentException("Dimension definition is empty.");
        value.normalize();
        return value;
    }

    private static SsuDimensionManagerDataPayload data(
            ServerPlayer player,
            String requestedSelection,
            long requestId,
            String notice,
            boolean error
    ) {
        if (!canAdmin(player)) return SsuDimensionManagerDataPayload.denied(requestId, "Dimension administration denied.");
        List<SsuDimensionManagerDataPayload.Entry> entries = SimpleServerUtilities.DIMENSIONS.dimensionInfos().stream()
                .map(info -> new SsuDimensionManagerDataPayload.Entry(
                        info.id(), info.displayName(), info.preset(), info.loaded(), info.vanilla(), info.managed()))
                .toList();
        String selectedId = requestedSelection == null ? "" : requestedSelection.trim();
        String selectedJson = "";
        if (!selectedId.isBlank() && selectedId.startsWith("simpleserverutilities:")) {
            String managedId = selectedId.substring("simpleserverutilities:".length());
            selectedJson = SimpleServerUtilities.DIMENSIONS.find(managedId).map(GSON::toJson).orElse("");
            if (selectedJson.isBlank()) selectedId = "";
        }
        if (selectedId.isBlank()) {
            for (SsuDimensionManagerDataPayload.Entry entry : entries) {
                if (entry.managed()) {
                    selectedId = entry.id();
                    String managedId = selectedId.substring("simpleserverutilities:".length());
                    selectedJson = SimpleServerUtilities.DIMENSIONS.find(managedId).map(GSON::toJson).orElse("");
                    break;
                }
            }
        }
        return new SsuDimensionManagerDataPayload(requestId, notice, error,
                SimpleServerUtilities.DIMENSIONS.restartRequired(), selectedId, selectedJson, entries);
    }

    private static boolean canAdmin(ServerPlayer player) {
        return PermissionService.isAdmin(player)
                && PermissionService.getBoolean(player, PermissionKeys.DIMENSIONS_ADMIN, false);
    }
}
