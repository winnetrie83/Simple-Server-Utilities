package be.winnetrie.mod.simpleserverutilities.settings;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.utilitymining.MiningActivationMode;

/** Persistent player-side UI choices, validated by the server. */
public final class PlayerUiPreferences {

    public static final int CURRENT_SCHEMA = 8;

    private int schema = CURRENT_SCHEMA;
    private String uuid = "";
    private String lastKnownName = "";
    private boolean dashboardHints = true;
    private boolean minimapEnabled = false;
    private int minimapSize = 96;
    private MinimapShape minimapShape = MinimapShape.CIRCLE;
    private MinimapPosition minimapPosition = MinimapPosition.TOP_RIGHT;
    private boolean minimapNorthUp = true;
    private boolean minimapShowClaims = true;
    private boolean minimapShowRegions = true;
    private boolean worldMapShowClaims = true;
    private boolean worldMapShowRegions = true;
    private boolean worldMapShowMarkers = true;
    private boolean minimapShowMarkers = true;
    private boolean minimapShowCalendar;
    private boolean worldMarkersVisible = true;
    private boolean markerBeamsVisible = true;
    private int markerBeamDistance = 128;
    private int mapLiveUpdateRadiusChunks = 8;
    private boolean blockInformationEnabled = true;
    private boolean blockInformationDebugEnabled;
    private boolean mailAutoDeletePlayerAttachments;
    private boolean mailAutoDeleteSystemAttachments;
    private boolean mailAutoDeleteAuctionAttachments;
    private boolean treecapitatorEnabled;
    private MiningActivationMode treecapitatorActivation = MiningActivationMode.SNEAK;
    private int treecapitatorOutlineColor = 0xFF55FF77;
    private int treecapitatorOutlineBrightness = 85;
    private boolean veinminerEnabled;
    private MiningActivationMode veinminerActivation = MiningActivationMode.SNEAK;
    private int veinminerOutlineColor = 0xFF55AAFF;
    private int veinminerOutlineBrightness = 85;

    public PlayerUiPreferences() {
        // Required for Gson.
    }

    public PlayerUiPreferences(UUID playerId, String playerName) {
        setUuid(playerId);
        setLastKnownName(playerName);
    }

    public void normalize() {
        int previousSchema = schema;
        if (previousSchema < 2) {
            // Preserve the pre-dev2 world-map behaviour for existing players.
            worldMapShowClaims = true;
            worldMapShowRegions = true;
        }
        if (previousSchema < 3) {
            // New mining helpers are opt-in for existing players.
            treecapitatorEnabled = false;
            veinminerEnabled = false;
        }
        if (previousSchema < 4) {
            // Block information is enabled personally by default, while the server remains the hard gate.
            blockInformationEnabled = true;
        }
        if (previousSchema < 5) {
            // Technical block/entity details are opt-in and permission-gated.
            blockInformationDebugEnabled = false;
        }
        if (previousSchema < 6) {
            // Personal map markers are visible by default with a conservative beam range.
            worldMapShowMarkers = true;
            minimapShowMarkers = true;
            worldMarkersVisible = true;
            markerBeamsVisible = true;
            markerBeamDistance = 128;
        }
        if (previousSchema < 7) {
            // Keep live terrain refresh local and conservative by default.
            mapLiveUpdateRadiusChunks = 8;
        }
        if (previousSchema < 8) {
            // The day/time HUD line is optional and remains off for existing players.
            minimapShowCalendar = false;
        }
        schema = CURRENT_SCHEMA;
        minimapSize = Math.max(64, Math.min(256, minimapSize));
        if (minimapShape == null) {
            minimapShape = MinimapShape.CIRCLE;
        }
        if (minimapPosition == null) {
            minimapPosition = MinimapPosition.TOP_RIGHT;
        }
        if (treecapitatorActivation == null) {
            treecapitatorActivation = MiningActivationMode.SNEAK;
        }
        if (veinminerActivation == null) {
            veinminerActivation = MiningActivationMode.SNEAK;
        }
        treecapitatorOutlineBrightness = clampPercent(treecapitatorOutlineBrightness);
        veinminerOutlineBrightness = clampPercent(veinminerOutlineBrightness);
        markerBeamDistance = Math.max(16, Math.min(512, markerBeamDistance));
        mapLiveUpdateRadiusChunks = Math.max(1, Math.min(32, mapLiveUpdateRadiusChunks));
        treecapitatorOutlineColor |= 0xFF000000;
        veinminerOutlineColor |= 0xFF000000;
    }

