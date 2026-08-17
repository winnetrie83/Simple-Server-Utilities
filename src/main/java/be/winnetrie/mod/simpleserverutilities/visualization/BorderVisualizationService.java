package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
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
    private static final long REFRESH_INTERVAL_TICKS = 20L;
    private static final int MAX_OVERVIEW_ENTRIES = 1024;

    private final Map<UUID, RegionSelection> activeSelections = new HashMap<>();
    private final Map<UUID, PlayerSyncState> syncStates = new HashMap<>();
    private long claimsRevision;
    private long regionsRevision;
    private long nextRefreshTick;

    public void tick(MinecraftServer server) {
        if (!SsuModuleAccess.active("visualization")) return;
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
        activeSelections.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    public void syncOverview(ServerPlayer player, boolean force) {
        if (!SsuModuleAccess.active("visualization")) {
            clearClientLayers(player);
            syncStates.remove(player.getUUID());
            activeSelections.remove(player.getUUID());
            return;
        }
        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        String dimension = player.level().dimension().identifier().toString();
        ChunkPos chunk = player.chunkPosition();
        long settingsRevision = SimpleServerUtilities.BORDER_SETTINGS.revision();
        PlayerSyncState previous = syncStates.get(player.getUUID());
        boolean showClaims = canShowClaimBorders(player, preferences);
        boolean showRegions = canShowRegionBorders(player, preferences);

        boolean changed = force
                || previous == null
                || previous.chunkX() != chunk.x()
                || previous.chunkZ() != chunk.z()
                || !previous.dimension().equals(dimension)
                || previous.claimsRevision() != claimsRevision
                || previous.regionsRevision() != regionsRevision
                || previous.settingsRevision() != settingsRevision
                || previous.claimsVisible() != showClaims
                || previous.regionsVisible() != showRegions;

        if (!changed) {
            return;
        }

        if (showClaims) {
            sendClaimOverview(player);
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.CLAIM_FOCUS));
        } else {
            if (previous == null || previous.claimsVisible()) {
                PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.CLAIM));
            }
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.CLAIM_FOCUS));
        }

        if (showRegions) {
            sendRegionOverview(player);
            // Dev10.2 retires the former per-player pin layer. Clear it on every
            // effective sync so stale clients cannot keep an old focused copy.
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.REGION_FOCUS));
        } else {
            if (previous == null || previous.regionsVisible()) {
                PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.REGION));
            }
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.REGION_FOCUS));
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(BorderLayer.SELECTION));
        }

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
        setClaimVisible(player, claim, true);
    }

    public void hideClaim(ServerPlayer player, PlayerClaim claim) {
        setClaimVisible(player, claim, false);
    }

    public void hideClaim(ServerPlayer player) {
        SimpleServerUtilities.BORDER_SETTINGS.clearVisibleClaims(player.getUUID());
        syncOverview(player, true);
    }

    public void setClaimVisible(ServerPlayer player, PlayerClaim claim, boolean visible) {
        if (!SsuModuleAccess.active("claims") || claim == null || !claim.isOwner(player.getUUID())) return;
        SimpleServerUtilities.BORDER_SETTINGS.setClaimVisible(player.getUUID(), claim.getId(), visible);
        syncOverview(player, true);
    }

    public boolean isClaimShown(ServerPlayer player, PlayerClaim claim) {
        return claim != null && SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID())
                .isClaimVisible(claim.getId());
    }

    public void refreshShownClaim(ServerPlayer player) {
        markClaimsChanged();
        syncOverview(player, true);
    }

    public void showRegion(ServerPlayer player, Region region) {
        if (!SsuModuleAccess.active("regions")) return;
        if (region == null) {
            return;
        }
        SimpleServerUtilities.REGIONS.setBorderVisible(region.getName(), true);
        refreshAll(player.level().getServer());
    }

    public void hideRegion(ServerPlayer player, String regionName) {
        if (!SsuModuleAccess.active("regions")) return;
        SimpleServerUtilities.REGIONS.setBorderVisible(regionName, false);
        refreshAll(player.level().getServer());
    }

    public void hideRegion(ServerPlayer player) {
        if (!SsuModuleAccess.active("regions")) return;
        SimpleServerUtilities.REGIONS.setAllBordersVisible(false);
        refreshAll(player.level().getServer());
    }

    public boolean isRegionShown(ServerPlayer player, String regionName) {
        if (!SsuModuleAccess.active("regions")) return false;
        Region region = SimpleServerUtilities.REGIONS.get(regionName);
        return region != null && region.isBorderVisible();
    }

    public Set<String> shownRegions(ServerPlayer player) {
        if (!SsuModuleAccess.active("regions")) return Set.of();
        Set<String> visible = new HashSet<>();
        for (Region region : SimpleServerUtilities.REGIONS.getAll()) {
            if (region.isBorderVisible()) {
                visible.add(region.getName());
            }
        }
        return Set.copyOf(visible);
    }

    public void refreshShownRegion(ServerPlayer player) {
        markRegionsChanged();
        syncOverview(player, true);
    }

    public void showSelection(ServerPlayer player, RegionSelection selection) {
        if (!SsuModuleAccess.active("regions")) { hideSelection(player); return; }
        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        if (!canShowRegionBorders(player, preferences)
                || !RegionPolicy.canVisualizeRegions(player)
                || !selection.isComplete()) {
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
                settings.getRegionRenderDistanceBlocks(),
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

            RegionSelection selection = activeSelections.get(player.getUUID());
            if (selection != null) {
                showSelection(player, selection);
            }
        }
    }

    public void clearAllClients(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) clearClientLayers(player);
    }

    public void clearPlayer(ServerPlayer player) {
        if (player == null) return;
        clearClientLayers(player);
        activeSelections.remove(player.getUUID());
        syncStates.remove(player.getUUID());
    }

    private static void clearClientLayers(ServerPlayer player) {
        for (BorderLayer layer : BorderLayer.values()) {
            PacketDistributor.sendToPlayer(player, BorderVisualizationPayload.clear(layer));
        }
    }

    public void clear() {
        activeSelections.clear();
        syncStates.clear();
    }

    private void sendClaimOverview(ServerPlayer player) {
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        int radius = (settings.getClaimRenderDistanceBlocks() + 15) / 16;
        int centerX = player.chunkPosition().x();
        int centerZ = player.chunkPosition().z();
        String dimension = player.level().dimension().identifier().toString();
        List<BorderVisualizationPayload.Entry> entries = new ArrayList<>();

        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        for (PlayerClaim claim : SimpleServerUtilities.PLAYER_CLAIMS.getClaims()) {
            boolean ownClaim = claim.isOwner(player.getUUID());
            if (!dimension.equals(claim.getDimension())
                    || !hasChunkInRange(claim, centerX, centerZ, radius)
                    || (ownClaim && !preferences.isClaimVisible(claim.getId()))
                    || (!ownClaim && !preferences.isShowOtherClaims())) {
                continue;
            }

            BorderCategory category = ownClaim ? BorderCategory.OWN_CLAIM : BorderCategory.OTHER_CLAIM;
            entries.add(claimEntry(claim, claim.getChunks(), category));
            if (entries.size() >= MAX_OVERVIEW_ENTRIES) {
                break;
            }
        }

        PacketDistributor.sendToPlayer(player, claimPayload(BorderLayer.CLAIM, dimension, entries));
    }

    private static boolean canShowClaimBorders(
            ServerPlayer player,
            PlayerBorderPreferences preferences
    ) {
        return serverAllowsClaimBorders(player) && preferences.isClaimBordersVisible();
    }

    private static boolean canShowRegionBorders(
            ServerPlayer player,
            PlayerBorderPreferences preferences
    ) {
        return serverAllowsRegionBorders(player) && preferences.isRegionBordersVisible();
    }

    private static boolean serverAllowsClaimBorders(ServerPlayer player) {
        return SsuModuleAccess.active("claims")
                && PermissionService.getBooleanWithoutOperatorBypass(
                        player, PermissionKeys.BORDER_CLAIMS_VIEW, true);
    }

    private static boolean serverAllowsRegionBorders(ServerPlayer player) {
        return SsuModuleAccess.active("regions")
                && PermissionService.getBooleanWithoutOperatorBypass(
                        player, PermissionKeys.BORDER_REGIONS_VIEW, true);
    }

    private void sendRegionOverview(ServerPlayer player) {
        BorderVisualizationSettings settings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        double range = settings.getRegionRenderDistanceBlocks();
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
            if (!region.isBorderVisible() || distanceSquared2D(region, x, z) > rangeSquared) {
                continue;
            }
            entries.add(regionEntry(region, BorderCategory.SERVER_REGION));
            if (entries.size() >= MAX_OVERVIEW_ENTRIES) {
                break;
            }
        }

        PacketDistributor.sendToPlayer(player, regionPayload(BorderLayer.REGION, dimension, entries));
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
                SimpleServerUtilities.BORDER_SETTINGS.settings().getClaimRenderDistanceBlocks(),
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
                SimpleServerUtilities.BORDER_SETTINGS.settings().getRegionRenderDistanceBlocks(),
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
