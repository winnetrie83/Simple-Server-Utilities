package be.winnetrie.mod.simpleserverutilities.claim.map;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Creates compact, server-authoritative minimap overlays around the requesting player. */
public final class MinimapService {

    private static final int CHUNK_RADIUS = 7;
    private static final int MAX_CLAIMS = 512;
    private static final int MAX_REGIONS = 256;

    private MinimapService() {
    }

    public static void handleRequest(MinimapRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            send(player);
        }
    }

    public static void send(ServerPlayer player) {
        var preferences = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        boolean allowed = PermissionService.getBoolean(player, PermissionKeys.MINIMAP_USE, true);
        boolean enabled = allowed && preferences.isMinimapEnabled();
        String dimension = player.level().dimension().identifier().toString();
        int centerChunkX = player.chunkPosition().x();
        int centerChunkZ = player.chunkPosition().z();

        List<MinimapDataPayload.ClaimOverlay> claims = enabled && preferences.isMinimapShowClaims()
                ? collectClaims(player, dimension, centerChunkX, centerChunkZ)
                : List.of();
        List<MinimapDataPayload.RegionOverlay> regions = enabled && preferences.isMinimapShowRegions()
                ? collectRegions(dimension, centerChunkX, centerChunkZ)
                : List.of();

        var borderSettings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        PacketDistributor.sendToPlayer(player, new MinimapDataPayload(
                allowed,
                enabled,
                preferences.getMinimapSize(),
                preferences.getMinimapShape().name(),
                preferences.getMinimapPosition().name(),
                preferences.isMinimapNorthUp(),
                preferences.isMinimapShowClaims(),
                preferences.isMinimapShowRegions(),
                preferences.isMinimapShowCalendar(),
                preferences.getMapLiveUpdateRadiusChunks(),
                dimension,
                centerChunkX,
                centerChunkZ,
                borderSettings.getStrokeArgb(BorderCategory.OWN_CLAIM),
                borderSettings.getStrokeArgb(BorderCategory.OTHER_CLAIM),
                borderSettings.getStrokeArgb(BorderCategory.SERVER_REGION),
                claims,
                regions
        ));
    }

    private static List<MinimapDataPayload.ClaimOverlay> collectClaims(
            ServerPlayer player,
            String dimension,
            int centerChunkX,
            int centerChunkZ
    ) {
        List<MinimapDataPayload.ClaimOverlay> result = new ArrayList<>();
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
                if (Math.abs(chunk.getX() - centerChunkX) > CHUNK_RADIUS
                        || Math.abs(chunk.getZ() - centerChunkZ) > CHUNK_RADIUS) {
                    continue;
                }
                result.add(new MinimapDataPayload.ClaimOverlay(chunk.getX(), chunk.getZ(), status, claim.getId()));
                if (result.size() >= MAX_CLAIMS) {
                    return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<MinimapDataPayload.RegionOverlay> collectRegions(
            String dimension,
            int centerChunkX,
            int centerChunkZ
    ) {
        int minBlockX = (centerChunkX - CHUNK_RADIUS) << 4;
        int minBlockZ = (centerChunkZ - CHUNK_RADIUS) << 4;
        int maxBlockX = ((centerChunkX + CHUNK_RADIUS + 1) << 4) - 1;
        int maxBlockZ = ((centerChunkZ + CHUNK_RADIUS + 1) << 4) - 1;

        List<MinimapDataPayload.RegionOverlay> result = new ArrayList<>();
        for (var region : SimpleServerUtilities.REGIONS.getAll()) {
            if (!dimension.equals(region.getDimension().identifier().toString())) {
                continue;
            }
            if (region.getMaxX() < minBlockX || region.getMinX() > maxBlockX
                    || region.getMaxZ() < minBlockZ || region.getMinZ() > maxBlockZ) {
                continue;
            }
            result.add(new MinimapDataPayload.RegionOverlay(
                    region.getName(), region.getMinX(), region.getMinZ(), region.getMaxX(), region.getMaxZ()
            ));
            if (result.size() >= MAX_REGIONS) {
                break;
            }
        }
        return List.copyOf(result);
    }
}
