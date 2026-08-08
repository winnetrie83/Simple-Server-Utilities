package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupSavePayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportDestination;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportSafety;
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

/** GUI-first region setup workflow opened by right-clicking the SSU Region Tool. */
public final class RegionSetupToolService {
    private static final long SELECTION_OPERATION_LIMIT = RegionSelectionSnapshotManager.MAX_VOLUME;
    private static final Map<UUID, PreviewSession> PREVIEWS = new ConcurrentHashMap<>();

    private RegionSetupToolService() {
    }

    public static void clearPreview(UUID playerId) {
        if (playerId != null) PREVIEWS.remove(playerId);
    }

    public static boolean hasActivePreview(ServerPlayer player) {
        return player != null && PREVIEWS.containsKey(player.getUUID());
    }

    public static void cancelPreview(ServerPlayer player, String reason) {
        if (player == null || PREVIEWS.remove(player.getUUID()) == null) return;
        PacketDistributor.sendToPlayer(player, clearPreviewPayload());
        if (reason != null && !reason.isBlank()) player.sendSystemMessage(Component.literal(reason), true);
    }

    private static RegionSnapshotPreviewPayload clearPreviewPayload() {
        return new RegionSnapshotPreviewPayload(false, "", "", 0L, 0, 0, 0, 0, false, List.of());
    }

    public static boolean openContext(ServerPlayer player) {
        if (!RegionPolicy.canUseSelectionTool(player)) return false;
        Region current = SimpleServerUtilities.REGIONS.getAt(player.level().dimension(), player.blockPosition());
        if (current != null && RegionPolicy.canEditRegion(player)) {
            sendEdit(player, current, "", false, 0L);
            return true;
        }
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (selection.isComplete() && RegionPolicy.canCreateRegion(player)) {
            sendCreate(player, selection, "", false, 0L);
        } else {
            sendSelect(player, "No region is active here. Mark two corners with the Region Tool or choose another region.", false, 0L);
        }
        return true;
    }

