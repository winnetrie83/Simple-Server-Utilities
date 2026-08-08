package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.command.RegionCommands;
import be.winnetrie.mod.simpleserverutilities.content.ContentId;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreatePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreateResultPayload;
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
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Safe GUI-first creation of a managed minigame arena from the Minigame Setup Tool selection. */
public final class MinigameSelectionService {
    private static final Set<String> PENDING_IDS = ConcurrentHashMap.newKeySet();

    private MinigameSelectionService() {}

    static void clearPending() {
        PENDING_IDS.clear();
    }

    public static boolean canCreate(ServerPlayer player) {
        return player != null && Config.ENABLE_MINIGAMES.get() && Config.ENABLE_ADMIN_REGIONS.get()
                && SimpleServerUtilities.CORE.modules().isActive("minigames")
                && SimpleServerUtilities.CORE.modules().isActive("regions")
                && PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false);
    }

    public static void handleCreate(MinigameSelectionCreatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> create(player, payload));
    }

    private static void create(ServerPlayer player, MinigameSelectionCreatePayload payload) {
        long requestId = payload.requestId();
        String minigameId = "";
        String regionName = "";
        boolean draftCreated = false;
        boolean regionCreated = false;
        try {
            if (!canCreate(player)) throw new IllegalArgumentException("Minigame administrator permission is required.");
            MinigameGameType type = MinigameGameType.parse(payload.gameType());
            if (type != MinigameGameType.SPLEEF && type != MinigameGameType.CAPTURE_THE_FLAG
                    && type != MinigameGameType.DOMINATION && type != MinigameGameType.KING_OF_THE_HILL
                    && type != MinigameGameType.BLOCK_PARTY) {
                throw new IllegalArgumentException("That minigame type is not available in the Minigame Setup Tool yet.");
            }
            minigameId = ContentId.require(payload.minigameId(), "Minigame ID");
            if (SimpleServerUtilities.MINIGAMES.definition(minigameId) != null) {
                throw new IllegalArgumentException("A minigame with that ID already exists.");
            }
            if (!PENDING_IDS.add(minigameId)) throw new IllegalArgumentException("That minigame is already being created.");

            RegionSelection selection = RegionCommands.getSelectionManager().getSelection(player);
            if (!selection.isComplete()) throw new IllegalArgumentException("Set both selection points first.");
            if (!selection.getDimension().equals(player.level().dimension())) {
                throw new IllegalArgumentException("Travel to the selection dimension before creating the arena.");
            }
            RegionSelectionSchematicManager.Bounds bounds = RegionSelectionSchematicManager.bounds(selection);
            if (bounds.volume() < 4L) throw new IllegalArgumentException("The selected arena is too small.");
            if (bounds.volume() > RegionSelectionSchematicManager.MAX_VOLUME) {
                throw new IllegalArgumentException("The arena exceeds the selection volume limit.");
            }
            if (type == MinigameGameType.CAPTURE_THE_FLAG) {
                int horizontalLength = Math.max(bounds.maxX() - bounds.minX() + 1,
                        bounds.maxZ() - bounds.minZ() + 1);
                int horizontalWidth = Math.min(bounds.maxX() - bounds.minX() + 1,
                        bounds.maxZ() - bounds.minZ() + 1);
                if (horizontalLength < 5 || horizontalWidth < 2) {
                    throw new IllegalArgumentException(
                            "Capture the Flag needs an arena at least 5 blocks long and 2 blocks wide.");
                }
            } else if (type == MinigameGameType.DOMINATION) {
                int sizeX = bounds.maxX() - bounds.minX() + 1;
                int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
                if (sizeX < 15 || sizeZ < 15) {
                    throw new IllegalArgumentException(
                            "Domination needs an arena at least 15 by 15 blocks for five separated capture nodes.");
                }
            }

            regionName = managedRegionName(minigameId);
            if (SimpleServerUtilities.REGIONS.get(regionName) != null) {
                throw new IllegalArgumentException("The managed arena region already exists. Remove or rename the conflicting region first.");
            }
            RegionOperationResult created = SimpleServerUtilities.REGIONS.create(
                    regionName, selection.getDimension(), selection.getPoint1(), selection.getPoint2());
            if (!created.isSuccess()) throw new IllegalArgumentException(regionFailure(created));
            regionCreated = true;
            Region region = SimpleServerUtilities.REGIONS.get(regionName);
            if (region == null) throw new IllegalStateException("The arena region was created but could not be loaded.");
            configureManagedRegion(region);
            SimpleServerUtilities.REGIONS.save();

            MinigameDefinition definition = switch (type) {
                case CAPTURE_THE_FLAG -> captureTheFlagDefinition(player, payload, minigameId, regionName, bounds);
                case DOMINATION -> dominationDefinition(player, payload, minigameId, regionName, bounds);
                case KING_OF_THE_HILL -> kingOfTheHillDefinition(player, payload, minigameId, regionName, bounds);
                case BLOCK_PARTY -> blockPartyDefinition(player, payload, minigameId, regionName, bounds);
                default -> spleefDefinition(player, payload, minigameId, regionName, bounds);
            };
            // Persist an intentionally disabled draft before snapshot capture. A crash can
            // therefore never leave an enabled arena without a verified reset source.
            definition.enabled = false;
            definition.arenas.getFirst().resetRegionAfterMatch = false;
            if (!SimpleServerUtilities.MINIGAMES.saveManagedDefinition("", definition)) {
                throw new IllegalStateException("The minigame draft could not be saved.");
            }
            draftCreated = true;

            ServerLevel level = player.level().getServer().getLevel(selection.getDimension());
            if (level == null) throw new IllegalArgumentException("The selected dimension is not loaded.");
            placeInitialPhysicalMarkers(level, definition);
            var capture = SimpleServerUtilities.REGION_SNAPSHOTS.createCaptureJob(level, region);
            MinecraftServer server = player.level().getServer();
            String finalId = minigameId;
            String finalRegionName = regionName;
            SimpleServerUtilities.JOBS.submit(capture, result -> finishCapture(
                    server, player.getUUID(), finalId, finalRegionName, requestId, result.status(), result.error()));
            // Keep the wizard in its awaiting state until the asynchronous snapshot
            // has been verified. Sending an intermediate success response would re-enable
            // the Create button and permit a duplicate request while capture is pending.
        } catch (Exception exception) {
            if (!minigameId.isBlank()) PENDING_IDS.remove(minigameId);
            cleanupFailedDraft(draftCreated ? minigameId : "", regionCreated ? regionName : "");
            send(player, false, message(exception), requestId);
        }
    }

    private static void finishCapture(MinecraftServer server, java.util.UUID actorId, String minigameId,
                                      String regionName, long requestId, SsuJobScheduler.Status status, String error) {
        try {
            ServerPlayer actor = server.getPlayerList().getPlayer(actorId);
            if (status != SsuJobScheduler.Status.COMPLETED
                    || !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(regionName)) {
                cleanupFailedDraft(minigameId, regionName);
                if (actor != null) send(actor, false, "Arena snapshot failed" + suffix(error) + ". Creation was rolled back.", requestId);
                return;
            }
            MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.copy(SimpleServerUtilities.MINIGAMES.definition(minigameId));
            if (definition == null || definition.arenas.isEmpty()) {
                cleanupFailedDraft(minigameId, regionName);
                if (actor != null) send(actor, false, "The minigame draft disappeared during snapshot capture.", requestId);
                return;
            }
            definition.arenas.getFirst().resetRegionAfterMatch = true;
            definition.enabled = false;
            if (!SimpleServerUtilities.MINIGAMES.saveManagedDefinition(minigameId, definition)) {
                throw new IllegalStateException("The verified arena could not be attached to the minigame definition.");
            }
            if (actor != null) {
                RegionCommands.getSelectionManager().clear(actor);
                SimpleServerUtilities.BORDER_VISUALIZATIONS.hideSelection(actor);
                String label = MinigameGameType.parse(definition.gameType).label();
                send(actor, true, label + " arena created and snapshotted. Review the settings, then enable the minigame.", requestId);
                MinigameEditorService.open(actor, minigameId);
            }
        } catch (Exception exception) {
            ServerPlayer actor = server.getPlayerList().getPlayer(actorId);
            cleanupFailedDraft(minigameId, regionName);
            if (actor != null) send(actor, false, message(exception), requestId);
        } finally {
            PENDING_IDS.remove(minigameId);
        }
    }

    private static MinigameDefinition spleefDefinition(ServerPlayer player, MinigameSelectionCreatePayload payload,
                                                        String id, String regionName,
                                                        RegionSelectionSchematicManager.Bounds bounds) {
        MinigameDefinition definition = new MinigameDefinition();
        definition.id = id;
        definition.displayName = payload.displayName().isBlank() ? title(id) : payload.displayName();
        definition.description = "Break the floor beneath the other players. Last player standing wins.";
        definition.iconItem = "minecraft:diamond_shovel";
        definition.gameType = MinigameGameType.SPLEEF.id();
        definition.minPlayers = Math.max(2, payload.minPlayers());
        definition.maxPlayers = Math.max(definition.minPlayers, payload.maxPlayers());
        definition.teamCount = definition.maxPlayers;
        definition.victoryMode = "last_team_standing";
        definition.allowLateJoin = false;
        definition.countdownSeconds = 10;
        definition.matchDurationSeconds = 300;
        definition.postGameSeconds = 8;
        definition.arenas.clear();
        MinigameArenaDefinition arena = new MinigameArenaDefinition();
        arena.id = "arena_1";
        arena.displayName = definition.displayName + " Arena";
        arena.regionId = regionName;
        arena.enabled = true;
        arena.managedRegion = true;
        arena.lobby = MinigameLocation.of(player);
        arena.spectator = MinigameLocation.of(player);
        arena.playFloor = new MinigameAreaBounds(player.level().dimension().identifier().toString(),
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
        arena.teamSpawns.clear();
        int columns = (int) Math.ceil(Math.sqrt(definition.maxPlayers));
        int rows = (int) Math.ceil((double) definition.maxPlayers / columns);
        double usableX = Math.max(1.0D, bounds.maxX() - bounds.minX() - 1.0D);
        double usableZ = Math.max(1.0D, bounds.maxZ() - bounds.minZ() - 1.0D);
        double spawnY = bounds.maxY() + 1.0D;
        for (int index = 0; index < definition.maxPlayers; index++) {
            int column = index % columns;
            int row = index / columns;
            double x = bounds.minX() + 0.5D + usableX * (column + 0.5D) / columns;
            double z = bounds.minZ() + 0.5D + usableZ * (row + 0.5D) / rows;
            arena.teamSpawns.add(new MinigameSpawnPoint(index + 1,
                    new MinigameLocation(player.level().dimension().identifier().toString(), x, spawnY, z, 0.0F, 0.0F)));
        }
        definition.arenas.add(arena);
        definition.normalize();
        return definition;
    }

    private static MinigameDefinition captureTheFlagDefinition(ServerPlayer player, MinigameSelectionCreatePayload payload,
                                                                String id, String regionName,
                                                                RegionSelectionSchematicManager.Bounds bounds) {
        MinigameDefinition definition = new MinigameDefinition();
        definition.id = id;
        definition.displayName = payload.displayName().isBlank() ? title(id) : payload.displayName();
        definition.description = "Take the enemy flag and return it to your own base while defending your team's flag.";
        definition.iconItem = "minecraft:red_banner";
        definition.gameType = MinigameGameType.CAPTURE_THE_FLAG.id();
        definition.minPlayers = Math.max(2, payload.minPlayers());
        definition.maxPlayers = Math.max(definition.minPlayers, payload.maxPlayers());
        definition.teamCount = 2;
        definition.victoryMode = "highest_score";
        definition.allowLateJoin = false;
        definition.countdownSeconds = 10;
        definition.matchDurationSeconds = 600;
        definition.postGameSeconds = 10;
        definition.arenas.clear();

        MinigameArenaDefinition arena = new MinigameArenaDefinition();
        arena.id = "arena_1";
        arena.displayName = definition.displayName + " Arena";
        arena.regionId = regionName;
        arena.enabled = true;
        arena.managedRegion = true;
        arena.lobby = MinigameLocation.of(player);
        arena.spectator = MinigameLocation.of(player);
        arena.teamSpawns.clear();
        arena.flagPoints.clear();

        String dimension = player.level().dimension().identifier().toString();
        double spawnY = Math.max(bounds.minY() + 1.0D, Math.min(player.getY(), bounds.maxY() + 1.0D));
        int sizeX = bounds.maxX() - bounds.minX() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        boolean teamsAlongX = sizeX >= sizeZ;
        if (teamsAlongX) {
            double centerZ = (bounds.minZ() + bounds.maxZ() + 1.0D) / 2.0D;
            double redFlagX = bounds.minX() + 0.5D;
            double blueFlagX = bounds.maxX() + 0.5D;
            double redSpawnX = bounds.minX() + 2.5D;
            double blueSpawnX = bounds.maxX() - 1.5D;
            arena.teamSpawns.add(new MinigameSpawnPoint(1,
                    new MinigameLocation(dimension, redSpawnX, spawnY, centerZ, -90.0F, 0.0F)));
            arena.teamSpawns.add(new MinigameSpawnPoint(2,
                    new MinigameLocation(dimension, blueSpawnX, spawnY, centerZ, 90.0F, 0.0F)));
            arena.flagPoints.add(new MinigameFlagPoint(1,
                    new MinigameLocation(dimension, redFlagX, spawnY, centerZ, -90.0F, 0.0F)));
            arena.flagPoints.add(new MinigameFlagPoint(2,
                    new MinigameLocation(dimension, blueFlagX, spawnY, centerZ, 90.0F, 0.0F)));
        } else {
            double centerX = (bounds.minX() + bounds.maxX() + 1.0D) / 2.0D;
            double redFlagZ = bounds.minZ() + 0.5D;
            double blueFlagZ = bounds.maxZ() + 0.5D;
            double redSpawnZ = bounds.minZ() + 2.5D;
            double blueSpawnZ = bounds.maxZ() - 1.5D;
            arena.teamSpawns.add(new MinigameSpawnPoint(1,
                    new MinigameLocation(dimension, centerX, spawnY, redSpawnZ, 0.0F, 0.0F)));
            arena.teamSpawns.add(new MinigameSpawnPoint(2,
                    new MinigameLocation(dimension, centerX, spawnY, blueSpawnZ, 180.0F, 0.0F)));
            arena.flagPoints.add(new MinigameFlagPoint(1,
                    new MinigameLocation(dimension, centerX, spawnY, redFlagZ, 0.0F, 0.0F)));
            arena.flagPoints.add(new MinigameFlagPoint(2,
                    new MinigameLocation(dimension, centerX, spawnY, blueFlagZ, 180.0F, 0.0F)));
        }
        definition.arenas.add(arena);
        definition.normalize();
        return definition;
    }


    private static MinigameDefinition dominationDefinition(ServerPlayer player, MinigameSelectionCreatePayload payload,
                                                            String id, String regionName,
                                                            RegionSelectionSchematicManager.Bounds bounds) {
        MinigameDefinition definition = new MinigameDefinition();
        definition.id = id;
        definition.displayName = payload.displayName().isBlank() ? title(id) : payload.displayName();
        definition.description = "Capture and hold battlefield nodes to generate resources for your team.";
        definition.iconItem = "minecraft:beacon";
        definition.gameType = MinigameGameType.DOMINATION.id();
        definition.minPlayers = Math.max(2, payload.minPlayers());
        definition.maxPlayers = Math.max(definition.minPlayers, payload.maxPlayers());
        definition.teamCount = 2;
        definition.victoryMode = "highest_score";
        definition.allowLateJoin = false;
        definition.countdownSeconds = 15;
        definition.matchDurationSeconds = 900;
        definition.postGameSeconds = 10;
        definition.arenas.clear();

        MinigameArenaDefinition arena = new MinigameArenaDefinition();
        arena.id = "arena_1";
        arena.displayName = definition.displayName + " Battlefield";
        arena.regionId = regionName;
        arena.enabled = true;
        arena.managedRegion = true;
        arena.lobby = MinigameLocation.of(player);
        arena.spectator = MinigameLocation.of(player);
        arena.teamSpawns.clear();
        arena.flagPoints.clear();
        arena.controlPoints.clear();

        String dimension = player.level().dimension().identifier().toString();
        double y = Math.max(bounds.minY() + 1.0D, Math.min(player.getY(), bounds.maxY() + 1.0D));
        int sizeX = bounds.maxX() - bounds.minX() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        boolean teamsAlongX = sizeX >= sizeZ;
        double centerX = (bounds.minX() + bounds.maxX() + 1.0D) / 2.0D;
        double centerZ = (bounds.minZ() + bounds.maxZ() + 1.0D) / 2.0D;
        if (teamsAlongX) {
            double redX = bounds.minX() + 2.5D;
            double blueX = bounds.maxX() - 1.5D;
            arena.teamSpawns.add(new MinigameSpawnPoint(1,
                    new MinigameLocation(dimension, redX, y, centerZ, -90.0F, 0.0F)));
            arena.teamSpawns.add(new MinigameSpawnPoint(2,
                    new MinigameLocation(dimension, blueX, y, centerZ, 90.0F, 0.0F)));
        } else {
            double redZ = bounds.minZ() + 2.5D;
            double blueZ = bounds.maxZ() - 1.5D;
            arena.teamSpawns.add(new MinigameSpawnPoint(1,
                    new MinigameLocation(dimension, centerX, y, redZ, 0.0F, 0.0F)));
            arena.teamSpawns.add(new MinigameSpawnPoint(2,
                    new MinigameLocation(dimension, centerX, y, blueZ, 180.0F, 0.0F)));
        }

        double minX = bounds.minX() + 2.5D;
        double maxX = bounds.maxX() - 1.5D;
        double minZ = bounds.minZ() + 2.5D;
        double maxZ = bounds.maxZ() - 1.5D;
        arena.controlPoints.add(dominationPoint("farm", "Farm", dimension, minX, y, minZ, centerX, centerZ));
        arena.controlPoints.add(dominationPoint("lumber_mill", "Lumber Mill", dimension, maxX, y, minZ, centerX, centerZ));
        arena.controlPoints.add(dominationPoint("blacksmith", "Blacksmith", dimension, centerX, y, centerZ, centerX, centerZ));
        arena.controlPoints.add(dominationPoint("mine", "Mine", dimension, minX, y, maxZ, centerX, centerZ));
        arena.controlPoints.add(dominationPoint("stables", "Stables", dimension, maxX, y, maxZ, centerX, centerZ));
        definition.arenas.add(arena);
        definition.normalize();
        return definition;
    }

    private static MinigameDefinition kingOfTheHillDefinition(ServerPlayer player,
                                                               MinigameSelectionCreatePayload payload,
                                                               String id, String regionName,
                                                               RegionSelectionSchematicManager.Bounds bounds) {
        MinigameDefinition definition = new MinigameDefinition();
        definition.id = id;
        definition.displayName = payload.displayName().isBlank() ? title(id) : payload.displayName();
        definition.description = "Hold the central hill uncontested to generate points for your team.";
        definition.iconItem = "minecraft:golden_helmet";
        definition.gameType = MinigameGameType.KING_OF_THE_HILL.id();
        definition.minPlayers = Math.max(2, payload.minPlayers());
        definition.maxPlayers = Math.max(definition.minPlayers, payload.maxPlayers());
        definition.teamCount = 2;
        definition.victoryMode = "highest_score";
        definition.allowLateJoin = false;
        definition.countdownSeconds = 10;
        definition.matchDurationSeconds = 600;
        definition.postGameSeconds = 10;
        definition.arenas.clear();

        MinigameArenaDefinition arena = new MinigameArenaDefinition();
        arena.id = "arena_1";
        arena.displayName = definition.displayName + " Arena";
        arena.regionId = regionName;
        arena.enabled = true;
        arena.managedRegion = true;
        arena.lobby = MinigameLocation.of(player);
        arena.spectator = MinigameLocation.of(player);
        arena.teamSpawns.clear();
        String dimension = player.level().dimension().identifier().toString();
        double centerX = (bounds.minX() + bounds.maxX() + 1.0D) / 2.0D;
        double centerZ = (bounds.minZ() + bounds.maxZ() + 1.0D) / 2.0D;
        double y = Math.max(bounds.minY() + 1.0D, Math.min(player.getY(), bounds.maxY() + 1.0D));
        arena.hillCenter = new MinigameLocation(dimension, centerX, y, centerZ, 0.0F, 0.0F);
        int sizeX = bounds.maxX() - bounds.minX() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        if (sizeX >= sizeZ) {
            arena.teamSpawns.add(new MinigameSpawnPoint(1,
                    new MinigameLocation(dimension, bounds.minX() + 1.5D, y, centerZ, -90.0F, 0.0F)));
            arena.teamSpawns.add(new MinigameSpawnPoint(2,
                    new MinigameLocation(dimension, bounds.maxX() - 0.5D, y, centerZ, 90.0F, 0.0F)));
        } else {
            arena.teamSpawns.add(new MinigameSpawnPoint(1,
                    new MinigameLocation(dimension, centerX, y, bounds.minZ() + 1.5D, 0.0F, 0.0F)));
            arena.teamSpawns.add(new MinigameSpawnPoint(2,
                    new MinigameLocation(dimension, centerX, y, bounds.maxZ() - 0.5D, 180.0F, 0.0F)));
        }
        definition.arenas.add(arena);
        definition.normalize();
        return definition;
    }

    private static MinigameDefinition blockPartyDefinition(ServerPlayer player,
                                                            MinigameSelectionCreatePayload payload,
                                                            String id, String regionName,
                                                            RegionSelectionSchematicManager.Bounds bounds) {
        MinigameDefinition definition = new MinigameDefinition();
        definition.id = id;
        definition.displayName = payload.displayName().isBlank() ? title(id) : payload.displayName();
        definition.description = "Find the announced block before the rest of the dance floor disappears.";
        definition.iconItem = "minecraft:lime_concrete";
        definition.gameType = MinigameGameType.BLOCK_PARTY.id();
        definition.minPlayers = Math.max(2, payload.minPlayers());
        definition.maxPlayers = Math.min(32, Math.max(definition.minPlayers, payload.maxPlayers()));
        definition.teamCount = definition.maxPlayers;
        definition.victoryMode = "last_team_standing";
        definition.allowLateJoin = false;
        definition.countdownSeconds = 10;
        definition.matchDurationSeconds = 900;
        definition.postGameSeconds = 8;
        definition.arenas.clear();

        MinigameArenaDefinition arena = new MinigameArenaDefinition();
        arena.id = "arena_1";
        arena.displayName = definition.displayName + " Dance Floor";
        arena.regionId = regionName;
        arena.enabled = true;
        arena.managedRegion = true;
        arena.lobby = MinigameLocation.of(player);
        arena.spectator = MinigameLocation.of(player);
        String dimension = player.level().dimension().identifier().toString();
        int floorY = bounds.minY();
        arena.playFloor = new MinigameAreaBounds(dimension,
                new BlockPos(bounds.minX(), floorY, bounds.minZ()),
                new BlockPos(bounds.maxX(), floorY, bounds.maxZ()));
        arena.teamSpawns.clear();
        int columns = (int) Math.ceil(Math.sqrt(definition.maxPlayers));
        int rows = (int) Math.ceil((double) definition.maxPlayers / columns);
        double usableX = Math.max(1.0D, bounds.maxX() - bounds.minX());
        double usableZ = Math.max(1.0D, bounds.maxZ() - bounds.minZ());
        double spawnY = floorY + 1.0D;
        for (int index = 0; index < definition.maxPlayers; index++) {
            int column = index % columns;
            int row = index / columns;
            double x = bounds.minX() + 0.5D + usableX * (column + 0.5D) / columns;
            double z = bounds.minZ() + 0.5D + usableZ * (row + 0.5D) / rows;
            arena.teamSpawns.add(new MinigameSpawnPoint(index + 1,
                    new MinigameLocation(dimension, x, spawnY, z, 0.0F, 0.0F)));
        }
        definition.arenas.add(arena);
        definition.normalize();
        return definition;
    }

    private static void placeInitialPhysicalMarkers(ServerLevel level, MinigameDefinition definition) {
        if (level == null || definition == null || definition.arenas.isEmpty()) return;
        MinigameArenaDefinition arena = definition.arenas.getFirst();
        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            for (MinigameFlagPoint point : arena.flagPoints) {
                Block block = BuiltInRegistries.BLOCK.getOptional(
                        Identifier.parse(definition.captureTheFlag.flagBlock(point.team))).orElse(null);
                if (block != null) level.setBlockAndUpdate(BlockPos.containing(
                        point.location.x, point.location.y, point.location.z), block.defaultBlockState());
            }
        } else if (type == MinigameGameType.DOMINATION) {
            Block neutral = BuiltInRegistries.BLOCK.getOptional(
                    Identifier.parse(definition.domination.neutralBannerBlock)).orElse(null);
            if (neutral == null) return;
            for (MinigameControlPoint point : arena.controlPoints) {
                level.setBlockAndUpdate(BlockPos.containing(point.location.x, point.location.y, point.location.z),
                        neutral.defaultBlockState());
            }
        }
    }

    private static void configureManagedRegion(Region region) {
        region.setPriority(10_000);
        region.setBorderVisible(false);
        var settings = region.getSettings();
        // Kept locked while idle. MinigameManager grants only each live mode's narrow interactions.
        settings.setAllowBlockBreak(false);
        settings.setAllowBlockPlace(false);
        settings.setAllowInteract(false);
        settings.setAllowPvp(false);
        settings.setAllowExplosions(false);
        settings.setAllowPistons(false);
        settings.setAllowWaterFlow(false);
        settings.setAllowLavaFlow(false);
        settings.setAllowRedstone(false);
        settings.setAllowHoppers(false);
        settings.setAllowFireSpread(false);
    }

    private static void cleanupFailedDraft(String minigameId, String regionName) {
        try {
            if (minigameId != null && !minigameId.isBlank()
                    && SimpleServerUtilities.MINIGAMES.deleteDefinition(minigameId)) {
                return; // Managed region and snapshot are owned and cleaned by deleteDefinition.
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (regionName != null && !regionName.isBlank()) {
                SimpleServerUtilities.REGION_SNAPSHOTS.archiveSnapshot(regionName, "minigame-create-rollback");
                SimpleServerUtilities.REGIONS.delete(regionName);
            }
        } catch (Exception exception) {
            SimpleServerUtilities.LOGGER.error("Failed to fully roll back minigame arena '{}'.", regionName, exception);
        }
    }

    private static String managedRegionName(String id) {
        String normalized = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        String value = "ssu_mg_" + normalized;
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static String regionFailure(RegionOperationResult result) {
        return switch (result.getType()) {
            case NAME_ALREADY_EXISTS -> "The managed arena region already exists.";
            case OVERLAPS_PLAYER_CLAIM -> "The arena overlaps a player claim: " + result.getDetails();
            case INVALID_REGION_OVERLAP -> "The arena overlaps another server region incorrectly: " + result.getDetails();
            case REGION_NOT_FOUND -> "A required region could not be found: " + result.getDetails();
            case SUCCESS -> "The arena region could not be created.";
        };
    }

    private static MinigameControlPoint dominationPoint(String id, String name, String dimension,
                                                             double x, double y, double z,
                                                             double centerX, double centerZ) {
        MinigameControlPoint point = new MinigameControlPoint(id, name,
                new MinigameLocation(dimension, x, y, z, 0.0F, 0.0F));
        double dx = centerX - x;
        double dz = centerZ - z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double offset = length < 0.001D ? 2.5D : Math.min(2.5D, Math.max(1.5D, length * 0.35D));
        double spawnX = length < 0.001D ? x + offset : x + dx / length * offset;
        double spawnZ = length < 0.001D ? z : z + dz / length * offset;
        point.respawn = new MinigameLocation(dimension, spawnX, y, spawnZ, 0.0F, 0.0F);
        return point;
    }

    private static String title(String id) {
        StringBuilder result = new StringBuilder();
        for (String part : id.split("[_\\-.]+")) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.isEmpty() ? "Spleef" : result.toString();
    }

    private static String suffix(String error) { return error == null || error.isBlank() ? "" : ": " + error; }
    private static String message(Throwable error) {
        String value = error == null ? "Minigame creation failed safely." : error.getMessage();
        return value == null || value.isBlank() ? "Minigame creation failed safely." : value;
    }
    private static void send(ServerPlayer player, boolean success, String message, long requestId) {
        PacketDistributor.sendToPlayer(player, new MinigameSelectionCreateResultPayload(success, message, requestId));
        if (!success) player.sendSystemMessage(Component.literal(message));
    }
}
