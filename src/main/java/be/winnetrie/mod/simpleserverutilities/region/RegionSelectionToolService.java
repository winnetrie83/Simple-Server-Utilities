package be.winnetrie.mod.simpleserverutilities.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionClientTemplatePayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionClientTemplateUploadPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionToolOpenPayload;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-side controller for the GUI-first region selection tool workflow. */
public final class RegionSelectionToolService {
    private static final long OPERATION_LIMIT = RegionSelectionSchematicManager.MAX_VOLUME;

    private RegionSelectionToolService() {
    }

    public static boolean open(ServerPlayer player) {
        if (!RegionPolicy.canUseSelectionTool(player)) return false;
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) return false;
        RegionSelectionSchematicManager.Bounds bounds;
        try {
            bounds = RegionSelectionSchematicManager.bounds(selection);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        boolean canEditBlocks = canEdit(player);
        PacketDistributor.sendToPlayer(player, new RegionSelectionToolOpenPayload(
                selection.getDimension().identifier().toString(),
                selection.getPoint1().asLong(),
                selection.getPoint2().asLong(),
                bounds.volume(),
                RegionSelectionSchematicManager.MAX_VOLUME,
                RegionPolicy.canCreateRegion(player),
                be.winnetrie.mod.simpleserverutilities.minigame.MinigameSelectionService.canCreate(player),
                canEditBlocks,
                canEditBlocks && RegionSelectionSchematicManager.hasClipboard(player.getUUID()),
                canEditBlocks ? RegionSelectionSchematicManager.listServerTemplates(player.level().getServer()) : List.of()
        ));
        return true;
    }

