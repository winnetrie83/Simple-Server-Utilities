package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimChunk;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.policy.RegionPolicy;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public class BorderVisualizationService {

    private static final float CLAIM_STROKE_WIDTH = 2.5F;
    private static final float REGION_STROKE_WIDTH = 3.5F;
    private static final float SELECTION_STROKE_WIDTH = 3.0F;
    private static final long REFRESH_INTERVAL_TICKS = 10L;
    private static final int MAX_OVERVIEW_ENTRIES = 1024;

    private final Map<UUID, UUID> focusedClaims = new HashMap<>();
    private final Map<UUID, RegionSelection> activeSelections = new HashMap<>();
    private final Map<UUID, PlayerSyncState> syncStates = new HashMap<>();
    private long claimsRevision;
    private long regionsRevision;
    private long nextRefreshTick;

    public void tick(MinecraftServer server) {
        if (server.getTickCount() < nextRefreshTick) {
            return;
        }
        nextRefreshTick = server.getTickCount() + REFRESH_INTERVAL_TICKS;

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            syncOverview(player, false);
        }
        syncStates.keySet().removeIf(uuid -> !online.contains(uuid));
        focusedClaims.keySet().removeIf(uuid -> !online.contains(uuid));
        activeSelections.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    public void syncOverview(ServerPlayer player, boolean force) {
        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        String dimension = player.level().dimension().identifier().toString();
        ChunkPos chunk = player.chunkPosition();
        long settingsRevision = SimpleServerUtilities.BORDER_SETTINGS.revision();
        PlayerSyncState previous = syncStates.get(player.getUUID());

        boolean changed = force
                || previous == null
                || previous.chunkX() != chunk.x()
                || previous.chunkZ() != chunk.z()
                || !previous.dimension().equals(dimension)
                || previous.claimsRevision() != claimsRevision
                || previous.regionsRevision() != regionsRevision
                || previous.settingsRevision() != settingsRevision;

        if (!changed) {
            return;
        }

        boolean showClaims = preferences.isClaimBordersVisible()
                && PermissionService.getBoolean(player, PermissionKeys.BORDER_CLAIMS_VIEW, true);
        boolean showRegions = preferences.isRegionBordersVisible()
                && PermissionService.getBoolean(player, PermissionKeys.BORDER_REGIONS_VIEW, true);

        if (showClaims) {
            sendClaimOverview(player);
        } else if (previous == null || previous.claimsVisible()) {
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.CLAIM));
        }

        if (showRegions) {
            sendRegionOverview(player);
        } else if (previous == null || previous.regionsVisible()) {
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.REGION));
        }

        sendPinnedRegions(player);

        syncStates.put(player.getUUID(), new PlayerSyncState(
                dimension,
                chunk.x(),
                chunk.z(),
                claimsRevision,
                regionsRevision,
                settingsRevision,
                showClaims,
                showRegions
        ));
    }

    public void showClaim(ServerPlayer player, PlayerClaim claim) {
        focusedClaims.put(player.getUUID(), claim.getId());
        BorderCategory category = claim.isOwner(player.getUUID())
                ? BorderCategory.OWN_CLAIM
                : BorderCategory.OTHER_CLAIM;
        PacketDistributor.sendToPlayer(player, claimPayload(
                BorderLayer.CLAIM_FOCUS,
                claim.getDimension(),
                List.of(claimEntry(claim, claim.getChunks(), category))
        ));
    }

    public void hideClaim(ServerPlayer player) {
        focusedClaims.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.CLAIM_FOCUS));
    }

    public void refreshShownClaim(ServerPlayer player) {
        markClaimsChanged();
        UUID claimId = focusedClaims.get(player.getUUID());
        if (claimId == null) {
            syncOverview(player, true);
            return;
        }

        PlayerClaim claim = findClaim(claimId);
        if (claim == null) {
            hideClaim(player);
        } else {
            showClaim(player, claim);
        }
        syncOverview(player, true);
    }

    public void showRegion(ServerPlayer player, Region region) {
        SimpleServerUtilities.BORDER_SETTINGS.pinRegion(player.getUUID(), region.getName());
        sendPinnedRegions(player);
    }

    public void hideRegion(ServerPlayer player, String regionName) {
        SimpleServerUtilities.BORDER_SETTINGS.unpinRegion(player.getUUID(), regionName);
        sendPinnedRegions(player);
    }

    public void hideRegion(ServerPlayer player) {
        SimpleServerUtilities.BORDER_SETTINGS.clearPinnedRegions(player.getUUID());
        PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.REGION_FOCUS));
    }

    public boolean isRegionShown(ServerPlayer player, String regionName) {
        return SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID()).isRegionPinned(regionName);
    }

    public Set<String> shownRegions(ServerPlayer player) {
        return SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID()).getPinnedRegions();
    }

    public void refreshShownRegion(ServerPlayer player) {
        markRegionsChanged();
        sendPinnedRegions(player);
        syncOverview(player, true);
    }

    public void showSelection(ServerPlayer player, RegionSelection selection) {
        if (!RegionPolicy.canVisualizeRegions(player) || !selection.isComplete()) {
            hideSelection(player);
            return;
        }

        int minX = Math.min(selection.getPoint1().getX(), selection.getPoint2().getX());
        int minY = Math.min(selection.getPoint1().getY(), selection.getPoint2().getY());
        int minZ = Math.min(selection.getPoint1().getZ(), selection.getPoint2().getZ());
        int maxX = Math.max(selection.getPoint1().getX(), selection.getPoint2().getX());
        int maxY = Math.max(selection.getPoint1().getY(), selection.getPoint2().getY());
        int maxZ = Math.max(selection.getPoint1().getZ(), selection.getPoint2().getZ());

        activeSelections.put(player.getUUID(), selection);
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        BorderVisualizationPayload.Entry entry = new BorderVisualizationPayload.Entry(
                BorderCategory.SELECTION,
                "Region selection",
                settings.getStrokeArgb(BorderCategory.SELECTION),
                settings.getFillArgb(BorderCategory.SELECTION),
                SELECTION_STROKE_WIDTH,
                true,
                List.of(new BorderVisualizationPayload.Box(minX, minY, minZ, maxX, maxY, maxZ)),
                List.of()
        );

        PacketDistributor.sendToPlayer(player, new BorderVisualizationPayload(
                BorderLayer.SELECTION,
                true,
                selection.getDimension().identifier().toString(),
                settings.getClaimVerticalRange(),
                List.of(entry)
        ));
    }

    public void hideSelection(ServerPlayer player) {
        activeSelections.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.SELECTION));
    }

    public void markClaimsChanged() {
        claimsRevision++;
    }

    public void markRegionsChanged() {
        regionsRevision++;
    }

    public void refreshAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncOverview(player, true);

            UUID claimId = focusedClaims.get(player.getUUID());
            if (claimId != null) {
                PlayerClaim claim = findClaim(claimId);
                if (claim == null) {
                    hideClaim(player);
                } else {
                    showClaim(player, claim);
                }
            }

            sendPinnedRegions(player);

            RegionSelection selection = activeSelections.get(player.getUUID());
            if (selection != null) {
                showSelection(player, selection);
            }
        }
    }

    public void clearPlayer(ServerPlayer player) {
        focusedClaims.remove(player.getUUID());
        activeSelections.remove(player.getUUID());
        syncStates.remove(player.getUUID());
    }

    public void clear() {
        focusedClaims.clear();
        activeSelections.clear();
        syncStates.clear();
    }

    private void sendClaimOverview(ServerPlayer player) {
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        int radius = settings.getViewDistanceChunks();
        int centerX = player.chunkPosition().x();
        int centerZ = player.chunkPosition().z();
        String dimension = player.level().dimension().identifier().toString();
        List<BorderVisualizationPayload.Entry> entries = new ArrayList<>();

        for (PlayerClaim claim : SimpleServerUtilities.PLAYER_CLAIMS.getClaims()) {
            if (!dimension.equals(claim.getDimension()) || !hasChunkInRange(claim, centerX, centerZ, radius)) {
                continue;
            }

            BorderCategory category = claim.isOwner(player.getUUID())
                    ? BorderCategory.OWN_CLAIM
                    : BorderCategory.OTHER_CLAIM;
            entries.add(claimEntry(claim, claim.getChunks(), category));
            if (entries.size() >= MAX_OVERVIEW_ENTRIES) {
                break;
            }
        }

        PacketDistributor.sendToPlayer(player, claimPayload(BorderLayer.CLAIM, dimension, entries));
    }

    private void sendRegionOverview(ServerPlayer player) {
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        double range = settings.getViewDistanceChunks() * 16.0;
        double rangeSquared = range * range;
        double x = player.getX();
        double z = player.getZ();
        String dimension = player.level().dimension().identifier().toString();
        List<BorderVisualizationPayload.Entry> entries = new ArrayList<>();

        int minX = (int) Math.floor(x - range);
        int minZ = (int) Math.floor(z - range);
        int maxX = (int) Math.ceil(x + range);
        int maxZ = (int) Math.ceil(z + range);

        for (Region region : SimpleServerUtilities.REGIONS.getIntersecting2D(
                player.level().dimension(), minX, minZ, maxX, maxZ)) {
            if (distanceSquared2D(region, x, z) > rangeSquared) {
                continue;
            }
            entries.add(regionEntry(region, BorderCategory.SERVER_REGION));
            if (entries.size() >= MAX_OVERVIEW_ENTRIES) {
                break;
            }
        }

        PacketDistributor.sendToPlayer(player, regionPayload(BorderLayer.REGION, dimension, entries));
    }

    private void sendPinnedRegions(ServerPlayer player) {
        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        String dimension = player.level().dimension().identifier().toString();
        List<BorderVisualizationPayload.Entry> entries = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String regionName : preferences.getPinnedRegions()) {
            Region region = SimpleServerUtilities.REGIONS.get(regionName);
            if (region == null) {
                missing.add(regionName);
                continue;
            }
            if (!dimension.equals(region.getDimension().identifier().toString())) {
                continue;
            }
            entries.add(regionEntry(region, BorderCategory.SERVER_REGION));
            if (entries.size() >= MAX_OVERVIEW_ENTRIES) {
                break;
            }
        }

        for (String missingName : missing) {
            SimpleServerUtilities.BORDER_SETTINGS.unpinRegion(player.getUUID(), missingName);
        }

        if (entries.isEmpty()) {
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.REGION_FOCUS));
        } else {
            PacketDistributor.sendToPlayer(player, regionPayload(BorderLayer.REGION_FOCUS, dimension, entries));
        }
    }

    private BorderVisualizationPayload claimPayload(
            BorderLayer layer,
            String dimension,
            List<BorderVisualizationPayload.Entry> entries
    ) {
        return new BorderVisualizationPayload(
                layer,
                true,
                dimension,
                SimpleServerUtilities.BORDER_SETTINGS.settings().getClaimVerticalRange(),
                entries
        );
    }

    private BorderVisualizationPayload regionPayload(
            BorderLayer layer,
            String dimension,
            List<BorderVisualizationPayload.Entry> entries
    ) {
        return new BorderVisualizationPayload(
                layer,
                true,
                dimension,
                SimpleServerUtilities.BORDER_SETTINGS.settings().getClaimVerticalRange(),
                entries
        );
    }

    private BorderVisualizationPayload.Entry claimEntry(
            PlayerClaim claim,
            Set<ClaimChunk> contourChunks,
            BorderCategory category
    ) {
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        List<BorderVisualizationPayload.Edge> edges = ClaimBorderGeometry.buildOuterEdges(contourChunks)
                .stream()
                .limit(8_192)
                .map(edge -> new BorderVisualizationPayload.Edge(edge.x1(), edge.z1(), edge.x2(), edge.z2()))
                .toList();

        return new BorderVisualizationPayload.Entry(
                category,
                claim.getDisplayName(),
                settings.getStrokeArgb(category),
                settings.getFillArgb(category),
                CLAIM_STROKE_WIDTH,
                false,
                List.of(),
                edges
        );
    }

    private BorderVisualizationPayload.Entry regionEntry(Region region, BorderCategory category) {
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        return new BorderVisualizationPayload.Entry(
                category,
                region.getName(),
                settings.getStrokeArgb(category),
                settings.getFillArgb(category),
                REGION_STROKE_WIDTH,
                true,
                List.of(new BorderVisualizationPayload.Box(
                        region.getMinX(),
                        region.getMinY(),
                        region.getMinZ(),
                        region.getMaxX(),
                        region.getMaxY(),
                        region.getMaxZ()
                )),
                List.of()
        );
    }

    private PlayerClaim findClaim(UUID claimId) {
        return SimpleServerUtilities.PLAYER_CLAIMS.getClaims()
                .stream()
                .filter(candidate -> candidate.getId().equals(claimId))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasChunkInRange(PlayerClaim claim, int centerX, int centerZ, int radius) {
        for (ClaimChunk chunk : claim.getChunks()) {
            if (Math.abs(chunk.getX() - centerX) <= radius
                    && Math.abs(chunk.getZ() - centerZ) <= radius) {
                return true;
            }
        }
        return false;
    }

    private static double distanceSquared2D(Region region, double x, double z) {
        double dx = axisDistance(x, region.getMinX(), region.getMaxX() + 1.0);
        double dz = axisDistance(z, region.getMinZ(), region.getMaxZ() + 1.0);
        return dx * dx + dz * dz;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0;
    }

    private record PlayerSyncState(
            String dimension,
            int chunkX,
            int chunkZ,
            long claimsRevision,
            long regionsRevision,
            long settingsRevision,
            boolean claimsVisible,
            boolean regionsVisible
    ) {
    }
}
