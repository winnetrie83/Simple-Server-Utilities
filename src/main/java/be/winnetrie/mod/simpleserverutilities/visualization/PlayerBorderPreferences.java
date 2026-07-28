package be.winnetrie.mod.simpleserverutilities.visualization;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PlayerBorderPreferences {

    private int schemaVersion = 2;
    private UUID player;
    private boolean claimBordersVisible;
    private boolean regionBordersVisible;
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
        schemaVersion = Math.max(schemaVersion, 2);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    private void ensureCollections() {
        if (pinnedRegions == null) {
            pinnedRegions = new LinkedHashSet<>();
        }
    }

    private static String normalize(String rawName) {
        return rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
    }
}
