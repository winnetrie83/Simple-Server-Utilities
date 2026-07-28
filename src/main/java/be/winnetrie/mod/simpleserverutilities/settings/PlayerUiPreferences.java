package be.winnetrie.mod.simpleserverutilities.settings;

import java.util.UUID;

/** Persistent player-side UI choices, validated by the server. */
public final class PlayerUiPreferences {

    public static final int CURRENT_SCHEMA = 1;

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

    public PlayerUiPreferences() {
        // Required for Gson.
    }

    public PlayerUiPreferences(UUID playerId, String playerName) {
        setUuid(playerId);
        setLastKnownName(playerName);
    }

    public void normalize() {
        schema = CURRENT_SCHEMA;
        minimapSize = Math.max(64, Math.min(256, minimapSize));
        if (minimapShape == null) {
            minimapShape = MinimapShape.CIRCLE;
        }
        if (minimapPosition == null) {
            minimapPosition = MinimapPosition.TOP_RIGHT;
        }
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
}
