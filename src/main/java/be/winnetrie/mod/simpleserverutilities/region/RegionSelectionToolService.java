package be.winnetrie.mod.simpleserverutilities.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
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
                selection.getDimension().location().toString(),
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
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("regions")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        String operation = payload.operation().toLowerCase(Locale.ROOT);
        try {
            switch (operation) {
                case "copy" -> scheduleCapture(player, payload, CaptureTarget.CLIPBOARD);
                case "cut" -> cut(player, payload);
                case "save_server" -> scheduleCapture(player, payload, CaptureTarget.SERVER_TEMPLATE);
                case "save_client" -> scheduleCapture(player, payload, CaptureTarget.CLIENT_TEMPLATE);
                case "paste" -> paste(player, RegionSelectionSchematicManager.clipboard(player.getUUID()), "Clipboard", payload.requestId());
                case "load_server" -> loadServer(player, payload);
                case "load_snapshot" -> loadSnapshot(player, payload);
                case "fill" -> fill(player, payload);
                case "fill_water" -> fillFixed(player, payload, "minecraft:water=100", "Water fill");
                case "fill_lava" -> fillFixed(player, payload, "minecraft:lava=100", "Lava fill");
                case "replace" -> replaceBlocks(player, payload);
                case "rotate_left" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.ROTATE_LEFT, "Rotate left");
                case "rotate_right" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.ROTATE_RIGHT, "Rotate right");
                case "rotate_180" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.ROTATE_180, "Rotate 180 degrees");
                case "mirror_x" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.MIRROR_X, "Mirror east/west");
                case "mirror_z" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.MIRROR_Z, "Mirror north/south");
                case "flip_vertical" -> transformSelection(player, payload, RegionSelectionSchematicManager.SelectionTransform.FLIP_VERTICAL, "Flip vertically");
                case "offset" -> offsetSelection(player, payload);
                case "clear_blocks" -> clearBlocks(player, payload);
                case "undo" -> undo(player, payload.requestId());
                case "redo" -> redo(player, payload.requestId());
                case "clear_selection" -> clearSelection(player, payload.requestId());
                case "refresh" -> result(player, true, "World Edit refreshed.", payload.requestId(), false);
                default -> result(player, false, "Unknown World Edit operation.", payload.requestId(), false);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            result(player, false, exception.getMessage(), payload.requestId(), false);
        }
    }

    public static void handleClientTemplateUpload(RegionSelectionClientTemplateUploadPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("regions")) return;
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
        RegionSelectionSchematicManager.Bounds originalSelection = RegionSelectionSchematicManager.bounds(selection);
        BlockPos origin = selection.getPoint1();
        RegionSelectionSchematicManager.PasteJob prepared = RegionSelectionSchematicManager.createPasteJob(level, origin, template);
        RegionSelectionSchematicManager.Bounds destination = prepared.destination();
        withUndoSnapshot(player, level, destination, originalSelection, label + " paste", requestId, online -> {
            RegionSelection current = selection(online);
            ServerLevel currentLevel = selectionLevel(online, current);
            RegionSelectionSchematicManager.PasteJob job = RegionSelectionSchematicManager.createPasteJob(currentLevel,
                    new BlockPos(destination.minX(), destination.minY(), destination.minZ()), template);
            schedulePasteJob(online, currentLevel, job, label, requestId);
        });
    }

    private static void schedulePasteJob(ServerPlayer player, ServerLevel level,
                                         RegionSelectionSchematicManager.PasteJob job, String label, long requestId) {
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
        RegionSelection updated = RegionCommands.getSelectionManager().getSelection(player);
        updated.setPoint1(level.dimension(), new BlockPos(destination.minX(), destination.minY(), destination.minZ()));
        updated.setPoint2(level.dimension(), new BlockPos(destination.maxX(), destination.maxY(), destination.maxZ()));
        if (SsuModuleAccess.active("visualization")) SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, updated);
        result(player, true, label + " paste scheduled as job " + jobId + ".", requestId, false);
    }

    private static void transformSelection(ServerPlayer player, RegionSelectionActionPayload payload,
                                           RegionSelectionSchematicManager.SelectionTransform transform, String label) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionSelectionSchematicManager.Bounds source = RegionSelectionSchematicManager.bounds(selection);
        int sizeX = source.maxX() - source.minX() + 1;
        int sizeY = source.maxY() - source.minY() + 1;
        int sizeZ = source.maxZ() - source.minZ() + 1;
        int targetX = transform.swapsHorizontalAxes() ? sizeZ : sizeX;
        int targetZ = transform.swapsHorizontalAxes() ? sizeX : sizeZ;
        RegionSelectionSchematicManager.Bounds destination = new RegionSelectionSchematicManager.Bounds(
                source.minX(), source.minY(), source.minZ(),
                source.minX() + targetX - 1, source.minY() + sizeY - 1, source.minZ() + targetZ - 1);
        RegionSelectionSchematicManager.Bounds affected = union(source, destination);
        withUndoSnapshot(player, level, affected, source, label, payload.requestId(),
                online -> transformSelectionNoHistory(online, payload, transform, label));
    }

    private static void transformSelectionNoHistory(ServerPlayer player, RegionSelectionActionPayload payload,
                                           RegionSelectionSchematicManager.SelectionTransform transform, String label) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionSelectionSchematicManager.Bounds source = RegionSelectionSchematicManager.bounds(selection);
        var dimension = selection.getDimension();
        RegionSelectionSnapshotManager.CaptureJob capture = RegionSelectionSnapshotManager.createCaptureJob(level, selection);
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID captureId = SimpleServerUtilities.JOBS.submit(capture, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED
                    || capture.template() == null) {
                result(online, false, label + " capture " + completed.status().name().toLowerCase(Locale.ROOT)
                        + suffix(completed.error()), payload.requestId(), false);
                return;
            }
            RegionSelection current = RegionCommands.getSelectionManager().getSelection(online);
            if (!current.isComplete() || !dimension.equals(current.getDimension())
                    || !source.equals(RegionSelectionSchematicManager.bounds(current))) {
                result(online, false, label + " cancelled because the selection changed while it was being captured.",
                        payload.requestId(), false);
                return;
            }
            RegionSelectionSnapshotManager.SnapshotTemplate transformed =
                    RegionSelectionSnapshotManager.transform(capture.template(), transform);
            int targetX = transformed.sizeX(), targetY = transformed.sizeY(), targetZ = transformed.sizeZ();
            RegionSelectionSchematicManager.Bounds destination = new RegionSelectionSchematicManager.Bounds(
                    source.minX(), source.minY(), source.minZ(),
                    source.minX() + targetX - 1, source.minY() + targetY - 1, source.minZ() + targetZ - 1);
            RegionSelectionSchematicManager.Bounds affected = union(source, destination);
            scheduleFullSnapshotRelocation(online, level, affected, destination, transformed, label, payload.requestId());
        });
        result(player, true, label + " full snapshot capture scheduled as job " + captureId + ".", payload.requestId(), false);
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
        if (SsuModuleAccess.active("visualization")) SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, updated);
        result(player, true, label + " scheduled as job " + jobId + ". The old footprint is cleared safely.", requestId, false);
    }

    private static void fill(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        String weighted = weightedFillFromPayload(player, payload, true);
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionSelectionSchematicManager.Bounds bounds = RegionSelectionSchematicManager.bounds(selection);
        withUndoSnapshot(player, level, bounds, bounds, "Selection fill", payload.requestId(), online -> {
            RegionSelection current = selection(online);
            ServerLevel currentLevel = selectionLevel(online, current);
            RegionWorldEditManager.RegionFillJob job = RegionWorldEditManager.createFillJob(
                    currentLevel, current, weighted, OPERATION_LIMIT, true);
            scheduleWorldEdit(online, job, "Selection fill", payload.requestId());
        });
    }

    private static void fillFixed(ServerPlayer player, RegionSelectionActionPayload payload, String weighted, String label) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionSelectionSchematicManager.Bounds bounds = RegionSelectionSchematicManager.bounds(selection);
        withUndoSnapshot(player, level, bounds, bounds, label, payload.requestId(), online -> {
            RegionSelection current = selection(online);
            ServerLevel currentLevel = selectionLevel(online, current);
            RegionWorldEditManager.RegionFillJob job = RegionWorldEditManager.createFillJob(
                    currentLevel, current, weighted, OPERATION_LIMIT, true);
            scheduleWorldEdit(online, job, label, payload.requestId());
        });
    }

    private static void clearBlocks(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionSelectionSchematicManager.Bounds bounds = RegionSelectionSchematicManager.bounds(selection);
        withUndoSnapshot(player, level, bounds, bounds, "Selection clear", payload.requestId(), online -> {
            RegionSelection current = selection(online);
            ServerLevel currentLevel = selectionLevel(online, current);
            RegionWorldEditManager.RegionClearJob job = RegionWorldEditManager.createClearJob(currentLevel, current, OPERATION_LIMIT);
            scheduleWorldEdit(online, job, "Selection clear", payload.requestId());
        });
    }

    private static String weightedFillFromPayload(ServerPlayer player, RegionSelectionActionPayload payload, boolean allowAirRemainder) {
        if (payload.inventorySlots().size() != payload.percentages().size()) {
            throw new IllegalArgumentException("The block list and percentages do not match.");
        }
        if (payload.inventorySlots().isEmpty()) throw new IllegalArgumentException("Choose at least one inventory block.");
        long total = 0L;
        java.util.Set<Integer> usedSlots = new java.util.HashSet<>();
        List<String> weighted = new ArrayList<>();
        for (int i = 0; i < payload.inventorySlots().size(); i++) {
            int percentage = payload.percentages().get(i);
            if (percentage < 1 || percentage > 100) {
                throw new IllegalArgumentException("Each block percentage must be between 1 and 100.");
            }
            total += percentage;
            int slot = payload.inventorySlots().get(i);
            if (!usedSlots.add(slot)) throw new IllegalArgumentException("Each inventory item may only appear once in the list.");
            String blockId = blockIdFromInventorySlot(player, slot, true);
            weighted.add(blockId + "=" + percentage);
        }
        if (allowAirRemainder) {
            if (total > 100L) throw new IllegalArgumentException("Fill percentages may total at most 100%. Current total: " + total + "%.");
            if (total < 100L) weighted.add("minecraft:air=" + (100L - total));
        } else if (total != 100L) {
            throw new IllegalArgumentException("Replacement target percentages must total exactly 100%. Current total: " + total + "%.");
        }
        return String.join(",", weighted);
    }

    private static String blockIdFromInventorySlot(ServerPlayer player, int slot, boolean allowFluids) {
        if (slot < 0 || slot >= 36) throw new IllegalArgumentException("One selected inventory slot is invalid.");
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) throw new IllegalArgumentException("One selected inventory slot is empty.");
        if (stack.getItem() instanceof BlockItem blockItem) {
            return BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        }
        if (allowFluids && stack.is(Items.WATER_BUCKET)) return "minecraft:water";
        if (allowFluids && stack.is(Items.LAVA_BUCKET)) return "minecraft:lava";
        throw new IllegalArgumentException(allowFluids
                ? "Only block items, water buckets and lava buckets can be used here."
                : "Only block items can be used here.");
    }

    private static void replaceBlocks(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        if (payload.inventorySlots().size() != payload.percentages().size()) {
            throw new IllegalArgumentException("The replace block list and percentage list do not match.");
        }
        List<String> sources = new ArrayList<>();
        List<Integer> targetSlots = new ArrayList<>();
        List<Integer> targetPercentages = new ArrayList<>();
        java.util.Set<Integer> sourceSlotSet = new java.util.HashSet<>();
        java.util.Set<Integer> targetSlotSet = new java.util.HashSet<>();
        for (int i = 0; i < payload.inventorySlots().size(); i++) {
            int slot = payload.inventorySlots().get(i);
            int percentage = payload.percentages().get(i);
            if (percentage <= 0) {
                if (!sourceSlotSet.add(slot)) throw new IllegalArgumentException("Each source block may only appear once.");
                sources.add(blockIdFromInventorySlot(player, slot, false));
            } else {
                if (!targetSlotSet.add(slot)) throw new IllegalArgumentException("Each replacement block may only appear once.");
                targetSlots.add(slot); targetPercentages.add(percentage);
            }
        }
        if (sources.isEmpty()) throw new IllegalArgumentException("Choose at least one source block to replace.");
        if (targetSlots.isEmpty()) throw new IllegalArgumentException("Choose at least one replacement block.");
        RegionSelectionActionPayload targets = new RegionSelectionActionPayload("fill", "", targetSlots, targetPercentages, payload.requestId());
        String weightedTargets = weightedFillFromPayload(player, targets, false);
        RegionSelection selection = selection(player);
        ServerLevel level = selectionLevel(player, selection);
        RegionSelectionSchematicManager.Bounds bounds = RegionSelectionSchematicManager.bounds(selection);
        withUndoSnapshot(player, level, bounds, bounds, "Selection replace", payload.requestId(), online -> {
            RegionSelection current = selection(online);
            ServerLevel currentLevel = selectionLevel(online, current);
            RegionWorldEditManager.RegionReplaceJob job = RegionWorldEditManager.createReplaceJob(
                    currentLevel, current, sources, weightedTargets, OPERATION_LIMIT);
            scheduleWorldEdit(online, job, "Selection replace", payload.requestId());
        });
    }


    private static void withUndoSnapshot(ServerPlayer player, ServerLevel level,
                                         RegionSelectionSchematicManager.Bounds affected,
                                         RegionSelectionSchematicManager.Bounds restoreSelection,
                                         String label, long requestId,
                                         java.util.function.Consumer<ServerPlayer> operation) {
        if (affected == null || restoreSelection == null || operation == null) {
            throw new IllegalArgumentException("World Edit history capture is incomplete.");
        }
        if (affected.volume() > RegionSelectionSnapshotManager.MAX_VOLUME) {
            throw new IllegalArgumentException("This edit is too large for safe undo history (max "
                    + RegionSelectionSnapshotManager.MAX_VOLUME + " blocks).");
        }
        RegionSelection snapshotSelection = selectionForBounds(level, affected);
        RegionSelectionSnapshotManager.CaptureJob capture = RegionSelectionSnapshotManager.createCaptureJob(level, snapshotSelection);
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID jobId = SimpleServerUtilities.JOBS.submit(capture, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED
                    || capture.template() == null) {
                result(online, false, label + " could not start because its undo snapshot "
                        + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error()), requestId, false);
                return;
            }
            WorldEditHistoryManager.pushUndo(actor, new WorldEditHistoryManager.Entry(
                    level.dimension(), affected, restoreSelection, capture.template()));
            try {
                operation.accept(online);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                result(online, false, exception.getMessage(), requestId, false);
            }
        });
        result(player, true, label + " safety snapshot scheduled as job " + jobId + ".", requestId, false);
    }

    private static void cut(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        RegionSelection current = selection(player);
        ServerLevel level = selectionLevel(player, current);
        RegionSelectionSchematicManager.Bounds bounds = RegionSelectionSchematicManager.bounds(current);
        RegionSelectionSnapshotManager.CaptureJob capture = RegionSelectionSnapshotManager.createCaptureJob(level, current);
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID jobId = SimpleServerUtilities.JOBS.submit(capture, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED
                    || capture.template() == null) {
                result(online, false, "Cut capture " + completed.status().name().toLowerCase(Locale.ROOT)
                        + suffix(completed.error()), payload.requestId(), false);
                return;
            }
            WorldEditHistoryManager.pushUndo(actor, new WorldEditHistoryManager.Entry(
                    level.dimension(), bounds, bounds, capture.template()));
            RegionSelectionSchematicManager.setClipboard(actor, toBlockTemplate(capture.template()));
            RegionWorldEditManager.RegionClearJob clear = RegionWorldEditManager.createClearJob(level,
                    selectionForBounds(level, bounds), OPERATION_LIMIT);
            scheduleWorldEdit(online, clear, "Cut selection", payload.requestId());
        });
        result(player, true, "Cut capture scheduled as job " + jobId + ".", payload.requestId(), false);
    }

    private static RegionSelectionSchematicManager.SelectionTemplate toBlockTemplate(
            RegionSelectionSnapshotManager.SnapshotTemplate snapshot) {
        List<RegionSelectionSchematicManager.TemplateBlock> blocks = snapshot.blocks().stream()
                .map(block -> new RegionSelectionSchematicManager.TemplateBlock(block.relativeIndex(), block.paletteIndex()))
                .toList();
        return new RegionSelectionSchematicManager.SelectionTemplate(
                snapshot.sizeX(), snapshot.sizeY(), snapshot.sizeZ(), snapshot.palette(), blocks);
    }

    private static void loadSnapshot(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        String name = RegionSelectionSnapshotManager.validateName(payload.name());
        MinecraftServer server = player.level().getServer();
        UUID actor = player.getUUID();
        SimpleServerUtilities.STORAGE.submitTask(() -> RegionSelectionSnapshotManager.load(server, name))
                .whenComplete((snapshot, failure) -> server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(actor);
                    if (online == null) return;
                    if (failure != null || snapshot == null) {
                        result(online, false, "Snapshot could not be loaded: " + rootMessage(failure), payload.requestId(), false);
                        return;
                    }
                    try {
                        RegionSelection current = selection(online);
                        ServerLevel level = selectionLevel(online, current);
                        BlockPos origin = current.getPoint1();
                        RegionSelectionSnapshotManager.PasteJob prepared = RegionSelectionSnapshotManager.createPasteJob(level, origin, snapshot);
                        RegionSelectionSnapshotManager.Bounds raw = prepared.destination();
                        RegionSelectionSchematicManager.Bounds destination = new RegionSelectionSchematicManager.Bounds(
                                raw.minX(), raw.minY(), raw.minZ(), raw.maxX(), raw.maxY(), raw.maxZ());
                        RegionSelectionSchematicManager.Bounds restoreSelection = RegionSelectionSchematicManager.bounds(current);
                        withUndoSnapshot(online, level, destination, restoreSelection,
                                "Load snapshot '" + name + "'", payload.requestId(), ready -> {
                                    ServerLevel readyLevel = selectionLevel(ready, selection(ready));
                                    RegionSelectionSnapshotManager.PasteJob job = RegionSelectionSnapshotManager.createPasteJob(
                                            readyLevel, new BlockPos(destination.minX(), destination.minY(), destination.minZ()), snapshot);
                                    scheduleSnapshotPaste(ready, readyLevel, job, "Snapshot '" + name + "'", payload.requestId());
                                });
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        result(online, false, exception.getMessage(), payload.requestId(), false);
                    }
                }));
        result(player, true, "Loading snapshot '" + name + "'…", payload.requestId(), false);
    }

    private static void scheduleSnapshotPaste(ServerPlayer player, ServerLevel level,
                                              RegionSelectionSnapshotManager.PasteJob job,
                                              String label, long requestId) {
        UUID actor = player.getUUID();
        MinecraftServer server = player.level().getServer();
        UUID jobId = SimpleServerUtilities.JOBS.submit(job, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            online.sendSystemMessage(Component.literal(label + " "
                    + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error())));
        });
        RegionSelectionSnapshotManager.Bounds destination = job.destination();
        RegionSelection updated = RegionCommands.getSelectionManager().getSelection(player);
        updated.setPoint1(level.dimension(), new BlockPos(destination.minX(), destination.minY(), destination.minZ()));
        updated.setPoint2(level.dimension(), new BlockPos(destination.maxX(), destination.maxY(), destination.maxZ()));
        if (SsuModuleAccess.active("visualization")) SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, updated);
        result(player, true, label + " placement scheduled as job " + jobId + ".", requestId, false);
    }

    private static void offsetSelection(ServerPlayer player, RegionSelectionActionPayload payload) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        String[] parts = (payload.name() == null ? "" : payload.name().trim()).split(",");
        if (parts.length != 3) throw new IllegalArgumentException("Offset must be entered as X,Y,Z.");
        int dx, dy, dz;
        try {
            dx = Integer.parseInt(parts[0].trim()); dy = Integer.parseInt(parts[1].trim()); dz = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("Offset X, Y and Z must be whole numbers."); }
        if (dx == 0 && dy == 0 && dz == 0) throw new IllegalArgumentException("Choose a non-zero offset.");
        RegionSelection current = selection(player);
        ServerLevel level = selectionLevel(player, current);
        RegionSelectionSchematicManager.Bounds source = RegionSelectionSchematicManager.bounds(current);
        RegionSelectionSchematicManager.Bounds destination = new RegionSelectionSchematicManager.Bounds(
                source.minX() + dx, source.minY() + dy, source.minZ() + dz,
                source.maxX() + dx, source.maxY() + dy, source.maxZ() + dz);
        RegionSelectionSchematicManager.Bounds affected = union(source, destination);
        withUndoSnapshot(player, level, affected, source, "Move selection", payload.requestId(), online -> {
            RegionSelection moving = selection(online);
            ServerLevel movingLevel = selectionLevel(online, moving);
            RegionSelectionSnapshotManager.CaptureJob capture = RegionSelectionSnapshotManager.createCaptureJob(movingLevel, moving);
            UUID actor = online.getUUID(); MinecraftServer server = online.level().getServer();
            UUID captureId = SimpleServerUtilities.JOBS.submit(capture, completed -> {
                ServerPlayer ready = server.getPlayerList().getPlayer(actor);
                if (ready == null) return;
                if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED
                        || capture.template() == null) {
                    result(ready, false, "Move capture " + completed.status().name().toLowerCase(Locale.ROOT)
                            + suffix(completed.error()), payload.requestId(), false); return;
                }
                scheduleFullSnapshotRelocation(ready, movingLevel, affected, destination, capture.template(),
                        "Move selection", payload.requestId());
            });
            result(online, true, "Move selection full snapshot capture scheduled as job " + captureId + ".", payload.requestId(), false);
        });
    }

    private static void scheduleFullSnapshotRelocation(ServerPlayer player, ServerLevel level,
                                                       RegionSelectionSchematicManager.Bounds affected,
                                                       RegionSelectionSchematicManager.Bounds destination,
                                                       RegionSelectionSnapshotManager.SnapshotTemplate template,
                                                       String label, long requestId) {
        int sx = affected.maxX() - affected.minX() + 1;
        int sy = affected.maxY() - affected.minY() + 1;
        int sz = affected.maxZ() - affected.minZ() + 1;
        RegionSelectionSnapshotManager.SnapshotTemplate empty = new RegionSelectionSnapshotManager.SnapshotTemplate(
                sx, sy, sz, List.of(), List.of(), List.of());
        RegionSelectionSnapshotManager.PasteJob clear = RegionSelectionSnapshotManager.createPasteJob(level,
                new BlockPos(affected.minX(), affected.minY(), affected.minZ()), empty);
        UUID actor = player.getUUID(); MinecraftServer server = player.level().getServer();
        UUID clearId = SimpleServerUtilities.JOBS.submit(clear, cleared -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (cleared.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                result(online, false, label + " clear " + cleared.status().name().toLowerCase(Locale.ROOT)
                        + suffix(cleared.error()), requestId, false); return;
            }
            RegionSelectionSnapshotManager.PasteJob paste = RegionSelectionSnapshotManager.createPasteJob(level,
                    new BlockPos(destination.minX(), destination.minY(), destination.minZ()), template);
            scheduleSnapshotPaste(online, level, paste, label, requestId);
        });
        result(player, true, label + " safe relocation scheduled as job " + clearId + ".", requestId, false);
    }

    private static void undo(ServerPlayer player, long requestId) {
        restoreHistory(player, WorldEditHistoryManager.popUndo(player.getUUID()), true, requestId);
    }

    private static void redo(ServerPlayer player, long requestId) {
        restoreHistory(player, WorldEditHistoryManager.popRedo(player.getUUID()), false, requestId);
    }

    private static void restoreHistory(ServerPlayer player, WorldEditHistoryManager.Entry entry,
                                       boolean undo, long requestId) {
        if (!canEdit(player)) throw new IllegalArgumentException("Region editing permission is required.");
        if (entry == null) throw new IllegalArgumentException(undo ? "Nothing to undo." : "Nothing to redo.");
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null) {
            reinsertHistory(player.getUUID(), entry, undo);
            throw new IllegalArgumentException("The history dimension is not loaded.");
        }
        if (!player.level().dimension().equals(entry.dimension())) {
            reinsertHistory(player.getUUID(), entry, undo);
            throw new IllegalArgumentException("Travel to the edited dimension before using undo/redo.");
        }
        RegionSelection affected = selectionForBounds(level, entry.affectedBounds());
        RegionSelection selectedNow = RegionCommands.getSelectionManager().getSelection(player);
        RegionSelectionSchematicManager.Bounds inverseSelection = selectedNow.isComplete()
                && entry.dimension().equals(selectedNow.getDimension())
                ? RegionSelectionSchematicManager.bounds(selectedNow) : entry.affectedBounds();
        RegionSelectionSnapshotManager.CaptureJob currentCapture = RegionSelectionSnapshotManager.createCaptureJob(level, affected);
        UUID actor = player.getUUID();
        UUID captureId = SimpleServerUtilities.JOBS.submit(currentCapture, completed -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online == null) return;
            if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED
                    || currentCapture.template() == null) {
                reinsertHistory(actor, entry, undo);
                result(online, false, (undo ? "Undo" : "Redo") + " safety capture "
                        + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error()), requestId, false);
                return;
            }
            WorldEditHistoryManager.Entry inverse = new WorldEditHistoryManager.Entry(
                    entry.dimension(), entry.affectedBounds(), inverseSelection, currentCapture.template());
            if (undo) WorldEditHistoryManager.pushRedo(actor, inverse);
            else WorldEditHistoryManager.pushUndoFromRedo(actor, inverse);
            RegionSelectionSnapshotManager.PasteJob paste = RegionSelectionSnapshotManager.createPasteJob(level,
                    new BlockPos(entry.affectedBounds().minX(), entry.affectedBounds().minY(), entry.affectedBounds().minZ()),
                    entry.snapshot());
            UUID pasteId = SimpleServerUtilities.JOBS.submit(paste, restored -> {
                ServerPlayer ready = server.getPlayerList().getPlayer(actor);
                if (ready == null) return;
                if (restored.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                    setSelectionBounds(ready, entry.dimension(), entry.restoreSelectionBounds());
                    ready.sendSystemMessage(Component.literal((undo ? "Undo" : "Redo") + " completed."));
                } else {
                    ready.sendSystemMessage(Component.literal((undo ? "Undo" : "Redo") + " "
                            + restored.status().name().toLowerCase(Locale.ROOT) + suffix(restored.error())));
                }
            });
            result(online, true, (undo ? "Undo" : "Redo") + " restore scheduled as job " + pasteId + ".", requestId, false);
        });
        result(player, true, (undo ? "Undo" : "Redo") + " safety capture scheduled as job " + captureId + ".", requestId, false);
    }

    private static void reinsertHistory(UUID playerId, WorldEditHistoryManager.Entry entry, boolean undo) {
        if (undo) WorldEditHistoryManager.pushUndoFromRedo(playerId, entry);
        else WorldEditHistoryManager.pushRedo(playerId, entry);
    }

    private static RegionSelection selectionForBounds(ServerLevel level, RegionSelectionSchematicManager.Bounds bounds) {
        RegionSelection value = new RegionSelection();
        value.setPoint1(level.dimension(), new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
        value.setPoint2(level.dimension(), new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        return value;
    }

    private static void setSelectionBounds(ServerPlayer player, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                           RegionSelectionSchematicManager.Bounds bounds) {
        RegionSelection selected = RegionCommands.getSelectionManager().getSelection(player);
        selected.setPoint1(dimension, new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
        selected.setPoint2(dimension, new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        if (SsuModuleAccess.active("visualization")) SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, selected);
    }

    private static RegionSelectionSchematicManager.Bounds union(RegionSelectionSchematicManager.Bounds a,
                                                                 RegionSelectionSchematicManager.Bounds b) {
        return new RegionSelectionSchematicManager.Bounds(
                Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()),
                Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
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
        if (SsuModuleAccess.active("visualization")) SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
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
