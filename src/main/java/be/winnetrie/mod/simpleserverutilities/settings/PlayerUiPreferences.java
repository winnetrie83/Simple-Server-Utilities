package be.winnetrie.mod.simpleserverutilities.settings;

import java.util.UUID;

/** Persistent player-side UI choices, validated by the server. */
public final class PlayerUiPreferences {

    public static final int CURRENT_SCHEMA = 2;

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
    private boolean mailAutoDeletePlayerAttachments;
    private boolean mailAutoDeleteSystemAttachments;
    private boolean mailAutoDeleteAuctionAttachments;

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
            case SYSTEM, RECOVERY -> mailAutoDeleteSystemAttachments;
        };
    }

}
