package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlayerBorderPreferences {

    private int schemaVersion = 3;
    private UUID player;
    private boolean claimBordersVisible;
    private boolean showOtherClaims;
    private boolean regionBordersVisible;
    private Set<String> visibleClaims = new LinkedHashSet<>();
    private Set<String> pinnedRegions = new LinkedHashSet<>();

    public PlayerBorderPreferences() {
    }

    public PlayerBorderPreferences(UUID player) {
        this.player = player;
    }

    public UUID getPlayer() {
        return player;
    }

    public boolean isClaimBordersVisible() {
        return claimBordersVisible;
    }

    public void setClaimBordersVisible(boolean claimBordersVisible) {
        this.claimBordersVisible = claimBordersVisible;
    }

    public boolean isShowOtherClaims() {
        return showOtherClaims;
    }

    public void setShowOtherClaims(boolean showOtherClaims) {
        this.showOtherClaims = showOtherClaims;
    }

    public boolean isClaimVisible(UUID claimId) {
        ensureCollections();
        return claimId != null && visibleClaims.contains(claimId.toString());
    }

    public boolean setClaimVisible(UUID claimId, boolean visible) {
        ensureCollections();
        if (claimId == null) return false;
        return visible ? visibleClaims.add(claimId.toString()) : visibleClaims.remove(claimId.toString());
    }

    public boolean clearVisibleClaims() {
        ensureCollections();
        if (visibleClaims.isEmpty()) return false;
        visibleClaims.clear();
        return true;
    }

    public Set<String> getVisibleClaims() {
        ensureCollections();
        return Set.copyOf(visibleClaims);
    }

    public boolean isRegionBordersVisible() {
        return regionBordersVisible;
    }

    public void setRegionBordersVisible(boolean regionBordersVisible) {
        this.regionBordersVisible = regionBordersVisible;
    }

    public Set<String> getPinnedRegions() {
        ensureCollections();
        return Set.copyOf(pinnedRegions);
    }

    public boolean isRegionPinned(String rawName) {
        ensureCollections();
        return pinnedRegions.contains(normalize(rawName));
    }

    public boolean pinRegion(String rawName) {
        ensureCollections();
        return pinnedRegions.add(normalize(rawName));
    }

    public boolean unpinRegion(String rawName) {
        ensureCollections();
        return pinnedRegions.remove(normalize(rawName));
    }

    public boolean clearPinnedRegions() {
        ensureCollections();
        if (pinnedRegions.isEmpty()) {
            return false;
        }
        pinnedRegions.clear();
        return true;
    }

    public void ensureDefaults() {
        ensureCollections();
        schemaVersion = Math.max(schemaVersion, 3);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    private void ensureCollections() {
        if (visibleClaims == null) {
            visibleClaims = new LinkedHashSet<>();
        }
        if (pinnedRegions == null) {
            pinnedRegions = new LinkedHashSet<>();
        }
    }

    private static String normalize(String rawName) {
        return rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
    }
}