    public String getUuid() {
        return uuid == null ? "" : uuid;
    }

    public void setUuid(UUID playerId) {
        uuid = playerId == null ? "" : playerId.toString();
    }

    public String getLastKnownName() {
        return lastKnownName == null ? "" : lastKnownName;
    }

    public void setLastKnownName(String playerName) {
        lastKnownName = playerName == null ? "" : playerName.trim();
    }

    public boolean isDashboardHints() {
        return dashboardHints;
    }

    public void setDashboardHints(boolean dashboardHints) {
        this.dashboardHints = dashboardHints;
    }

    public boolean isMinimapEnabled() {
        return minimapEnabled;
    }

    public void setMinimapEnabled(boolean minimapEnabled) {
        this.minimapEnabled = minimapEnabled;
    }

    public int getMinimapSize() {
        return minimapSize;
    }

    public void setMinimapSize(int minimapSize) {
        this.minimapSize = Math.max(64, Math.min(256, minimapSize));
    }

    public MinimapShape getMinimapShape() {
        return minimapShape == null ? MinimapShape.CIRCLE : minimapShape;
    }

    public void setMinimapShape(MinimapShape minimapShape) {
        this.minimapShape = minimapShape == null ? MinimapShape.CIRCLE : minimapShape;
    }

    public MinimapPosition getMinimapPosition() {
        return minimapPosition == null ? MinimapPosition.TOP_RIGHT : minimapPosition;
    }

    public void setMinimapPosition(MinimapPosition minimapPosition) {
        this.minimapPosition = minimapPosition == null ? MinimapPosition.TOP_RIGHT : minimapPosition;
    }

    public boolean isMinimapNorthUp() {
        return minimapNorthUp;
    }

    public void setMinimapNorthUp(boolean minimapNorthUp) {
        this.minimapNorthUp = minimapNorthUp;
    }

    public boolean isMinimapShowClaims() {
        return minimapShowClaims;
    }

    public void setMinimapShowClaims(boolean minimapShowClaims) {
        this.minimapShowClaims = minimapShowClaims;
    }

    public boolean isMinimapShowRegions() {
        return minimapShowRegions;
    }

    public void setMinimapShowRegions(boolean minimapShowRegions) {
        this.minimapShowRegions = minimapShowRegions;
    }
    public boolean isWorldMapShowClaims() {
        return worldMapShowClaims;
    }

    public void setWorldMapShowClaims(boolean worldMapShowClaims) {
        this.worldMapShowClaims = worldMapShowClaims;
    }

    public boolean isWorldMapShowRegions() {
        return worldMapShowRegions;
    }

    public void setWorldMapShowRegions(boolean worldMapShowRegions) {
        this.worldMapShowRegions = worldMapShowRegions;
    }

    public boolean isWorldMapShowMarkers() {
        return worldMapShowMarkers;
    }

    public void setWorldMapShowMarkers(boolean value) {
        worldMapShowMarkers = value;
    }

    public boolean isMinimapShowMarkers() {
        return minimapShowMarkers;
    }

    public void setMinimapShowMarkers(boolean value) {
        minimapShowMarkers = value;
    }

    public boolean isMinimapShowCalendar() {
        return minimapShowCalendar;
    }

    public void setMinimapShowCalendar(boolean value) {
        minimapShowCalendar = value;
    }

    public boolean isWorldMarkersVisible() {
        return worldMarkersVisible;
    }

    public void setWorldMarkersVisible(boolean value) {
        worldMarkersVisible = value;
    }

    public boolean isMarkerBeamsVisible() {
        return markerBeamsVisible;
    }

    public void setMarkerBeamsVisible(boolean value) {
        markerBeamsVisible = value;
    }

    public int getMarkerBeamDistance() {
        return Math.max(16, Math.min(512, markerBeamDistance));
    }

    public void setMarkerBeamDistance(int value) {
        markerBeamDistance = Math.max(16, Math.min(512, value));
    }