    public static void handleAction(RegionSelectionActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        String operation = payload.operation().toLowerCase(Locale.ROOT);
        try {
            switch (operation) {
                case "copy" -> scheduleCapture(player, payload, CaptureTarget.CLIPBOARD);
                case "save_server" -> scheduleCapture(player, payload, CaptureTarget.SERVER_TEMPLATE);
                case "save_client" -> scheduleCapture(player, payload, CaptureTarget.CLIENT_TEMPLATE);
                case "paste" -> paste(player, RegionSelectionSchematicManager.clipboard(player.getUUID()), "Clipboard", payload.requestId());
                case "load_server" -> loadServer(player, payload);
                case "fill" -> fill(player, payload);
                case "rotate_left" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.ROTATE_LEFT, "Rotate left");
                case "rotate_right" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.ROTATE_RIGHT, "Rotate right");
                case "rotate_180" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.ROTATE_180, "Rotate 180 degrees");
                case "mirror_x" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.MIRROR_X, "Mirror east/west");
                case "mirror_z" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.MIRROR_Z, "Mirror north/south");
                case "flip_vertical" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.FLIP_VERTICAL, "Flip vertically");
                case "clear_blocks" -> clearBlocks(player, payload);
                case "clear_selection" -> clearSelection(player, payload.requestId());
                case "refresh" -> result(player, true, "Selection tools refreshed.", payload.requestId(), false);
                default -> result(player, false, "Unknown selection operation.", payload.requestId(), false);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            result(player, false, exception.getMessage(), payload.requestId(), false);
        }
    }

    public static void handleClientTemplateUpload(RegionSelectionClientTemplateUploadPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canEdit(player)) {
            result(player, false, "Region editing permission is required.", payload.requestId(), false);
            return;
        }
        String name;
        try {
            name = RegionSelectionSchematicManager.validateName(payload.name());
        } catch (IllegalArgumentException exception) {
            result(player, false, exception.getMessage(), payload.requestId(), false);
            return;
        }
        byte[] bytes = payload.data();
        MinecraftServer server = player.level().getServer();
        SimpleServerUtilities.STORAGE.submitTask(() -> RegionSelectionSchematicManager.decode(bytes))
                .whenComplete((template, failure) -> server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
                    if (online == null) return;
                    if (failure != null || template == null) {
                        result(online, false, "Client template could not be loaded: " + rootMessage(failure), payload.requestId(), false);
                        return;
                    }
                    paste(online, template, "Client template '" + name + "'", payload.requestId());
                }));
    }

    private static void scheduleCapture(ServerPlayer player, RegionSelectionActionPayload payload, CaptureTarget target) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        if (target != CaptureTarget.CLIPBOARD) RegionSelectionSchematicManager.validateName(payload.name());
        RegionSelectionSchematicManager.CaptureJob job = RegionSelectionSchematicManager.createCaptureJob(level, selection);
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID jobId = SimpleServerUtilities.JOBS.submit(job, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED
                    || job.template() == null) {
                result(online, false, "Selection capture " + completed.status().name().toLowerCase(Locale.ROOT)
                        + suffix(completed.error()), payload.requestId(), false);
                return;
            }
            switch (target) {
                case CLIPBOARD -> {
                    RegionSelectionSchematicManager.setClipboard(actor, job.template());
                    result(online, true, "Copied " + job.savedBlocks() + " non-air block(s) to your server clipboard.",
                            payload.requestId(), false);
                }
                case SERVER_TEMPLATE -> SimpleServerUtilities.STORAGE.submitTask(() -> {
                    RegionSelectionSchematicManager.saveServerTemplate(server, payload.name(), job.template());
                    return true;
                }).whenComplete((ignored, failure) -> server.execute(() -> {
                    ServerPlayer current = server.getPlayerList().getPlayer(actor);
                    if (current == null) return;
                    result(current, failure == null,
                            failure == null ? "Saved server template '" + payload.name() + "'."
                                    : "Server template could not be saved: " + rootMessage(failure),
                            payload.requestId(), false);
                }));
                case CLIENT_TEMPLATE -> SimpleServerUtilities.STORAGE.submitTask(() -> RegionSelectionSchematicManager.encode(job.template()))
                        .whenComplete((bytes, failure) -> server.execute(() -> {
                            ServerPlayer current = server.getPlayerList().getPlayer(actor);
                            if (current == null) return;
                            if (failure != null || bytes == null) {
                                result(current, false, "Client template could not be exported: " + rootMessage(failure),
                                        payload.requestId(), false);
                                return;
                            }
                            PacketDistributor.sendToPlayer(current,
                                    new RegionSelectionClientTemplatePayload(payload.name(), bytes, payload.requestId()));
                        }));
            }
        });
        result(player, true, "Selection capture scheduled as job " + jobId + ".", payload.requestId(), false);
    }

    private static void loadServer(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        String name = RegionSelectionSchematicManager.validateName(payload.name());
        MinecraftServer server = player.level().getServer();
        UUID actor = player.getUUID();
        SimpleServerUtilities.STORAGE.submitTask(() -> RegionSelectionSchematicManager.loadServerTemplate(server, name))
                .whenComplete((template, failure) -> server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(actor);
                    if (online == null) return;
                    if (failure != null || template == null) {
                        result(online, false, "Server template could not be loaded: " + rootMessage(failure), payload.requestId(), false);
                        return;
                    }
                    paste(online, template, "Server template '" + name + "'", payload.requestId());
                }));
        result(player, true, "Loading server template '" + name + "'…", payload.requestId(), false);
    }

    private static void paste(ServerPlayer player, RegionSelectionSchematicManager.SelectionTemplate template,
                              String label, long requestId) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        if (template == null) throw new IllegalArgumentException("Copy or load a selection template first.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        BlockPos origin = selection.getPoint1();
        RegionSelectionSchematicManager.PasteJob job = RegionSelectionSchematicManager.createPasteJob(level, origin, template);
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID jobId = SimpleServerUtilities.JOBS.submit(job, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                online.sendSystemMessage(Component.literal(label + " pasted. Changed " + job.changedBlocks() + " block(s)."));
            } else {
                online.sendSystemMessage(Component.literal(label + " paste "
                        + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error())));
            }
        });
        RegionSelectionSchematicManager.Bounds destination = job.destination();
        RegionCommands.getSelectionManager().setPoint2(player,
                new BlockPos(destination.maxX(), destination.maxY(), destination.maxZ()));
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, selection(player));
        result(player, true, label + " paste scheduled as job " + jobId + " at selection point 1.", requestId, false);
    }

    private static void transformSelection(ServerPlayer player, RegionSelectionActionPayload payload,
                                           RegionSelectionSchematicManager.SelectionTransform transform, String label) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionSelectionSchematicManager.Bounds source = RegionSelectionSchematicManager.bounds(selection);
        var dimension = selection.getDimension();
        RegionSelectionSchematicManager.CaptureJob capture = RegionSelectionSchematicManager.createCaptureJob(level, selection);
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID captureId = SimpleServerUtilities.JOBS.submit(capture, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED
                    || capture.template() == null) {
                online.sendSystemMessage(Component.literal(label + " capture "
                        + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error())));
                return;
            }
            RegionSelection current = RegionCommands.getSelectionManager().getSelection(online);
            if (!current.isComplete() || !dimension.equals(current.getDimension())
                    || !source.equals(RegionSelectionSchematicManager.bounds(current))) {
                online.sendSystemMessage(Component.literal(label
                        + " cancelled because the selection changed while it was being captured."));
                return;
            }
            RegionSelectionSchematicManager.SelectionTemplate transformed =
                    RegionSelectionSchematicManager.transform(capture.template(), transform);
            pasteTransform(online, level, source, transformed, label, payload.requestId());
        });
        result(player, true, label + " capture scheduled as job " + captureId + ".", payload.requestId(), false);
    }

    private static void pasteTransform(ServerPlayer player, ServerLevel level,
                                       RegionSelectionSchematicManager.Bounds source,
                                       RegionSelectionSchematicManager.SelectionTemplate template,
                                       String label, long requestId) {
        BlockPos origin = new BlockPos(source.minX(), source.minY(), source.minZ());
        RegionSelectionSchematicManager.PasteJob job =
                RegionSelectionSchematicManager.createTransformPasteJob(level, source, origin, template);
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID jobId = SimpleServerUtilities.JOBS.submit(job, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                online.sendSystemMessage(Component.literal(label + " completed. Changed " + job.changedBlocks() + " block(s)."));
            } else {
                online.sendSystemMessage(Component.literal(label + " "
                        + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error())));
            }
        });
        RegionSelectionSchematicManager.Bounds destination = job.destination();
        RegionSelection updated = selection(player);
        updated.setPoint1(level.dimension(), new BlockPos(destination.minX(), destination.minY(), destination.minZ()));
        updated.setPoint2(level.dimension(), new BlockPos(destination.maxX(), destination.maxY(), destination.maxZ()));
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, updated);
        result(player, true, label + " scheduled as job " + jobId + ". The old footprint is cleared safely.", requestId, false);
    }

    private static void fill(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        if (payload.inventorySlots().size() != payload.percentages().size()) {
            throw new IllegalArgumentException("The fill block list and percentages do not match.");
        }
        long total = 0L;
        java.util.Set<Integer> usedSlots = new java.util.HashSet<>();
        for (int percentage : payload.percentages()) {
            if (percentage < 1 || percentage > 100) {
                throw new IllegalArgumentException("Each fill percentage must be between 1 and 100.");
            }
            total += percentage;
        }
        if (total > 100L) throw new IllegalArgumentException("Fill percentages may total at most 100%. Current total: " + total + "%.");
        List<String> weighted = new ArrayList<>();
        for (int i = 0; i < payload.inventorySlots().size(); i++) {
            int slot = payload.inventorySlots().get(i);
            if (slot < 0 || slot >= 36) throw new IllegalArgumentException("One selected inventory slot is invalid.");
            if (!usedSlots.add(slot)) throw new IllegalArgumentException("Each inventory item may only appear once in the fill list.");
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) throw new IllegalArgumentException("One selected inventory slot is empty.");
            String blockId;
            if (stack.getItem() instanceof BlockItem blockItem) {
                blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
            } else if (stack.is(Items.WATER_BUCKET)) {
                blockId = "minecraft:water";
            } else if (stack.is(Items.LAVA_BUCKET)) {
                blockId = "minecraft:lava";
            } else {
                throw new IllegalArgumentException("Only block items, water buckets and lava buckets can be used for a fill mix.");
            }
            weighted.add(blockId + "=" + payload.percentages().get(i));
        }
        int airPercentage = 100 - (int) total;
        if (airPercentage > 0 || weighted.isEmpty()) weighted.add("minecraft:air=" + Math.max(1, airPercentage));
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionWorldEditManager.RegionFillJob job = RegionWorldEditManager.createFillJob(
                level, selection, String.join(",", weighted), OPERATION_LIMIT, true);
        scheduleWorldEdit(player, job, "Selection fill", payload.requestId());
    }

    private static void clearBlocks(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionWorldEditManager.RegionClearJob job = RegionWorldEditManager.createClearJob(level, selection, OPERATION_LIMIT);
        scheduleWorldEdit(player, job, "Selection clear", payload.requestId());
    }

    private static void scheduleWorldEdit(ServerPlayer player, be.winnetrie.mod.simpleserverutilities.core.job.SsuJob job,
                                          String label, long requestId) {
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID jobId = SimpleServerUtilities.JOBS.submit(job, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            online.sendSystemMessage(Component.literal(label + " " + completed.status().name().toLowerCase(Locale.ROOT)
                    + suffix(completed.error())));
        });
        result(player, true, label + " scheduled as job " + jobId + ".", requestId, false);
    }

    private static void clearSelection(ServerPlayer player, long requestId) {
        if (!RegionPolicy.canUseSelectionTool(player)) throw new IllegalArgumentException("Region selection permission is required.");
        RegionCommands.getSelectionManager().clear(player);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        result(player, true, "Region selection cleared.", requestId, true);
    }

    private static RegionSelection selection(ServerPlayer player) {
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) throw new IllegalArgumentException("Set both selection points first.");
        return selection;
    }

    private static ServerLevel selectionLevel(ServerPlayer player, RegionSelection selection) {
        ServerLevel level = player.level().getServer().getLevel(selection.getDimension());
        if (level == null) throw new IllegalArgumentException("Selection dimension is not loaded.");
        if (!selection.getDimension().equals(player.level().dimension())) {
            throw new IllegalArgumentException("Travel to the selection dimension before editing it.");
        }
        return level;
    }

    private static boolean canEdit(ServerPlayer player) {
        return RegionPolicy.canUseSelectionTool(player) && RegionPolicy.canEditRegion(player);
    }

    private static void result(ServerPlayer player, boolean successful, String message, long requestId, boolean selectionCleared) {
        PacketDistributor.sendToPlayer(player, new RegionSelectionActionResultPayload(
                successful,
                message == null ? "Operation failed." : message,
                requestId,
                canEdit(player) && RegionSelectionSchematicManager.hasClipboard(player.getUUID()),
                selectionCleared,
                canEdit(player) ? RegionSelectionSchematicManager.listServerTemplates(player.level().getServer()) : List.of()
        ));
    }

    private static String suffix(String error) {
        return error == null || error.isBlank() ? "." : ": " + error;
    }

    private static String rootMessage(Throwable failure) {
        if (failure == null) return "unknown error";
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum CaptureTarget { CLIPBOARD, SERVER_TEMPLATE, CLIENT_TEMPLATE }
}
