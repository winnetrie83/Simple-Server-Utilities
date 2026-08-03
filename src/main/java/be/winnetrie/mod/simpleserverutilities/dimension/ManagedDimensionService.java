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
                default -> throw new IllegalArgumentException("Unknown dimension action.");
            }
        } catch (Exception exception) {
            notice = exception.getMessage() == null ? "Dimension action failed safely." : exception.getMessage();
            error = true;
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