    public int getMapLiveUpdateRadiusChunks() {
        return Math.max(1, Math.min(32, mapLiveUpdateRadiusChunks));
    }

    public void setMapLiveUpdateRadiusChunks(int value) {
        mapLiveUpdateRadiusChunks = Math.max(1, Math.min(32, value));
    }

    public boolean isBlockInformationEnabled() {
        return blockInformationEnabled;
    }

    public void setBlockInformationEnabled(boolean blockInformationEnabled) {
        this.blockInformationEnabled = blockInformationEnabled;
    }

    public boolean isBlockInformationDebugEnabled() {
        return blockInformationDebugEnabled;
    }

    public void setBlockInformationDebugEnabled(boolean blockInformationDebugEnabled) {
        this.blockInformationDebugEnabled = blockInformationDebugEnabled;
    }

    public boolean isMailAutoDeletePlayerAttachments() {
        return mailAutoDeletePlayerAttachments;
    }

    public void setMailAutoDeletePlayerAttachments(boolean value) {
        mailAutoDeletePlayerAttachments = value;
    }

    public boolean isMailAutoDeleteSystemAttachments() {
        return mailAutoDeleteSystemAttachments;
    }

    public void setMailAutoDeleteSystemAttachments(boolean value) {
        mailAutoDeleteSystemAttachments = value;
    }

    public boolean isMailAutoDeleteAuctionAttachments() {
        return mailAutoDeleteAuctionAttachments;
    }

    public void setMailAutoDeleteAuctionAttachments(boolean value) {
        mailAutoDeleteAuctionAttachments = value;
    }

    public boolean shouldAutoDeleteAttachmentMail(be.winnetrie.mod.simpleserverutilities.mail.MailSource source) {
        return switch (source == null ? be.winnetrie.mod.simpleserverutilities.mail.MailSource.SYSTEM : source) {
            case PLAYER -> mailAutoDeletePlayerAttachments;
            case AUCTION -> mailAutoDeleteAuctionAttachments;
            case SYSTEM, MINIGAME, RECOVERY -> mailAutoDeleteSystemAttachments;
        };
    }

    public boolean isTreecapitatorEnabled() {
        return treecapitatorEnabled;
    }

    public void setTreecapitatorEnabled(boolean value) {
        treecapitatorEnabled = value;
    }

    public MiningActivationMode getTreecapitatorActivation() {
        return treecapitatorActivation == null ? MiningActivationMode.SNEAK : treecapitatorActivation;
    }

    public void setTreecapitatorActivation(MiningActivationMode value) {
        treecapitatorActivation = value == null ? MiningActivationMode.SNEAK : value;
    }

    public int getTreecapitatorOutlineColor() {
        return treecapitatorOutlineColor | 0xFF000000;
    }

    public void setTreecapitatorOutlineColor(int value) {
        treecapitatorOutlineColor = value | 0xFF000000;
    }

    public int getTreecapitatorOutlineBrightness() {
        return clampPercent(treecapitatorOutlineBrightness);
    }

    public void setTreecapitatorOutlineBrightness(int value) {
        treecapitatorOutlineBrightness = clampPercent(value);
    }

    public boolean isVeinminerEnabled() {
        return veinminerEnabled;
    }

    public void setVeinminerEnabled(boolean value) {
        veinminerEnabled = value;
    }

    public MiningActivationMode getVeinminerActivation() {
        return veinminerActivation == null ? MiningActivationMode.SNEAK : veinminerActivation;
    }

    public void setVeinminerActivation(MiningActivationMode value) {
        veinminerActivation = value == null ? MiningActivationMode.SNEAK : value;
    }

    public int getVeinminerOutlineColor() {
        return veinminerOutlineColor | 0xFF000000;
    }

    public void setVeinminerOutlineColor(int value) {
        veinminerOutlineColor = value | 0xFF000000;
    }

    public int getVeinminerOutlineBrightness() {
        return clampPercent(veinminerOutlineBrightness);
    }

    public void setVeinminerOutlineBrightness(int value) {
        veinminerOutlineBrightness = clampPercent(value);
    }

    private static int clampPercent(int value) {
        return Math.max(10, Math.min(100, value));
    }

}