    public static void handleRequest(RegionSetupRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            String operation = payload.operation().trim().toLowerCase(Locale.ROOT);
            if ("edit".equals(operation) && !payload.regionName().isBlank()) {
                Region region = SimpleServerUtilities.REGIONS.get(payload.regionName());
                if (region == null) sendSelect(player, "That region no longer exists.", true, payload.requestId());
                else sendEdit(player, region, "", false, payload.requestId());
            } else if ("create".equals(operation)) {
                RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
                if (!selection.isComplete()) sendSelect(player, "Mark both corners with the Region Tool first.", true, payload.requestId());
                else sendCreate(player, selection, "", false, payload.requestId());
            } else if ("selection".equals(operation)) {
                sendSelect(player, "Selection and region list refreshed.", false, payload.requestId());
            } else {
                openContext(player);
            }
        });
    }

    public static void handleSave(RegionSetupSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> save(player, payload));
    }

    public static void handleAction(RegionSetupActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> action(player, payload));
    }

    private static void save(ServerPlayer player, RegionSetupSavePayload payload) {
        boolean create = "CREATE".equalsIgnoreCase(payload.mode());
        if (create && !RegionPolicy.canCreateRegion(player)) {
            sendSelect(player, "Region creation permission is required.", true, payload.requestId());
            return;
        }
        if (!create && !RegionPolicy.canEditRegion(player)) {
            sendSelect(player, "Region editing permission is required.", true, payload.requestId());
            return;
        }

        String name = payload.regionName().trim();
        if (!name.matches("[A-Za-z0-9._-]{1,64}")) {
            reopen(player, create, name, "Use 1-64 letters, numbers, dots, underscores or dashes.", true, payload.requestId());
            return;
        }

        RegionSelection selection = null;
        Region region = null;
        if (create) {
            selection = RegionCommands.getSelectionManager().getSelection(player);
            if (!selection.isComplete() || !selection.getDimension().equals(player.level().dimension())) {
                sendSelect(player, "Set both region points in this dimension first.", true, payload.requestId());
                return;
            }
            if (SimpleServerUtilities.REGIONS.get(name) != null) {
                sendCreate(player, selection, "A region with that name already exists.", true, payload.requestId());
                return;
            }
        } else {
            region = SimpleServerUtilities.REGIONS.get(name);
            if (region == null) {
                sendSelect(player, "The region no longer exists.", true, payload.requestId());
                return;
            }
        }

        // Validate every user-controlled field before creating or mutating a region. This keeps
        // failed CREATE requests transactional instead of leaving a partially configured region.
        long priceMinor = 0L;
        if (payload.rentable()) {
            try {
                priceMinor = MoneyFormat.parseMinor(payload.rentPrice().isBlank() ? "0" : payload.rentPrice(),
                        SimpleServerUtilities.ECONOMY.settings());
            } catch (IllegalArgumentException exception) {
                reopen(player, create, name, exception.getMessage(), true, payload.requestId());
                return;
            }
            if (payload.rentPeriodDays() == 0 || payload.rentPeriodDays() < -1) {
                reopen(player, create, name, "Rent period must be -1 for permanent or at least 1 day.", true, payload.requestId());
                return;
            }
        }

        String weightedPreset = create ? "" : region.getResetSettings().getWeightedPreset();
        if (payload.replacePreset()) {
            try {
                weightedPreset = buildPreset(player, payload.presetSlots(), payload.presetPercentages());
            } catch (IllegalArgumentException exception) {
                reopen(player, create, name, exception.getMessage(), true, payload.requestId());
                return;
            }
        }
        RegionResetMode resetMode = RegionResetMode.parse(payload.resetMode());
        if (payload.scheduledResetEnabled()) {
            if (payload.resetIntervalSeconds() < 10L) {
                reopen(player, create, name, "Scheduled reset interval must be at least 10 seconds.", true, payload.requestId());
                return;
            }
            if (resetMode == RegionResetMode.PRESET && weightedPreset.isBlank()) {
                reopen(player, create, name, "Choose at least one inventory block for the scheduled preset reset.", true, payload.requestId());
                return;
            }
        }

        if (create) {
            RegionOperationResult result = SimpleServerUtilities.REGIONS.create(name, selection.getDimension(),
                    selection.getPoint1(), selection.getPoint2());
            if (!result.isSuccess()) {
                sendCreate(player, selection, operationMessage(result), true, payload.requestId());
                return;
            }
            region = SimpleServerUtilities.REGIONS.get(name);
            if (region == null) {
                sendSelect(player, "The newly created region could not be loaded.", true, payload.requestId());
                return;
            }
        }

        region.setPriority(payload.priority());
        region.setBorderVisible(payload.borderVisible());
        RegionSettings settings = region.getSettings();
        settings.setAllowBlockBreak(payload.allowBreak());
        settings.setAllowBlockPlace(payload.allowPlace());
        settings.setAllowInteract(payload.allowInteract());
        settings.setAllowPvp(payload.allowPvp());
        settings.setAllowExplosions(payload.allowExplosions());
        settings.setAllowPistons(payload.allowPistons());
        settings.setAllowWaterFlow(payload.allowWater());
        settings.setAllowLavaFlow(payload.allowLava());
        settings.setAllowRedstone(payload.allowRedstone());
        settings.setAllowHoppers(payload.allowHoppers());
        settings.setAllowFireSpread(payload.allowFireSpread());
        region.setWelcomeMessage(payload.welcomeMessage());
        region.setLeaveMessage(payload.leaveMessage());

        RegionRentData rent = region.getRentData();
        rent.setRentable(payload.rentable());
        rent.setPriceMinor(priceMinor, SimpleServerUtilities.ECONOMY.settings());
        rent.setPeriodDays(payload.rentable() ? payload.rentPeriodDays() : -1);
        rent.setResetOnExpire(payload.resetOnExpire());
        rent.setResetOnUnrent(payload.resetOnUnrent());

        RegionResetSettings reset = region.getResetSettings();
        boolean scheduleChanged = reset.isEnabled() != payload.scheduledResetEnabled()
                || reset.getIntervalSeconds() != payload.resetIntervalSeconds();
        reset.setEnabled(payload.scheduledResetEnabled());
        reset.setIntervalSeconds(payload.resetIntervalSeconds());
        reset.setMode(resetMode);
        reset.setOnlyWhenEmpty(payload.resetOnlyWhenEmpty());
        if (payload.replacePreset()) reset.setWeightedPreset(weightedPreset);
        if (reset.isEnabled() && (scheduleChanged || reset.getNextResetAt() <= 0L)) {
            reset.scheduleFrom(System.currentTimeMillis());
        }
        if (!reset.isEnabled()) reset.setNextResetAt(-1L);

        SimpleServerUtilities.REGIONS.save();
        if (create) {
            RegionCommands.getSelectionManager().clear(player);
            SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        }
        SimpleServerUtilities.SERVER_OPERATIONS.audit(player, create ? "region.create" : "region.save", region.getName(),
                "priority=" + region.getPriority() + ", reset=" + region.getResetSettings().isEnabled());
        sendEdit(player, region, create ? "Region created and configured." : "Region settings saved.", false, payload.requestId());
    }

    private static void action(ServerPlayer player, RegionSetupActionPayload payload) {
        String operation = payload.operation().trim().toLowerCase(Locale.ROOT);
        Region region = null;
        try {
            if (operation.startsWith("set_point") || "clear_selection".equals(operation) || "open_create".equals(operation)) {
                selectionAction(player, operation, payload.requestId());
                return;
            }
            if (operation.startsWith("fill_selection_") || "clear_selection_blocks".equals(operation)) {
                selectionWorldEdit(player, operation, payload.requestId());
                return;
            }
            if ("save_selection_snapshot".equals(operation)) {
                captureSelectionSnapshot(player, payload.value(), payload.requestId());
                return;
            }
            if ("preview_snapshot".equals(operation)) {
                loadPreview(player, payload.value(), payload.requestId());
                return;
            }
            if (operation.startsWith("preview_")) {
                previewAction(player, operation, payload.requestId());
                return;
            }
            region = SimpleServerUtilities.REGIONS.get(payload.regionName());
            if (region == null) {
                sendSelect(player, "Region not found.", true, payload.requestId());
                return;
            }
            if (!RegionPolicy.canEditRegion(player)) {
                sendEdit(player, region, "Region editing permission is required.", true, payload.requestId());
                return;
            }
            switch (operation) {
                case "refresh" -> sendEdit(player, region, "", false, payload.requestId());
                case "teleport" -> teleportToRegion(player, region, payload.requestId());
                case "set_spawn" -> {
                    if (!region.contains(player.level().dimension(), player.blockPosition())) throw new IllegalArgumentException("Stand inside the region to set its spawn.");
                    region.setSpawn(player.blockPosition(), player.getYRot(), player.getXRot());
                    SimpleServerUtilities.REGIONS.save();
                    sendEdit(player, region, "Region spawn set to your current position.", false, payload.requestId());
                }
                case "clear_spawn" -> {
                    region.clearSpawn(); SimpleServerUtilities.REGIONS.save();
                    sendEdit(player, region, "Region spawn cleared.", false, payload.requestId());
                }
                case "toggle_region_selection" -> toggleRegionSelection(player, region, payload.requestId());
                case "capture_snapshot" -> captureSnapshot(player, region, payload.requestId());
                case "reset_now" -> {
                    RegionResetScheduler.Result result = RegionResetScheduler.triggerNow(player, region);
                    if (result.success()) SimpleServerUtilities.SERVER_OPERATIONS.audit(player, "region.reset", region.getName(), result.message());
                    sendEdit(player, region, result.message(), !result.success(), payload.requestId());
                }
                case "redefine" -> redefine(player, region, payload.requestId());
                case "delete_confirm" -> delete(player, region, payload.requestId());
                case "add_manager" -> access(player, region, payload.value(), true, true, payload.requestId());
                case "remove_manager" -> access(player, region, payload.value(), true, false, payload.requestId());
                case "add_member" -> access(player, region, payload.value(), false, true, payload.requestId());
                case "remove_member" -> access(player, region, payload.value(), false, false, payload.requestId());
                default -> sendEdit(player, region, "Unknown region setup action.", true, payload.requestId());
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String message = exception.getMessage() == null ? "The region operation failed safely." : exception.getMessage();
            if (region != null) sendEdit(player, region, message, true, payload.requestId());
            else sendCurrent(player, message, true, payload.requestId());
        }
    }

    private static void selectionAction(ServerPlayer player, String operation, long requestId) {
        RegionSelectionManager manager = RegionCommands.getSelectionManager();
        switch (operation) {
            case "set_point1_here" -> manager.setPoint1(player, player.blockPosition());
            case "set_point2_here" -> manager.setPoint2(player, player.blockPosition());
            case "clear_selection" -> manager.clear(player);
            case "open_create" -> {
                RegionSelection selection = manager.getSelection(player);
                if (!selection.isComplete()) { sendSelect(player, "Mark both corners with the Region Tool first.", true, requestId); return; }
                sendCreate(player, selection, "", false, requestId); return;
            }
            default -> { sendSelect(player, "Unknown selection action.", true, requestId); return; }
        }
        RegionSelection selection = manager.getSelection(player);
        if (selection.isComplete()) SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, selection);
        else if (!selection.isComplete()) SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        sendSelect(player, "Selection updated.", false, requestId);
    }

    private static void selectionWorldEdit(ServerPlayer player, String operation, long requestId) {
        if (!RegionPolicy.canUseSelectionTool(player) || !RegionPolicy.canEditRegion(player)) {
            sendSelect(player, "Region selection editing permission is required.", true, requestId);
            return;
        }
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) {
            sendSelect(player, "Mark both corners with the Region Tool first.", true, requestId);
            return;
        }
        ServerLevel level = player.level().getServer().getLevel(selection.getDimension());
        if (level == null || !selection.getDimension().equals(player.level().dimension())) {
            sendSelect(player, "Travel to the selection dimension before editing it.", true, requestId);
            return;
        }
        be.winnetrie.mod.simpleserverutilities.core.job.SsuJob job;
        String label;
        if ("clear_selection_blocks".equals(operation) || "fill_selection_air".equals(operation)) {
            job = RegionWorldEditManager.createClearJob(level, selection, SELECTION_OPERATION_LIMIT);
            label = "Selection clear";
        } else {
            String block = switch (operation) {
                case "fill_selection_water" -> "minecraft:water=100";
                case "fill_selection_lava" -> "minecraft:lava=100";
                default -> throw new IllegalArgumentException("Unknown selection fill operation.");
            };
            job = RegionWorldEditManager.createFillJob(level, selection, block, SELECTION_OPERATION_LIMIT, true);
            label = operation.endsWith("water") ? "Water fill" : "Lava fill";
        }
        scheduleSelectionJob(player, job, label, requestId);
    }

    private static void scheduleSelectionJob(ServerPlayer player,
                                             be.winnetrie.mod.simpleserverutilities.core.job.SsuJob job,
                                             String label, long requestId) {
        MinecraftServer server = player.level().getServer();
        UUID actor = player.getUUID();
        UUID jobId = SimpleServerUtilities.JOBS.submit(job, completed -> server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(actor);
            if (online != null) sendCurrent(online, label + " "
                    + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error()),
                    completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED,
                    requestId);
        }));
        sendCurrent(player, label + " scheduled as job " + jobId + ".", false, requestId);
    }

    private static void captureSelectionSnapshot(ServerPlayer player, String rawName, long requestId) {
        if (!RegionPolicy.canUseSelectionTool(player) || !RegionPolicy.canEditRegion(player)) {
            sendSelect(player, "Region selection editing permission is required.", true, requestId);
            return;
        }
        String name;
        try { name = RegionSelectionSnapshotManager.validateName(rawName); }
        catch (IllegalArgumentException exception) { sendCurrent(player, exception.getMessage(), true, requestId); return; }
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) { sendSelect(player, "Mark both corners with the Region Tool first.", true, requestId); return; }
        ServerLevel level = player.level().getServer().getLevel(selection.getDimension());
        if (level == null || !selection.getDimension().equals(player.level().dimension())) {
            sendCurrent(player, "Travel to the selection dimension before saving it.", true, requestId); return;
        }
        RegionSelectionSnapshotManager.CaptureJob job = RegionSelectionSnapshotManager.createCaptureJob(level, selection);
        MinecraftServer server = player.level().getServer(); UUID actor = player.getUUID();
        UUID jobId = SimpleServerUtilities.JOBS.submit(job, completed -> {
            if (completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED || job.template() == null) {
                server.execute(() -> { ServerPlayer online = server.getPlayerList().getPlayer(actor); if (online != null)
                    sendCurrent(online, "Snapshot capture " + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error()), true, requestId); });
                return;
            }
            SimpleServerUtilities.STORAGE.submitTask(() -> { RegionSelectionSnapshotManager.save(server, name, job.template()); return true; })
                    .whenComplete((saved, failure) -> server.execute(() -> {
                        ServerPlayer online = server.getPlayerList().getPlayer(actor);
                        if (online != null) sendCurrent(online, failure == null
                                ? "Selection snapshot '" + name + "' saved with inventories and structural entities."
                                : "Selection snapshot could not be saved: " + rootMessage(failure), failure != null, requestId);
                    }));
        });
        sendCurrent(player, "Full selection snapshot capture scheduled as job " + jobId + ".", false, requestId);
    }

    private static void loadPreview(ServerPlayer player, String rawName, long requestId) {
        if (!RegionPolicy.canEditRegion(player)) { sendCurrent(player, "Region editing permission is required.", true, requestId); return; }
        String name;
        try { name = RegionSelectionSnapshotManager.validateName(rawName); }
        catch (IllegalArgumentException exception) { sendCurrent(player, exception.getMessage(), true, requestId); return; }
        MinecraftServer server = player.level().getServer(); UUID actor = player.getUUID();
        SimpleServerUtilities.STORAGE.submitTask(() -> RegionSelectionSnapshotManager.load(server, name))
                .whenComplete((template, failure) -> server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(actor);
                    if (online == null) return;
                    if (failure != null || template == null) {
                        sendCurrent(online, "Snapshot preview could not be loaded: " + rootMessage(failure), true, requestId); return;
                    }
                    double yawRadians = Math.toRadians(online.getYRot());
                    int dx = (int)Math.round(-Math.sin(yawRadians) * 5.0D);
                    int dz = (int)Math.round(Math.cos(yawRadians) * 5.0D);
                    BlockPos origin = online.blockPosition().offset(dx, 0, dz);
                    PreviewSession session = new PreviewSession(name, online.level().dimension().identifier().toString(), origin, template);
                    PREVIEWS.put(actor, session);
                    sendPreview(online, session);
                    online.sendSystemMessage(Component.literal("Ghost preview loaded. Use the placement controls or Free mode."), true);
                }));
    }

    private static void previewAction(ServerPlayer player, String operation, long requestId) {
        PreviewSession session = PREVIEWS.get(player.getUUID());
        if (session == null) {
            PacketDistributor.sendToPlayer(player, clearPreviewPayload());
            sendCurrent(player, "No selection snapshot preview is active.", true, requestId);
            return;
        }
        switch (operation) {
            case "preview_close", "preview_cancel" -> {
                PREVIEWS.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, clearPreviewPayload());
                sendCurrent(player, "Snapshot preview cancelled.", false, requestId);
                return;
            }
            case "preview_move_x_plus", "preview_east" -> session.move(1,0,0);
            case "preview_move_x_minus", "preview_west" -> session.move(-1,0,0);
            case "preview_move_y_plus", "preview_up" -> session.move(0,1,0);
            case "preview_move_y_minus", "preview_down" -> session.move(0,-1,0);
            case "preview_move_z_plus", "preview_south" -> session.move(0,0,1);
            case "preview_move_z_minus", "preview_north" -> session.move(0,0,-1);
            case "preview_rotate_left" -> session.transform(RegionSelectionSchematicManager.SelectionTransform.ROTATE_LEFT);
            case "preview_rotate_right" -> session.transform(RegionSelectionSchematicManager.SelectionTransform.ROTATE_RIGHT);
            case "preview_rotate_180" -> session.transform(RegionSelectionSchematicManager.SelectionTransform.ROTATE_180);
            case "preview_mirror_x" -> session.transform(RegionSelectionSchematicManager.SelectionTransform.MIRROR_X);
            case "preview_mirror_z" -> session.transform(RegionSelectionSchematicManager.SelectionTransform.MIRROR_Z);
            case "preview_confirm" -> { confirmPreview(player, session, requestId); return; }
            default -> {
                player.sendSystemMessage(Component.literal("Unknown preview action."), true);
                return;
            }
        }
        sendPreview(player, session);
    }

    private static void confirmPreview(ServerPlayer player, PreviewSession session, long requestId) {
        if (!RegionPolicy.canEditRegion(player)) {
            sendCurrent(player, "Region editing permission is required.", true, requestId);
            return;
        }
        if (!player.level().dimension().identifier().toString().equals(session.dimension)) {
            sendCurrent(player, "Travel to the preview dimension before placing the snapshot.", true, requestId); return;
        }
        RegionSelectionSnapshotManager.PasteJob job;
        try { job = RegionSelectionSnapshotManager.createPasteJob(player.level(), session.origin, session.template); }
        catch (IllegalArgumentException exception) { sendCurrent(player, exception.getMessage(), true, requestId); return; }
        PREVIEWS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, clearPreviewPayload());
        RegionSelectionSnapshotManager.Bounds destination = job.destination();
        RegionSelectionManager selections = RegionCommands.getSelectionManager();
        selections.setPoint1(player, new BlockPos(destination.minX(), destination.minY(), destination.minZ()));
        selections.setPoint2(player, new BlockPos(destination.maxX(), destination.maxY(), destination.maxZ()));
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, selections.getSelection(player));
        scheduleSelectionJob(player, job, "Snapshot placement", requestId);
    }

    private static void sendPreview(ServerPlayer player, PreviewSession session) {
        List<RegionSnapshotPreviewPayload.PreviewBlock> preview = new ArrayList<>();
        List<RegionSelectionSnapshotManager.SnapshotBlock> blocks = session.template.blocks();
        int step = Math.max(1, (int)Math.ceil(blocks.size() / (double)RegionSnapshotPreviewPayload.MAX_BLOCKS));
        for (int i = 0; i < blocks.size() && preview.size() < RegionSnapshotPreviewPayload.MAX_BLOCKS; i += step) {
            var block = blocks.get(i);
            String palette = session.template.palette().get(block.paletteIndex());
            int rgb = 0x303030 | (palette.hashCode() & 0x00CFCFCF);
            preview.add(new RegionSnapshotPreviewPayload.PreviewBlock(block.relativeIndex(), 0x50000000 | rgb));
        }
        PacketDistributor.sendToPlayer(player, new RegionSnapshotPreviewPayload(true, session.name, session.dimension,
                session.origin.asLong(), session.template.sizeX(), session.template.sizeY(), session.template.sizeZ(),
                blocks.size(), step > 1, preview));
    }

    private static void teleportToRegion(ServerPlayer player, Region region, long requestId) {
        ServerLevel level = player.level().getServer().getLevel(region.getDimension());
        if (level == null) { sendEdit(player, region, "Region dimension is not loaded.", true, requestId); return; }
        BlockPos preferred = region.getSpawnPos();
        float yaw = preferred == null ? player.getYRot() : region.getSpawnYaw();
        float pitch = preferred == null ? player.getXRot() : region.getSpawnPitch();
        java.util.Optional<TeleportDestination> destination = java.util.Optional.empty();
        if (preferred != null) destination = TeleportSafety.findSafeDestination(level, preferred.getX()+0.5, preferred.getY(), preferred.getZ()+0.5, 12);
        int centerX = (region.getMinX()+region.getMaxX())/2, centerZ = (region.getMinZ()+region.getMaxZ())/2;
        if (destination.isEmpty()) {
            for (int radius=0; radius<=16 && destination.isEmpty(); radius++) {
                for (int dx=-radius; dx<=radius && destination.isEmpty(); dx++) {
                    for (int dz=-radius; dz<=radius && destination.isEmpty(); dz++) {
                        if (radius>0 && Math.abs(dx)!=radius && Math.abs(dz)!=radius) continue;
                        int x=centerX+dx,z=centerZ+dz;
                        if (x<region.getMinX()||x>region.getMaxX()||z<region.getMinZ()||z>region.getMaxZ()) continue;
                        destination=TeleportSafety.findSafeDestination(level,x+0.5,region.getMaxY()+1.0,z+0.5,Math.max(16,region.getMaxY()-region.getMinY()+8));
                    }
                }
            }
        }
        if (destination.isEmpty()) { sendEdit(player, region, "No safe teleport location was found in or directly above the region.", true, requestId); return; }
        TeleportDestination target=destination.get();
        player.teleportTo(level,target.x(),target.y(),target.z(),Set.of(),yaw,pitch,true);
        sendEdit(player, region, "Teleported to region '"+region.getName()+"'.", false, requestId);
    }

    private static void toggleRegionSelection(ServerPlayer player, Region region, long requestId) {
        RegionSelectionManager manager = RegionCommands.getSelectionManager();
        RegionSelection selection = manager.getSelection(player);
        BlockPos min = new BlockPos(region.getMinX(), region.getMinY(), region.getMinZ());
        BlockPos max = new BlockPos(region.getMaxX(), region.getMaxY(), region.getMaxZ());
        boolean selected = selection.isComplete()
                && region.getDimension().equals(selection.getDimension())
                && sameBounds(selection.getPoint1(), selection.getPoint2(), min, max);
        if (selected) {
            manager.clear(player);
            SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
            sendEdit(player, region, "Region selection cleared.", false, requestId);
            return;
        }
        selection.setPoint1(region.getDimension(), min);
        selection.setPoint2(region.getDimension(), max);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, selection);
        sendEdit(player, region, "Region bounds selected in the Region Tool.", false, requestId);
    }

    private static boolean sameBounds(BlockPos firstA, BlockPos secondA, BlockPos firstB, BlockPos secondB) {
        return Math.min(firstA.getX(), secondA.getX()) == Math.min(firstB.getX(), secondB.getX())
                && Math.min(firstA.getY(), secondA.getY()) == Math.min(firstB.getY(), secondB.getY())
                && Math.min(firstA.getZ(), secondA.getZ()) == Math.min(firstB.getZ(), secondB.getZ())
                && Math.max(firstA.getX(), secondA.getX()) == Math.max(firstB.getX(), secondB.getX())
                && Math.max(firstA.getY(), secondA.getY()) == Math.max(firstB.getY(), secondB.getY())
                && Math.max(firstA.getZ(), secondA.getZ()) == Math.max(firstB.getZ(), secondB.getZ());
    }

    private static void captureSnapshot(ServerPlayer player, Region region, long requestId) {
        RegionMutationGuard.Check safety = RegionMutationGuard.saveSnapshot(region);
        if (!safety.allowed()) { sendEdit(player, region, safety.message(), true, requestId); return; }
        ServerLevel level = player.level().getServer().getLevel(region.getDimension());
        if (level == null) { sendEdit(player, region, "Region dimension is not loaded.", true, requestId); return; }
        try {
            var job = SimpleServerUtilities.REGION_SNAPSHOTS.createCaptureJob(level, region);
            MinecraftServer server = player.level().getServer(); UUID actor = player.getUUID();
            UUID id = SimpleServerUtilities.JOBS.submit(job, completed -> server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(actor);
                if (online != null) sendEdit(online, region, "Snapshot capture "
                        + completed.status().name().toLowerCase(Locale.ROOT) + suffix(completed.error()),
                        completed.status() != be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED, requestId);
            }));
            sendEdit(player, region, "Snapshot capture scheduled as job " + id + ".", false, requestId);
        } catch (IOException | IllegalStateException exception) {
            sendEdit(player, region, "Snapshot could not be scheduled: " + exception.getMessage(), true, requestId);
        }
    }

    private static void redefine(ServerPlayer player, Region region, long requestId) {
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        if (!selection.isComplete()) { sendEdit(player, region, "Set both selection points before redefining.", true, requestId); return; }
        RegionMutationGuard.Check safety = RegionMutationGuard.redefine(region);
        if (!safety.allowed()) { sendEdit(player, region, safety.message(), true, requestId); return; }
        RegionOperationResult result = SimpleServerUtilities.REGIONS.redefine(region.getName(), selection.getDimension(), selection.getPoint1(), selection.getPoint2());
        if (!result.isSuccess()) { sendEdit(player, region, operationMessage(result), true, requestId); return; }
        try { SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(region.getName(), "redefined"); }
        catch (IOException exception) { SimpleServerUtilities.LOGGER.warn("Could not archive redefined region snapshot {}", region.getName(), exception); }
        RegionCommands.getSelectionManager().clear(player); SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
        sendEdit(player, SimpleServerUtilities.REGIONS.get(region.getName()), "Region bounds redefined.", false, requestId);
    }

    private static void delete(ServerPlayer player, Region region, long requestId) {
        if (!RegionPolicy.canDeleteRegion(player)) { sendEdit(player, region, "Region delete permission is required.", true, requestId); return; }
        RegionMutationGuard.Check safety = RegionMutationGuard.delete(region);
        if (!safety.allowed()) { sendEdit(player, region, safety.message(), true, requestId); return; }
        try { SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(region.getName(), "deleted"); }
        catch (IOException exception) { sendEdit(player, region, "The snapshot could not be archived safely: " + exception.getMessage(), true, requestId); return; }
        if (!SimpleServerUtilities.REGIONS.delete(region.getName())) { sendEdit(player, region, "Region could not be deleted.", true, requestId); return; }
        sendSelect(player, "Region '" + region.getName() + "' deleted.", false, requestId);
    }

    private static void access(ServerPlayer actor, Region region, String playerName, boolean manager, boolean add, long requestId) {
        ServerPlayer target = actor.level().getServer().getPlayerList().getPlayerByName(playerName.trim());
        if (target == null) { sendEdit(actor, region, "That player must be online.", true, requestId); return; }
        if (manager) { if (add) region.addManager(target.getUUID()); else region.removeManager(target.getUUID()); }
        else { if (add) region.addMember(target.getUUID()); else region.removeMember(target.getUUID()); }
        SimpleServerUtilities.REGIONS.save();
        sendEdit(actor, region, (manager ? "Manager" : "Member") + (add ? " added: " : " removed: ") + target.getName().getString() + ".", false, requestId);
    }

    private static String buildPreset(ServerPlayer player, List<Integer> slots, List<Integer> percentages) {
        if (slots.size() != percentages.size()) throw new IllegalArgumentException("Preset blocks and percentages do not match.");
        if (slots.isEmpty()) return "";
        int total = 0; Set<Integer> used = new HashSet<>(); List<String> weighted = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i), percentage = percentages.get(i);
            if (slot < 0 || slot >= 36 || !used.add(slot)) throw new IllegalArgumentException("One preset inventory slot is invalid or duplicated.");
            if (percentage < 1 || percentage > 100) throw new IllegalArgumentException("Preset percentages must be between 1 and 100.");
            total += percentage; if (total > 100) throw new IllegalArgumentException("Preset percentages may total at most 100%.");
            ItemStack stack = player.getInventory().getItem(slot); if (stack.isEmpty()) throw new IllegalArgumentException("One preset inventory slot is empty.");
            String blockId;
            if (stack.getItem() instanceof BlockItem blockItem) blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
            else if (stack.is(Items.WATER_BUCKET)) blockId = "minecraft:water";
            else if (stack.is(Items.LAVA_BUCKET)) blockId = "minecraft:lava";
            else throw new IllegalArgumentException("Preset entries must be block items or water/lava buckets.");
            weighted.add(blockId + "=" + percentage);
        }
        if (total < 100) weighted.add("minecraft:air=" + (100 - total));
        return String.join(",", weighted);
    }

    private static void sendCurrent(ServerPlayer player, String notice, boolean error, long requestId) {
        // Selection/snapshot jobs must not unexpectedly jump back into the physically local
        // region editor. Keep the administrator on the shared Selection page instead.
        sendSelect(player, notice, error, requestId);
    }

    private static List<RegionSetupOpenPayload.RegionEntry> regionEntries(ServerPlayer player) {
        if (!RegionPolicy.canEditRegion(player)) return List.of();
        return SimpleServerUtilities.REGIONS.getAll().stream()
                .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(RegionSetupOpenPayload.MAX_REGIONS)
                .map(region -> new RegionSetupOpenPayload.RegionEntry(region.getName(),
                        region.getDimension().identifier().toString(), region.getVolume(), region.getPriority(),
                        region.getSpawnPos()!=null, region.getSpawnPos()==null?0L:region.getSpawnPos().asLong()))
                .toList();
    }

    private static SelectionSummary selectionSummary(ServerPlayer player) {
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        boolean point1 = selection.getPoint1() != null;
        boolean point2 = selection.getPoint2() != null;
        long selectedVolume = selection.isComplete() ? volume(selection.getPoint1(), selection.getPoint2()) : 0L;
        String dimension = selection.getDimension() == null ? player.level().dimension().identifier().toString()
                : selection.getDimension().identifier().toString();
        return new SelectionSummary(dimension, point1, point1 ? selection.getPoint1().asLong() : 0L,
                point2, point2 ? selection.getPoint2().asLong() : 0L, selectedVolume);
    }

    private static String localRegionName(ServerPlayer player) {
        Region local = SimpleServerUtilities.REGIONS.getAt(player.level().dimension(), player.blockPosition());
        return local == null ? "" : local.getName();
    }

    private static String rootMessage(Throwable failure) {
        if (failure == null) return "unknown error";
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void reopen(ServerPlayer player, boolean create, String name, String notice, boolean error, long requestId) {
        if (create) {
            RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
            if (selection.isComplete()) sendCreate(player, selection, notice, error, requestId); else sendSelect(player, notice, error, requestId);
        } else {
            Region region = SimpleServerUtilities.REGIONS.get(name);
            if (region == null) sendSelect(player, notice, error, requestId); else sendEdit(player, region, notice, error, requestId);
        }
    }

    private static void sendSelect(ServerPlayer player, String notice, boolean error, long requestId) {
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        boolean p1 = selection.getPoint1() != null, p2 = selection.getPoint2() != null;
        long volume = selection.isComplete() ? volume(selection.getPoint1(), selection.getPoint2()) : 0L;
        PacketDistributor.sendToPlayer(player, emptyPayload(player,"SELECT", notice, error, requestId, "", player.level().dimension().identifier().toString(),
                p1, p1 ? selection.getPoint1().asLong() : 0L, p2, p2 ? selection.getPoint2().asLong() : 0L, volume,
                RegionPolicy.canCreateRegion(player), RegionPolicy.canEditRegion(player), RegionPolicy.canDeleteRegion(player)));
    }

    private static void sendCreate(ServerPlayer player, RegionSelection selection, String notice, boolean error, long requestId) {
        PacketDistributor.sendToPlayer(player, emptyPayload(player,"CREATE", notice, error, requestId, "", selection.getDimension().identifier().toString(),
                true, selection.getPoint1().asLong(), true, selection.getPoint2().asLong(), volume(selection.getPoint1(), selection.getPoint2()),
                RegionPolicy.canCreateRegion(player), RegionPolicy.canEditRegion(player), RegionPolicy.canDeleteRegion(player)));
    }

    private static void sendEdit(ServerPlayer player, Region region, String notice, boolean error, long requestId) {
        if (region == null) { sendSelect(player, notice, true, requestId); return; }
        RegionSettings s = region.getSettings(); RegionRentData rent = region.getRentData(); RegionResetSettings reset = region.getResetSettings();
        BlockPos spawn = region.getSpawnPos();
        SelectionSummary selection = selectionSummary(player);
        PacketDistributor.sendToPlayer(player, new RegionSetupOpenPayload(
                "EDIT", notice, error, requestId, region.getName(), region.getDimension().identifier().toString(),
                true, new BlockPos(region.getMinX(), region.getMinY(), region.getMinZ()).asLong(),
                true, new BlockPos(region.getMaxX(), region.getMaxY(), region.getMaxZ()).asLong(), region.getVolume(),
                region.getPriority(), region.isBorderVisible(),
                s.isAllowBlockBreak(), s.isAllowBlockPlace(), s.isAllowInteract(), s.isAllowPvp(), s.isAllowExplosions(),
                s.isAllowPistons(), s.isAllowWaterFlow(), s.isAllowLavaFlow(), s.isAllowRedstone(), s.isAllowHoppers(), s.isAllowFireSpread(),
                region.getWelcomeMessage(), region.getLeaveMessage(), spawn != null, spawn == null ? 0L : spawn.asLong(),
                region.getSpawnYaw(), region.getSpawnPitch(), rent.isRentable(),
                MoneyFormat.format(rent.getPriceMinor(SimpleServerUtilities.ECONOMY.settings()), SimpleServerUtilities.ECONOMY.settings()),
                rent.getPeriodDays(), rent.isResetOnExpire(), rent.isResetOnUnrent(), region.getManagers().size(), region.getMembers().size(),
                SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName()), reset.isEnabled(), reset.getIntervalSeconds(),
                reset.getMode().name(), reset.isOnlyWhenEmpty(), presetSummary(reset.getWeightedPreset()), reset.getNextResetAt(), reset.getLastResetAt(),
                RegionPolicy.canCreateRegion(player), RegionPolicy.canEditRegion(player), RegionPolicy.canDeleteRegion(player),
                selection.dimension(), selection.hasPoint1(), selection.point1(), selection.hasPoint2(), selection.point2(), selection.volume(),
                localRegionName(player), regionEntries(player), RegionSelectionSnapshotManager.list(player.level().getServer()),
                PREVIEWS.containsKey(player.getUUID()), PREVIEWS.containsKey(player.getUUID()) ? PREVIEWS.get(player.getUUID()).name : ""));
    }

    private static RegionSetupOpenPayload emptyPayload(ServerPlayer player,String mode,String notice,boolean error,long requestId,String name,String dimension,
                                                        boolean hasP1,long p1,boolean hasP2,long p2,long volume,
                                                        boolean canCreate,boolean canEdit,boolean canDelete) {
        SelectionSummary selection = selectionSummary(player);
        return new RegionSetupOpenPayload(mode,notice,error,requestId,name,dimension,hasP1,p1,hasP2,p2,volume,0,false,
                false,false,false,false,false,false,false,false,true,false,false,"","",false,0L,0F,0F,
                false,"0",-1,true,true,0,0,false,false,RegionResetSettings.DEFAULT_INTERVAL_SECONDS,
                RegionResetMode.SNAPSHOT.name(),true,"",-1L,-1L,canCreate,canEdit,canDelete,
                selection.dimension(), selection.hasPoint1(), selection.point1(), selection.hasPoint2(), selection.point2(), selection.volume(),
                localRegionName(player), regionEntries(player), RegionSelectionSnapshotManager.list(player.level().getServer()),
                PREVIEWS.containsKey(player.getUUID()), PREVIEWS.containsKey(player.getUUID()) ? PREVIEWS.get(player.getUUID()).name : "");
    }

    private static String presetSummary(String weighted) {
        if (weighted == null || weighted.isBlank()) return "No block preset saved";
        return weighted.length() <= 180 ? weighted : weighted.substring(0, 179) + "…";
    }
    private static long volume(BlockPos a, BlockPos b) {
        return ((long)Math.abs(a.getX()-b.getX())+1L)*((long)Math.abs(a.getY()-b.getY())+1L)*((long)Math.abs(a.getZ()-b.getZ())+1L);
    }
    private static String operationMessage(RegionOperationResult result) {
        return switch (result.getType()) {
            case NAME_ALREADY_EXISTS -> "A region with that name already exists.";
            case OVERLAPS_PLAYER_CLAIM -> "The selection overlaps a player claim: " + result.getDetails();
            case INVALID_REGION_OVERLAP -> "The selection overlaps another region incorrectly: " + result.getDetails();
            case REGION_NOT_FOUND -> "Region not found: " + result.getDetails();
            case SUCCESS -> "The region operation failed.";
        };
    }
    private record SelectionSummary(String dimension, boolean hasPoint1, long point1, boolean hasPoint2, long point2, long volume) { }

    private static final class PreviewSession {
        private final String name;
        private final String dimension;
        private BlockPos origin;
        private RegionSelectionSnapshotManager.SnapshotTemplate template;
        private PreviewSession(String name,String dimension,BlockPos origin,RegionSelectionSnapshotManager.SnapshotTemplate template){
            this.name=name;this.dimension=dimension;this.origin=origin.immutable();this.template=template;
        }
        private void move(int x,int y,int z){origin=origin.offset(x,y,z);}
        private void transform(RegionSelectionSchematicManager.SelectionTransform transform){
            template=RegionSelectionSnapshotManager.transform(template,transform);
        }
    }

    private static String suffix(String error) { return error == null || error.isBlank() ? "." : ": " + error; }
}
