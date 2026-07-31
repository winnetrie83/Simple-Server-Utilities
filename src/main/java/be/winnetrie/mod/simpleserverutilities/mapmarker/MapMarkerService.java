package be.winnetrie.mod.simpleserverutilities.mapmarker;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Validation, mutation and synchronization for personal markers. */
public final class MapMarkerService {
    private MapMarkerService() {
    }

    public static void handleAction(MapMarkerActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        SimpleServerUtilities.MAP_MARKERS.ensurePlayer(player);

        MapMarkerManager.Result result;
        try {
            result = switch (payload.action()) {
                case "create" -> create(player, payload);
                case "update" -> update(player, payload);
                case "delete" -> SimpleServerUtilities.MAP_MARKERS.delete(player.getUUID(), payload.markerId());
                default -> new MapMarkerManager.Result(false, null, "Unknown marker action.");
            };
        } catch (Exception e) {
            SimpleServerUtilities.LOGGER.error("Map marker action failed for {}", player.getName().getString(), e);
            result = new MapMarkerManager.Result(false, null, "The marker action failed safely.");
        }

        if (result.success()) {
            SimpleServerUtilities.MAP_MARKERS.save();
            sync(player);
        }
        PacketDistributor.sendToPlayer(player, new MapMarkerActionResultPayload(result.success(), result.message()));
    }

    public static void sync(ServerPlayer player) {
        SimpleServerUtilities.MAP_MARKERS.ensurePlayer(player);
        var preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        var entries = SimpleServerUtilities.MAP_MARKERS.markers(player.getUUID()).stream()
                .map(marker -> new MapMarkerSyncPayload.Entry(
                        marker.id(), marker.name(), marker.dimension(), marker.x(), marker.y(), marker.z(), marker.colorArgb()))
                .toList();
        PacketDistributor.sendToPlayer(player, new MapMarkerSyncPayload(
                preferences.isWorldMapShowMarkers(),
                preferences.isWorldMarkersVisible(),
                preferences.isMinimapShowMarkers(),
                preferences.isMarkerBeamsVisible(),
                preferences.getMarkerBeamDistance(),
                entries));
    }

    private static MapMarkerManager.Result create(ServerPlayer player, MapMarkerActionPayload payload) {
        String dimension = currentDimension(player);
        if (!payload.dimension().isBlank() && !dimension.equals(payload.dimension())) {
            return new MapMarkerManager.Result(false, null, "Markers can only be created in your current dimension.");
        }
        Coordinates coordinates = coordinates(player, dimension, payload.x(), payload.y(), payload.z(), payload.resolveSurfaceHeight());
        if (coordinates == null) return new MapMarkerManager.Result(false, null, "Invalid marker coordinates.");
        return SimpleServerUtilities.MAP_MARKERS.create(
                player.getUUID(), validatedName(payload.name()), dimension,
                coordinates.x(), coordinates.y(), coordinates.z(), payload.colorArgb());
    }

    private static MapMarkerManager.Result update(ServerPlayer player, MapMarkerActionPayload payload) {
        MapMarker marker = SimpleServerUtilities.MAP_MARKERS.marker(player.getUUID(), payload.markerId());
        if (marker == null) return new MapMarkerManager.Result(false, null, "Marker not found.");
        if (!marker.dimension().equals(payload.dimension()) && !payload.dimension().isBlank()) {
            return new MapMarkerManager.Result(false, null, "A marker cannot be moved to another dimension.");
        }
        if (!marker.dimension().equals(currentDimension(player)) && payload.resolveSurfaceHeight()) {
            return new MapMarkerManager.Result(false, null, "Automatic height requires the marker dimension to be active.");
        }
        Coordinates coordinates = coordinates(player, marker.dimension(), payload.x(), payload.y(), payload.z(), payload.resolveSurfaceHeight());
        if (coordinates == null) return new MapMarkerManager.Result(false, null, "Invalid marker coordinates.");
        return SimpleServerUtilities.MAP_MARKERS.update(
                player.getUUID(), marker.id(), validatedName(payload.name()),
                coordinates.x(), coordinates.y(), coordinates.z(), payload.colorArgb());
    }

    private static Coordinates coordinates(
            ServerPlayer player,
            String dimension,
            int x,
            int suppliedY,
            int z,
            boolean resolveSurface
    ) {
        if (Math.abs((long) x) > 30_000_000L || Math.abs((long) z) > 30_000_000L) return null;
        ServerLevel level = level(player, dimension);
        if (level == null) return null;
        int y = suppliedY;
        BlockPos check = new BlockPos(x, Math.max(level.getMinY(), Math.min(level.getMaxY(), suppliedY)), z);
        if (resolveSurface && level.hasChunkAt(check)) {
            // Level#getHeight already returns the first free block above WORLD_SURFACE.
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        }
        y = Math.max(level.getMinY(), Math.min(level.getMaxY(), y));
        return new Coordinates(x, y, z);
    }

    private static ServerLevel level(ServerPlayer player, String dimension) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
            return player.level().getServer().getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String validatedName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) return "Marker";
        return value.length() <= 40 ? value : value.substring(0, 40);
    }

    private static String currentDimension(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    private record Coordinates(int x, int y, int z) {
    }
}
