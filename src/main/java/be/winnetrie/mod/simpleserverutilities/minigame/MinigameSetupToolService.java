package be.winnetrie.mod.simpleserverutilities.minigame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolConfigurePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupVisualPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.region.RegionOperationResult;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelection;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionSchematicManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative setup workflow for creating and physically maintaining minigame arenas. */
public final class MinigameSetupToolService {
    private static final Set<String> PENDING_SNAPSHOTS = ConcurrentHashMap.newKeySet();
    private static final String LOBBY_SETUP_BANNER = "minecraft:green_banner";
    private static final String SPECTATOR_SETUP_BANNER = "minecraft:purple_banner";
    private static final String PLAYER_SETUP_BANNER = "minecraft:yellow_banner";
    private static final String NODE_RESPAWN_SETUP_BANNER = "minecraft:orange_banner";

    private MinigameSetupToolService() {
    }

    public static boolean canAdmin(ServerPlayer player) {
        return player != null && Config.ENABLE_MINIGAMES.get()
                && SimpleServerUtilities.CORE.modules().isActive("minigames")
                && PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false);
    }

    public static void clearRuntime() {
        PENDING_SNAPSHOTS.clear();
        SimpleServerUtilities.MINIGAME_SETUP_TOOLS.clear();
    }

    public static void open(ServerPlayer player) {
        open(player, "", false, 0L);
    }

    public static void open(ServerPlayer player, String notice, boolean error, long requestId) {
        if (!canAdmin(player)) {
            player.sendSystemMessage(Component.literal("Minigame administrator permission is required."));
            return;
        }
        MinigameSetupToolManager.Session session = SimpleServerUtilities.MINIGAME_SETUP_TOOLS.session(player);
        normalizeTarget(session);
        sendSetupVisuals(player, session);
        PacketDistributor.sendToPlayer(player, payload(player, session, notice, error, requestId));
    }

    public static void handleConfigure(MinigameSetupToolConfigurePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> configure(player, payload));
    }

    private static void configure(ServerPlayer player, MinigameSetupToolConfigurePayload payload) {
        if (!canAdmin(player)) return;
        MinigameSetupToolManager.Session session = SimpleServerUtilities.MINIGAME_SETUP_TOOLS.session(player);
        String operation = payload.operation().trim().toLowerCase(Locale.ROOT);
        try {
            switch (operation) {
                case "select" -> {
                    if (session.dirty && (!session.minigameId.equals(payload.minigameId())
                            || !session.arenaId.equals(payload.arenaId()))) {
                        throw new IllegalArgumentException("Save the edited arena snapshot before switching to another target.");
                    }
                    MinigameSetupAction action = MinigameSetupAction.parse(payload.action());
                    MinigameDefinition definition = payload.minigameId().isBlank() ? null
                            : SimpleServerUtilities.MINIGAMES.definition(payload.minigameId());
                    MinigameGameType type = definition == null ? null : MinigameGameType.parse(definition.gameType);
                    if (!action.availableFor(type) || action.needsTarget() && definition == null) {
                        throw new IllegalArgumentException("That setup action is not available for the selected game.");
                    }
                    if (definition != null && arena(definition, payload.arenaId()) == null) {
                        throw new IllegalArgumentException("Select a valid arena.");
                    }
                    if (session.hasTarget() && (!session.minigameId.equals(payload.minigameId())
                            || !session.arenaId.equals(payload.arenaId()))) {
                        MinigameDefinition oldDefinition = SimpleServerUtilities.MINIGAMES.definition(session.minigameId);
                        MinigameArenaDefinition oldArena = oldDefinition == null ? null : arena(oldDefinition, session.arenaId);
                        removePhysicalSetupMarkers(player.level().getServer(), oldDefinition, oldArena);
                    }
                    session.configure(payload.minigameId(), payload.arenaId(), action,
                            payload.team(), payload.index());
                    open(player, "Selected: " + action.label() + ". Close the menu and use left-click.", false, payload.requestId());
                }
                case "clear_point" -> {
                    session.clearPoint();
                    RegionCommands.getSelectionManager().clear(player);
                    SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(player);
                    open(player, "The pending first corner was cleared.", false, payload.requestId());
                }
                case "give_tool" -> {
                    if (session.dirty && !payload.minigameId().isBlank()
                            && !session.minigameId.equals(payload.minigameId())) {
                        throw new IllegalArgumentException("Save the edited arena snapshot before switching to another minigame.");
                    }
                    if (!payload.minigameId().isBlank()) {
                        MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.definition(payload.minigameId());
                        if (definition == null) throw new IllegalArgumentException("That minigame no longer exists.");
                        String arenaId = payload.arenaId();
                        if (arenaId.isBlank() && !definition.arenas.isEmpty()) arenaId = definition.arenas.getFirst().id;
                        MinigameSetupAction action = MinigameSetupAction.parse(payload.action());
                        if (!action.availableFor(MinigameGameType.parse(definition.gameType))) action = MinigameSetupAction.ARENA_BOUNDS;
                        session.configure(definition.id, arenaId, action, payload.team(), payload.index());
                    }
                    SimpleServerUtilities.MINIGAME_SETUP_TOOLS.giveTool(player);
                    open(player, "Minigame Setup Tool added to your inventory.", false, payload.requestId());
                }
                default -> throw new IllegalArgumentException("Unknown setup-tool operation.");
            }
        } catch (RuntimeException exception) {
            open(player, message(exception), true, payload.requestId());
        }
    }

    public static boolean handleLeftClick(ServerPlayer player, BlockPos clicked) {
        if (!canAdmin(player)) return false;
        MinigameSetupToolManager.Session session = SimpleServerUtilities.MINIGAME_SETUP_TOOLS.session(player);
        MinigameSetupAction action = session.action;
        try {
            if (action == MinigameSetupAction.EDIT_BLOCKS) return false;
            if (action == MinigameSetupAction.NEW_ARENA_BOUNDS) {
                selectNewArenaCorner(player, session, clicked);
                return true;
            }
            MinigameDefinition definition = targetDefinition(session);
            MinigameArenaDefinition arena = targetArena(definition, session);
            if (SimpleServerUtilities.MINIGAMES.hasActiveRuntimeFor(definition.id)) {
                throw new IllegalArgumentException("Stop active matches and queues before changing this minigame.");
            }
            switch (action) {
                case ARENA_BOUNDS -> selectResizeCorner(player, session, definition, arena, clicked);
                case SAVE_SNAPSHOT -> saveSnapshot(player, session, definition, arena);
                case LOBBY -> updateLocation(player, definition, arena, "lobby", clicked.above());
                case SPECTATOR_SPAWN -> updateLocation(player, definition, arena, "spectator", clicked.above());
                case SPECTATOR_BOUNDS -> selectAreaCorner(player, session, definition, arena, clicked, false);
                case TEAM_SPAWN -> setTeamSpawn(player, session, definition, arena, clicked.above());
                case SPLEEF_FLOOR -> selectAreaCorner(player, session, definition, arena, clicked, true);
                case CTF_FLAG -> setFlag(player, session, definition, arena, clicked.above());
                case DOMINATION_NODE -> setNode(player, session, definition, arena, clicked.above());
                case DOMINATION_NODE_SPAWN -> setNodeSpawn(player, session, definition, arena, clicked.above());
                default -> throw new IllegalArgumentException("Choose a setup action first.");
            }
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal(message(exception)), true);
        } finally {
            sendSetupVisuals(player, session);
        }
        return true;
    }

    private static void selectNewArenaCorner(ServerPlayer player, MinigameSetupToolManager.Session session, BlockPos clicked) {
        var selections = RegionCommands.getSelectionManager();
        RegionSelection selection = selections.getSelection(player);
        if (selection.getPoint1() == null || selection.isComplete()) {
            selections.clear(player);
            selections.setPoint1(player, clicked);
            player.sendSystemMessage(Component.literal("New arena corner 1 set to " + compact(clicked)
                    + ". Left-click the opposite corner."), true);
        } else {
            selections.setPoint2(player, clicked);
            player.sendSystemMessage(Component.literal("New arena bounds selected. Right-click the Setup Tool and choose Create new game."), true);
        }
        SimpleServerUtilities.BORDER_VISUALIZATIONS.showSelection(player, selections.getSelection(player));
        session.clearPoint();
    }

    private static void selectResizeCorner(ServerPlayer player, MinigameSetupToolManager.Session session,
                                           MinigameDefinition definition, MinigameArenaDefinition arena, BlockPos clicked) {
        if (session.firstPoint == null) {
            session.setFirst(player.level().dimension().identifier().toString(), clicked);
            player.sendSystemMessage(Component.literal("Resize corner 1 set to " + compact(clicked)
                    + ". Left-click the opposite corner."), true);
            return;
        }
        if (!session.firstDimension.equals(player.level().dimension().identifier().toString())) {
            session.clearPoint();
            throw new IllegalArgumentException("Both arena corners must be in the same dimension.");
        }
        BlockPos first = session.firstPoint;
        session.clearPoint();
        resizeArena(player, session, definition, arena, first, clicked);
    }

    private static void selectAreaCorner(ServerPlayer player, MinigameSetupToolManager.Session session,
                                         MinigameDefinition definition, MinigameArenaDefinition arena,
                                         BlockPos clicked, boolean playFloor) {
        if (session.firstPoint == null) {
            session.setFirst(player.level().dimension().identifier().toString(), clicked);
            player.sendSystemMessage(Component.literal((playFloor ? "Playfloor" : "Spectator area")
                    + " corner 1 set. Left-click the opposite corner."), true);
            return;
        }
        if (!session.firstDimension.equals(player.level().dimension().identifier().toString())) {
            session.clearPoint();
            throw new IllegalArgumentException("Both corners must be in the same dimension.");
        }
        MinigameAreaBounds bounds = new MinigameAreaBounds(session.firstDimension, session.firstPoint, clicked);
        session.clearPoint();
        Region region = requireRegion(arena);
        if (!insideRegion(bounds, region, playFloor ? 0 : 32)) {
            throw new IllegalArgumentException((playFloor ? "The Spleef playfloor" : "The spectator bounds")
                    + " must stay inside or directly around the arena.");
        }
        if (playFloor) arena.playFloor = bounds;
        else arena.spectatorBounds = bounds;
        saveTarget(definition);
        player.sendSystemMessage(Component.literal((playFloor ? "Spleef playfloor" : "Spectator bounds")
                + " saved: " + bounds.compact()), true);
    }

    private static void updateLocation(ServerPlayer player, MinigameDefinition definition,
                                       MinigameArenaDefinition arena, String kind, BlockPos position) {
        MinigameLocation previous = "lobby".equals(kind) ? arena.lobby : arena.spectator;
        removePhysicalSetupMarker(player.level().getServer(), definition, arena, previous);
        MinigameLocation location = location(player, position);
        if ("lobby".equals(kind)) arena.lobby = location;
        else arena.spectator = location;
        saveTarget(definition);
        player.sendSystemMessage(Component.literal(("lobby".equals(kind) ? "Lobby" : "Spectator spawn")
                + " set to " + compact(position) + "."), true);
    }

    private static void setTeamSpawn(ServerPlayer player, MinigameSetupToolManager.Session session,
                                     MinigameDefinition definition, MinigameArenaDefinition arena, BlockPos position) {
        int team = MinigameGameType.parse(definition.gameType) == MinigameGameType.SPLEEF
                ? Math.max(1, session.index + 1) : Math.max(1, Math.min(definition.teamCount, session.team));
        int ordinal = Math.max(0, session.index);
        ArrayList<MinigameSpawnPoint> matching = new ArrayList<>();
        for (MinigameSpawnPoint spawn : arena.teamSpawns) if (spawn.team == team) matching.add(spawn);
        MinigameLocation location = location(player, position);
        if (ordinal < matching.size()) {
            removePhysicalSetupMarker(player.level().getServer(), definition, arena, matching.get(ordinal).location);
            matching.get(ordinal).location = location;
        } else arena.teamSpawns.add(new MinigameSpawnPoint(team, location));
        saveTarget(definition);
        player.sendSystemMessage(Component.literal((MinigameGameType.parse(definition.gameType) == MinigameGameType.SPLEEF
                ? "Player spawn " + team : "Team " + team + " spawn " + (ordinal + 1))
                + " set to " + compact(position) + "."), true);
    }

    private static void setFlag(ServerPlayer player, MinigameSetupToolManager.Session session,
                                MinigameDefinition definition, MinigameArenaDefinition arena, BlockPos position) {
        if (MinigameGameType.parse(definition.gameType) != MinigameGameType.CAPTURE_THE_FLAG) {
            throw new IllegalArgumentException("Select a Capture the Flag minigame.");
        }
        markDirty(player, session, definition, arena);
        definition = targetDefinition(session);
        arena = targetArena(definition, session);
        int team = Math.max(1, Math.min(2, session.team));
        MinigameFlagPoint point = arena.flagForTeam(team);
        BlockPos oldPosition = point == null ? null : blockPos(point.location);
        if (point == null) {
            point = new MinigameFlagPoint(team, location(player, position));
            arena.flagPoints.add(point);
        } else point.location = location(player, position);
        removeMovedBanner(player, oldPosition, position);
        placeBlock(player, position, definition.captureTheFlag.flagBlock(team));
        saveTarget(definition);
        player.sendSystemMessage(Component.literal((team == 1 ? "Red" : "Blue")
                + " flag placed. Save the arena snapshot when building is finished."), true);
    }

    private static void setNode(ServerPlayer player, MinigameSetupToolManager.Session session,
                                MinigameDefinition definition, MinigameArenaDefinition arena, BlockPos position) {
        if (MinigameGameType.parse(definition.gameType) != MinigameGameType.DOMINATION) {
            throw new IllegalArgumentException("Select a Domination minigame.");
        }
        markDirty(player, session, definition, arena);
        definition = targetDefinition(session);
        arena = targetArena(definition, session);
        int index = Math.max(0, Math.min(8, session.index));
        while (arena.controlPoints.size() <= index) {
            int number = arena.controlPoints.size() + 1;
            MinigameLocation nodeLocation = location(player, position);
            MinigameControlPoint created = new MinigameControlPoint("node_" + number, "Node " + number, nodeLocation);
            created.respawn = MinigameLocation.of(player);
            arena.controlPoints.add(created);
        }
        MinigameControlPoint point = arena.controlPoints.get(index);
        BlockPos oldPosition = blockPos(point.location);
        MinigameLocation oldLocation = point.location == null ? null : point.location.copy();
        MinigameLocation newLocation = location(player, position);
        point.location = newLocation;
        if (oldLocation != null && point.respawn != null) {
            point.respawn = new MinigameLocation(point.respawn.dimension,
                    point.respawn.x + (newLocation.x - oldLocation.x),
                    point.respawn.y + (newLocation.y - oldLocation.y),
                    point.respawn.z + (newLocation.z - oldLocation.z),
                    point.respawn.yaw, point.respawn.pitch);
        } else if (point.respawn == null) point.respawn = MinigameLocation.of(player);
        removeMovedBanner(player, oldPosition, position);
        placeBlock(player, position, definition.domination.neutralBannerBlock);
        saveTarget(definition);
        player.sendSystemMessage(Component.literal("Domination node " + (index + 1)
                + " placed. Its linked respawn moved with the node; set it separately if needed."), true);
    }

    private static void setNodeSpawn(ServerPlayer player, MinigameSetupToolManager.Session session,
                                     MinigameDefinition definition, MinigameArenaDefinition arena, BlockPos position) {
        if (MinigameGameType.parse(definition.gameType) != MinigameGameType.DOMINATION) {
            throw new IllegalArgumentException("Select a Domination minigame.");
        }
        int index = Math.max(0, Math.min(8, session.index));
        if (index >= arena.controlPoints.size()) {
            throw new IllegalArgumentException("Create that Domination node before setting its linked spawn.");
        }
        MinigameControlPoint point = arena.controlPoints.get(index);
        removePhysicalSetupMarker(player.level().getServer(), definition, arena, point.respawn);
        point.respawn = location(player, position);
        saveTarget(definition);
        player.sendSystemMessage(Component.literal(point.displayName + " respawn set to "
                + compact(position) + "."), true);
    }

    private static void resizeArena(ServerPlayer player, MinigameSetupToolManager.Session session,
                                    MinigameDefinition definition, MinigameArenaDefinition arena,
                                    BlockPos first, BlockPos second) {
        if (!arena.managedRegion) throw new IllegalArgumentException("Only Setup Tool-managed arena regions can be resized.");
        Region old = requireRegion(arena);
        if (!old.getDimension().equals(player.level().dimension())) {
            throw new IllegalArgumentException("Resize the arena from its current dimension.");
        }
        long volume = (long) (Math.abs(first.getX() - second.getX()) + 1)
                * (Math.abs(first.getY() - second.getY()) + 1)
                * (Math.abs(first.getZ() - second.getZ()) + 1);
        if (volume > RegionSelectionSchematicManager.MAX_VOLUME) throw new IllegalArgumentException("The arena exceeds the region volume limit.");
        validateMinimumSize(MinigameGameType.parse(definition.gameType), first, second);
        boolean gameEnabled = definition.enabled;
        boolean arenaEnabled = arena.enabled;
        definition.enabled = false;
        arena.enabled = false;
        arena.resetRegionAfterMatch = false;
        saveTarget(definition);
        RegionOperationResult result = SimpleServerUtilities.REGIONS.redefine(arena.regionId,
                player.level().dimension(), first, second);
        if (!result.isSuccess()) {
            definition.enabled = gameEnabled;
            arena.enabled = arenaEnabled;
            arena.resetRegionAfterMatch = true;
            saveTarget(definition);
            throw new IllegalArgumentException("Arena resize failed: " + result.getDetails());
        }
        Region resized = requireRegion(arena);
        clampArenaLocations(arena, resized);
        try {
            SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(arena.regionId, "minigame-arena-resize");
        } catch (IOException exception) {
            throw new IllegalArgumentException("The old arena snapshot could not be archived: " + exception.getMessage());
        }
        saveTarget(definition);
        captureSnapshot(player, definition.id, arena.id, gameEnabled, arenaEnabled, "Arena resized");
    }

    public static boolean canEditBlock(ServerPlayer player, BlockPos position) {
        if (!canAdmin(player)) return false;
        MinigameSetupToolManager.Session session = SimpleServerUtilities.MINIGAME_SETUP_TOOLS.existing(player);
        if (session == null || session.action != MinigameSetupAction.EDIT_BLOCKS || !session.hasTarget()) return false;
        MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.definition(session.minigameId);
        MinigameArenaDefinition arena = definition == null ? null : arena(definition, session.arenaId);
        Region region = arena == null ? null : SimpleServerUtilities.REGIONS.get(arena.regionId);
        return region != null && region.contains(player.level().dimension(), position)
                && !SimpleServerUtilities.MINIGAMES.hasActiveRuntimeFor(definition.id);
    }

    public static void onArenaBlockEdited(ServerPlayer player, BlockPos position) {
        if (!canEditBlock(player, position)) return;
        MinigameSetupToolManager.Session session = SimpleServerUtilities.MINIGAME_SETUP_TOOLS.session(player);
        try {
            MinigameDefinition definition = targetDefinition(session);
            MinigameArenaDefinition arena = targetArena(definition, session);
            markDirty(player, session, definition, arena);
        } catch (RuntimeException exception) {
            player.sendSystemMessage(Component.literal(message(exception)), true);
        }
    }

    private static void markDirty(ServerPlayer player, MinigameSetupToolManager.Session session,
                                  MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (session.dirty) return;
        if (SimpleServerUtilities.MINIGAMES.hasActiveRuntimeFor(definition.id)) {
            throw new IllegalArgumentException("Stop active matches and queues before editing this arena.");
        }
        session.gameWasEnabled = definition.enabled;
        session.arenaWasEnabled = arena.enabled;
        definition.enabled = false;
        arena.enabled = false;
        arena.resetRegionAfterMatch = false;
        try {
            SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(arena.regionId, "minigame-arena-edit");
        } catch (IOException exception) {
            throw new IllegalArgumentException("The previous reset snapshot could not be archived: " + exception.getMessage());
        }
        saveTarget(definition);
        session.dirty = true;
        player.sendSystemMessage(Component.literal("Arena edit mode: the game is disabled until Save arena snapshot completes."), true);
    }

    private static void saveSnapshot(ServerPlayer player, MinigameSetupToolManager.Session session,
                                     MinigameDefinition definition, MinigameArenaDefinition arena) {
        if (SimpleServerUtilities.MINIGAMES.hasActiveRuntimeFor(definition.id)) {
            throw new IllegalArgumentException("Stop active matches and queues before saving the arena snapshot.");
        }
        boolean gameEnabled = session.dirty ? session.gameWasEnabled : definition.enabled;
        boolean arenaEnabled = session.dirty ? session.arenaWasEnabled : arena.enabled;
        definition.enabled = false;
        arena.enabled = false;
        arena.resetRegionAfterMatch = false;
        saveTarget(definition);
        try {
            SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(arena.regionId, "minigame-arena-recapture");
        } catch (IOException exception) {
            throw new IllegalArgumentException("The previous snapshot could not be archived: " + exception.getMessage());
        }
        session.dirty = false;
        captureSnapshot(player, definition.id, arena.id, gameEnabled, arenaEnabled, "Arena snapshot saved");
    }

    private static void captureSnapshot(ServerPlayer player, String gameId, String arenaId,
                                        boolean restoreGameEnabled, boolean restoreArenaEnabled, String successMessage) {
        String key = gameId + ":" + arenaId;
        if (!PENDING_SNAPSHOTS.add(key)) throw new IllegalArgumentException("A snapshot job is already running for this arena.");
        MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.copy(SimpleServerUtilities.MINIGAMES.definition(gameId));
        MinigameArenaDefinition arena = definition == null ? null : arena(definition, arenaId);
        Region region = arena == null ? null : SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) {
            PENDING_SNAPSHOTS.remove(key);
            throw new IllegalArgumentException("The managed arena region is missing.");
        }
        ServerLevel level = player.level().getServer().getLevel(region.getDimension());
        if (level == null) {
            PENDING_SNAPSHOTS.remove(key);
            throw new IllegalArgumentException("The arena dimension is not loaded.");
        }
        removePhysicalSetupMarkers(player.level().getServer(), definition, arena);
        try {
            var capture = SimpleServerUtilities.REGION_SNAPSHOTS.createCaptureJob(level, region);
            MinecraftServer server = player.level().getServer();
            java.util.UUID actor = player.getUUID();
            SimpleServerUtilities.JOBS.submit(capture, result -> {
                try {
                    ServerPlayer online = server.getPlayerList().getPlayer(actor);
                    if (result.status() != SsuJobScheduler.Status.COMPLETED
                            || !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
                        if (online != null) online.sendSystemMessage(Component.literal("Arena snapshot failed. The game remains disabled."));
                        return;
                    }
                    MinigameDefinition saved = SimpleServerUtilities.MINIGAMES.copy(SimpleServerUtilities.MINIGAMES.definition(gameId));
                    MinigameArenaDefinition savedArena = saved == null ? null : arena(saved, arenaId);
                    if (saved == null || savedArena == null) return;
                    savedArena.resetRegionAfterMatch = true;
                    savedArena.enabled = restoreArenaEnabled;
                    saved.enabled = restoreGameEnabled;
                    if (!SimpleServerUtilities.MINIGAMES.saveManagedDefinition(gameId, saved)) {
                        saved.enabled = false;
                        savedArena.enabled = false;
                        SimpleServerUtilities.MINIGAMES.saveManagedDefinition(gameId, saved);
                        if (online != null) online.sendSystemMessage(Component.literal("Snapshot completed, but arena validation failed. The game remains disabled."));
                        return;
                    }
                    if (online != null) online.sendSystemMessage(Component.literal(successMessage + ". Reset protection is verified."));
                } catch (RuntimeException exception) {
                    ServerPlayer online = server.getPlayerList().getPlayer(actor);
                    if (online != null) online.sendSystemMessage(Component.literal("Snapshot finalization failed: " + message(exception)));
                } finally {
                    PENDING_SNAPSHOTS.remove(key);
                    ServerPlayer online = server.getPlayerList().getPlayer(actor);
                    MinigameSetupToolManager.Session current = online == null ? null
                            : SimpleServerUtilities.MINIGAME_SETUP_TOOLS.existing(online);
                    if (online != null && current != null && gameId.equals(current.minigameId)
                            && arenaId.equals(current.arenaId)) sendSetupVisuals(online, current);
                }
            });
            player.sendSystemMessage(Component.literal("Capturing the arena snapshot…"), true);
        } catch (IOException exception) {
            PENDING_SNAPSHOTS.remove(key);
            MinigameSetupToolManager.Session current = SimpleServerUtilities.MINIGAME_SETUP_TOOLS.existing(player);
            if (current != null && gameId.equals(current.minigameId) && arenaId.equals(current.arenaId)) {
                sendSetupVisuals(player, current);
            }
            throw new IllegalArgumentException("Snapshot capture could not start: " + exception.getMessage());
        }
    }

    private static MinigameSetupToolOpenPayload payload(ServerPlayer player, MinigameSetupToolManager.Session session,
                                                         String notice, boolean error, long requestId) {
        ArrayList<MinigameSetupToolOpenPayload.GameEntry> games = new ArrayList<>();
        for (MinigameDefinition definition : SimpleServerUtilities.MINIGAMES.definitions()) {
            MinigameGameType type = MinigameGameType.parse(definition.gameType);
            if (type == MinigameGameType.GENERIC || !type.implemented()) continue;
            ArrayList<MinigameSetupToolOpenPayload.ArenaEntry> arenas = new ArrayList<>();
            for (MinigameArenaDefinition arena : definition.arenas) {
                Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
                String bounds = region == null ? "Missing region" : region.getBoundsText();
                int special = type == MinigameGameType.CAPTURE_THE_FLAG ? arena.flagPoints.size()
                        : type == MinigameGameType.DOMINATION ? arena.controlPoints.size() : 0;
                arenas.add(new MinigameSetupToolOpenPayload.ArenaEntry(arena.id, arena.displayName, arena.regionId,
                        arena.enabled, bounds, arena.playFloor.compact(), arena.spectatorBounds.compact(),
                        arena.teamSpawns.size(), special));
            }
            String team1Name = "Team 1", team2Name = "Team 2";
            int team1Color = 0xE53935, team2Color = 0x1E88E5;
            if (type == MinigameGameType.CAPTURE_THE_FLAG) {
                team1Name = definition.captureTheFlag.team1Name;
                team2Name = definition.captureTheFlag.team2Name;
                team1Color = definition.captureTheFlag.team1Color;
                team2Color = definition.captureTheFlag.team2Color;
            } else if (type == MinigameGameType.DOMINATION) {
                team1Name = definition.domination.team1Name;
                team2Name = definition.domination.team2Name;
                team1Color = definition.domination.team1Color;
                team2Color = definition.domination.team2Color;
            }
            games.add(new MinigameSetupToolOpenPayload.GameEntry(definition.id, definition.displayName,
                    definition.gameType, team1Name, team2Name, team1Color, team2Color, arenas));
        }
        games.sort(Comparator.comparing(MinigameSetupToolOpenPayload.GameEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
        boolean complete = selection.isComplete();
        return new MinigameSetupToolOpenPayload(notice, error, requestId, session.minigameId, session.arenaId,
                session.action.id(), session.team, session.index, session.firstPoint != null,
                session.firstPoint == null ? 0L : session.firstPoint.asLong(), complete,
                complete ? selection.getDimension().identifier().toString() : "",
                complete ? selection.getPoint1().asLong() : 0L, complete ? selection.getPoint2().asLong() : 0L,
                complete ? RegionSelectionSchematicManager.bounds(selection).volume() : 0L, games);
    }

    private static void normalizeTarget(MinigameSetupToolManager.Session session) {
        MinigameDefinition definition = session.minigameId.isBlank() ? null
                : SimpleServerUtilities.MINIGAMES.definition(session.minigameId);
        if (definition == null) {
            session.configure("", "", MinigameSetupAction.NEW_ARENA_BOUNDS, 1, 0);
            return;
        }
        MinigameArenaDefinition arena = arena(definition, session.arenaId);
        if (arena == null && !definition.arenas.isEmpty()) arena = definition.arenas.getFirst();
        if (arena == null) {
            session.configure("", "", MinigameSetupAction.NEW_ARENA_BOUNDS, 1, 0);
            return;
        }
        MinigameSetupAction action = session.action.availableFor(MinigameGameType.parse(definition.gameType))
                ? session.action : MinigameSetupAction.ARENA_BOUNDS;
        session.configure(definition.id, arena.id, action, session.team, session.index);
    }

    private static MinigameDefinition targetDefinition(MinigameSetupToolManager.Session session) {
        MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.copy(
                SimpleServerUtilities.MINIGAMES.definition(session.minigameId));
        if (definition == null) throw new IllegalArgumentException("Select a minigame in the Setup Tool menu.");
        return definition;
    }

    private static MinigameArenaDefinition targetArena(MinigameDefinition definition,
                                                        MinigameSetupToolManager.Session session) {
        MinigameArenaDefinition arena = arena(definition, session.arenaId);
        if (arena == null) throw new IllegalArgumentException("Select a valid arena in the Setup Tool menu.");
        return arena;
    }

    private static MinigameArenaDefinition arena(MinigameDefinition definition, String id) {
        if (definition == null) return null;
        for (MinigameArenaDefinition arena : definition.arenas) if (arena.id.equals(id)) return arena;
        return null;
    }

    private static Region requireRegion(MinigameArenaDefinition arena) {
        Region region = arena == null ? null : SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (region == null) throw new IllegalArgumentException("The selected arena region is missing.");
        return region;
    }

    private static void saveTarget(MinigameDefinition definition) {
        if (!SimpleServerUtilities.MINIGAMES.saveManagedDefinition(definition.id, definition)) {
            throw new IllegalArgumentException("The minigame change could not be saved safely.");
        }
    }

    private static MinigameLocation location(ServerPlayer player, BlockPos position) {
        return new MinigameLocation(player.level().dimension().identifier().toString(), position.getX() + 0.5D,
                position.getY(), position.getZ() + 0.5D, player.getYRot(), player.getXRot());
    }

    private static void placeBlock(ServerPlayer player, BlockPos position, String blockId) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown marker block: " + blockId));
        if (!(player.level() instanceof ServerLevel level)) throw new IllegalArgumentException("The arena dimension is unavailable.");
        level.setBlockAndUpdate(position, block.defaultBlockState());
        if (level.getBlockState(position).getBlock() != block) {
            throw new IllegalArgumentException("The physical setup marker could not be placed at " + compact(position) + ".");
        }
    }

    private static void removeMovedBanner(ServerPlayer player, BlockPos oldPosition, BlockPos newPosition) {
        if (oldPosition == null || oldPosition.equals(newPosition) || !(player.level() instanceof ServerLevel level)) return;
        if (level.getBlockState(oldPosition).getBlock() instanceof AbstractBannerBlock) {
            level.setBlockAndUpdate(oldPosition, Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockPos blockPos(MinigameLocation location) {
        return location == null ? null : BlockPos.containing(location.x, location.y, location.z);
    }

    private static void sendSetupVisuals(ServerPlayer player, MinigameSetupToolManager.Session session) {
        if (player == null || session == null || !session.hasTarget()) {
            if (player != null) PacketDistributor.sendToPlayer(player, MinigameSetupVisualPayload.clear());
            return;
        }
        MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.definition(session.minigameId);
        MinigameArenaDefinition arena = definition == null ? null : arena(definition, session.arenaId);
        if (definition == null || arena == null) {
            PacketDistributor.sendToPlayer(player, MinigameSetupVisualPayload.clear());
            return;
        }
        if (!SimpleServerUtilities.MINIGAMES.hasActiveRuntimeFor(definition.id)) {
            ensurePhysicalGameMarkers(player.level().getServer(), definition, arena);
            if (!PENDING_SNAPSHOTS.contains(definition.id + ":" + arena.id)) {
                ensurePhysicalSetupMarkers(player.level().getServer(), definition, arena);
            }
        }
        ArrayList<MinigameSetupVisualPayload.Entry> markers = new ArrayList<>();
        addMarker(markers, arena.lobby, "Lobby", 0x43A047, MinigameSetupVisualPayload.Entry.LOBBY);
        addMarker(markers, arena.spectator, "Spectator", 0x8E24AA, MinigameSetupVisualPayload.Entry.SPECTATOR);

        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        int spawnNumber = 0;
        int[] teamSpawnNumbers = new int[17];
        for (MinigameSpawnPoint spawn : arena.teamSpawns) {
            if (spawn == null || spawn.location == null) continue;
            spawnNumber++;
            int color = teamColor(definition, spawn.team);
            int teamSlot = spawn.team >= 0 && spawn.team < teamSpawnNumbers.length
                    ? ++teamSpawnNumbers[spawn.team] : spawnNumber;
            String label = type == MinigameGameType.SPLEEF
                    ? "Player spawn " + spawnNumber
                    : teamName(definition, spawn.team) + " spawn " + teamSlot;
            addMarker(markers, spawn.location, label, color, MinigameSetupVisualPayload.Entry.SPAWN);
        }
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            for (MinigameFlagPoint flag : arena.flagPoints) {
                if (flag == null) continue;
                addMarker(markers, flag.location, teamName(definition, flag.team) + " flag",
                        teamColor(definition, flag.team), MinigameSetupVisualPayload.Entry.FLAG);
            }
        } else if (type == MinigameGameType.DOMINATION) {
            for (MinigameControlPoint point : arena.controlPoints) {
                if (point == null) continue;
                addMarker(markers, point.location, point.displayName, 0xF5F5F5,
                        MinigameSetupVisualPayload.Entry.NODE);
                addMarker(markers, point.respawn, point.displayName + " respawn", 0xFFB300,
                        MinigameSetupVisualPayload.Entry.NODE_SPAWN);
            }
        }
        PacketDistributor.sendToPlayer(player, new MinigameSetupVisualPayload(true, markers));
    }


    /** Places temporary physical setup banners. They are deliberately excluded from arena snapshots and matches. */
    private static void ensurePhysicalSetupMarkers(MinecraftServer server, MinigameDefinition definition,
                                                   MinigameArenaDefinition arena) {
        if (server == null || definition == null || arena == null) return;
        placeSetupBanner(server, definition, arena, arena.lobby, LOBBY_SETUP_BANNER);
        placeSetupBanner(server, definition, arena, arena.spectator, SPECTATOR_SETUP_BANNER);
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        for (MinigameSpawnPoint spawn : arena.teamSpawns) {
            if (spawn == null || spawn.location == null) continue;
            String blockId = switch (type) {
                case CAPTURE_THE_FLAG -> definition.captureTheFlag.flagBlock(spawn.team);
                case DOMINATION -> definition.domination.bannerBlock(spawn.team);
                default -> PLAYER_SETUP_BANNER;
            };
            placeSetupBanner(server, definition, arena, spawn.location, blockId);
        }
        if (type == MinigameGameType.DOMINATION) {
            for (MinigameControlPoint point : arena.controlPoints) {
                if (point != null) placeSetupBanner(server, definition, arena, point.respawn, NODE_RESPAWN_SETUP_BANNER);
            }
        }
    }

    public static void restorePhysicalSetupMarkers(MinecraftServer server, MinigameDefinition definition,
                                                   MinigameArenaDefinition arena) {
        if (server == null || definition == null || arena == null) return;
        ensurePhysicalGameMarkers(server, definition, arena);
        ensurePhysicalSetupMarkers(server, definition, arena);
    }

    /** Removes every temporary lobby/spectator/spawn banner while preserving actual CTF flags and Domination nodes. */
    public static void removePhysicalSetupMarkers(MinecraftServer server, MinigameDefinition definition,
                                                  MinigameArenaDefinition arena) {
        if (server == null || definition == null || arena == null) return;
        removePhysicalSetupMarker(server, definition, arena, arena.lobby);
        removePhysicalSetupMarker(server, definition, arena, arena.spectator);
        for (MinigameSpawnPoint spawn : arena.teamSpawns) {
            if (spawn != null) removePhysicalSetupMarker(server, definition, arena, spawn.location);
        }
        if (MinigameGameType.parse(definition.gameType) == MinigameGameType.DOMINATION) {
            for (MinigameControlPoint point : arena.controlPoints) {
                if (point != null) removePhysicalSetupMarker(server, definition, arena, point.respawn);
            }
        }
    }

    public static void removeAllPhysicalSetupMarkers(MinecraftServer server) {
        if (server == null) return;
        for (MinigameDefinition definition : SimpleServerUtilities.MINIGAMES.definitions()) {
            if (definition == null || definition.arenas == null) continue;
            for (MinigameArenaDefinition arena : definition.arenas) {
                removePhysicalSetupMarkers(server, definition, arena);
            }
        }
    }

    private static void placeSetupBanner(MinecraftServer server, MinigameDefinition definition,
                                         MinigameArenaDefinition arena, MinigameLocation location, String blockId) {
        if (location == null || blockId == null || blockId.isBlank()) return;
        ServerLevel level = level(server, location.dimension);
        if (level == null) return;
        BlockPos pos = blockPos(location);
        if (pos == null || isPhysicalGameMarkerPosition(arena, pos)) return;
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId)).orElse(null);
        if (!(block instanceof AbstractBannerBlock)) return;
        Block current = level.getBlockState(pos).getBlock();
        if (!level.getBlockState(pos).isAir() && !(current instanceof AbstractBannerBlock)) return;
        if (current != block) level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    private static void removePhysicalSetupMarker(MinecraftServer server, MinigameDefinition definition,
                                                  MinigameArenaDefinition arena, MinigameLocation location) {
        if (server == null || location == null) return;
        ServerLevel level = level(server, location.dimension);
        BlockPos pos = blockPos(location);
        if (level == null || pos == null || isPhysicalGameMarkerPosition(arena, pos)) return;
        if (level.getBlockState(pos).getBlock() instanceof AbstractBannerBlock) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static boolean isPhysicalGameMarkerPosition(MinigameArenaDefinition arena, BlockPos pos) {
        if (arena == null || pos == null) return false;
        for (MinigameFlagPoint flag : arena.flagPoints) {
            if (flag != null && pos.equals(blockPos(flag.location))) return true;
        }
        for (MinigameControlPoint point : arena.controlPoints) {
            if (point != null && pos.equals(blockPos(point.location))) return true;
        }
        return false;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) return null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().identifier().toString().equals(dimension)) return candidate;
        }
        return null;
    }

    private static void ensurePhysicalGameMarkers(MinecraftServer server, MinigameDefinition definition, MinigameArenaDefinition arena) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            for (MinigameFlagPoint flag : arena.flagPoints) {
                if (flag == null || flag.location == null) continue;
                placeConfiguredBanner(server, flag.location, definition.captureTheFlag.flagBlock(flag.team));
            }
        } else if (type == MinigameGameType.DOMINATION) {
            for (MinigameControlPoint point : arena.controlPoints) {
                if (point == null || point.location == null) continue;
                placeConfiguredBanner(server, point.location, definition.domination.neutralBannerBlock);
            }
        }
    }

    private static void placeConfiguredBanner(MinecraftServer server, MinigameLocation location, String blockId) {
        if (location == null || location.dimension == null || location.dimension.isBlank()) return;
        ServerLevel level = null;
        if (server != null) {
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().identifier().toString().equals(location.dimension)) {
                    level = candidate;
                    break;
                }
            }
        }
        if (level == null) return;
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId)).orElse(null);
        if (!(block instanceof AbstractBannerBlock)) return;
        BlockPos pos = blockPos(location);
        if (pos != null && level.getBlockState(pos).getBlock() != block) {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
    }

    private static void addMarker(List<MinigameSetupVisualPayload.Entry> markers, MinigameLocation location,
                                  String label, int color, byte kind) {
        if (location == null || location.dimension == null || location.dimension.isBlank()) return;
        markers.add(new MinigameSetupVisualPayload.Entry(location.dimension, location.x, location.y, location.z,
                label, color, kind));
    }

    private static String teamName(MinigameDefinition definition, int team) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) return definition.captureTheFlag.teamName(team);
        if (type == MinigameGameType.DOMINATION) return definition.domination.teamName(team);
        return "Team " + team;
    }

    private static int teamColor(MinigameDefinition definition, int team) {
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) return definition.captureTheFlag.color(team);
        if (type == MinigameGameType.DOMINATION) return definition.domination.color(team);
        return 0x26C6DA;
    }

    private static void validateMinimumSize(MinigameGameType type, BlockPos first, BlockPos second) {
        int x = Math.abs(first.getX() - second.getX()) + 1;
        int z = Math.abs(first.getZ() - second.getZ()) + 1;
        if (type == MinigameGameType.CAPTURE_THE_FLAG && Math.max(x, z) < 5) {
            throw new IllegalArgumentException("Capture the Flag needs an arena at least 5 blocks long.");
        }
        if (type == MinigameGameType.DOMINATION && (x < 15 || z < 15)) {
            throw new IllegalArgumentException("Domination needs an arena at least 15 by 15 blocks.");
        }
    }

    private static void clampArenaLocations(MinigameArenaDefinition arena, Region region) {
        for (MinigameSpawnPoint spawn : arena.teamSpawns) clamp(spawn.location, region, 1);
        for (MinigameFlagPoint flag : arena.flagPoints) clamp(flag.location, region, 0);
        for (MinigameControlPoint point : arena.controlPoints) { clamp(point.location, region, 0); clamp(point.respawn, region, 1); }
        if (!near(arena.spectator, region, 24, 32)) {
            arena.spectator = new MinigameLocation(region.getDimension().identifier().toString(),
                    (region.getMinX() + region.getMaxX() + 1.0D) / 2.0D, region.getMaxY() + 2.0D,
                    (region.getMinZ() + region.getMaxZ() + 1.0D) / 2.0D, 0, 0);
        }
        if (arena.playFloor.configured() && !insideRegion(arena.playFloor, region, 0)) arena.playFloor = new MinigameAreaBounds();
        if (arena.spectatorBounds.configured() && !insideRegion(arena.spectatorBounds, region, 32)) arena.spectatorBounds = new MinigameAreaBounds();
    }

    private static void clamp(MinigameLocation location, Region region, int topMargin) {
        if (location == null) return;
        location.dimension = region.getDimension().identifier().toString();
        location.x = Math.max(region.getMinX() + 0.5D, Math.min(region.getMaxX() + 0.5D, location.x));
        location.y = Math.max(region.getMinY(), Math.min(region.getMaxY() + topMargin, location.y));
        location.z = Math.max(region.getMinZ() + 0.5D, Math.min(region.getMaxZ() + 0.5D, location.z));
    }

    private static boolean insideRegion(MinigameAreaBounds bounds, Region region, int margin) {
        if (bounds == null || !bounds.configured()) return true;
        return bounds.dimension.equals(region.getDimension().identifier().toString())
                && bounds.minX >= region.getMinX() - margin && bounds.maxX <= region.getMaxX() + margin
                && bounds.minY >= region.getMinY() - margin && bounds.maxY <= region.getMaxY() + margin
                && bounds.minZ >= region.getMinZ() - margin && bounds.maxZ <= region.getMaxZ() + margin;
    }

    private static boolean near(MinigameLocation location, Region region, double horizontal, double vertical) {
        return location != null && region.getDimension().identifier().toString().equals(location.dimension)
                && location.x >= region.getMinX() - horizontal && location.x <= region.getMaxX() + 1 + horizontal
                && location.z >= region.getMinZ() - horizontal && location.z <= region.getMaxZ() + 1 + horizontal
                && location.y >= region.getMinY() - vertical && location.y <= region.getMaxY() + vertical;
    }

    private static String compact(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static String message(Throwable throwable) {
        String value = throwable == null ? "The minigame setup action failed safely." : throwable.getMessage();
        return value == null || value.isBlank() ? "The minigame setup action failed safely." : value;
    }
}
