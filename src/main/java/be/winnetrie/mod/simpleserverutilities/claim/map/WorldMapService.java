package be.winnetrie.mod.simpleserverutilities.claim.map;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Supplies only claim/region metadata; terrain remains entirely client-side. */
public final class WorldMapService {

    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 32;
    private static final int MAX_CENTER_DISTANCE = 128;
    private static final int MAX_CLAIMS = 8192;
    private static final int MAX_REGIONS = 512;

    private WorldMapService() {
    }

    public static void handleRequest(WorldMapRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        boolean allowed = PermissionService.getBoolean(player, PermissionKeys.MINIMAP_USE, true);
        int radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, payload.radius()));
        ChunkPos playerChunk = player.chunkPosition();
        int centerChunkX = clamp(
                payload.centerChunkX(),
                playerChunk.x() - MAX_CENTER_DISTANCE,
                playerChunk.x() + MAX_CENTER_DISTANCE
        );
        int centerChunkZ = clamp(
                payload.centerChunkZ(),
                playerChunk.z() - MAX_CENTER_DISTANCE,
                playerChunk.z() + MAX_CENTER_DISTANCE
        );
        String dimension = player.level().dimension().identifier().toString();

        List<WorldMapDataPayload.ClaimOverlay> claims = allowed
                ? collectClaims(player, dimension, centerChunkX, centerChunkZ, radius)
                : List.of();
        List<WorldMapDataPayload.RegionOverlay> regions = allowed
                ? collectRegions(dimension, centerChunkX, centerChunkZ, radius)
                : List.of();

        var borderSettings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        var preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        PacketDistributor.sendToPlayer(player, new WorldMapDataPayload(
                allowed,
                dimension,
                centerChunkX,
                centerChunkZ,
                radius,
                borderSettings.getStrokeArgb(BorderCategory.OWN_CLAIM),
                borderSettings.getStrokeArgb(BorderCategory.OTHER_CLAIM),
                borderSettings.getStrokeArgb(BorderCategory.SERVER_REGION),
                preferences.isWorldMapShowClaims(),
                preferences.isWorldMapShowRegions(),
                claims,
                regions
        ));
    }

    private static List<WorldMapDataPayload.ClaimOverlay> collectClaims(
            ServerPlayer player,
            String dimension,
            int centerChunkX,
            int centerChunkZ,
            int radius
    ) {
        List<WorldMapDataPayload.ClaimOverlay> result = new ArrayList<>();
        for (var claim : SimpleServerUtilities.PLAYER_CLAIMS.getClaims()) {
            if (!dimension.equals(claim.getDimension())) {
                continue;
            }
            ClaimChunkStatus status = claim.isOwner(player.getUUID())
                    ? ClaimChunkStatus.OWNED_BY_SELF
                    : claim.isTrusted(player.getUUID())
                            ? ClaimChunkStatus.OWNED_BY_TRUSTED
                            : ClaimChunkStatus.OWNED_BY_OTHER;
            for (var chunk : claim.getChunks()) {
                if (Math.abs(chunk.getX() - centerChunkX) > radius
                        || Math.abs(chunk.getZ() - centerChunkZ) > radius) {
                    continue;
                }
                result.add(new WorldMapDataPayload.ClaimOverlay(chunk.getX(), chunk.getZ(), status));
                if (result.size() >= MAX_CLAIMS) {
                    return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<WorldMapDataPayload.RegionOverlay> collectRegions(
            String dimension,
            int centerChunkX,
            int centerChunkZ,
            int radius
    ) {
        int minBlockX = (centerChunkX - radius) << 4;
        int minBlockZ = (centerChunkZ - radius) << 4;
        int maxBlockX = ((centerChunkX + radius + 1) << 4) - 1;
        int maxBlockZ = ((centerChunkZ + radius + 1) << 4) - 1;

        List<WorldMapDataPayload.RegionOverlay> result = new ArrayList<>();
        for (var region : SimpleServerUtilities.REGIONS.getAll()) {
            if (!dimension.equals(region.getDimension().identifier().toString())) {
                continue;
            }
            if (region.getMaxX() < minBlockX || region.getMinX() > maxBlockX
                    || region.getMaxZ() < minBlockZ || region.getMinZ() > maxBlockZ) {
                continue;
            }
            result.add(new WorldMapDataPayload.RegionOverlay(
                    region.getName(),
                    region.getMinX(),
                    region.getMinZ(),
                    region.getMaxX(),
                    region.getMaxZ()
            ));
            if (result.size() >= MAX_REGIONS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
